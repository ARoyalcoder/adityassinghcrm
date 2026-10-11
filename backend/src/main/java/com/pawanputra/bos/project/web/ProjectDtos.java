package com.pawanputra.bos.project.web;

import com.pawanputra.bos.project.internal.ProjectService;
import com.pawanputra.bos.project.internal.ProjectStatusService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class ProjectDtos {

    private ProjectDtos() {
    }

    record CreateProjectRequest(
            @NotBlank @Size(max = 250) String title,
            String description,
            @NotNull UUID customerId,
            @NotBlank @Size(max = 255) String customerName,
            UUID orderId,
            String orderNumber,
            UUID serviceId,
            String serviceName,
            UUID projectManagerId,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            String status,
            String priority,
            @DecimalMin("0.00") BigDecimal budget,
            @DecimalMin("0.00") BigDecimal revenue,
            String notes) {

        ProjectService.CreateProjectCmd toCommand() {
            return new ProjectService.CreateProjectCmd(
                    title,
                    description,
                    customerId,
                    customerName,
                    orderId,
                    orderNumber,
                    serviceId,
                    serviceName,
                    projectManagerId,
                    startDate,
                    expectedCompletionDate,
                    status,
                    priority,
                    budget,
                    revenue,
                    notes);
        }
    }

    record UpdateProjectRequest(
            @Size(max = 250) String title,
            String description,
            UUID projectManagerId,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            LocalDate actualCompletionDate,
            String priority,
            BigDecimal budget,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal materialCost,
            BigDecimal labourCost,
            BigDecimal vendorCost,
            BigDecimal travelCost,
            BigDecimal otherExpenses,
            BigDecimal expectedCost,
            String notes) {

        ProjectService.UpdateProjectCmd toCommand() {
            return new ProjectService.UpdateProjectCmd(
                    title,
                    description,
                    projectManagerId,
                    startDate,
                    expectedCompletionDate,
                    actualCompletionDate,
                    priority,
                    budget,
                    revenue,
                    cost,
                    materialCost,
                    labourCost,
                    vendorCost,
                    travelCost,
                    otherExpenses,
                    expectedCost,
                    notes);
        }
    }

    record UpdateStatusRequest(
            @NotBlank String status,
            String notes) {
    }

    record AddMemberRequest(
            @NotNull UUID userId,
            @NotBlank String role) {

        ProjectService.AddMemberCmd toCommand() {
            return new ProjectService.AddMemberCmd(userId, role);
        }
    }

    record AddMilestoneRequest(
            @NotBlank @Size(max = 250) String title,
            String description,
            @NotNull LocalDate dueDate,
            int displayOrder) {

        ProjectService.AddMilestoneCmd toCommand() {
            return new ProjectService.AddMilestoneCmd(title, description, dueDate, displayOrder);
        }
    }

    record UpdateMilestoneRequest(
            @NotBlank @Size(max = 250) String title,
            String description,
            @NotNull LocalDate dueDate,
            int displayOrder,
            String status) {

        ProjectService.UpdateMilestoneCmd toCommand() {
            return new ProjectService.UpdateMilestoneCmd(title, description, dueDate, displayOrder, status);
        }
    }

    record AddTaskRequest(
            UUID milestoneId,
            @NotBlank @Size(max = 250) String title,
            String description,
            UUID assignedToId,
            String priority,
            LocalDate startDate,
            LocalDate dueDate,
            BigDecimal estimatedHours,
            int displayOrder,
            List<UUID> dependsOnTaskIds) {

        ProjectService.AddTaskCmd toCommand() {
            return new ProjectService.AddTaskCmd(
                    milestoneId,
                    title,
                    description,
                    assignedToId,
                    priority,
                    startDate,
                    dueDate,
                    estimatedHours,
                    displayOrder,
                    dependsOnTaskIds);
        }
    }

    record UpdateTaskRequest(
            UUID milestoneId,
            @NotBlank @Size(max = 250) String title,
            String description,
            UUID assignedToId,
            String priority,
            LocalDate startDate,
            LocalDate dueDate,
            BigDecimal estimatedHours,
            BigDecimal actualHours,
            String status) {

        ProjectService.UpdateTaskCmd toCommand() {
            return new ProjectService.UpdateTaskCmd(
                    milestoneId,
                    title,
                    description,
                    assignedToId,
                    priority,
                    startDate,
                    dueDate,
                    estimatedHours,
                    actualHours,
                    status);
        }
    }

    record AssignTaskRequest(
            UUID assignedToId) {
    }

    record UpdateTaskStatusRequest(
            @NotBlank String status) {
    }

    record AddDependencyRequest(
            @NotNull UUID dependsOnTaskId,
            String dependencyType) {
    }

    record AddChecklistRequest(
            @NotBlank @Size(max = 500) String itemText) {
    }

    record ToggleChecklistRequest(
            boolean completed) {
    }

    record AddDeliverableRequest(
            UUID milestoneId,
            @NotBlank @Size(max = 250) String title,
            String description,
            LocalDate dueDate,
            String acceptanceCriteria,
            String fileUrl) {

        ProjectService.AddDeliverableCmd toCommand() {
            return new ProjectService.AddDeliverableCmd(milestoneId, title, description, dueDate, acceptanceCriteria, fileUrl);
        }
    }

    record UpdateDeliverableStatusRequest(
            @NotBlank String status) {
    }

    record CreateStatusConfigRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            int displayOrder,
            String color,
            String category,
            boolean isDefault,
            boolean isCompleted,
            boolean isCancelled) {

        ProjectStatusService.CreateStatusCmd toCommand() {
            return new ProjectStatusService.CreateStatusCmd(
                    code, name, displayOrder, color, category, isDefault, isCompleted, isCancelled);
        }
    }

    record UpdateStatusConfigRequest(
            @NotBlank @Size(max = 100) String name,
            String color,
            String category,
            boolean isDefault,
            boolean isCompleted,
            boolean isCancelled) {

        ProjectStatusService.UpdateStatusCmd toCommand() {
            return new ProjectStatusService.UpdateStatusCmd(
                    name, color, category, isDefault, isCompleted, isCancelled);
        }
    }

    record ReorderStatusesRequest(
            @NotNull List<UUID> statusIds) {
    }

    record UpdateProfitabilityRequest(
            BigDecimal revenue,
            BigDecimal materialCost,
            BigDecimal labourCost,
            BigDecimal vendorCost,
            BigDecimal travelCost,
            BigDecimal otherExpenses,
            BigDecimal expectedCost) {

        ProjectService.UpdateProfitabilityCmd toCommand() {
            return new ProjectService.UpdateProfitabilityCmd(
                    revenue,
                    materialCost,
                    labourCost,
                    vendorCost,
                    travelCost,
                    otherExpenses,
                    expectedCost);
        }
    }
}
