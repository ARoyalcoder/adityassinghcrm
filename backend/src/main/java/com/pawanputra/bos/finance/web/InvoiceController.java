package com.pawanputra.bos.finance.web;

import static com.pawanputra.bos.finance.web.FinanceWeb.actor;
import static com.pawanputra.bos.finance.web.FinanceWeb.trimToNull;

import com.pawanputra.bos.finance.api.InvoiceStatus;
import com.pawanputra.bos.finance.internal.InvoiceService;
import com.pawanputra.bos.finance.internal.InvoiceService.Detail;
import com.pawanputra.bos.finance.internal.InvoiceService.Noted;
import com.pawanputra.bos.finance.internal.InvoiceService.Preview;
import com.pawanputra.bos.finance.internal.InvoiceService.Summary;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invoices. A draft is prepared and edited freely; issuing it makes it a financial record that is
 * only ever corrected by a credit note, a debit note or cancellation, never edited or deleted.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/invoices")
@Tag(name = "Invoices")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class InvoiceController {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "createdAt", "invoiceNumber", "invoiceNumber", "issueDate", "issueDate",
            "dueDate", "dueDate", "customer", "customerName", "total", "total");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    /** One line. Percentages left out count as zero. */
    record ItemRequest(
            UUID serviceId,
            @NotBlank @Size(max = 500) String description,
            @Pattern(regexp = "^[0-9]{4,8}$", message = "must be 4 to 8 digits") String hsnSac,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal quantity,
            @Size(max = 20) String unit,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal taxPercent) {

        InvoiceService.ItemInput input() {
            return new InvoiceService.ItemInput(serviceId, description.trim(), trimToNull(hsnSac), quantity,
                    trimToNull(unit), unitPrice, discountPercent, taxPercent);
        }
    }

    /**
     * Create or edit a draft.
     *
     * @param billingAddress   left out: the customer's billing address
     * @param customerTaxId    left out: the customer's tax id
     * @param paymentTermsDays days from issue until the invoice is due; left out: the configured default
     * @param version          required when editing: the version that was loaded
     */
    record DraftRequest(
            @NotNull UUID customerId,
            @Size(max = 1000) String billingAddress,
            @Size(max = 30) String customerTaxId,
            @Size(max = 100) String reference,
            @Min(0) @Max(365) Integer paymentTermsDays,
            @Size(max = 2000) String notes,
            @Size(max = 4000) String terms,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid ItemRequest> items,
            Long version) {

        InvoiceService.DraftInput input() {
            return new InvoiceService.DraftInput(customerId, trimToNull(billingAddress), trimToNull(customerTaxId),
                    trimToNull(reference), paymentTermsDays, trimToNull(notes), trimToNull(terms),
                    items.stream().map(ItemRequest::input).toList());
        }
    }

    record PreviewRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid ItemRequest> items) {
    }

    /** @param issueDate left out: today. @param dueDate left out: the issue date plus the payment terms */
    record IssueRequest(LocalDate issueDate, LocalDate dueDate) {
    }

    record CancelRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /** @param noteDate left out: today */
    record NoteRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
            LocalDate noteDate,
            @NotBlank @Size(max = 500) String reason) {

        InvoiceService.NoteInput input() {
            return new InvoiceService.NoteInput(amount, noteDate, reason.trim());
        }
    }

    // ------------------------------------------------------------------ reading

    @GetMapping
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "Search invoices. Filters: search (number, customer, reference), customerId, status (DRAFT, "
            + "ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED, as shown: OVERDUE is an open invoice past its due "
            + "date), outstanding=true (anything still owed). Sort: createdAt (default, newest first), "
            + "invoiceNumber, issueDate, dueDate, customer, total. Requires FINANCE_VIEW.")
    public PageResponse<Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Boolean outstanding,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                invoices.search(actor(),
                        new InvoiceService.Filter(search, status, customerId, Boolean.TRUE.equals(outstanding)),
                        Paging.of(page, size, sort, SORTS, DEFAULT_SORT)),
                Function.identity());
    }

    @GetMapping("/open")
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "A customer's invoices that can take a payment, the one due earliest first. "
            + "Requires FINANCE_VIEW.")
    public List<Summary> open(@RequestParam UUID customerId) {
        return invoices.openFor(actor(), customerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "One invoice with its lines, the payments allocated to it, its credit and debit notes, the "
            + "company's details for printing, and what the caller may do with it. Requires FINANCE_VIEW.")
    public Detail get(@PathVariable UUID id) {
        return invoices.get(actor(), id);
    }

    // ------------------------------------------------------------------ drafts

    @PostMapping("/preview")
    @PreAuthorize(FinanceWeb.CAN_MANAGE)
    @Operation(summary = "What the given lines add up to, without saving anything. Requires FINANCE_MANAGE.")
    public Preview preview(@Valid @RequestBody PreviewRequest request) {
        return invoices.preview(request.items().stream().map(ItemRequest::input).toList());
    }

    @PostMapping
    @PreAuthorize(FinanceWeb.CAN_MANAGE)
    @Operation(summary = "Create a draft invoice. Requires FINANCE_MANAGE.")
    public ResponseEntity<Detail> create(@Valid @RequestBody DraftRequest request) {
        Detail created = invoices.create(actor(), request.input());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/invoices/" + created.invoice().id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(FinanceWeb.CAN_MANAGE)
    @Operation(summary = "Change a draft. An issued invoice cannot be changed. Requires FINANCE_MANAGE.")
    public Detail update(@PathVariable UUID id, @Valid @RequestBody DraftRequest request) {
        if (request.version() == null) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "version is required when updating");
        }
        return invoices.update(actor(), id, request.input(), request.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(FinanceWeb.CAN_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Discard a draft. An issued invoice cannot be deleted, only cancelled. Requires FINANCE_MANAGE.")
    public void discard(@PathVariable UUID id) {
        invoices.discard(actor(), id);
    }

    // ------------------------------------------------------------------ issuing and correcting

    @PostMapping("/{id}/issue")
    @PreAuthorize(FinanceWeb.CAN_MANAGE)
    @Operation(summary = "Issue a draft: it gets the next invoice number of its fiscal year, an issue date and a due "
            + "date, and can no longer be changed. Requires FINANCE_MANAGE.")
    public Detail issue(@PathVariable UUID id, @RequestBody(required = false) IssueRequest request) {
        return invoices.issue(actor(), id, request == null ? null : request.issueDate(),
                request == null ? null : request.dueDate());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(FinanceWeb.CAN_APPROVE)
    @Operation(summary = "Cancel an issued invoice that has no payments or notes against it. It keeps its number. "
            + "Requires FINANCE_APPROVE.")
    public Detail cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        return invoices.cancel(actor(), id, request.reason().trim());
    }

    @PostMapping("/{id}/credit-notes")
    @PreAuthorize(FinanceWeb.CAN_APPROVE)
    @Operation(summary = "Record a credit note: reduces what is owed on the invoice, by no more than is outstanding. "
            + "Requires FINANCE_APPROVE." + FinanceWeb.IDEMPOTENCY_NOTE)
    public ResponseEntity<Detail> creditNote(
            @PathVariable UUID id, @Valid @RequestBody NoteRequest request,
            @RequestHeader(name = FinanceWeb.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return noted(invoices.addCreditNote(actor(), id, request.input(), FinanceWeb.idempotencyKey(idempotencyKey)));
    }

    @PostMapping("/{id}/debit-notes")
    @PreAuthorize(FinanceWeb.CAN_APPROVE)
    @Operation(summary = "Record a debit note: adds to what is owed on the invoice. Requires FINANCE_APPROVE."
            + FinanceWeb.IDEMPOTENCY_NOTE)
    public ResponseEntity<Detail> debitNote(
            @PathVariable UUID id, @Valid @RequestBody NoteRequest request,
            @RequestHeader(name = FinanceWeb.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return noted(invoices.addDebitNote(actor(), id, request.input(), FinanceWeb.idempotencyKey(idempotencyKey)));
    }

    private static ResponseEntity<Detail> noted(Noted noted) {
        return FinanceWeb.replayed(ResponseEntity.status(HttpStatus.CREATED), noted.replayed()).body(noted.detail());
    }
}
