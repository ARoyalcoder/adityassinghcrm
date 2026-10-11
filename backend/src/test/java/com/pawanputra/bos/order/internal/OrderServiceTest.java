package com.pawanputra.bos.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.order.api.OrderEvents;
import com.pawanputra.bos.order.api.OrderProjectBridge;
import com.pawanputra.bos.order.api.OrderStatus;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import com.pawanputra.bos.quotation.api.QuotationDirectory;
import com.pawanputra.bos.quotation.api.QuotationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private OrderRepository orders;
    private OrderItemRepository items;
    private OrderHistoryRepository history;
    private QuotationDirectory quotations;
    private UserDirectory users;
    private DomainEventPublisher events;
    private OrderProjectBridge projectBridge;
    private Clock clock;
    private OrderService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-09T12:00:00Z");

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        items = mock(OrderItemRepository.class);
        history = mock(OrderHistoryRepository.class);
        quotations = mock(QuotationDirectory.class);
        users = mock(UserDirectory.class);
        events = mock(DomainEventPublisher.class);
        projectBridge = mock(OrderProjectBridge.class);
        clock = Clock.fixed(now, ZoneOffset.UTC);

        service = new OrderService(
                orders,
                items,
                history,
                quotations,
                users,
                events,
                projectBridge,
                clock);
    }

    @Test
    void convertFromQuotation_SuccessfullyPreservesQuotationAndCreatesOrder() {
        UUID quotationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        QuotationDirectory.QuotationItemSummary itemSummary = new QuotationDirectory.QuotationItemSummary(
                UUID.randomUUID(),
                1,
                serviceId,
                "Solar Panel Setup",
                "10kW Rooftop Solar",
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(5000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(18),
                BigDecimal.valueOf(9000),
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(59000));

        QuotationDirectory.QuotationForOrder quote = new QuotationDirectory.QuotationForOrder(
                quotationId,
                "Q-0001",
                2,
                QuotationStatus.ACCEPTED,
                customerId,
                "Acme Corp",
                null,
                null,
                null,
                null,
                userId,
                "Jane Doe",
                BigDecimal.valueOf(50000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(9000),
                BigDecimal.valueOf(59000),
                "Payment in 30 days",
                "Priority handling",
                List.of(itemSummary));

        when(quotations.findQuotationForOrder(orgId, quotationId)).thenReturn(Optional.of(quote));
        when(orders.findByOrganizationIdAndQuotationId(orgId, quotationId)).thenReturn(Optional.empty());
        when(orders.countByOrganizationId(orgId)).thenReturn(0L);
        when(orders.existsByOrganizationIdAndOrderNumber(eq(orgId), any())).thenReturn(false);
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderService.ConvertQuotationCommand cmd = new OrderService.ConvertQuotationCommand(
                quotationId,
                "Acme Solar Order",
                userId,
                LocalDate.of(2026, 11, 1),
                "Deliver by next month");

        OrderViews.Detail result = service.convertFromQuotation(orgId, cmd, userId, "Admin");

        assertThat(result).isNotNull();
        assertThat(result.orderNumber()).isEqualTo("ORD-0001");
        assertThat(result.title()).isEqualTo("Acme Solar Order");
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.customerName()).isEqualTo("Acme Corp");
        assertThat(result.quotationId()).isEqualTo(quotationId);
        assertThat(result.quotationNumber()).isEqualTo("Q-0001");
        assertThat(result.quotationVersionNumber()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.subtotal()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(result.finalAmount()).isEqualByComparingTo(BigDecimal.valueOf(59000));

        verify(events).publish(any(OrderEvents.OrderCreatedEvent.class));
        verify(projectBridge).onOrderConfirmed(eq(orgId), any(OrderProjectBridge.OrderProjectContext.class));
    }

    @Test
    void convertFromQuotation_RejectsNonAcceptedQuotation() {
        UUID quotationId = UUID.randomUUID();
        QuotationDirectory.QuotationForOrder quote = new QuotationDirectory.QuotationForOrder(
                quotationId,
                "Q-0002",
                1,
                QuotationStatus.DRAFT,
                UUID.randomUUID(),
                "Client",
                null,
                null,
                null,
                null,
                userId,
                "Jane",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                List.of());

        when(quotations.findQuotationForOrder(orgId, quotationId)).thenReturn(Optional.of(quote));

        OrderService.ConvertQuotationCommand cmd = new OrderService.ConvertQuotationCommand(
                quotationId, null, null, null, null);

        assertThatThrownBy(() -> service.convertFromQuotation(orgId, cmd, userId, "Admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only ACCEPTED quotations can be converted");
    }

    @Test
    void convertFromQuotation_RejectsAlreadyConvertedQuotation() {
        UUID quotationId = UUID.randomUUID();
        QuotationDirectory.QuotationForOrder quote = new QuotationDirectory.QuotationForOrder(
                quotationId,
                "Q-0003",
                1,
                QuotationStatus.ACCEPTED,
                UUID.randomUUID(),
                "Client",
                null,
                null,
                null,
                null,
                userId,
                "Jane",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                List.of());

        when(quotations.findQuotationForOrder(orgId, quotationId)).thenReturn(Optional.of(quote));
        Order existingOrder = new Order(
                orgId, "ORD-0001", "Existing", quote.customerId(), quote.customerName(),
                quotationId, "Q-0003", 1, userId, "Jane", "Admin");
        when(orders.findByOrganizationIdAndQuotationId(orgId, quotationId)).thenReturn(Optional.of(existingOrder));

        OrderService.ConvertQuotationCommand cmd = new OrderService.ConvertQuotationCommand(
                quotationId, null, null, null, null);

        assertThatThrownBy(() -> service.convertFromQuotation(orgId, cmd, userId, "Admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been converted into an order");
    }

    @Test
    void updateStatus_TransitionsAlongLifecycle() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(
                orgId, "ORD-0010", "Solar Project", UUID.randomUUID(), "Customer",
                null, null, null, userId, "Jane", "Admin");
        order.setStatus(OrderStatus.CONFIRMED);

        when(orders.findByIdAndOrganizationId(orderId, orgId)).thenReturn(Optional.of(order));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // CONFIRMED -> IN_PROGRESS
        OrderService.UpdateStatusCommand cmdInProgress = new OrderService.UpdateStatusCommand(
                OrderStatus.IN_PROGRESS, null, "Commenced procurement");
        OrderViews.Detail result = service.updateStatus(orgId, orderId, cmdInProgress, userId, "Admin");

        assertThat(result.status()).isEqualTo(OrderStatus.IN_PROGRESS);
        verify(events).publish(any(OrderEvents.OrderStatusChangedEvent.class));

        // IN_PROGRESS -> COMPLETED
        OrderService.UpdateStatusCommand cmdCompleted = new OrderService.UpdateStatusCommand(
                OrderStatus.COMPLETED, null, "All installations complete");
        OrderViews.Detail completedResult = service.updateStatus(orgId, orderId, cmdCompleted, userId, "Admin");

        assertThat(completedResult.status()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void updateStatus_RequiresReasonWhenCancelling() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(
                orgId, "ORD-0010", "Solar Project", UUID.randomUUID(), "Customer",
                null, null, null, userId, "Jane", "Admin");
        order.setStatus(OrderStatus.CONFIRMED);

        when(orders.findByIdAndOrganizationId(orderId, orgId)).thenReturn(Optional.of(order));

        OrderService.UpdateStatusCommand invalidCancel = new OrderService.UpdateStatusCommand(
                OrderStatus.CANCELLED, null, null);

        assertThatThrownBy(() -> service.updateStatus(orgId, orderId, invalidCancel, userId, "Admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cancellation reason is required");
    }
}
