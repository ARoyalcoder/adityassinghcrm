package com.pawanputra.bos.quotation.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationDirectory {

    record QuotationItemSummary(
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
    }

    record QuotationForOrder(
            UUID quotationId,
            String quotationNumber,
            int versionNumber,
            QuotationStatus status,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID opportunityId,
            String opportunityTitle,
            UUID ownerId,
            String ownerName,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal finalAmount,
            String terms,
            String notes,
            List<QuotationItemSummary> items) {
    }

    Optional<QuotationForOrder> findQuotationForOrder(UUID organizationId, UUID quotationId);
}
