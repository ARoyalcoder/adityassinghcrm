package com.pawanputra.bos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawanputra.bos.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Checks the migrated schema itself, independently of the JPA mappings. */
class SchemaMigrationIT extends AbstractIntegrationTest {

    private static final Set<String> SOFT_DELETE_TABLES =
            Set.of("organizations", "branches", "users", "service_categories", "services");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsAreAppliedSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL "
                        + "ORDER BY installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",
                "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23");
    }

    @Test
    void schemaContainsExactlyTheFoundationTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "organizations", "branches",
                "users", "roles", "permissions", "user_roles", "role_permissions",
                "user_sessions", "refresh_tokens", "password_reset_tokens",
                "audit_logs", "entity_activity_logs",
                "customers", "customer_contacts", "customer_addresses", "customer_tags",
                "customer_tag_assignments", "customer_notes", "customer_interactions",
                "service_custom_fields", "lead_custom_field_values",
                "leads", "lead_activities", "qualification_questions", "lead_qualification_answers",
                "lead_score_history",
                "activities", "activity_reminders",
                "pipelines", "pipeline_stages", "opportunities", "opportunity_stage_history", "opportunity_notes",
                "quotations", "quotation_versions", "quotation_items", "quotation_audit_logs",
                "orders", "order_items", "order_history",
                "project_statuses", "projects", "project_members", "project_milestones", "project_tasks",
                "project_task_dependencies", "project_checklist_items", "project_deliverables",
                "project_templates", "project_template_stages",
                "project_stage_states", "project_stage_history", "project_feedback",
                "field_jobs", "field_job_checklist_items", "field_job_materials", "field_job_photos", "field_job_notes",
                "payment_methods", "finance_document_sequences", "finance_idempotency_keys",
                "invoices", "invoice_items", "payments", "payment_allocations", "refunds", "credit_notes", "debit_notes",
                "service_verticals", "service_categories", "services");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "users", "roles", "permissions", "user_roles", "organizations", "branches",
        "service_verticals", "service_categories", "services"
    })
    void everyRequiredTableHasAUuidPrimaryKeyAndTimestamps(String table) {
        List<String> primaryKey = jdbc.queryForList(
                "SELECT c.column_name || ':' || c.data_type "
                        + "FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage k "
                        + "  ON k.constraint_name = tc.constraint_name AND k.table_schema = tc.table_schema "
                        + "JOIN information_schema.columns c "
                        + "  ON c.table_schema = k.table_schema AND c.table_name = k.table_name "
                        + " AND c.column_name = k.column_name "
                        + "WHERE tc.constraint_type = 'PRIMARY KEY' "
                        + "  AND tc.table_schema = current_schema() AND tc.table_name = ?",
                String.class, table);
        assertThat(primaryKey).as("primary key of %s", table).containsExactly("id:uuid");

        List<String> columns = columnsOf(table);
        assertThat(columns).as("columns of %s", table).contains("created_at");
        if (!table.equals("user_roles")) { // a role grant is immutable: it is created and removed, never updated
            assertThat(columns).as("columns of %s", table).contains("updated_at");
        }
        assertThat(columns.contains("deleted_at"))
                .as("%s soft-deletable", table)
                .isEqualTo(SOFT_DELETE_TABLES.contains(table));
    }

    @Test
    void seedDataIsExactlyTheSixVerticals() {
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM service_verticals ORDER BY display_order", String.class);

        assertThat(codes).containsExactly(
                "CCTV_SECURITY", "DIGITAL_MARKETING", "INTERIOR_DESIGN",
                "ARCHITECTURE_TECH", "SOLAR", "IT_SUPPORT");
    }

    @Test
    void databaseRejectsASeventhVertical() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO service_verticals (code, name, display_order) VALUES ('REAL_ESTATE', 'Real Estate', 7)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_service_verticals_code");
    }

    @Test
    void everyForeignKeyColumnIsTheLeadingColumnOfAnIndex() {
        // An unindexed foreign key makes parent deletes and child lookups scan the whole child table.
        List<String> unindexed = jdbc.queryForList(
                "SELECT c.conrelid::regclass::text || '.' || a.attname "
                        + "FROM pg_constraint c "
                        + "JOIN pg_namespace n ON n.oid = c.connamespace "
                        + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1] "
                        + "WHERE c.contype = 'f' AND n.nspname = current_schema() "
                        + "  AND NOT EXISTS (SELECT 1 FROM pg_index i "
                        + "                  WHERE i.indrelid = c.conrelid AND i.indkey[0] = c.conkey[1])",
                String.class);

        assertThat(unindexed).isEmpty();
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ?",
                String.class, table);
    }
}
