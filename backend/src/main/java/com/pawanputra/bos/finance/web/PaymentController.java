package com.pawanputra.bos.finance.web;

import static com.pawanputra.bos.finance.web.FinanceWeb.actor;
import static com.pawanputra.bos.finance.web.FinanceWeb.trimToNull;

import com.pawanputra.bos.finance.api.PaymentStatus;
import com.pawanputra.bos.finance.internal.PaymentService;
import com.pawanputra.bos.finance.internal.PaymentService.Detail;
import com.pawanputra.bos.finance.internal.PaymentService.Recorded;
import com.pawanputra.bos.finance.internal.PaymentService.Summary;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payments received from customers. There is no way to edit or delete one: an allocation is
 * released, money is refunded, a payment is reversed, and each of those is a record of its own.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/payments")
@Tag(name = "Payments")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class PaymentController {

    private static final Map<String, String> SORTS = Map.of(
            "receivedOn", "receivedOn", "paymentNumber", "paymentNumber", "customer", "customerName",
            "amount", "amount", "createdAt", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    record AllocationRequest(
            @NotNull UUID invoiceId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount) {

        PaymentService.AllocationInput input() {
            return new PaymentService.AllocationInput(invoiceId, amount);
        }
    }

    /**
     * Record a payment.
     *
     * @param receivedOn   left out: today
     * @param allocations  which invoices it settles and by how much; left out or empty: an advance
     * @param autoAllocate settle the customer's invoices oldest first instead (ignores allocations)
     */
    record PaymentRequest(
            @NotNull UUID customerId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
            LocalDate receivedOn,
            @NotNull UUID methodId,
            @Size(max = 100) String reference,
            @Size(max = 1000) String notes,
            @Size(max = 100) List<@NotNull @Valid AllocationRequest> allocations,
            Boolean autoAllocate) {

        PaymentService.PaymentInput input() {
            return new PaymentService.PaymentInput(customerId, amount, receivedOn, methodId, trimToNull(reference),
                    trimToNull(notes), inputs(allocations), Boolean.TRUE.equals(autoAllocate));
        }
    }

    record AllocateRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid AllocationRequest> allocations) {
    }

    record ReasonRequest(@NotBlank @Size(max = 400) String reason) {
    }

    /** @param refundDate left out: today */
    record RefundRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
            LocalDate refundDate,
            @NotNull UUID methodId,
            @Size(max = 100) String reference,
            @NotBlank @Size(max = 500) String reason) {

        PaymentService.RefundInput input() {
            return new PaymentService.RefundInput(amount, refundDate, methodId, trimToNull(reference), reason.trim());
        }
    }

    // ------------------------------------------------------------------ reading

    @GetMapping
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "Search payments. Filters: search (receipt number, customer, reference), customerId, status "
            + "(RECEIVED, REVERSED), unallocated=true (money still to allocate or refund). Sort: createdAt (default, "
            + "newest first), receivedOn, paymentNumber, customer, amount. Requires FINANCE_VIEW.")
    public PageResponse<Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Boolean unallocated,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                payments.search(actor(),
                        new PaymentService.Filter(search, status, customerId, Boolean.TRUE.equals(unallocated)),
                        Paging.of(page, size, sort, SORTS, DEFAULT_SORT)),
                Function.identity());
    }

    @GetMapping("/{id}")
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "One payment with its allocations, its refunds and what the caller may do with it. "
            + "Requires FINANCE_VIEW.")
    public Detail get(@PathVariable UUID id) {
        return payments.get(actor(), id);
    }

    // ------------------------------------------------------------------ recording

    @PostMapping
    @PreAuthorize(FinanceWeb.CAN_MANAGE)
    @Operation(summary = "Record a payment and, in the same transaction, what it settles. Requires FINANCE_MANAGE."
            + FinanceWeb.IDEMPOTENCY_NOTE)
    public ResponseEntity<Detail> record(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(name = FinanceWeb.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        Recorded recorded = payments.record(actor(), request.input(), FinanceWeb.idempotencyKey(idempotencyKey));
        return FinanceWeb.replayed(
                        ResponseEntity.created(URI.create(ApiPaths.V1 + "/payments/" + recorded.detail().payment().id())),
                        recorded.replayed())
                .body(recorded.detail());
    }

    @PostMapping("/{id}/allocations")
    @PreAuthorize(FinanceWeb.CAN_MANAGE)
    @Operation(summary = "Put more of a payment's unallocated money against invoices. Requires FINANCE_MANAGE."
            + FinanceWeb.IDEMPOTENCY_NOTE)
    public ResponseEntity<Detail> allocate(
            @PathVariable UUID id, @Valid @RequestBody AllocateRequest request,
            @RequestHeader(name = FinanceWeb.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return created(payments.allocate(actor(), id, inputs(request.allocations()),
                FinanceWeb.idempotencyKey(idempotencyKey)));
    }

    // ------------------------------------------------------------------ putting things right

    @PostMapping("/{id}/allocations/{allocationId}/release")
    @PreAuthorize(FinanceWeb.CAN_APPROVE)
    @Operation(summary = "Take an allocation back: the invoice is owed that much again and the payment has it to "
            + "use. The allocation stays on record, marked released. Requires FINANCE_APPROVE.")
    public Detail release(
            @PathVariable UUID id, @PathVariable UUID allocationId, @Valid @RequestBody ReasonRequest request) {
        return payments.release(actor(), id, allocationId, request.reason().trim());
    }

    @PostMapping("/{id}/refunds")
    @PreAuthorize(FinanceWeb.CAN_APPROVE)
    @Operation(summary = "Refund unallocated money to the customer. Requires FINANCE_APPROVE."
            + FinanceWeb.IDEMPOTENCY_NOTE)
    public ResponseEntity<Detail> refund(
            @PathVariable UUID id, @Valid @RequestBody RefundRequest request,
            @RequestHeader(name = FinanceWeb.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return created(payments.refund(actor(), id, request.input(), FinanceWeb.idempotencyKey(idempotencyKey)));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize(FinanceWeb.CAN_APPROVE)
    @Operation(summary = "Reverse a payment that should not stand (a bounced cheque, a mistaken entry). All its "
            + "allocations are released; the payment stays on record with the reason. Requires FINANCE_APPROVE.")
    public Detail reverse(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return payments.reverse(actor(), id, request.reason().trim());
    }

    private static List<PaymentService.AllocationInput> inputs(List<AllocationRequest> allocations) {
        return allocations == null ? List.of() : allocations.stream().map(AllocationRequest::input).toList();
    }

    private static ResponseEntity<Detail> created(Recorded recorded) {
        return FinanceWeb.replayed(ResponseEntity.status(HttpStatus.CREATED), recorded.replayed())
                .body(recorded.detail());
    }
}
