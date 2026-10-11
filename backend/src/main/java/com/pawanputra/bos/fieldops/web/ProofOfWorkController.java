package com.pawanputra.bos.fieldops.web;

import com.pawanputra.bos.fieldops.api.ProofAttachmentType;
import com.pawanputra.bos.fieldops.internal.ProofOfWorkDtos;
import com.pawanputra.bos.fieldops.internal.ProofOfWorkService;
import com.pawanputra.bos.fieldops.internal.ProofOfWorkService.Actor;
import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiPaths.V1 + "/proof-of-work")
@Tag(name = "Proof of Work")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ProofOfWorkController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.PROOF_OF_WORK_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.PROOF_OF_WORK_CREATE + "')";
    private static final String CAN_VERIFY = "hasAuthority('" + Permissions.PROOF_OF_WORK_VERIFY + "')";
    private static final String CAN_DELETE = "hasAuthority('" + Permissions.PROOF_OF_WORK_DELETE + "')";

    private final ProofOfWorkService service;

    public ProofOfWorkController(ProofOfWorkService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/timeline")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get project Proof of Work timeline with before/after photos, checklists, and sign-offs")
    public ProofOfWorkDtos.TimelineView getProjectTimeline(@PathVariable UUID projectId) {
        return service.getProjectTimeline(actor(), projectId);
    }

    @GetMapping("/jobs/{jobId}/timeline")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get field job Proof of Work timeline")
    public ProofOfWorkDtos.TimelineView getJobTimeline(@PathVariable UUID jobId) {
        return service.getJobTimeline(actor(), jobId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get single Proof of Work item details")
    public ProofOfWorkDtos.ProofItemDetail getItem(@PathVariable UUID id) {
        return service.getItem(actor(), id);
    }

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create a Proof of Work record for a project or field job")
    public ResponseEntity<ProofOfWorkDtos.ProofItemDetail> create(
            @Valid @RequestBody ProofOfWorkDtos.CreateProofItemRequest req) {
        ProofOfWorkDtos.ProofItemDetail created = service.createProofItem(actor(), req);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/proof-of-work/" + created.id())).body(created);
    }

    @PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Upload photo (before/during/after) or technical document to S3/MinIO storage abstraction")
    public ProofOfWorkDtos.ProofItemDetail addAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") ProofAttachmentType type,
            @RequestParam(value = "caption", required = false) String caption) {
        try (InputStream stream = file.getInputStream()) {
            return service.addAttachment(
                    actor(),
                    id,
                    type,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    caption,
                    stream);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Could not read uploaded attachment", e);
        }
    }

    @PutMapping("/{id}/checklists/{checklistId}")
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Toggle checklist item completion with technician sign and timestamp")
    public ProofOfWorkDtos.ProofItemDetail toggleChecklist(
            @PathVariable UUID id,
            @PathVariable UUID checklistId,
            @Valid @RequestBody ProofOfWorkDtos.ChecklistToggleRequest req) {
        return service.toggleChecklistItem(actor(), id, checklistId, req.completed());
    }

    @PostMapping("/{id}/customer-confirmation")
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Record customer sign-off confirmation with signature and remarks")
    public ProofOfWorkDtos.ProofItemDetail recordCustomerConfirmation(
            @PathVariable UUID id,
            @Valid @RequestBody ProofOfWorkDtos.CustomerConfirmationRequest req) {
        return service.recordCustomerConfirmation(actor(), id, req);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize(CAN_VERIFY)
    @Operation(summary = "Verify proof of work submission (PM/Admin sign-off)")
    public ProofOfWorkDtos.ProofItemDetail verify(
            @PathVariable UUID id,
            @Valid @RequestBody ProofOfWorkDtos.VerificationRequest req) {
        return service.verifyProofItem(actor(), id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Safely delete/archive proof of work item with audit reason (Admin only, non-verified)")
    public void deleteProof(
            @PathVariable UUID id,
            @Valid @RequestBody ProofOfWorkDtos.DeleteProofRequest req) {
        service.deleteProofItem(actor(), id, req.reason());
    }

    private static Actor actor() {
        CurrentUser user = CurrentUser.require();
        return new Actor(
                user.id(),
                user.organizationId(),
                user.email(),
                user.hasPermission(Permissions.PROOF_OF_WORK_VIEW),
                user.hasPermission(Permissions.PROOF_OF_WORK_CREATE),
                user.hasPermission(Permissions.PROOF_OF_WORK_VERIFY),
                user.hasPermission(Permissions.PROOF_OF_WORK_DELETE));
    }
}
