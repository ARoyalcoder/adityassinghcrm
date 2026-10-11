package com.pawanputra.bos.project.internal.analytics;

import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.project.api.ProjectTemplates;
import com.pawanputra.bos.project.internal.analytics.ProjectStageService.StageOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Figures about an organization's projects, worked out from the project records on request.
 * Nothing is stored and nothing is estimated: a figure that cannot be worked out from what is
 * recorded is {@code null}, never zero.
 *
 * <p>What "completed" and "cancelled" mean comes from the organization's own project statuses,
 * and the pipeline stages come from its project templates. No status or stage is named here.
 */
@Service
@Transactional(readOnly = true)
public class ProjectAnalyticsService {

    /** Every live project of the organization, with what its status means. */
    private static final String PROJECTS = """
            WITH p AS (
                SELECT pr.*,
                       coalesce(ps.is_completed, false) AS done,
                       coalesce(ps.is_cancelled, false) AS cancelled,
                       NOT coalesce(ps.is_completed, false) AND NOT coalesce(ps.is_cancelled, false) AS active
                FROM projects pr
                LEFT JOIN project_statuses ps ON ps.organization_id = pr.organization_id AND ps.code = pr.status
                WHERE pr.organization_id = ? AND pr.deleted_at IS NULL)
            """;

    private final JdbcTemplate jdbc;
    private final ProjectTemplates templates;
    private final Clock clock;

