package com.pawanputra.bos.order.internal;

import com.pawanputra.bos.order.api.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OrderViews {

    private OrderViews() {
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
            BigDecimal totalAmount,
            BigDecimal deliveredQuantity) {

        public static ItemView from(OrderItem i) {
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
                    i.getTotalAmount(),
                    i.getDeliveredQuantity());
        }
    }

    public record HistoryView(
            UUID id,
            String action,
            String fromStatus,
            String toStatus,
            String notes,
            String performedBy,
            Instant performedAt) {

        public static HistoryView from(OrderHistory h) {
            return new HistoryView(
                    h.getId(),
                    h.getAction(),
                    h.getFromStatus(),
                    h.getToStatus(),
                    h.getNotes(),
                    h.getPerformedBy(),
                    h.getPerformedAt());
        }
    }

    public record Summary(
            UUID id,
            String orderNumber,
            String title,
            UUID customerId,
            String customerName,
            UUID quotationId,
            String quotationNumber,
            Integer quotationVersionNumber,
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal finalAmount,
            UUID ownerId,
            String ownerName,
            LocalDate deliveryDate,
            int itemCount,
            Instant createdAt) {

        public static Summary from(Order o) {
            return new Summary(
                    o.getId(),
                    o.getOrderNumber(),
                    o.getTitle(),
                    o.getCustomerId(),
                    o.getCustomerName(),
                    o.getQuotationId(),
                    o.getQuotationNumber(),
                    o.getQuotationVersionNumber(),
                    o.getStatus(),
                    o.getSubtotal(),
                    o.getDiscountAmount(),
                    o.getTaxAmount(),
                    o.getFinalAmount(),
                    o.getOwnerId(),
                    o.getOwnerName(),
                    o.getDeliveryDate(),
                    o.getItems() != null ? o.getItems().size() : 0,
                    o.getCreatedAt());
        }
    }

    public record Detail(
            UUID id,
            String orderNumber,
            String title,
            UUID customerId,
            String customerName,
            UUID quotationId,
            String quotationNumber,
            Integer quotationVersionNumber,
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal finalAmount,
            UUID ownerId,
            String ownerName,
            LocalDate deliveryDate,
            String notes,
            String terms,
            String cancellationReason,
            Instant cancelledAt,
            String cancelledBy,
            Instant completedAt,
            String completedBy,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            List<ItemView> items,
            List<HistoryView> history) {

        public static Detail from(Order o, List<ItemView> items, List<HistoryView> history) {
            return new Detail(
                    o.getId(),
                    o.getOrderNumber(),
                    o.getTitle(),
                    o.getCustomerId(),
                    o.getCustomerName(),
                    o.getQuotationId(),
                    o.getQuotationNumber(),
                    o.getQuotationVersionNumber(),
                    o.getStatus(),
                    o.getSubtotal(),
                    o.getDiscountAmount(),
                    o.getTaxAmount(),
                    o.getFinalAmount(),
                    o.getOwnerId(),
                    o.getOwnerName(),
                    o.getDeliveryDate(),
                    o.getNotes(),
                    o.getTerms(),
                    o.getCancellationReason(),
                    o.getCancelledAt(),
                    o.getCancelledBy(),
                    o.getCompletedAt(),
                    o.getCompletedBy(),
                    o.getCreatedAt(),
                    o.getCreatedBy(),
                    o.getUpdatedAt(),
                    o.getUpdatedBy(),
                    items,
                    history);
        }
    }

    public record Dashboard(
            long totalOrders,
            BigDecimal totalValue,
            long confirmedCount,
            long inProgressCount,
            long partiallyDeliveredCount,
            long completedCount,
            long cancelledCount,
            List<Summary> recentOrders) {
    }
}
