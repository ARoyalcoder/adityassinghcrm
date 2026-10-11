package com.pawanputra.bos.project.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The plan a service's projects follow, as defined by administrators: which stages, in what order,
 * how long each takes, who is responsible, and what is done and handed over in each.
 *
 * <p>Everything here is data. Code that works with a template must never look for a particular
 * stage: the stages of one service have nothing in common with those of another.
 */
public interface ProjectTemplates {

    /**
     * One piece of work in a stage.
     *
     * @param responsibleRole a role code, or {@code null} to use the stage's
     * @param checklist       what must be ticked off before the task is done
     */
    record TaskSpec(
            String title, String description, String responsibleRole, BigDecimal estimatedHours,
            List<String> checklist) {
    }

    /** A document that the stage needs from the customer or produces for them. */
    record DocumentSpec(String name, boolean required, String description) {
    }

    /**
     * One stage of a template.
     *
     * @param key             identifies the stage within its template
     * @param durationDays    estimated duration in calendar days
     * @param responsibleRole a role code, or {@code null}
     * @param dependsOn       keys of the stages that must finish before this one starts
     * @param milestones      checkpoints reached in this stage
     */
    record StageSpec(
            String key,
            String name,
            String description,
            int durationDays,
            String responsibleRole,
            List<String> dependsOn,
            List<String> milestones,
            List<TaskSpec> tasks,
            List<DocumentSpec> documents) {
    }

    /**
     * A stage placed in time, counted in days from the start of the project.
     *
     * @param startDay the day the stage can start: when the last of its dependencies ends (0 if it has none)
     * @param endDay   {@code startDay + durationDays}
     */
    record ScheduledStage(StageSpec stage, int startDay, int endDay) {
    }

    /**
     * @param stages    in the order the administrator listed them
     * @param totalDays the estimated duration of the whole project: when the last stage ends
     */
    record Plan(List<ScheduledStage> stages, int totalDays) {
    }

    record Template(UUID id, UUID serviceId, String name, String description, Plan plan) {
    }

    /** The template to use for a new project of the service, if the organization has an active one. */
    Optional<Template> activeForService(UUID organizationId, UUID serviceId);
}
