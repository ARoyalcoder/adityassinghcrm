package com.pawanputra.bos.project.internal.template;

import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.project.api.ProjectTemplates.DocumentSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.Plan;
import com.pawanputra.bos.project.api.ProjectTemplates.ScheduledStage;
import com.pawanputra.bos.project.api.ProjectTemplates.StageSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.TaskSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.Template;
import com.pawanputra.bos.project.internal.MilestoneRepository;
import com.pawanputra.bos.project.internal.Project;
import com.pawanputra.bos.project.internal.ProjectRepository;
import com.pawanputra.bos.project.internal.ProjectService;
import com.pawanputra.bos.project.internal.ProjectTaskRepository;
import com.pawanputra.bos.project.internal.analytics.ProjectStageService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a template into the plan of one project: the project gets its own copy of the stages as
 * milestones, with their tasks, checklists and required documents, dated from its start date.
 *
 * <p>It is a copy on purpose. Changing a template later changes future projects, never a project
 * that is already running.
 */
@Service
public class ProjectTemplateApplier {

    private final ProjectTemplateService templates;
    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestones;
    private final ProjectTaskRepository tasks;
    private final ProjectStageService stages;

    ProjectTemplateApplier(
            ProjectTemplateService templates, ProjectService projects, ProjectRepository projectRepository,
            MilestoneRepository milestones, ProjectTaskRepository tasks, ProjectStageService stages) {
        this.templates = templates;
        this.projects = projects;
        this.projectRepository = projectRepository;
        this.milestones = milestones;
        this.tasks = tasks;
        this.stages = stages;
    }

    /** What applying the template created, and the completion date it estimates. */
    public record Applied(
            UUID templateId, String templateName, int milestones, int tasks, int checklistItems, int deliverables,
            LocalDate expectedCompletionDate) {
    }

    @Transactional
    public Applied apply(UUID organizationId, UUID projectId, String actor) {
        Project project = projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (project.getServiceId() == null) {
            throw new BusinessRuleException("This project is not for a service, so there is no template to apply");
        }
        Template template = templates.activeForService(organizationId, project.getServiceId())
                .orElseThrow(() -> new BusinessRuleException(
                        "There is no active project template for this project's service"));
        if (milestones.countByProjectId(projectId) > 0 || tasks.countByProjectId(projectId) > 0) {
            // Applying twice would duplicate everything, and merging with a hand-made plan cannot be done safely.
            throw new ConflictException("This project already has milestones or tasks; a template can only be "
                    + "applied to a project that has none");
        }

        Plan plan = template.plan();
        LocalDate start = project.getStartDate();
        int milestoneCount = 0;
        int taskCount = 0;
        int checklistCount = 0;
        int deliverableCount = 0;
        // For joining stages: a stage's first task waits for the last task of each stage it depends on.
        Map<String, UUID> lastTaskOfStage = new HashMap<>();

        for (ScheduledStage scheduled : TemplatePlanner.inDependencyOrder(plan)) {
            StageSpec stage = scheduled.stage();
            LocalDate stageStart = start.plusDays(scheduled.startDay());
            LocalDate stageEnd = start.plusDays(scheduled.endDay());

            // The stage itself: the project has no separate notion of a stage, so it is the milestone
            // that the stage's tasks and documents hang from.
            UUID stageMilestone = projects.addMilestone(organizationId, projectId,
                    new ProjectService.AddMilestoneCmd(stage.name(), describe(stage), stageEnd, ++milestoneCount),
                    actor).id();
            for (String checkpoint : stage.milestones()) {
                projects.addMilestone(organizationId, projectId, new ProjectService.AddMilestoneCmd(
                        checkpoint, "Checkpoint of the stage \"" + stage.name() + "\"", stageEnd, ++milestoneCount), actor);
            }

            List<UUID> waitsFor = new ArrayList<>();
            for (String dependency : stage.dependsOn()) {
                UUID last = lastTaskOfStage.get(dependency);
                if (last != null) {
                    waitsFor.add(last);
                }
            }
            for (int index = 0; index < stage.tasks().size(); index++) {
                TaskSpec task = stage.tasks().get(index);
                String role = task.responsibleRole() != null ? task.responsibleRole() : stage.responsibleRole();
                UUID taskId = projects.addTask(organizationId, projectId, new ProjectService.AddTaskCmd(
                        stageMilestone, task.title(), withRole(task.description(), role), null, "MEDIUM", stageStart,
                        stageEnd, task.estimatedHours(), ++taskCount, index == 0 ? waitsFor : List.of()), actor).id();
                for (String item : task.checklist()) {
                    projects.addChecklistItem(organizationId, projectId, taskId, item, actor);
                    checklistCount++;
                }
                lastTaskOfStage.put(stage.key(), taskId);
            }
            if (stage.tasks().isEmpty()) {
                // A stage without tasks passes its predecessors on, so the chain is not broken.
                stage.dependsOn().stream().map(lastTaskOfStage::get).filter(java.util.Objects::nonNull)
                        .reduce((first, second) -> second).ifPresent(last -> lastTaskOfStage.put(stage.key(), last));
            }

            for (DocumentSpec document : stage.documents()) {
                projects.addDeliverable(organizationId, projectId, new ProjectService.AddDeliverableCmd(
                        stageMilestone, document.name(), document.description(), stageEnd,
                        document.required() ? "Required document" : "Optional document", null), actor);
                deliverableCount++;
            }
        }

        LocalDate expected = start.plusDays(plan.totalDays());
        project.setExpectedCompletionDate(expected);
        projectRepository.save(project);
        // The project now follows this template: it starts in the template's first stage.
        stages.start(organizationId, projectId, template, actor);
        return new Applied(template.id(), template.name(), milestoneCount, taskCount, checklistCount, deliverableCount,
                expected);
    }

    private static String describe(StageSpec stage) {
        return withRole(stage.description(), stage.responsibleRole());
    }

    /** A project task has no field for a role, so the responsible role is written into its description. */
    private static String withRole(String description, String role) {
        if (role == null) {
            return description;
        }
        String responsible = "Responsible role: " + role;
        return description == null || description.isBlank() ? responsible : description + "\n" + responsible;
    }
}
