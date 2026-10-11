package com.pawanputra.bos.audit.internal;

import com.pawanputra.bos.audit.api.ActivityFeed;
import com.pawanputra.bos.audit.api.AuditAction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reading the audit log. Every query is limited to one organization: an event is visible only to
 * the organization it belongs to, and events without an organization are visible to nobody here.
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService implements ActivityFeed {

    private static final String LIST_COLUMNS = """
            id, occurred_at, actor_id, actor_label, action, module, entity_type, entity_id, entity_label,
            summary, ip_address
            """;

    private static final TypeReference<List<Map<String, Object>>> CHANGE_LIST = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public AuditQueryService(NamedParameterJdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Every filter is optional; {@code null} means "any".
     *
     * @param from   events at or after this moment
     * @param to     events before this moment
     * @param search text contained in the summary, the actor or the entity's name (case-insensitive)
     */
    public record Filter(
            UUID actorId, String module, AuditAction action, String entityType, String entityId,
            Instant from, Instant to, String search) {
    }

    /** One row of the audit list; the heavy JSON columns are left for the detail view. */
    public record Entry(
            UUID id, Instant occurredAt, UUID actorId, String actorLabel, AuditAction action, String module,
            String entityType, String entityId, String entityLabel, String summary, String ipAddress) {
    }

    public record Detail(
            Entry entry, JsonNode before, JsonNode after, JsonNode metadata, String userAgent, String requestId) {
    }

    public record ActorOption(UUID id, String label) {
    }

    /** The values that actually occur in the organization's log, for the filter drop-downs. */
    public record Facets(List<String> modules, List<String> entityTypes, List<ActorOption> actors) {
    }

    public record Activity(
            UUID id, Instant occurredAt, AuditAction action, UUID actorId, String actorLabel, String message,
            JsonNode changes, UUID auditLogId) {
    }

    public Page<Entry> search(UUID organizationId, Filter filter, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("org", organizationId);
        List<String> where = new ArrayList<>(List.of("organization_id = :org"));
        if (filter.actorId() != null) {
            where.add("actor_id = :actor");
            params.addValue("actor", filter.actorId());
        }
        if (hasText(filter.module())) {
            where.add("module = :module");
            params.addValue("module", filter.module().trim());
        }
        if (filter.action() != null) {
            where.add("action = :action");
            params.addValue("action", filter.action().name());
        }
        if (hasText(filter.entityType())) {
            where.add("entity_type = :entityType");
            params.addValue("entityType", filter.entityType().trim());
        }
        if (hasText(filter.entityId())) {
            where.add("entity_id = :entityId");
            params.addValue("entityId", filter.entityId().trim());
        }
        if (filter.from() != null) {
            where.add("occurred_at >= :from");
            params.addValue("from", filter.from().atOffset(ZoneOffset.UTC));
        }
        if (filter.to() != null) {
            where.add("occurred_at < :to");
            params.addValue("to", filter.to().atOffset(ZoneOffset.UTC));
        }
        if (hasText(filter.search())) {
            where.add("(lower(summary) LIKE :q ESCAPE '\\' OR lower(actor_label) LIKE :q ESCAPE '\\' "
                    + "OR lower(coalesce(entity_label, '')) LIKE :q ESCAPE '\\')");
            params.addValue("q", "%" + escapeLike(filter.search().trim().toLowerCase(Locale.ROOT)) + "%");
        }
        String condition = String.join(" AND ", where);

        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE " + condition, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<Entry> entries = jdbc.query(
                "SELECT " + LIST_COLUMNS + " FROM audit_logs WHERE " + condition
                        // id breaks ties so pages stay stable when several events share a timestamp
                        + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset",
                params, (rs, row) -> entry(rs));
        return new PageImpl<>(entries, pageable, total == null ? 0 : total);
    }

    public Optional<Detail> find(UUID organizationId, UUID id) {
        return jdbc.query(
                "SELECT " + LIST_COLUMNS + ", before_value, after_value, metadata, user_agent, request_id "
                        + "FROM audit_logs WHERE id = :id AND organization_id = :org",
                new MapSqlParameterSource("id", id).addValue("org", organizationId),
                (rs, row) -> new Detail(
                        entry(rs),
                        json(rs.getString("before_value")),
                        json(rs.getString("after_value")),
                        json(rs.getString("metadata")),
                        rs.getString("user_agent"),
                        rs.getString("request_id")))
                .stream().findFirst();
    }

    public Facets facets(UUID organizationId) {
        MapSqlParameterSource params = new MapSqlParameterSource("org", organizationId);
        List<String> modules = jdbc.queryForList(
                "SELECT DISTINCT module FROM audit_logs WHERE organization_id = :org ORDER BY module",
                params, String.class);
        List<String> entityTypes = jdbc.queryForList(
                "SELECT DISTINCT entity_type FROM audit_logs WHERE organization_id = :org "
                        + "AND entity_type IS NOT NULL ORDER BY entity_type",
                params, String.class);
        List<ActorOption> actors = jdbc.query(
                // One option per person; if their email changed, show the most recent one.
                "SELECT DISTINCT ON (actor_id) actor_id, actor_label FROM audit_logs "
                        + "WHERE organization_id = :org AND actor_id IS NOT NULL "
                        + "ORDER BY actor_id, occurred_at DESC LIMIT 500",
                params, (rs, row) -> new ActorOption(rs.getObject("actor_id", UUID.class), rs.getString("actor_label")));
        actors = actors.stream().sorted(java.util.Comparator.comparing(ActorOption::label)).toList();
        return new Facets(modules, entityTypes, actors);
    }

    /** The timeline of one record, newest first. */
    public Page<Activity> activity(UUID organizationId, String entityType, String entityId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("org", organizationId)
                .addValue("entityType", entityType).addValue("entityId", entityId);
        String condition = "organization_id = :org AND entity_type = :entityType AND entity_id = :entityId";
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM entity_activity_logs WHERE " + condition, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<Activity> items = jdbc.query(
                "SELECT id, occurred_at, action, actor_id, actor_label, message, changes, audit_log_id "
                        + "FROM entity_activity_logs WHERE " + condition
                        + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset",
                params, (rs, row) -> new Activity(
                        rs.getObject("id", UUID.class),
                        instant(rs, "occurred_at"),
                        AuditAction.valueOf(rs.getString("action")),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("actor_label"),
                        rs.getString("message"),
                        json(rs.getString("changes")),
                        rs.getObject("audit_log_id", UUID.class)));
        return new PageImpl<>(items, pageable, total == null ? 0 : total);
    }

    @Override
    public Page<ActivityItem> forEntity(UUID organizationId, String entityType, String entityId, Pageable pageable) {
        return activity(organizationId, entityType, entityId, pageable).map(item -> new ActivityItem(
                item.id(), item.occurredAt(), item.action(), item.actorLabel(), item.message(),
                item.changes() == null ? List.of() : jsonMapper.convertValue(item.changes(), CHANGE_LIST)));
    }

    @Override
    public List<RecordedChange> changesUpTo(
            UUID organizationId, String entityType, String entityId, Instant notAfter, int limit,
            Set<String> excludingChangedTypes) {
        MapSqlParameterSource params = new MapSqlParameterSource("org", organizationId)
                .addValue("entityType", entityType).addValue("entityId", entityId).addValue("limit", limit);
        String upTo = "";
        if (notAfter != null) {
            upTo = " AND a.occurred_at <= :notAfter";
            params.addValue("notAfter", notAfter.atOffset(ZoneOffset.UTC));
        }
        if (excludingChangedTypes != null && !excludingChangedTypes.isEmpty()) {
            upTo += " AND (l.entity_type IS NULL OR l.entity_type NOT IN (:excluded))";
            params.addValue("excluded", excludingChangedTypes);
        }
        return jdbc.query(
                "SELECT a.id, a.occurred_at, a.action, a.actor_label, a.message, "
                        + "l.entity_type AS changed_type, l.entity_label AS changed_label "
                        + "FROM entity_activity_logs a LEFT JOIN audit_logs l ON l.id = a.audit_log_id "
                        + "WHERE a.organization_id = :org AND a.entity_type = :entityType AND a.entity_id = :entityId"
                        + upTo + " ORDER BY a.occurred_at DESC, a.id DESC LIMIT :limit",
                params, (rs, row) -> new RecordedChange(
                        rs.getObject("id", UUID.class),
                        instant(rs, "occurred_at"),
                        AuditAction.valueOf(rs.getString("action")),
                        rs.getString("actor_label"),
                        rs.getString("message"),
                        rs.getString("changed_type"),
                        rs.getString("changed_label")));
    }

    private static Entry entry(ResultSet rs) throws SQLException {
        return new Entry(
                rs.getObject("id", UUID.class),
                instant(rs, "occurred_at"),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_label"),
                AuditAction.valueOf(rs.getString("action")),
                rs.getString("module"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("entity_label"),
                rs.getString("summary"),
                rs.getString("ip_address"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private JsonNode json(String text) {
        return text == null ? null : jsonMapper.readTree(text);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
