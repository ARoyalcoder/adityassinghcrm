package com.pawanputra.bos.audit.internal;

import com.pawanputra.bos.audit.api.AuditEvent;
import com.pawanputra.bos.audit.api.AuditRecorder;
import com.pawanputra.bos.platform.logging.RequestCorrelationFilter;
import com.pawanputra.bos.platform.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit rows with plain JDBC on the caller's connection.
 *
 * <p>JDBC rather than JPA on purpose: most events are recorded from inside a Hibernate flush (see
 * {@link EntityAuditListener}), where persisting another entity is not allowed. A JDBC insert on the
 * transaction's own connection is safe there, and commits or rolls back with the business change.
 */
@Component
class JdbcAuditRecorder implements AuditRecorder {

    private static final String SYSTEM = "system";
    private static final String ANONYMOUS = "anonymous";

    private static final String INSERT_AUDIT = """
            INSERT INTO audit_logs (id, occurred_at, organization_id, actor_id, actor_label, action, module,
                                    entity_type, entity_id, entity_label, summary,
                                    before_value, after_value, metadata, ip_address, user_agent, request_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?)
            """;

    private static final String INSERT_ACTIVITY = """
            INSERT INTO entity_activity_logs (id, occurred_at, organization_id, entity_type, entity_id, action,
                                              actor_id, actor_label, message, changes, audit_log_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    JdbcAuditRecorder(JdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public UUID record(AuditEvent event) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        Optional<HttpServletRequest> request = currentRequest();
        Actor actor = resolveActor(event, request.isPresent());

        Map<String, Object> before = AuditValues.plainMap(event.before());
        Map<String, Object> after = AuditValues.plainMap(event.after());
        Map<String, Object> metadata = event.metadata().isEmpty() ? null : AuditValues.plainMap(event.metadata());
        String summary = AuditValues.truncate(event.summary(), 500);

        jdbc.update(INSERT_AUDIT,
                id, now, actor.organizationId(), actor.id(), actor.label(), event.action().name(), event.module(),
                event.entityType(), event.entityId(), AuditValues.truncate(event.entityLabel(), 200), summary,
                json(before), json(after), json(metadata),
                request.map(HttpServletRequest::getRemoteAddr).orElse(null),
                request.map(r -> AuditValues.truncate(r.getHeader(HttpHeaders.USER_AGENT), 500)).orElse(null),
                MDC.get(RequestCorrelationFilter.MDC_KEY));

        if (event.timelineType() != null && event.timelineId() != null) {
            List<Map<String, Object>> changes = AuditValues.changes(before, after);
            jdbc.update(INSERT_ACTIVITY,
                    UUID.randomUUID(), now, actor.organizationId(), event.timelineType(), event.timelineId(),
                    event.action().name(), actor.id(), actor.label(), summary,
                    json(changes.isEmpty() ? null : changes), id);
        }
        return id;
    }

    private record Actor(UUID id, String label, UUID organizationId) {
    }

    private static Actor resolveActor(AuditEvent event, boolean inRequest) {
        if (event.actorOverridden()) {
            String label = event.actorLabel() != null ? event.actorLabel() : ANONYMOUS;
            return new Actor(event.actorId(), AuditValues.truncate(label, 254), event.organizationId());
        }
        return CurrentUser.get()
                .map(user -> new Actor(user.id(), user.email(), user.organizationId()))
                // No signed-in user: a scheduled job or start-up task, or a public endpoint.
                .orElseGet(() -> new Actor(null, inRequest ? ANONYMOUS : SYSTEM, null));
    }

    private static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    /** JSON text bound as VARCHAR and cast to jsonb in SQL; {@code null} stays SQL NULL. */
    private SqlParameterValue json(Object value) {
        return new SqlParameterValue(Types.VARCHAR, value == null ? null : jsonMapper.writeValueAsString(value));
    }
}
