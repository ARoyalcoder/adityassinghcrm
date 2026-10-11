package com.pawanputra.bos.project.internal.analytics;

import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.project.api.ProjectTemplates;
import com.pawanputra.bos.project.api.ProjectTemplates.ScheduledStage;
import com.pawanputra.bos.project.api.ProjectTemplates.Template;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which pipeline stage a project is in. The stages a project can be in are the stages of its
 * service's template, as administrators defined them; this class names none of them.
 */
@Service
@Transactional(readOnly = true)
public class ProjectStageService {

    private final JdbcTemplate jdbc;
    private final ProjectTemplates templates;
    private final Clock clock;

    ProjectStageService(JdbcTemplate jdbc, ProjectTemplates templates, Clock clock) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.clock = clock;
    }

    public record StageOption(String key, String name) {
    }

    /**
     * @param stageKey  the stage the project is in; {@code null} if it has not been placed in one
     * @param available the stages it can be moved to: those of its service's active template, in order
     */
    public record ProjectStage(String stageKey, String stageName, Instant enteredAt, List<StageOption> available) {
    }

    public ProjectStage stageOf(UUID organizationId, UUID projectId) {
        UUID serviceId = serviceOf(organizationId, projectId);
        List<StageOption> available = template(organizationId, serviceId)
                .map(ProjectStageService::options).orElse(List.of());
        return jdbc.query(
                "SELECT stage_key, stage_name, entered_at FROM project_stage_states WHERE project_id = ?",
                row -> row.next()
                        ? new ProjectStage(row.getString(1), row.getString(2), row.getTimestamp(3).toInstant(), available)
                        : new ProjectStage(null, null, null, available),
                projectId);
    }

    /** Moves the project to another stage of its template and records how long it was in the previous one. */
    @Transactional
    public ProjectStage move(UUID organizationId, UUID projectId, String stageKey, String actor) {
        UUID serviceId = serviceOf(organizationId, projectId);
        Template template = template(organizationId, serviceId).orElseThrow(() -> new BusinessRuleException(
                "This project's service has no active project template, so there are no stages to move it through"));
        List<ScheduledStage> stages = template.plan().stages();
        int position = -1;
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).stage().key().equals(stageKey)) {
                position = i;
            }
        }
        if (position < 0) {
            throw new FieldValidationException("stageKey", "is not a stage of this project's template");
        }
        place(organizationId, projectId, template.id(), stageKey, stages.get(position).stage().name(), position, actor);
        return stageOf(organizationId, projectId);
    }

    /** Puts a project that is in no stage yet into the first stage of the template it was planned from. */
    @Transactional
    public void start(UUID organizationId, UUID projectId, Template template, String actor) {
        Integer placed = jdbc.queryForObject(
                "SELECT count(*) FROM project_stage_states WHERE project_id = ?", Integer.class, projectId);
        List<ScheduledStage> stages = template.plan().stages();
        if ((placed != null && placed > 0) || stages.isEmpty()) {
            return;
        }
        place(organizationId, projectId, template.id(), stages.getFirst().stage().key(),
                stages.getFirst().stage().name(), 0, actor);
    }

    private void place(
            UUID organizationId, UUID projectId, UUID templateId, String key, String name, int position, String actor) {
        Timestamp now = Timestamp.from(clock.instant());
        String current = jdbc.query("SELECT stage_key FROM project_stage_states WHERE project_id = ?",
                row -> row.next() ? row.getString(1) : null, projectId);
        if (key.equals(current)) {
            return; // already there: moving a project to the stage it is in is not a move
        }
        jdbc.update("UPDATE project_stage_history SET left_at = ? WHERE project_id = ? AND left_at IS NULL", now, projectId);
        jdbc.update("INSERT INTO project_stage_history (project_id, stage_key, stage_name, entered_at, moved_by) "
                + "VALUES (?, ?, ?, ?, ?)", projectId, key, name, now, actor);
        jdbc.update("INSERT INTO project_stage_states (project_id, organization_id, template_id, stage_key, stage_name, "
                        + "stage_position, entered_at, moved_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (project_id) DO UPDATE SET template_id = excluded.template_id, "
                        + "stage_key = excluded.stage_key, stage_name = excluded.stage_name, "
                        + "stage_position = excluded.stage_position, entered_at = excluded.entered_at, "
                        + "moved_by = excluded.moved_by, updated_at = excluded.entered_at",
                projectId, organizationId, templateId, key, name, position, now, actor);
    }

    private Optional<Template> template(UUID organizationId, UUID serviceId) {
        return serviceId == null ? Optional.empty() : templates.activeForService(organizationId, serviceId);
    }

    static List<StageOption> options(Template template) {
        return template.plan().stages().stream()
                .map(scheduled -> new StageOption(scheduled.stage().key(), scheduled.stage().name())).toList();
    }

    /** The project's service (may be {@code null}); fails as "not found" for another organization's project. */
    private UUID serviceOf(UUID organizationId, UUID projectId) {
        List<UUID> found = jdbc.query(
                "SELECT service_id FROM projects WHERE id = ? AND organization_id = ? AND deleted_at IS NULL",
                (row, index) -> row.getObject(1, UUID.class), projectId, organizationId);
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return found.getFirst();
    }
}
