package com.pawanputra.bos.order.internal;

import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.order.api.OrderEvents;
import com.pawanputra.bos.order.api.OrderProjectBridge;
import com.pawanputra.bos.order.api.OrderStatus;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import com.pawanputra.bos.quotation.api.QuotationDirectory;
import com.pawanputra.bos.quotation.api.QuotationStatus;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderHistoryRepository history;
    private final QuotationDirectory quotations;
    private final UserDirectory users;
    private final DomainEventPublisher events;
    private final OrderProjectBridge projectBridge;
    private final Clock clock;

    public OrderService(
            OrderRepository orders,
            OrderItemRepository items,
            OrderHistoryRepository history,
            QuotationDirectory quotations,
            UserDirectory users,
            DomainEventPublisher events,
            OrderProjectBridge projectBridge,
            Clock clock) {
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.quotations = quotations;
        this.users = users;
        this.events = events;
        this.projectBridge = projectBridge;
        this.clock = clock;
    }

    public record ItemInput(
            UUID serviceId,
            String serviceName,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercentage,
            BigDecimal taxPercentage) {
    }

    public record CreateDirectCommand(
            String title,
            UUID customerId,
            String customerName,
            UUID ownerId,
            LocalDate deliveryDate,
            String notes,
            String terms,
            List<ItemInput> items) {
    }

    public record ConvertQuotationCommand(
            UUID quotationId,
            String title,
            UUID ownerId,
            LocalDate deliveryDate,
            String notes) {
    }

    public record UpdateStatusCommand(
            OrderStatus status,
            String reason,
            String notes) {
    }

    public record UpdateDetailsCommand(
            String title,
            LocalDate deliveryDate,
            String notes,
            String terms) {
    }

    public record OrderFilter(
            OrderStatus status,
            UUID customerId,
            UUID ownerId,
            String search) {
    }

    /**
     * Converts an ACCEPTED quotation into a confirmed Order.
     * Preserves quotation reference, customer, services, and pricing.
     */
    public OrderViews.Detail convertFromQuotation(
            UUID orgId, ConvertQuotationCommand cmd, UUID actorId, String actorName) {
        QuotationDirectory.QuotationForOrder q = quotations.findQuotationForOrder(orgId, cmd.quotationId())
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", cmd.quotationId()));

        if (q.status() != QuotationStatus.ACCEPTED) {
            throw new IllegalStateException("Only ACCEPTED quotations can be converted into orders. Current status: " + q.status());
        }

        if (orders.findByOrganizationIdAndQuotationId(orgId, q.quotationId()).isPresent()) {
            throw new IllegalStateException("Quotation " + q.quotationNumber() + " has already been converted into an order");
        }

        String ownerName = actorName;
        UUID ownerId = cmd.ownerId() != null ? cmd.ownerId() : q.ownerId();
        if (ownerId != null) {
            ownerName = users.find(orgId, ownerId)
                    .map(UserDirectory.UserSummary::fullName)
                    .orElse(q.ownerName() != null ? q.ownerName() : actorName);
        }

        String orderNumber = generateOrderNumber(orgId);
        String title = (cmd.title() != null && !cmd.title().isBlank())
                ? cmd.title()
                : "Order for " + q.quotationNumber();

        Order order = new Order(
                orgId,
                orderNumber,
                title,
                q.customerId(),
                q.customerName(),
                q.quotationId(),
                q.quotationNumber(),
                q.versionNumber(),
                ownerId != null ? ownerId : actorId,
                ownerName,
                actorName);

        order.setDeliveryDate(cmd.deliveryDate());
        order.setNotes(cmd.notes() != null ? cmd.notes() : q.notes());
        order.setTerms(q.terms());
        order.setSubtotal(q.subtotal());
        order.setDiscountAmount(q.discountAmount());
        order.setTaxAmount(q.taxAmount());
        order.setFinalAmount(q.finalAmount());
        order.setStatus(OrderStatus.CONFIRMED);

        if (q.items() != null) {
            for (QuotationDirectory.QuotationItemSummary qi : q.items()) {
                OrderItem oi = new OrderItem(
                        order,
                        qi.itemOrder(),
                        qi.serviceId(),
                        qi.serviceName(),
                        qi.description(),
                        qi.quantity(),
                        qi.unitPrice(),
                        qi.discountPercentage(),
                        qi.discountAmount(),
                        qi.taxPercentage(),
                        qi.taxAmount(),
                        qi.subtotal(),
                        qi.totalAmount(),
                        actorName);
                order.addItem(oi);
            }
        }

        OrderHistory historyEntry = new OrderHistory(
                order,
                "CONVERTED_FROM_QUOTATION",
                null,
                OrderStatus.CONFIRMED.name(),
                "Converted from Quotation " + q.quotationNumber() + " (v" + q.versionNumber() + ")",
                actorName);
        order.addHistory(historyEntry);

        Order saved = orders.save(order);

        // Publish OrderCreatedEvent
        events.publish(OrderEvents.OrderCreatedEvent.of(
                saved.getId(),
                orgId,
                saved.getOrderNumber(),
                saved.getTitle(),
                saved.getCustomerId(),
                saved.getCustomerName(),
                saved.getQuotationId(),
                saved.getQuotationNumber(),
                saved.getFinalAmount(),
                saved.getStatus(),
                saved.getOwnerId(),
                actorName));

        // Invoke Project Extension SPI
        notifyProjectBridge(orgId, saved);

        return toDetail(saved);
    }

    /**
     * Creates a new Order directly without a pre-existing quotation.
     */
    public OrderViews.Detail createDirect(
            UUID orgId, CreateDirectCommand cmd, UUID actorId, String actorName) {
        String ownerName = actorName;
        if (cmd.ownerId() != null) {
            ownerName = users.find(orgId, cmd.ownerId())
                    .map(UserDirectory.UserSummary::fullName)
                    .orElse(actorName);
        }

        String orderNumber = generateOrderNumber(orgId);
        Order order = new Order(
                orgId,
                orderNumber,
                cmd.title(),
                cmd.customerId(),
                cmd.customerName(),
                null,
                null,
                null,
                cmd.ownerId() != null ? cmd.ownerId() : actorId,
                ownerName,
                actorName);

        order.setDeliveryDate(cmd.deliveryDate());
        order.setNotes(cmd.notes());
        order.setTerms(cmd.terms());
        order.setStatus(OrderStatus.CONFIRMED);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal finalAmount = BigDecimal.ZERO;

        if (cmd.items() != null && !cmd.items().isEmpty()) {
            int orderNum = 1;
            for (ItemInput inp : cmd.items()) {
                BigDecimal qty = inp.quantity() != null ? inp.quantity() : BigDecimal.ONE;
                BigDecimal price = inp.unitPrice() != null ? inp.unitPrice() : BigDecimal.ZERO;
                BigDecimal itemSub = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);

                BigDecimal discPct = inp.discountPercentage() != null ? inp.discountPercentage() : BigDecimal.ZERO;
                BigDecimal discAmt = itemSub.multiply(discPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                BigDecimal afterDisc = itemSub.subtract(discAmt).max(BigDecimal.ZERO);
                BigDecimal taxPct = inp.taxPercentage() != null ? inp.taxPercentage() : BigDecimal.valueOf(18.00);
                BigDecimal taxAmt = afterDisc.multiply(taxPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                BigDecimal itemTotal = afterDisc.add(taxAmt).setScale(2, RoundingMode.HALF_UP);

                subtotal = subtotal.add(itemSub);
                totalDiscount = totalDiscount.add(discAmt);
                totalTax = totalTax.add(taxAmt);
                finalAmount = finalAmount.add(itemTotal);

                OrderItem item = new OrderItem(
                        order,
                        orderNum++,
                        inp.serviceId(),
                        inp.serviceName(),
                        inp.description(),
                        qty,
                        price,
                        discPct,
                        discAmt,
                        taxPct,
                        taxAmt,
                        itemSub,
                        itemTotal,
                        actorName);
                order.addItem(item);
            }
        }

        order.setSubtotal(subtotal);
        order.setDiscountAmount(totalDiscount);
        order.setTaxAmount(totalTax);
        order.setFinalAmount(finalAmount);

        OrderHistory historyEntry = new OrderHistory(
                order,
                "CREATED",
                null,
                OrderStatus.CONFIRMED.name(),
                "Direct order created",
                actorName);
        order.addHistory(historyEntry);

        Order saved = orders.save(order);

        events.publish(OrderEvents.OrderCreatedEvent.of(
                saved.getId(),
                orgId,
                saved.getOrderNumber(),
                saved.getTitle(),
                saved.getCustomerId(),
                saved.getCustomerName(),
                null,
                null,
                saved.getFinalAmount(),
                saved.getStatus(),
                saved.getOwnerId(),
                actorName));

        notifyProjectBridge(orgId, saved);

        return toDetail(saved);
    }

    /**
     * Updates an order's status along the lifecycle:
     * CONFIRMED -> IN_PROGRESS -> PARTIALLY_DELIVERED / COMPLETED, or CANCELLED.
     */
    public OrderViews.Detail updateStatus(
            UUID orgId, UUID orderId, UpdateStatusCommand cmd, UUID actorId, String actorName) {
        Order order = getEntity(orgId, orderId);
        OrderStatus currentStatus = order.getStatus();
        OrderStatus targetStatus = cmd.status();

        if (currentStatus == targetStatus) {
            return toDetail(order);
        }

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "Cannot transition order from " + currentStatus + " to " + targetStatus);
        }

        Instant now = Instant.now(clock);
        order.setStatus(targetStatus);
        order.setUpdatedAt(now);
        order.setUpdatedBy(actorName);

        if (targetStatus == OrderStatus.CANCELLED) {
            if (cmd.reason() == null || cmd.reason().isBlank()) {
                throw new IllegalArgumentException("Cancellation reason is required");
            }
            order.setCancellationReason(cmd.reason());
            order.setCancelledAt(now);
            order.setCancelledBy(actorName);
        } else if (targetStatus == OrderStatus.COMPLETED) {
            order.setCompletedAt(now);
            order.setCompletedBy(actorName);
        }

        String notes = cmd.notes() != null && !cmd.notes().isBlank()
                ? cmd.notes()
                : (cmd.reason() != null ? cmd.reason() : "Status changed to " + targetStatus);

        OrderHistory historyEntry = new OrderHistory(
                order,
                "STATUS_CHANGED",
                currentStatus.name(),
                targetStatus.name(),
                notes,
                actorName);
        order.addHistory(historyEntry);

        Order saved = orders.save(order);

        events.publish(OrderEvents.OrderStatusChangedEvent.of(
                saved.getId(),
                orgId,
                saved.getOrderNumber(),
                currentStatus,
                targetStatus,
                notes,
                actorName));

        return toDetail(saved);
    }

    /**
     * Updates editable order metadata like delivery date, notes, or terms.
     */
    public OrderViews.Detail updateDetails(
            UUID orgId, UUID orderId, UpdateDetailsCommand cmd, UUID actorId, String actorName) {
        Order order = getEntity(orgId, orderId);

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot edit details of a " + order.getStatus() + " order");
        }

        if (cmd.title() != null && !cmd.title().isBlank()) {
            order.setTitle(cmd.title());
        }
        order.setDeliveryDate(cmd.deliveryDate());
        order.setNotes(cmd.notes());
        order.setTerms(cmd.terms());
        order.setUpdatedAt(Instant.now(clock));
        order.setUpdatedBy(actorName);

        OrderHistory historyEntry = new OrderHistory(
                order,
                "DETAILS_UPDATED",
                order.getStatus().name(),
                order.getStatus().name(),
                "Order details updated",
                actorName);
        order.addHistory(historyEntry);

        Order saved = orders.save(order);
        return toDetail(saved);
    }

    /**
     * Updates delivered quantity for a line item and records fulfillment progress.
     */
    public OrderViews.Detail updateDeliveredQuantity(
            UUID orgId, UUID orderId, UUID itemId, BigDecimal deliveredQuantity, UUID actorId, String actorName) {
        Order order = getEntity(orgId, orderId);

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot update items of a " + order.getStatus() + " order");
        }

        OrderItem targetItem = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", itemId));

        if (deliveredQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Delivered quantity cannot be negative");
        }

        BigDecimal prevDelivered = targetItem.getDeliveredQuantity();
        targetItem.setDeliveredQuantity(deliveredQuantity);
        order.setUpdatedAt(Instant.now(clock));
        order.setUpdatedBy(actorName);

        OrderHistory historyEntry = new OrderHistory(
                order,
                "DELIVERY_UPDATED",
                order.getStatus().name(),
                order.getStatus().name(),
                "Updated delivered quantity for " + targetItem.getServiceName()
                        + " from " + prevDelivered + " to " + deliveredQuantity,
                actorName);
        order.addHistory(historyEntry);

        Order saved = orders.save(order);
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public OrderViews.Detail getById(UUID orgId, UUID orderId) {
        Order order = getEntity(orgId, orderId);
        return toDetail(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderViews.Summary> findPage(UUID orgId, OrderFilter filter, Pageable pageable) {
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (filter != null) {
                if (filter.status() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.status()));
                }
                if (filter.customerId() != null) {
                    predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
                }
                if (filter.ownerId() != null) {
                    predicates.add(cb.equal(root.get("ownerId"), filter.ownerId()));
                }
                if (filter.search() != null && !filter.search().isBlank()) {
                    String pattern = "%" + filter.search().toLowerCase().trim() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("orderNumber")), pattern),
                            cb.like(cb.lower(root.get("title")), pattern),
                            cb.like(cb.lower(root.get("customerName")), pattern),
                            cb.like(cb.lower(root.get("quotationNumber")), pattern)));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return orders.findAll(spec, pageable).map(OrderViews.Summary::from);
    }

    @Transactional(readOnly = true)
    public OrderViews.Dashboard getDashboard(UUID orgId) {
        long totalOrders = orders.countByOrganizationId(orgId);
        BigDecimal totalValue = orders.sumFinalAmountByOrganizationId(orgId);
        long confirmed = orders.countByOrganizationIdAndStatus(orgId, OrderStatus.CONFIRMED);
        long inProgress = orders.countByOrganizationIdAndStatus(orgId, OrderStatus.IN_PROGRESS);
        long partiallyDelivered = orders.countByOrganizationIdAndStatus(orgId, OrderStatus.PARTIALLY_DELIVERED);
        long completed = orders.countByOrganizationIdAndStatus(orgId, OrderStatus.COMPLETED);
        long cancelled = orders.countByOrganizationIdAndStatus(orgId, OrderStatus.CANCELLED);

        PageRequest recentRequest = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<OrderViews.Summary> recent = findPage(orgId, null, recentRequest).getContent();

        return new OrderViews.Dashboard(
                totalOrders,
                totalValue != null ? totalValue : BigDecimal.ZERO,
                confirmed,
                inProgress,
                partiallyDelivered,
                completed,
                cancelled,
                recent);
    }

    private Order getEntity(UUID orgId, UUID orderId) {
        return orders.findByIdAndOrganizationId(orderId, orgId)
                .filter(o -> o.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private String generateOrderNumber(UUID orgId) {
        long count = orders.countByOrganizationId(orgId) + 1;
        String candidate = String.format("ORD-%04d", count);
        int attempts = 0;
        while (orders.existsByOrganizationIdAndOrderNumber(orgId, candidate) && attempts < 100) {
            count++;
            candidate = String.format("ORD-%04d", count);
            attempts++;
        }
        return candidate;
    }

    private void notifyProjectBridge(UUID orgId, Order order) {
        List<OrderProjectBridge.OrderItemSummary> itemSummaries = order.getItems().stream()
                .map(i -> new OrderProjectBridge.OrderItemSummary(
                        i.getId(),
                        i.getServiceId(),
                        i.getServiceName(),
                        i.getDescription(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getTotalAmount()))
                .toList();

        OrderProjectBridge.OrderProjectContext context = new OrderProjectBridge.OrderProjectContext(
                order.getId(),
                order.getOrderNumber(),
                order.getTitle(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getQuotationId(),
                order.getQuotationNumber(),
                order.getOwnerId(),
                order.getOwnerName(),
                order.getFinalAmount(),
                order.getDeliveryDate(),
                order.getNotes(),
                itemSummaries);

        projectBridge.onOrderConfirmed(orgId, context);
    }

    private OrderViews.Detail toDetail(Order order) {
        List<OrderItem> itemList = items.findByOrderIdOrderByItemOrderAsc(order.getId());
        List<OrderHistory> historyList = history.findByOrderIdOrderByPerformedAtDesc(order.getId());

        List<OrderViews.ItemView> itemViews = itemList.stream().map(OrderViews.ItemView::from).toList();
        List<OrderViews.HistoryView> historyViews = historyList.stream().map(OrderViews.HistoryView::from).toList();

        return OrderViews.Detail.from(order, itemViews, historyViews);
    }
}
