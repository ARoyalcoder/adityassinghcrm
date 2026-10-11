package com.pawanputra.bos.order.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.order.api.OrderStatus;
import com.pawanputra.bos.order.internal.OrderService;
import com.pawanputra.bos.order.internal.OrderViews;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/orders")
@Tag(name = "Orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class OrderController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.ORDER_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.ORDER_CREATE + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.ORDER_UPDATE + "')";

    private static final Map<String, String> SORTS = Map.of(
            "orderNumber", "orderNumber",
            "title", "title",
            "finalAmount", "finalAmount",
            "status", "status",
            "deliveryDate", "deliveryDate",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "List orders with filtering and pagination")
    public PageResponse<OrderViews.Summary> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser user = CurrentUser.require();
        Pageable pageable = Paging.of(page, size, sort, SORTS, DEFAULT_SORT);
        OrderService.OrderFilter filter = new OrderService.OrderFilter(status, customerId, ownerId, search);

        Page<OrderViews.Summary> result = orderService.findPage(user.organizationId(), filter, pageable);
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/dashboard")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get order dashboard KPIs and metrics")
    public OrderViews.Dashboard getDashboard() {
        CurrentUser user = CurrentUser.require();
        return orderService.getDashboard(user.organizationId());
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get complete order detail including items and history")
    public OrderViews.Detail getById(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return orderService.getById(user.organizationId(), id);
    }

    @PostMapping("/from-quotation")
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Convert an accepted quotation into an order")
    public ResponseEntity<OrderViews.Detail> convertFromQuotation(
            @Valid @RequestBody OrderDtos.ConvertQuotationRequest request) {
        CurrentUser user = CurrentUser.require();
        OrderViews.Detail created = orderService.convertFromQuotation(
                user.organizationId(),
                request.toCommand(),
                user.id(),
                user.email());
        return ResponseEntity
                .created(URI.create(ApiPaths.V1 + "/orders/" + created.id()))
                .body(created);
    }

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create an order directly")
    public ResponseEntity<OrderViews.Detail> createDirect(
            @Valid @RequestBody OrderDtos.CreateOrderRequest request) {
        CurrentUser user = CurrentUser.require();
        OrderViews.Detail created = orderService.createDirect(
                user.organizationId(),
                request.toCommand(),
                user.id(),
                user.email());
        return ResponseEntity
                .created(URI.create(ApiPaths.V1 + "/orders/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Transition order status along lifecycle or cancel")
    public OrderViews.Detail updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderDtos.UpdateStatusRequest request) {
        CurrentUser user = CurrentUser.require();
        return orderService.updateStatus(
                user.organizationId(),
                id,
                request.toCommand(),
                user.id(),
                user.email());
    }

    @PatchMapping("/{id}/details")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update order editable details")
    public OrderViews.Detail updateDetails(
            @PathVariable UUID id,
            @Valid @RequestBody OrderDtos.UpdateDetailsRequest request) {
        CurrentUser user = CurrentUser.require();
        return orderService.updateDetails(
                user.organizationId(),
                id,
                request.toCommand(),
                user.id(),
                user.email());
    }

    @PatchMapping("/{id}/items/{itemId}/delivery")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update delivered quantity on an order item")
    public OrderViews.Detail updateDeliveredQuantity(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody OrderDtos.UpdateDeliveryRequest request) {
        CurrentUser user = CurrentUser.require();
        return orderService.updateDeliveredQuantity(
                user.organizationId(),
                id,
                itemId,
                request.deliveredQuantity(),
                user.id(),
                user.email());
    }
}
