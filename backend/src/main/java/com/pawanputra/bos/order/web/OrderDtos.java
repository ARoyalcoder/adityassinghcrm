package com.pawanputra.bos.order.web;

import com.pawanputra.bos.order.api.OrderStatus;
import com.pawanputra.bos.order.internal.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class OrderDtos {

    private OrderDtos() {
    }

    record ConvertQuotationRequest(
            @NotNull UUID quotationId,
            @Size(max = 250) String title,
            UUID ownerId,
            LocalDate deliveryDate,
            String notes) {

        OrderService.ConvertQuotationCommand toCommand() {
            return new OrderService.ConvertQuotationCommand(
                    quotationId,
                    title != null ? title.trim() : null,
                    ownerId,
                    deliveryDate,
                    notes != null ? notes.trim() : null);
        }
    }

    record ItemRequest(
            UUID serviceId,
            @NotBlank @Size(max = 255) String serviceName,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @Min(0) @Max(100) BigDecimal discountPercentage,
            @Min(0) @Max(100) BigDecimal taxPercentage) {

        OrderService.ItemInput toInput() {
            return new OrderService.ItemInput(
                    serviceId,
                    serviceName.trim(),
                    description != null ? description.trim() : null,
                    quantity,
                    unitPrice,
                    discountPercentage,
                    taxPercentage);
        }
    }

    record CreateOrderRequest(
            @NotBlank @Size(max = 250) String title,
            @NotNull UUID customerId,
            @NotBlank @Size(max = 255) String customerName,
            UUID ownerId,
            LocalDate deliveryDate,
            String notes,
            String terms,
            List<@Valid ItemRequest> items) {

        OrderService.CreateDirectCommand toCommand() {
            List<OrderService.ItemInput> itemInputs = items != null
                    ? items.stream().map(ItemRequest::toInput).toList()
                    : List.of();
            return new OrderService.CreateDirectCommand(
                    title.trim(),
                    customerId,
                    customerName.trim(),
                    ownerId,
                    deliveryDate,
                    notes != null ? notes.trim() : null,
                    terms != null ? terms.trim() : null,
                    itemInputs);
        }
    }

    record UpdateStatusRequest(
            @NotNull OrderStatus status,
            @Size(max = 1000) String reason,
            @Size(max = 1000) String notes) {

        OrderService.UpdateStatusCommand toCommand() {
            return new OrderService.UpdateStatusCommand(
                    status,
                    reason != null ? reason.trim() : null,
                    notes != null ? notes.trim() : null);
        }
    }

    record UpdateDetailsRequest(
            @Size(max = 250) String title,
            LocalDate deliveryDate,
            String notes,
            String terms) {

        OrderService.UpdateDetailsCommand toCommand() {
            return new OrderService.UpdateDetailsCommand(
                    title != null ? title.trim() : null,
                    deliveryDate,
                    notes != null ? notes.trim() : null,
                    terms != null ? terms.trim() : null);
        }
    }

    record UpdateDeliveryRequest(
            @NotNull @DecimalMin("0.00") BigDecimal deliveredQuantity) {
    }
}
