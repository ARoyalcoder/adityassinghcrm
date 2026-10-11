package com.pawanputra.bos.quotation.internal;

import com.pawanputra.bos.quotation.api.QuotationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class QuotationViews {

    private QuotationViews() {
    }

    public record ItemView(
            UUID id,
            int itemOrder,
            UUID serviceId,
            String serviceName,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercentage,
            BigDecimal discountAmount,
            BigDecimal taxPercentage,
            BigDecimal taxAmount,
            BigDecimal subtotal,
            BigDecimal totalAmount) {

        public static ItemView from(QuotationItem i) {
            return new ItemView(
                    i.getId(),
                    i.getItemOrder(),
                    i.getServiceId(),
                    i.getServiceName(),
                    i.getDescription(),
                    i.getQuantity(),
                    i.getUnitPrice(),
                    i.getDiscountPercentage(),
                    i.getDiscountAmount(),
                    i.getTaxPercentage(),
                    i.getTaxAmount(),
                    i.getSubtotal(),
                    i.getTotalAmount());
        }
    }

    public record VersionSummary(
            UUID id,
            int versionNumber,
            BigDecimal subtotal,
            BigDecimal discountPercentage,
            BigDecimal discountAmount,
            BigDecimal taxPercentage,
            BigDecimal taxAmount,
            BigDecimal finalAmount,
            String terms,
            LocalDate validUntil,
            String notes,
            String pdfStorageKey,
            Instant pdfGeneratedAt,
            String changeSummary,
            Instant createdAt,
            String createdBy,
            int itemCount) {

        public static VersionSummary from(QuotationVersion v) {
            return new VersionSummary(
                    v.getId(),
                    v.getVersionNumber(),
                    v.getSubtotal(),
                    v.getDiscountPercentage(),
                    v.getDiscountAmount(),
                    v.getTaxPercentage(),
                    v.getTaxAmount(),
                    v.getFinalAmount(),
                    v.getTerms(),
                    v.getValidUntil(),
                    v.getNotes(),
                    v.getPdfStorageKey(),
                    v.getPdfGeneratedAt(),
                    v.getChangeSummary(),
                    v.getCreatedAt(),
                    v.getCreatedBy(),
                    v.getItems() != null ? v.getItems().size() : 0);
        }
    }

    public record VersionDetail(
            UUID id,
            int versionNumber,
            BigDecimal subtotal,
            BigDecimal discountPercentage,
            BigDecimal discountAmount,
            BigDecimal taxPercentage,
            BigDecimal taxAmount,
            BigDecimal finalAmount,
            String terms,
            LocalDate validUntil,
            String notes,
            String pdfStorageKey,
            Instant pdfGeneratedAt,
            String changeSummary,
            Instant createdAt,
            String createdBy,
            List<ItemView> items) {

        public static VersionDetail from(QuotationVersion v) {
            return new VersionDetail(
                    v.getId(),
                    v.getVersionNumber(),
                    v.getSubtotal(),
                    v.getDiscountPercentage(),
                    v.getDiscountAmount(),
                    v.getTaxPercentage(),
                    v.getTaxAmount(),
                    v.getFinalAmount(),
                    v.getTerms(),
                    v.getValidUntil(),
                    v.getNotes(),
                    v.getPdfStorageKey(),
                    v.getPdfGeneratedAt(),
                    v.getChangeSummary(),
                    v.getCreatedAt(),
                    v.getCreatedBy(),
                    v.getItems().stream().map(ItemView::from).toList());
        }
    }

    public record AuditLogView(
            UUID id,
            String action,
            QuotationStatus fromStatus,
            QuotationStatus toStatus,
            Integer versionNumber,
            String notes,
            String performedBy,
            Instant performedAt) {

        public static AuditLogView from(QuotationAuditLog a) {
            return new AuditLogView(
                    a.getId(),
                    a.getAction(),
                    a.getFromStatus(),
                    a.getToStatus(),
                    a.getVersionNumber(),
                    a.getNotes(),
                    a.getPerformedBy(),
                    a.getPerformedAt());
        }
    }

    public record Summary(
            UUID id,
            String quotationNumber,
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID opportunityId,
            String opportunityTitle,
            QuotationStatus status,
            int currentVersionNumber,
            LocalDate validUntil,
            UUID ownerId,
            String ownerName,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal finalAmount,
            LocalDate nextFollowUpDate,
            Instant createdAt,
            Instant updatedAt) {

        public static Summary from(Quotation q) {
            return new Summary(
                    q.getId(),
                    q.getQuotationNumber(),
                    q.getTitle(),
                    q.getCustomerId(),
                    q.getCustomerName(),
                    q.getLeadId(),
                    q.getLeadName(),
                    q.getOpportunityId(),
                    q.getOpportunityTitle(),
                    q.getStatus(),
                    q.getCurrentVersionNumber(),
                    q.getValidUntil(),
                    q.getOwnerId(),
                    q.getOwnerName(),
                    q.getSubtotal(),
                    q.getDiscountAmount(),
                    q.getTaxAmount(),
                    q.getFinalAmount(),
                    q.getNextFollowUpDate(),
                    q.getCreatedAt(),
                    q.getUpdatedAt());
        }
    }

    public record Detail(
            UUID id,
            String quotationNumber,
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID opportunityId,
            String opportunityTitle,
            QuotationStatus status,
            int currentVersionNumber,
            UUID currentVersionId,
            LocalDate validUntil,
            UUID ownerId,
            String ownerName,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal finalAmount,
            boolean approvalRequired,
            Instant approvalRequestedAt,
            String approvalRequestedBy,
            Instant approvedAt,
            String approvedBy,
            Instant rejectedAt,
            String rejectedBy,
            String rejectionReason,
            Instant sentAt,
            String sentBy,
            Instant viewedAt,
            Instant acceptedAt,
            String acceptedBy,
            String clientRejectionReason,
            Instant cancelledAt,
            String cancellationReason,
            LocalDate nextFollowUpDate,
            VersionDetail currentVersion,
            List<VersionSummary> allVersions,
            List<AuditLogView> auditLogs,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        public static Detail from(
                Quotation q,
                VersionDetail currentVersion,
                List<VersionSummary> allVersions,
                List<AuditLogView> auditLogs) {
            return new Detail(
                    q.getId(),
                    q.getQuotationNumber(),
                    q.getTitle(),
                    q.getCustomerId(),
                    q.getCustomerName(),
                    q.getLeadId(),
                    q.getLeadName(),
                    q.getOpportunityId(),
                    q.getOpportunityTitle(),
                    q.getStatus(),
                    q.getCurrentVersionNumber(),
                    q.getCurrentVersionId(),
                    q.getValidUntil(),
                    q.getOwnerId(),
                    q.getOwnerName(),
                    q.getSubtotal(),
                    q.getDiscountAmount(),
                    q.getTaxAmount(),
                    q.getFinalAmount(),
                    q.isApprovalRequired(),
                    q.getApprovalRequestedAt(),
                    q.getApprovalRequestedBy(),
                    q.getApprovedAt(),
                    q.getApprovedBy(),
                    q.getRejectedAt(),
                    q.getRejectedBy(),
                    q.getRejectionReason(),
                    q.getSentAt(),
                    q.getSentBy(),
                    q.getViewedAt(),
                    q.getAcceptedAt(),
                    q.getAcceptedBy(),
                    q.getClientRejectionReason(),
                    q.getCancelledAt(),
                    q.getCancellationReason(),
                    q.getNextFollowUpDate(),
                    currentVersion,
                    allVersions,
                    auditLogs,
                    q.getCreatedAt(),
                    q.getUpdatedAt(),
                    q.getVersion());
        }
    }

    public record Dashboard(
            long totalQuotations,
            BigDecimal totalValue,
            long draftCount,
            long pendingApprovalCount,
            long sentCount,
            long acceptedCount,
            long rejectedCount,
            long expiredCount,
            long todaysFollowUpsCount,
            List<Summary> todaysFollowUps) {
    }
}
