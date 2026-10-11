package com.pawanputra.bos.order.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Extension SPI for future Project Management integration.
 * <p>
 * When an order is confirmed, implementations of this bridge are notified,
 * allowing project initialization, milestone generation, and resource assignment
 * without tightly coupling order domain to project domain.
 */
public interface OrderProjectBridge {

    record OrderItemSummary(
            UUID id,
            UUID serviceId,
            String serviceName,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount) {
    }

    record OrderProjectContext(
            UUID orderId,
            String orderNumber,
            String title,
            UUID customerId,
            String customerName,
            UUID quotationId,
            String quotationNumber,
            UUID ownerId,
            String ownerName,
            BigDecimal finalAmount,
            LocalDate deliveryDate,
            String notes,
            List<OrderItemSummary> items) {
    }

    /**
     * Extension point hook invoked when an order is created and confirmed.
     *
     * @param organizationId tenant organization identifier
     * @param context order snapshot data for project provisioning
     */
    void onOrderConfirmed(UUID organizationId, OrderProjectContext context);
}