    ProjectAnalyticsService(JdbcTemplate jdbc, ProjectTemplates templates, Clock clock) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.clock = clock;
    }

    /**
     * The dashboard cards.
     *
     * @param delayedProjects            active projects whose expected completion date has passed
     * @param revenueInProgress          the revenue of the active projects
     * @param completedRevenue           the revenue of every completed project
     * @param averageProjectDurationDays start to actual completion, over completed projects; null if none
     * @param averageDeliveryDelayDays   days past the expected date, over projects delivered late; null if none
     * @param clientSatisfaction         the average rating (1 to 5); null if no project has been rated
     */
    public record Cards(
            long activeProjects, long startingToday, long dueToday, long delayedProjects, long completedThisMonth,
            BigDecimal revenueInProgress, BigDecimal completedRevenue, BigDecimal completedRevenueThisMonth,
            BigDecimal averageProjectDurationDays, BigDecimal averageDeliveryDelayDays, BigDecimal clientSatisfaction,
            long ratedProjects) {
    }

    /** One pipeline stage with the projects currently in it. */
    public record PipelineStage(String name, long projects, BigDecimal revenue) {
    }

    public record ProjectRef(UUID id, String projectNumber, String title, long days) {
    }

    /**
     * How completed projects were delivered. Only projects with a recorded completion date count.
     *
     * @param onTimePercentage null if nothing has been delivered
     */
    public record Delivery(
            long deliveredOnTime, long deliveredLate, BigDecimal onTimePercentage, BigDecimal averageDelayDays,
            ProjectRef fastestDelivery, ProjectRef longestDelivery, BigDecimal averageCompletionDays) {
    }

    public record Employee(UUID userId, String name, long tasksCompleted, long openTasks, BigDecimal hoursLogged, long projects) {
    }

    /**
     * @param averageHours        hours logged on tasks per completed project (projects with tasks only)
     * @param averageDeliverables deliverables per completed project
     * @param averageDrawings     always null today: drawings are not recorded separately from deliverables
     */
    public record Productivity(
            long projectsCompleted, long projectsCompletedThisMonth, BigDecimal averageHours,
            BigDecimal averageDeliverables, BigDecimal averageDrawings, List<Employee> employees) {
    }

    /** @param unstagedProjects active projects that have not been placed in any stage */
    public record Analytics(
            LocalDate asOf, Cards cards, List<PipelineStage> pipeline, long unstagedProjects, Delivery delivery,
            Productivity productivity) {
    }

    /** An active project with the stage it is in and the stages it could be moved to. */
    public record PipelineProject(
            UUID id, String projectNumber, String title, String customerName, LocalDate expectedCompletionDate,
            boolean delayed, String stageKey, String stageName, List<StageOption> stages) {
    }

    public record Feedback(int rating, String comment, String recordedBy, Instant recordedAt) {
    }

    public Analytics analytics(UUID organizationId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonth = monthStart.plusMonths(1);

        Map<String, Object> totals = jdbc.queryForMap(PROJECTS + """
                SELECT count(*) FILTER (WHERE active) AS active,
                       count(*) FILTER (WHERE active AND start_date = ?) AS starting_today,
                       count(*) FILTER (WHERE active AND expected_completion_date = ?) AS due_today,
                       count(*) FILTER (WHERE active AND expected_completion_date < ?) AS delayed,
                       count(*) FILTER (WHERE done) AS completed,
                       count(*) FILTER (WHERE done AND actual_completion_date >= ? AND actual_completion_date < ?) AS completed_month,
                       coalesce(sum(revenue) FILTER (WHERE active), 0) AS revenue_in_progress,
                       coalesce(sum(revenue) FILTER (WHERE done), 0) AS completed_revenue,
                       coalesce(sum(revenue) FILTER (WHERE done AND actual_completion_date >= ?
                                                     AND actual_completion_date < ?), 0) AS completed_revenue_month,
                       avg(actual_completion_date - start_date) FILTER (WHERE done AND actual_completion_date IS NOT NULL) AS avg_duration,
                       avg(actual_completion_date - expected_completion_date)
                           FILTER (WHERE done AND actual_completion_date > expected_completion_date) AS avg_delay,
                       count(*) FILTER (WHERE done AND actual_completion_date <= expected_completion_date) AS on_time,
                       count(*) FILTER (WHERE done AND actual_completion_date > expected_completion_date) AS late
                FROM p
                """, organizationId, today, today, today, monthStart, nextMonth, monthStart, nextMonth);

        Map<String, Object> satisfaction = jdbc.queryForMap(PROJECTS
                + "SELECT avg(f.rating) AS rating, count(*) AS rated FROM project_feedback f JOIN p ON p.id = f.project_id",
                organizationId);

        Cards cards = new Cards(
                number(totals, "active"), number(totals, "starting_today"), number(totals, "due_today"),
                number(totals, "delayed"), number(totals, "completed_month"),
                (BigDecimal) totals.get("revenue_in_progress"), (BigDecimal) totals.get("completed_revenue"),
                (BigDecimal) totals.get("completed_revenue_month"),
                rounded(totals.get("avg_duration")), rounded(totals.get("avg_delay")),
                rounded(satisfaction.get("rating")), number(satisfaction, "rated"));

        List<PipelineStage> pipeline = jdbc.query(PROJECTS + """
                SELECT s.stage_name, count(*) AS projects, coalesce(sum(p.revenue), 0) AS revenue
                FROM project_stage_states s JOIN p ON p.id = s.project_id
                GROUP BY s.stage_name ORDER BY min(s.stage_position), s.stage_name
                """, (row, index) -> new PipelineStage(row.getString(1), row.getLong(2), row.getBigDecimal(3)),
                organizationId);
        Long unstaged = jdbc.queryForObject(PROJECTS + "SELECT count(*) FROM p WHERE active AND NOT EXISTS "
                + "(SELECT 1 FROM project_stage_states s WHERE s.project_id = p.id)", Long.class, organizationId);

        long onTime = number(totals, "on_time");
        long late = number(totals, "late");
        Delivery delivery = new Delivery(
                onTime, late,
                onTime + late == 0 ? null : BigDecimal.valueOf(onTime * 100.0 / (onTime + late)).setScale(1, RoundingMode.HALF_UP),
                rounded(totals.get("avg_delay")), extreme(organizationId, "ASC"), extreme(organizationId, "DESC"),
                rounded(totals.get("avg_duration")));

        Map<String, Object> perProject = jdbc.queryForMap(PROJECTS + """
                SELECT (SELECT avg(hours) FROM (SELECT sum(t.actual_hours) AS hours FROM project_tasks t
                                                JOIN p ON p.id = t.project_id WHERE p.done GROUP BY t.project_id) h) AS avg_hours,
                       (SELECT avg(n) FROM (SELECT count(d.id) AS n FROM p LEFT JOIN project_deliverables d
                                            ON d.project_id = p.id WHERE p.done GROUP BY p.id) c) AS avg_deliverables
                """, organizationId);
        List<Employee> employees = jdbc.query(PROJECTS + """
                SELECT t.assigned_to_id, max(t.assigned_to_name),
                       count(*) FILTER (WHERE t.status = 'COMPLETED'),
                       count(*) FILTER (WHERE t.status NOT IN ('COMPLETED', 'CANCELLED')),
                       coalesce(sum(t.actual_hours), 0), count(DISTINCT t.project_id)
                FROM project_tasks t JOIN p ON p.id = t.project_id
                WHERE t.assigned_to_id IS NOT NULL
                GROUP BY t.assigned_to_id
                ORDER BY 3 DESC, 5 DESC, 2 LIMIT 20
                """, (row, index) -> new Employee(row.getObject(1, UUID.class), row.getString(2), row.getLong(3),
                        row.getLong(4), row.getBigDecimal(5), row.getLong(6)), organizationId);
        Productivity productivity = new Productivity(
                number(totals, "completed"), number(totals, "completed_month"), rounded(perProject.get("avg_hours")),
                rounded(perProject.get("avg_deliverables")), null, employees);

        return new Analytics(today, cards, pipeline, unstaged == null ? 0 : unstaged, delivery, productivity);
    }

    /** The active projects (most urgent first) with their stage, for moving them along the pipeline. */
    public List<PipelineProject> pipelineProjects(UUID organizationId) {
        LocalDate today = LocalDate.now(clock);
        Map<UUID, List<StageOption>> stagesByService = new HashMap<>();
        return jdbc.query(PROJECTS + """
                SELECT p.id, p.project_number, p.title, p.customer_name, p.expected_completion_date, p.service_id,
                       s.stage_key, s.stage_name
                FROM p LEFT JOIN project_stage_states s ON s.project_id = p.id
                WHERE p.active ORDER BY p.expected_completion_date, p.project_number LIMIT 100
                """, (row, index) -> {
                    UUID serviceId = row.getObject("service_id", UUID.class);
                    List<StageOption> stages = serviceId == null ? List.of() : stagesByService.computeIfAbsent(
                            serviceId, id -> templates.activeForService(organizationId, id)
                                    .map(ProjectStageService::options).orElse(List.of()));
                    LocalDate expected = row.getObject("expected_completion_date", LocalDate.class);
                    return new PipelineProject(row.getObject("id", UUID.class), row.getString("project_number"),
                            row.getString("title"), row.getString("customer_name"), expected,
                            expected != null && expected.isBefore(today), row.getString("stage_key"),
                            row.getString("stage_name"), stages);
                }, organizationId);
    }

    /** Records (or replaces) how satisfied the client was with a project. */
    @Transactional
    public Feedback rate(UUID organizationId, UUID projectId, int rating, String comment, String actor) {
        Integer found = jdbc.queryForObject(
                "SELECT count(*) FROM projects WHERE id = ? AND organization_id = ? AND deleted_at IS NULL",
                Integer.class, projectId, organizationId);
        if (found == null || found == 0) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        Instant now = clock.instant();
        jdbc.update("INSERT INTO project_feedback (project_id, organization_id, rating, comment, recorded_by, recorded_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (project_id) DO UPDATE SET rating = excluded.rating, "
                        + "comment = excluded.comment, recorded_by = excluded.recorded_by, "
                        + "recorded_at = excluded.recorded_at, updated_at = excluded.recorded_at",
                projectId, organizationId, rating, comment, actor, Timestamp.from(now));
        return new Feedback(rating, comment, actor, now);
    }

    private ProjectRef extreme(UUID organizationId, String direction) {
        return jdbc.query(PROJECTS + "SELECT id, project_number, title, actual_completion_date - start_date AS days FROM p "
                        + "WHERE done AND actual_completion_date IS NOT NULL ORDER BY days " + direction
                        + ", project_number LIMIT 1",
                (ResultSet row) -> row.next() ? ref(row) : null, organizationId);
    }

    private static ProjectRef ref(ResultSet row) throws SQLException {
        return new ProjectRef(row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getLong(4));
    }

    private static long number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    /** An average to one decimal place; null stays null (there was nothing to average). */
    private static BigDecimal rounded(Object value) {
        return value == null ? null : new BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP);
    }
}
