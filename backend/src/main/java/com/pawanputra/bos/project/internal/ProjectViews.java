package com.pawanputra.bos.project.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProjectViews {

    private ProjectViews() {
    }

    public record StatusView(
            UUID id,
            String code,
            String name,
            int displayOrder,
            String color,
            String category,
            boolean isDefault,
            boolean isCompleted,
            boolean isCancelled) {

        public static StatusView from(ProjectStatusConfig s) {
            return new StatusView(
                    s.getId(),
                    s.getCode(),
                    s.getName(),
                    s.getDisplayOrder(),
                    s.getColor(),
                    s.getCategory(),
                    s.isDefault(),
                    s.isCompleted(),
                    s.isCancelled());
        }
    }

    public record MemberView(
            UUID id,
            UUID userId,
            String userName,
            String userEmail,
            String role,
            Instant joinedAt,
            String assignedBy) {

        public static MemberView from(ProjectMember m) {
            return new MemberView(
                    m.getId(),
                    m.getUserId(),
                    m.getUserName(),
                    m.getUserEmail(),
                    m.getRole(),
                    m.getJoinedAt(),
                    m.getAssignedBy());
        }
    }

    public record ChecklistItemView(
            UUID id,
            UUID taskId,
            String itemText,
            boolean completed,
            Instant completedAt,
            String completedBy,
            int displayOrder) {

        public static ChecklistItemView from(ChecklistItem c) {
            return new ChecklistItemView(
                    c.getId(),
                    c.getTask().getId(),
                    c.getItemText(),
                    c.isCompleted(),
                    c.getCompletedAt(),
                    c.getCompletedBy(),
                    c.getDisplayOrder());
        }
    }

    public record TaskDependencyView(
            UUID id,
            UUID taskId,
            UUID dependsOnTaskId,
            String dependsOnTaskTitle,
            String dependsOnTaskStatus,
            String dependencyType) {

        public static TaskDependencyView from(TaskDependency d) {
            return new TaskDependencyView(
                    d.getId(),
                    d.getTask().getId(),
                    d.getDependsOnTask().getId(),
                    d.getDependsOnTask().getTitle(),
                    d.getDependsOnTask().getStatus(),
                    d.getDependencyType());
        }
    }

    public record TaskView(
            UUID id,
            UUID projectId,
            UUID milestoneId,
            String milestoneTitle,
            String title,
            String description,
            UUID assignedToId,
            String assignedToName,
            String status,
            String priority,
            LocalDate startDate,
            LocalDate dueDate,
            Instant completedAt,
            BigDecimal estimatedHours,
            BigDecimal actualHours,
            int displayOrder,
            List<ChecklistItemView> checklists,
            List<TaskDependencyView> dependencies) {

        public static TaskView from(ProjectTask t, List<ChecklistItemView> checklists, List<TaskDependencyView> deps) {
            return new TaskView(
                    t.getId(),
                    t.getProject().getId(),
                    t.getMilestone() != null ? t.getMilestone().getId() : null,
                    t.getMilestone() != null ? t.getMilestone().getTitle() : null,
                    t.getTitle(),
                    t.getDescription(),
                    t.getAssignedToId(),
                    t.getAssignedToName(),
                    t.getStatus(),
                    t.getPriority(),
                    t.getStartDate(),
                    t.getDueDate(),
                    t.getCompletedAt(),
                    t.getEstimatedHours(),
                    t.getActualHours(),
                    t.getDisplayOrder(),
                    checklists,
                    deps);
        }
    }

    public record MilestoneView(
            UUID id,
            UUID projectId,
            String title,
            String description,
            LocalDate dueDate,
            LocalDate completedDate,
            String status,
            int displayOrder,
            int progressPercentage,
            long taskCount,
            long completedTaskCount) {

        public static MilestoneView from(Milestone m, long taskCount, long completedTaskCount) {
            return new MilestoneView(
                    m.getId(),
                    m.getProject().getId(),
                    m.getTitle(),
                    m.getDescription(),
                    m.getDueDate(),
                    m.getCompletedDate(),
                    m.getStatus(),
                    m.getDisplayOrder(),
                    m.getProgressPercentage(),
                    taskCount,
                    completedTaskCount);
        }
    }

    public record DeliverableView(
            UUID id,
            UUID projectId,
            UUID milestoneId,
            String milestoneTitle,
            String title,
            String description,
            String status,
            LocalDate dueDate,
            LocalDate deliveredDate,
            String acceptanceCriteria,
            String fileUrl,
            String reviewedBy,
            Instant reviewedAt) {

        public static DeliverableView from(Deliverable d) {
            return new DeliverableView(
                    d.getId(),
                    d.getProject().getId(),
                    d.getMilestone() != null ? d.getMilestone().getId() : null,
                    d.getMilestone() != null ? d.getMilestone().getTitle() : null,
                    d.getTitle(),
                    d.getDescription(),
                    d.getStatus(),
                    d.getDueDate(),
                    d.getDeliveredDate(),
                    d.getAcceptanceCriteria(),
                    d.getFileUrl(),
                    d.getReviewedBy(),
                    d.getReviewedAt());
        }
    }

    public record ProjectSummary(
            UUID id,
            String projectNumber,
            String title,
            String description,
            UUID customerId,
            String customerName,
            UUID orderId,
            String orderNumber,
            UUID serviceId,
            String serviceName,
            UUID projectManagerId,
            String projectManagerName,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            LocalDate actualCompletionDate,
            String status,
            String priority,
            BigDecimal budget,
            BigDecimal revenue,
            BigDecimal cost,
            int progressPercentage,
            int memberCount,
            int milestoneCount,
            int taskCount,
            int completedTaskCount,
            int deliverableCount,
            Instant createdAt) {

        public static ProjectSummary from(Project p) {
            int completedTasks = (int) p.getTasks().stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .count();

            return new ProjectSummary(
                    p.getId(),
                    p.getProjectNumber(),
                    p.getTitle(),
                    p.getDescription(),
                    p.getCustomerId(),
                    p.getCustomerName(),
                    p.getOrderId(),
                    p.getOrderNumber(),
                    p.getServiceId(),
                    p.getServiceName(),
                    p.getProjectManagerId(),
                    p.getProjectManagerName(),
                    p.getStartDate(),
                    p.getExpectedCompletionDate(),
                    p.getActualCompletionDate(),
                    p.getStatus(),
                    p.getPriority(),
                    p.getBudget(),
                    p.getRevenue(),
                    p.getCost(),
                    p.getProgressPercentage(),
                    p.getMembers().size(),
                    p.getMilestones().size(),
                    p.getTasks().size(),
                    completedTasks,
                    p.getDeliverables().size(),
                    p.getCreatedAt());
        }
    }

    public record ProjectDetail(
            UUID id,
            String projectNumber,
            String title,
            String description,
            UUID customerId,
            String customerName,
            UUID orderId,
            String orderNumber,
            UUID serviceId,
            String serviceName,
            UUID projectManagerId,
            String projectManagerName,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            LocalDate actualCompletionDate,
            String status,
            String priority,
            BigDecimal budget,
            BigDecimal revenue,
            BigDecimal cost,
            int progressPercentage,
            String notes,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            List<MemberView> members,
            List<MilestoneView> milestones,
            List<TaskView> tasks,
            List<DeliverableView> deliverables,
            ProjectHealth health) {
    }

    public record KanbanColumn(
            StatusView status,
            List<ProjectSummary> projects,
            BigDecimal totalBudget,
            BigDecimal totalRevenue) {
    }

    public record KanbanBoard(
            List<KanbanColumn> columns) {
    }

    public record TimelineItem(
            String id,
            String type, // MILESTONE or TASK
            String title,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int progressPercentage,
            String assigneeName,
            List<UUID> dependencyIds) {
    }

    public record ProjectTimeline(
            UUID projectId,
            String projectNumber,
            String title,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            List<TimelineItem> items) {
    }

    public record Dashboard(
            long totalProjects,
            BigDecimal totalBudget,
            BigDecimal totalRevenue,
            long activeProjects,
            long completedProjects,
            long delayedProjects,
            List<ProjectSummary> recentProjects) {
    }

    public record ProjectProfitability(
            BigDecimal revenue,
            BigDecimal materialCost,
            BigDecimal labourCost,
            BigDecimal vendorCost,
            BigDecimal travelCost,
            BigDecimal otherExpenses,
            BigDecimal actualCost,
            BigDecimal expectedCost,
            BigDecimal grossProfit,
            BigDecimal grossMargin,
            BigDecimal projectedFinalCost) {
    }

    public record RiskAlert(
            String code, // e.g. BUDGET_OVERRUN, SCHEDULE_DELAY, MARGIN_DECLINE, TASK_BOTTLENECK, APPROVAL_PENDING
            String severity, // CRITICAL, WARNING, INFO
            String title,
            String description) {
    }

    public record ProjectHealth(
            int overallScore,
            int scheduleHealth,
            int budgetHealth,
            int taskHealth,
            int clientApprovalHealth,
            int teamCapacityHealth,
            ProjectProfitability profitability,
            List<RiskAlert> riskAlerts) {
    }
}
