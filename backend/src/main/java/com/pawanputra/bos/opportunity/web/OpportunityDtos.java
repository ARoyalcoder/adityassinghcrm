package com.pawanputra.bos.opportunity.web;

import com.pawanputra.bos.opportunity.internal.OpportunityService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

final class OpportunityDtos {

    private OpportunityDtos() {
    }

    record CreateOpportunityRequest(
            @NotBlank @Size(max = 250) String title,
            UUID customerId,
            @Size(max = 255) String customerName,
            UUID leadId,
            @Size(max = 255) String leadName,
            UUID serviceId,
            @Size(max = 255) String serviceName,
            @Size(max = 50) String verticalCode,
            @DecimalMin("0.00") BigDecimal value,
            @Min(0) @Max(100) Integer probability,
            LocalDate expectedCloseDate,
            UUID ownerId,
            UUID pipelineId,
            UUID stageId) {

        OpportunityService.CreateCommand toCommand() {
            return new OpportunityService.CreateCommand(
                    title.trim(),
                    customerId,
                    customerName != null ? customerName.trim() : null,
                    leadId,
                    leadName != null ? leadName.trim() : null,
                    serviceId,
                    serviceName != null ? serviceName.trim() : null,
                    verticalCode != null ? verticalCode.trim() : null,
                    value != null ? value : BigDecimal.ZERO,
                    probability,
                    expectedCloseDate,
                    ownerId,
                    pipelineId,
                    stageId);
        }
    }

    record UpdateOpportunityRequest(
            @Size(max = 250) String title,
            UUID customerId,
            @Size(max = 255) String customerName,
            UUID leadId,
            @Size(max = 255) String leadName,
            UUID serviceId,
            @Size(max = 255) String serviceName,
            @Size(max = 50) String verticalCode,
            @DecimalMin("0.00") BigDecimal value,
            @Min(0) @Max(100) Integer probability,
            LocalDate expectedCloseDate,
            UUID ownerId) {

        OpportunityService.UpdateCommand toCommand() {
            return new OpportunityService.UpdateCommand(
                    title != null ? title.trim() : null,
                    customerId,
                    customerName != null ? customerName.trim() : null,
                    leadId,
                    leadName != null ? leadName.trim() : null,
                    serviceId,
                    serviceName != null ? serviceName.trim() : null,
                    verticalCode != null ? verticalCode.trim() : null,
                    value,
                    probability,
                    expectedCloseDate,
                    ownerId);
        }
    }

    record ChangeStageRequest(
            @NotNull UUID stageId,
            @Size(max = 1000) String notes,
            @Size(max = 500) String lossReason) {

        OpportunityService.ChangeStageCommand toCommand() {
            return new OpportunityService.ChangeStageCommand(
                    stageId,
                    notes != null ? notes.trim() : null,
                    lossReason != null ? lossReason.trim() : null);
        }
    }

    record AddNoteRequest(@NotBlank @Size(max = 4000) String body) {
    }
}
