package com.pawanputra.bos.quotation.web;

import com.pawanputra.bos.quotation.internal.QuotationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class QuotationDtos {

    private QuotationDtos() {
    }

    record ItemRequest(
            UUID serviceId,
            @NotBlank @Size(max = 255) String serviceName,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @Min(0) @Max(100) BigDecimal discountPercentage,
            @Min(0) @Max(100) BigDecimal taxPercentage) {

        QuotationService.ItemInput toInput() {
            return new QuotationService.ItemInput(
                    serviceId,
                    serviceName.trim(),
                    description != null ? description.trim() : null,
                    quantity,
                    unitPrice,
                    discountPercentage,
                    taxPercentage);
        }
    }

    record CreateQuotationRequest(
            @NotBlank @Size(max = 250) String title,
            UUID customerId,
            @Size(max = 255) String customerName,
            UUID leadId,
            @Size(max = 255) String leadName,
            UUID opportunityId,
            @Size(max = 255) String opportunityTitle,
            UUID ownerId,
            @Min(0) @Max(100) BigDecimal discountPercentage,
            @Min(0) @Max(100) BigDecimal taxPercentage,
            LocalDate validUntil,
            String terms,
            String notes,
            LocalDate nextFollowUpDate,
            @NotEmpty List<@Valid ItemRequest> items) {

        QuotationService.CreateCommand toCommand() {
            return new QuotationService.CreateCommand(
                    title.trim(),
                    customerId,
                    customerName != null ? customerName.trim() : null,
                    leadId,
                    leadName != null ? leadName.trim() : null,
                    opportunityId,
                    opportunityTitle != null ? opportunityTitle.trim() : null,
                    ownerId,
                    discountPercentage,
                    taxPercentage,
                    validUntil,
                    terms != null ? terms.trim() : null,
                    notes != null ? notes.trim() : null,
                    nextFollowUpDate,
                    items != null ? items.stream().map(ItemRequest::toInput).toList() : List.of());
        }
    }

    record UpdateDraftRequest(
            @Size(max = 250) String title,
            UUID customerId,
            @Size(max = 255) String customerName,
            UUID leadId,
            @Size(max = 255) String leadName,
            UUID opportunityId,
            @Size(max = 255) String opportunityTitle,
            UUID ownerId,
            @Min(0) @Max(100) BigDecimal discountPercentage,
            @Min(0) @Max(100) BigDecimal taxPercentage,
            LocalDate validUntil,
            String terms,
            String notes,
            LocalDate nextFollowUpDate,
            List<@Valid ItemRequest> items) {

        QuotationService.UpdateDraftCommand toCommand() {
            return new QuotationService.UpdateDraftCommand(
                    title != null ? title.trim() : null,
                    customerId,
                    customerName != null ? customerName.trim() : null,
                    leadId,
                    leadName != null ? leadName.trim() : null,
                    opportunityId,
                    opportunityTitle != null ? opportunityTitle.trim() : null,
                    ownerId,
                    discountPercentage,
                    taxPercentage,
                    validUntil,
                    terms != null ? terms.trim() : null,
                    notes != null ? notes.trim() : null,
                    nextFollowUpDate,
                    items != null ? items.stream().map(ItemRequest::toInput).toList() : null);
        }
    }

    record CreateVersionRequest(
            @Min(0) @Max(100) BigDecimal discountPercentage,
            @Min(0) @Max(100) BigDecimal taxPercentage,
            LocalDate validUntil,
            String terms,
            String notes,
            @Size(max = 500) String changeSummary,
            LocalDate nextFollowUpDate,
            @NotEmpty List<@Valid ItemRequest> items) {

        QuotationService.CreateVersionCommand toCommand() {
            return new QuotationService.CreateVersionCommand(
                    discountPercentage,
                    taxPercentage,
                    validUntil,
                    terms != null ? terms.trim() : null,
                    notes != null ? notes.trim() : null,
                    changeSummary != null ? changeSummary.trim() : null,
                    nextFollowUpDate,
                    items.stream().map(ItemRequest::toInput).toList());
        }
    }

    record ApprovalActionRequest(@Size(max = 1000) String notes, @Size(max = 1000) String reason) {
    }

    record SendQuotationRequest(
            LocalDate nextFollowUpDate,
            @Size(max = 1000) String notes) {
    }

    record ClientResponseRequest(@Size(max = 1000) String reason, @Size(max = 1000) String notes) {
    }
}
