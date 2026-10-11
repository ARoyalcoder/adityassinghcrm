package com.pawanputra.bos.audit.internal;

import com.pawanputra.bos.audit.api.ActivityOwner;
import com.pawanputra.bos.audit.api.AuditAction;
import com.pawanputra.bos.audit.api.AuditEvent;
import com.pawanputra.bos.audit.api.AuditExclude;
import com.pawanputra.bos.audit.api.AuditRecorder;
import com.pawanputra.bos.audit.api.Audited;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * The reason modules do not write audit code: Hibernate tells this listener about every row it
 * inserts, updates or deletes, and for entities marked {@link Audited} the listener turns that into
 * an audit event with before and after values.
 *
 * <p>How a change is classified:
 * <ul>
 *   <li>insert &rarr; {@code CREATE}</li>
 *   <li>update that sets {@code deletedAt} (a soft delete) &rarr; {@code DELETE}</li>
 *   <li>update of only {@code active} / {@code status} &rarr; {@code STATUS_CHANGE}</li>
 *   <li>any other update &rarr; {@code UPDATE}, with just the fields that changed</li>
 *   <li>real delete &rarr; {@code DELETE}</li>
 * </ul>
 */
@Component
class EntityAuditListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    /** Columns every entity has that say nothing about what the user changed. */
    private static final Set<String> TECHNICAL = Set.of(
            "id", "createdAt", "updatedAt", "createdBy", "updatedBy", "version", "deletedAt");
    private static final Set<String> STATUS_FIELDS = Set.of("active", "status");
    private static final String DELETED_AT = "deletedAt";

    private final AuditRecorder recorder;
    private final EntityManagerFactory entityManagerFactory;
    private final Map<String, Optional<EntityAuditInfo>> infoByEntityName = new ConcurrentHashMap<>();

    EntityAuditListener(AuditRecorder recorder, EntityManagerFactory entityManagerFactory) {
        this.recorder = recorder;
        this.entityManagerFactory = entityManagerFactory;
    }

    @PostConstruct
    void register() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getEventEngine().getListenerRegistry();
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_DELETE, this);
    }

    /**
     * What to record for one entity class, worked out once.
     *
     * @param included   per property (same order as the persister's): whether its value is recorded
     * @param labelIndex index of the property that names an instance, or -1
     */
    private record EntityAuditInfo(Audited audited, String[] names, boolean[] included, int labelIndex,
                                   int deletedAtIndex) {
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        info(event.getPersister(), event.getEntity()).ifPresent(info -> {
            Object[] state = event.getState();
            record(event.getEntity(), AuditEvent
                    .of(AuditAction.CREATE, info.audited().module(), "Created " + describe(info, state))
                    .entity(info.audited().entity(), event.getId(), label(info, state))
                    .after(snapshot(info, state, null)));
        });
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        info(event.getPersister(), event.getEntity()).ifPresent(info -> {
            Object[] state = event.getState();
            Object[] oldState = event.getOldState();
            int[] dirty = event.getDirtyProperties();

            if (isSoftDelete(info, state, oldState)) {
                record(event.getEntity(), AuditEvent
                        .of(AuditAction.DELETE, info.audited().module(), "Deleted " + describe(info, state))
                        .entity(info.audited().entity(), event.getId(), label(info, state))
                        .before(snapshot(info, oldState != null ? oldState : state, null)));
                return;
            }

            Map<String, Object> after = snapshot(info, state, dirty);
            if (after.isEmpty()) {
                return; // only technical or excluded fields changed (e.g. a last-login timestamp)
            }
            Map<String, Object> before = oldState == null ? null : snapshot(info, oldState, dirty);

            boolean statusOnly = STATUS_FIELDS.containsAll(after.keySet());
            AuditAction action = statusOnly ? AuditAction.STATUS_CHANGE : AuditAction.UPDATE;
            String summary = statusOnly
                    ? statusSummary(after) + " " + describe(info, state)
                    : "Updated " + describe(info, state) + ": " + String.join(", ", after.keySet());
            record(event.getEntity(), AuditEvent
                    .of(action, info.audited().module(), summary)
                    .entity(info.audited().entity(), event.getId(), label(info, state))
                    .before(before)
                    .after(after));
        });
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        info(event.getPersister(), event.getEntity()).ifPresent(info -> {
            Object[] state = event.getDeletedState();
            record(event.getEntity(), AuditEvent
                    .of(AuditAction.DELETE, info.audited().module(), "Deleted " + describe(info, state))
                    .entity(info.audited().entity(), event.getId(), label(info, state))
                    .before(snapshot(info, state, null)));
        });
    }

    private void record(Object entity, AuditEvent auditEvent) {
        if (entity instanceof ActivityOwner part) {
            auditEvent.activityOwner(part.activityOwnerType(), part.activityOwnerId());
        }
        recorder.record(auditEvent);
    }

    /** Run inside the transaction, during the flush, so the audit row shares the fate of the change. */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private Optional<EntityAuditInfo> info(EntityPersister persister, Object entity) {
        return infoByEntityName.computeIfAbsent(persister.getEntityName(), name -> {
            Class<?> type = entity.getClass();
            Audited audited = AnnotationUtils.findAnnotation(type, Audited.class);
            if (audited == null) {
                return Optional.empty();
            }
            String[] names = persister.getPropertyNames();
            Type[] types = persister.getPropertyTypes();
            boolean[] included = new boolean[names.length];
            int labelIndex = -1;
            int deletedAtIndex = -1;
            for (int i = 0; i < names.length; i++) {
                included[i] = !TECHNICAL.contains(names[i])
                        && !types[i].isCollectionType()
                        && !isExcluded(type, names[i]);
                if (names[i].equals(audited.label())) {
                    labelIndex = i;
                }
                if (names[i].equals(DELETED_AT)) {
                    deletedAtIndex = i;
                }
            }
            return Optional.of(new EntityAuditInfo(audited, names, included, labelIndex, deletedAtIndex));
        });
    }

    private static boolean isExcluded(Class<?> type, String property) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(property);
                return field.isAnnotationPresent(AuditExclude.class);
            } catch (NoSuchFieldException e) {
                // declared further up the hierarchy
            }
        }
        return false;
    }

    /**
     * @param only indexes of the properties to take, or {@code null} for all of them
     * @return recorded field name to JSON-friendly value, in mapping order
     */
    private static Map<String, Object> snapshot(EntityAuditInfo info, Object[] state, int[] only) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (only == null) {
            for (int i = 0; i < state.length; i++) {
                if (info.included()[i] && state[i] != null) {
                    values.put(info.names()[i], AuditValues.plain(state[i]));
                }
            }
        } else {
            for (int i : only) {
                if (info.included()[i]) {
                    values.put(info.names()[i], AuditValues.plain(state[i]));
                }
            }
        }
        return values;
    }

    private static boolean isSoftDelete(EntityAuditInfo info, Object[] state, Object[] oldState) {
        int index = info.deletedAtIndex();
        return index >= 0 && state[index] != null && (oldState == null || oldState[index] == null);
    }

    private static String label(EntityAuditInfo info, Object[] state) {
        if (info.labelIndex() < 0 || state[info.labelIndex()] == null) {
            return null;
        }
        return String.valueOf(AuditValues.plain(state[info.labelIndex()]));
    }

    /** "Service '3 kW rooftop'" or just "Service". */
    private static String describe(EntityAuditInfo info, Object[] state) {
        String label = label(info, state);
        return label == null ? info.audited().entity() : info.audited().entity() + " '" + label + "'";
    }

    private static String statusSummary(Map<String, Object> after) {
        if (after.containsKey("active")) {
            return Boolean.TRUE.equals(after.get("active")) ? "Activated" : "Deactivated";
        }
        return "Changed status to " + after.get("status") + " for";
    }
}
