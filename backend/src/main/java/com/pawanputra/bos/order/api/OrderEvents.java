package com.pawanputra.bos.order.api;

import com.pawanputra.bos.platform.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OrderEvents {

    private OrderEvents() {
    }

    public record OrderCreatedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID orderId,
            UUID organizationId,
            String orderNumber,
            String title,
            UUID customerId,
            String customerName,
            UUID quotationId,
            String quotationNumber,
            BigDecimal finalAmount,
            OrderStatus status,
            UUID ownerId,
            String createdBy) implements DomainEvent {

        public static OrderCreatedEvent of(
                UUID orderId,
                UUID organizationId,
                String orderNumber,
                String title,
                UUID customerId,
                String customerName,
                UUID quotationId,
                String quotationNumber,
                BigDecimal finalAmount,
                OrderStatus status,
                UUID ownerId,
                String createdBy) {
            return new OrderCreatedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    orderId,
                    organizationId,
                    orderNumber,
                    title,
                    customerId,
                    customerName,
                    quotationId,
                    quotationNumber,
                    finalAmount,
                    status,
                    ownerId,
                    createdBy);
        }
    }

    public record OrderStatusChangedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID orderId,
            UUID organizationId,
            String orderNumber,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String notes,
            String changedBy) implements DomainEvent {

        public static OrderStatusChangedEvent of(
                UUID orderId,
                UUID organizationId,
                String orderNumber,
                OrderStatus fromStatus,
                OrderStatus toStatus,
                String notes,
                String changedBy) {
            return new OrderStatusChangedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    orderId,
                    organizationId,
                    orderNumber,
                    fromStatus,
                    toStatus,
                    notes,
                    changedBy);
        }
    }
}
