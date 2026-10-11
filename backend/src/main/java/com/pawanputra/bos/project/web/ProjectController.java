package com.pawanputra.bos.project.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import com.pawanputra.bos.project.internal.ProjectService;
import com.pawanputra.bos.project.internal.ProjectViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/projects")
@Tag(name = "Projects")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ProjectController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.PROJECT_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.PROJECT_CREATE + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.PROJECT_UPDATE + "')";
    private static final String CAN_DELETE = "hasAuthority('" + Permissions.PROJECT_DELETE + "')";

    // Map.of stops at ten entries.
    private static final Map<String, String> SORTS = Map.ofEntries(
            Map.entry("projectNumber", "projectNumber"),
            Map.entry("title", "title"),
            Map.entry("customerName", "customerName"),
            Map.entry("startDate", "startDate"),
            Map.entry("expectedCompletionDate", "expectedCompletionDate"),
            Map.entry("status", "status"),
            Map.entry("priority", "priority"),
            Map.entry("progressPercentage", "progressPercentage"),
            Map.entry("budget", "budget"),
            Map.entry("revenue", "revenue"),
            Map.entry("createdAt", "createdAt"));
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "List projects with filtering and pagination")
    public PageResponse<ProjectViews.ProjectSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID projectManagerId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser user = CurrentUser.require();
        Pageable pageable = Paging.of(page, size, sort, SORTS, DEFAULT_SORT);
        ProjectService.ProjectFilter filter = new ProjectService.ProjectFilter(
                status, customerId, projectManagerId, priority, search);

        Page<ProjectViews.ProjectSummary> result = projectService.findPage(user.organizationId(), filter, pageable);
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/kanban")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get Kanban board columns grouped by configurable statuses")
    public ProjectViews.KanbanBoard getKanban() {
        CurrentUser user = CurrentUser.require();
        return projectService.getKanbanBoard(user.organizationId());
    }

    @GetMapping("/dashboard")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get project dashboard KPIs")
    public ProjectViews.Dashboard getDashboard() {
        CurrentUser user = CurrentUser.require();
        return projectService.getDashboard(user.organizationId());
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get project detail including milestones, tasks, members, and deliverables")
    public ProjectViews.ProjectDetail getById(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return projectService.getProject(user.organizationId(), id);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get chronological project timeline roadmap data")
    public ProjectViews.ProjectTimeline getTimeline(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return projectService.getTimeline(user.organizationId(), id);
    }

    @GetMapping("/{id}/health")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get project health intelligence scores and risk alerts")
    public ProjectViews.ProjectHealth getHealth(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return projectService.getHealth(user.organizationId(), id);
    }

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create a new project")
    public ResponseEntity<ProjectViews.ProjectDetail> create(
            @Valid @RequestBody ProjectDtos.CreateProjectRequest request) {
        CurrentUser user = CurrentUser.require();
        ProjectViews.ProjectDetail created = projectService.createProject(
                user.organizationId(),
                request.toCommand(),
                user.id(),
                user.email());
        return ResponseEntity
                .created(URI.create(ApiPaths.V1 + "/projects/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update project attributes")
    public ProjectViews.ProjectDetail update(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.UpdateProjectRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.updateProject(
                user.organizationId(),
                id,
                request.toCommand(),
                user.email());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update project status (Kanban drag-and-drop or status selector)")
    public ProjectViews.ProjectDetail updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.UpdateStatusRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.updateProjectStatus(
                user.organizationId(),
                id,
                request.status(),
                request.notes(),
                user.email());
    }

    @PutMapping("/{id}/profitability")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update project revenue and detailed cost breakdowns")
    public ProjectViews.ProjectDetail updateProfitability(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.UpdateProfitabilityRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.updateProfitability(
                user.organizationId(),
                id,
                request.toCommand(),
                user.email());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Delete / archive project")
    public void delete(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        projectService.deleteProject(user.organizationId(), id, user.email());
    }

    // --- Member endpoints ---

    @PostMapping("/{id}/members")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add member to project")
    public ProjectViews.MemberView addMember(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.AddMemberRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.addMember(user.organizationId(), id, request.toCommand(), user.email());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Remove member from project")
    public void removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        CurrentUser user = CurrentUser.require();
        projectService.removeMember(user.organizationId(), id, userId, user.email());
    }

    // --- Milestone endpoints ---

    @PostMapping("/{id}/milestones")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add milestone to project")
    public ProjectViews.MilestoneView addMilestone(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.AddMilestoneRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.addMilestone(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PutMapping("/{id}/milestones/{milestoneId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update milestone")
    public ProjectViews.MilestoneView updateMilestone(
            @PathVariable UUID id,
            @PathVariable UUID milestoneId,
            @Valid @RequestBody ProjectDtos.UpdateMilestoneRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.updateMilestone(user.organizationId(), id, milestoneId, request.toCommand(), user.email());
    }

    @PostMapping("/{id}/milestones/{milestoneId}/complete")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Mark milestone completed")
    public ProjectViews.MilestoneView completeMilestone(
            @PathVariable UUID id,
            @PathVariable UUID milestoneId) {
        CurrentUser user = CurrentUser.require();
        return projectService.completeMilestone(user.organizationId(), id, milestoneId, user.email());
    }

    @DeleteMapping("/{id}/milestones/{milestoneId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Delete milestone")
    public void deleteMilestone(
            @PathVariable UUID id,
            @PathVariable UUID milestoneId) {
        CurrentUser user = CurrentUser.require();
        projectService.deleteMilestone(user.organizationId(), id, milestoneId);
    }

    // --- Task endpoints ---

    @PostMapping("/{id}/tasks")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add task to project")
    public ProjectViews.TaskView addTask(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.AddTaskRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.addTask(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PutMapping("/{id}/tasks/{taskId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update task details")
    public ProjectViews.TaskView updateTask(
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @Valid @RequestBody ProjectDtos.UpdateTaskRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.updateTask(user.organizationId(), id, taskId, request.toCommand(), user.email());
    }

    @PatchMapping("/{id}/tasks/{taskId}/assign")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Assign task to user")
    public ProjectViews.TaskView assignTask(
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @Valid @RequestBody ProjectDtos.AssignTaskRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.assignTask(user.organizationId(), id, taskId, request.assignedToId(), user.email());
    }

    @PatchMapping("/{id}/tasks/{taskId}/status")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update task status")
    public ProjectViews.TaskView updateTaskStatus(
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @Valid @RequestBody ProjectDtos.UpdateTaskStatusRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.updateTaskStatus(user.organizationId(), id, taskId, request.status(), user.email());
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Delete task")
    public void deleteTask(
            @PathVariable UUID id,
            @PathVariable UUID taskId) {
        CurrentUser user = CurrentUser.require();
        projectService.deleteTask(user.organizationId(), id, taskId);
    }

    @PostMapping("/{id}/tasks/{taskId}/dependencies")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add task dependency")
    public void addDependency(
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @Valid @RequestBody ProjectDtos.AddDependencyRequest request) {
        CurrentUser user = CurrentUser.require();
        projectService.addDependency(
                user.organizationId(),
                id,
                taskId,
                request.dependsOnTaskId(),
                request.dependencyType(),
                user.email());
    }

    @DeleteMapping("/{id}/tasks/{taskId}/dependencies/{dependsOnTaskId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Remove task dependency")
    public void removeDependency(
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @PathVariable UUID dependsOnTaskId) {
        CurrentUser user = CurrentUser.require();
        projectService.removeDependency(user.organizationId(), id, taskId, dependsOnTaskId);
    }

    @PostMapping("/{id}/tasks/{taskId}/checklists")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add checklist item to task")
    public ProjectViews.ChecklistItemView addChecklistItem(
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @Valid @RequestBody ProjectDtos.AddChecklistRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.addChecklistItem(user.organizationId(), id, taskId, request.itemText(), user.email());
    }

    @PatchMapping("/{id}/tasks/{taskId}/checklists/{itemId}/toggle")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Toggle checklist item completion")
    public ProjectViews.ChecklistItemView toggleChecklistItem(
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ProjectDtos.ToggleChecklistRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.toggleChecklistItem(user.organizationId(), id, taskId, itemId, request.completed(), user.email());
    }

    @DeleteMapping("/{id}/tasks/{taskId}/checklists/{itemId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Delete checklist item")
    public void deleteChecklistItem(
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @PathVariable UUID itemId) {
        CurrentUser user = CurrentUser.require();
        projectService.deleteChecklistItem(user.organizationId(), id, taskId, itemId);
    }

    // --- Deliverable endpoints ---

    @PostMapping("/{id}/deliverables")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add deliverable to project")
    public ProjectViews.DeliverableView addDeliverable(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.AddDeliverableRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.addDeliverable(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PatchMapping("/{id}/deliverables/{delivId}/status")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update deliverable review status")
    public ProjectViews.DeliverableView updateDeliverableStatus(
            @PathVariable UUID id,
            @PathVariable UUID delivId,
            @Valid @RequestBody ProjectDtos.UpdateDeliverableStatusRequest request) {
        CurrentUser user = CurrentUser.require();
        return projectService.updateDeliverableStatus(user.organizationId(), id, delivId, request.status(), user.email());
    }

    @DeleteMapping("/{id}/deliverables/{delivId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Delete deliverable")
    public void deleteDeliverable(
            @PathVariable UUID id,
            @PathVariable UUID delivId) {
        CurrentUser user = CurrentUser.require();
        projectService.deleteDeliverable(user.organizationId(), id, delivId);
    }
}
