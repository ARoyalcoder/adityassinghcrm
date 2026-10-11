package com.pawanputra.bos.quotation.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import com.pawanputra.bos.quotation.api.QuotationStatus;
import com.pawanputra.bos.quotation.internal.QuotationService;
import com.pawanputra.bos.quotation.internal.QuotationViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/quotations")
@Tag(name = "Quotations")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class QuotationController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.QUOTATION_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.QUOTATION_CREATE + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.QUOTATION_UPDATE + "')";
    private static final String CAN_APPROVE = "hasAuthority('" + Permissions.QUOTATION_APPROVE + "')";
    private static final String CAN_DELETE = "hasAuthority('" + Permissions.QUOTATION_DELETE + "')";

    private static final Map<String, String> SORTS = Map.of(
            "quotationNumber", "quotationNumber",
            "title", "title",
            "finalAmount", "finalAmount",
            "status", "status",
            "validUntil", "validUntil",
            "nextFollowUpDate", "nextFollowUpDate",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final QuotationService quotations;

    public QuotationController(QuotationService quotations) {
        this.quotations = quotations;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "List quotations with filtering and pagination")
    public PageResponse<QuotationViews.Summary> list(
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID opportunityId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) LocalDate followUpDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser user = CurrentUser.require();
        Pageable pageable = Paging.of(page, size, sort, SORTS, DEFAULT_SORT);
        QuotationService.QuotationFilter filter = new QuotationService.QuotationFilter(
                status, customerId, leadId, opportunityId, ownerId, followUpDate, search);

        Page<QuotationViews.Summary> result = quotations.list(user.organizationId(), filter, pageable);
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/dashboard")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get quotation dashboard KPIs and today's follow-ups")
    public QuotationViews.Dashboard getDashboard() {
        CurrentUser user = CurrentUser.require();
        return quotations.getDashboard(user.organizationId());
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get quotation details including active version and version history")
    public QuotationViews.Detail get(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return quotations.get(user.organizationId(), id);
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get specific version details of a quotation")
    public QuotationViews.VersionDetail getVersion(
            @PathVariable UUID id, @PathVariable int versionNumber) {
        CurrentUser user = CurrentUser.require();
        return quotations.getVersion(user.organizationId(), id, versionNumber);
    }

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create a new quotation with version 1")
    public ResponseEntity<QuotationViews.Detail> create(@Valid @RequestBody QuotationDtos.CreateQuotationRequest request) {
        CurrentUser user = CurrentUser.require();
        QuotationViews.Detail created = quotations.create(
                user.organizationId(), request.toCommand(), user.id(), user.email());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/quotations/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update an existing draft quotation")
    public QuotationViews.Detail updateDraft(
            @PathVariable UUID id, @Valid @RequestBody QuotationDtos.UpdateDraftRequest request) {
        CurrentUser user = CurrentUser.require();
        return quotations.updateDraft(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create a new revision version for an existing quotation")
    public ResponseEntity<QuotationViews.Detail> createVersion(
            @PathVariable UUID id, @Valid @RequestBody QuotationDtos.CreateVersionRequest request) {
        CurrentUser user = CurrentUser.require();
        QuotationViews.Detail updated = quotations.createNewVersion(
                user.organizationId(), id, request.toCommand(), user.email());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/request-approval")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Submit quotation for internal approval")
    public QuotationViews.Detail requestApproval(
            @PathVariable UUID id, @RequestBody(required = false) QuotationDtos.ApprovalActionRequest request) {
        CurrentUser user = CurrentUser.require();
        String notes = request != null ? request.notes() : null;
        return quotations.requestApproval(user.organizationId(), id, notes, user.email());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Approve pending quotation")
    public QuotationViews.Detail approve(
            @PathVariable UUID id, @RequestBody(required = false) QuotationDtos.ApprovalActionRequest request) {
        CurrentUser user = CurrentUser.require();
        String notes = request != null ? request.notes() : null;
        return quotations.approve(user.organizationId(), id, notes, user.email());
    }

    @PostMapping("/{id}/reject-approval")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Reject approval request and return quotation to draft")
    public QuotationViews.Detail rejectApproval(
            @PathVariable UUID id, @RequestBody(required = false) QuotationDtos.ApprovalActionRequest request) {
        CurrentUser user = CurrentUser.require();
        String reason = request != null && request.reason() != null ? request.reason() : "Changes requested";
        return quotations.rejectApproval(user.organizationId(), id, reason, user.email());
    }

    @PostMapping("/{id}/send")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Mark quotation as sent to client and set follow-up date")
    public QuotationViews.Detail sendQuotation(
            @PathVariable UUID id, @RequestBody(required = false) QuotationDtos.SendQuotationRequest request) {
        CurrentUser user = CurrentUser.require();
        LocalDate followUp = request != null ? request.nextFollowUpDate() : null;
        String notes = request != null ? request.notes() : null;
        return quotations.markSent(user.organizationId(), id, followUp, notes, user.email());
    }

    @PostMapping("/{id}/viewed")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Record that quotation was viewed by client")
    public QuotationViews.Detail markViewed(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return quotations.markViewed(user.organizationId(), id, user.email());
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Record that quotation was accepted by client")
    public QuotationViews.Detail markAccepted(
            @PathVariable UUID id, @RequestBody(required = false) QuotationDtos.ClientResponseRequest request) {
        CurrentUser user = CurrentUser.require();
        String notes = request != null ? request.notes() : null;
        return quotations.markAccepted(user.organizationId(), id, notes, user.email());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Record that quotation was rejected by client")
    public QuotationViews.Detail markRejected(
            @PathVariable UUID id, @RequestBody(required = false) QuotationDtos.ClientResponseRequest request) {
        CurrentUser user = CurrentUser.require();
        String reason = request != null && request.reason() != null ? request.reason() : "Client declined";
        return quotations.markRejected(user.organizationId(), id, reason, user.email());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Cancel a quotation")
    public QuotationViews.Detail cancel(
            @PathVariable UUID id, @RequestBody(required = false) QuotationDtos.ClientResponseRequest request) {
        CurrentUser user = CurrentUser.require();
        String reason = request != null && request.reason() != null ? request.reason() : "Cancelled";
        return quotations.cancel(user.organizationId(), id, reason, user.email());
    }

    @PostMapping("/{id}/generate-pdf")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Generate and return pre-signed download URI for quotation PDF")
    public ResponseEntity<Map<String, String>> generatePdf(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer versionNumber) {
        CurrentUser user = CurrentUser.require();
        URI downloadUrl = quotations.generateAndDownloadPdf(user.organizationId(), id, versionNumber, user.email());
        return ResponseEntity.ok(Map.of("url", downloadUrl.toString()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Soft delete a quotation")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        quotations.delete(user.organizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
