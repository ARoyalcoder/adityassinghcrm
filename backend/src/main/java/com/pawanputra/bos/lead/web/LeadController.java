package com.pawanputra.bos.lead.web;

import com.pawanputra.bos.catalog.api.LeadCustomFields.FileReference;
import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.lead.api.LeadHealth;
import com.pawanputra.bos.lead.api.LeadPriority;
import com.pawanputra.bos.lead.api.LeadStatus;
import com.pawanputra.bos.lead.api.QualificationStatus;
import com.pawanputra.bos.lead.internal.LeadQualificationService;
import com.pawanputra.bos.lead.internal.LeadQualificationService.HistoryEntry;
import com.pawanputra.bos.lead.internal.LeadService;
import com.pawanputra.bos.lead.internal.LeadService.ActivityView;
import com.pawanputra.bos.lead.internal.LeadService.Detail;
import com.pawanputra.bos.lead.internal.LeadService.Summary;
import com.pawanputra.bos.lead.web.LeadDtos.ActivityRequest;
import com.pawanputra.bos.lead.web.LeadDtos.AnswersRequest;
import com.pawanputra.bos.lead.web.LeadDtos.LeadRequest;
import com.pawanputra.bos.lead.web.LeadDtos.QualificationResponse;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Sort;
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

/**
 * Leads and their qualification. The organization always comes from the access token. This class
 * only translates HTTP to service calls; the rules live in the services and the scorer.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/leads")
@Tag(name = "Leads")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class LeadController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.LEAD_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.LEAD_CREATE + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.LEAD_UPDATE + "')";
    private static final String CAN_DELETE = "hasAuthority('" + Permissions.LEAD_DELETE + "')";
    private static final String CAN_CREATE_OR_UPDATE = "hasAnyAuthority('" + Permissions.LEAD_CREATE + "', '"
            + Permissions.LEAD_UPDATE + "')";

    private static final Map<String, String> SORTS = Map.of(
            "score", "score",
            "name", "contactName",
            "leadNumber", "leadNumber",
            "estimatedValue", "estimatedValue",
            "createdAt", "createdAt");
    /** The leads most worth attention first. */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("score"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final LeadService leads;
    private final LeadQualificationService qualification;

    public LeadController(LeadService leads, LeadQualificationService qualification) {
        this.leads = leads;
        this.qualification = qualification;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Search leads. Filters: search (name, company, lead number, email, phone), status, priority, "
            + "health, qualificationStatus, serviceId. Sort: score (default, highest first), name, leadNumber, "
            + "estimatedValue, createdAt. Requires LEAD_VIEW.")
    public PageResponse<Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) LeadPriority priority,
            @RequestParam(required = false) LeadHealth health,
            @RequestParam(required = false) QualificationStatus qualificationStatus,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                leads.search(organizationId(),
                        new LeadService.Filter(search, status, priority, health, qualificationStatus, serviceId),
                        Paging.of(page, size, sort, SORTS, DEFAULT_SORT)),
                Function.identity());
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "One lead, with the answers to its service's own fields. Requires LEAD_VIEW.")
    public Detail get(@PathVariable UUID id) {
        return leads.get(organizationId(), id);
    }

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create a lead. customFields are checked against the service's fields; problems with them "
            + "are reported as customFields.<key>. Requires LEAD_CREATE.")
    public ResponseEntity<Detail> create(@Valid @RequestBody LeadRequest request) {
        Detail created = leads.create(organizationId(), request.details());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/leads/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Edit a lead, including closing it as WON or LOST. Requires LEAD_UPDATE.")
    public Detail update(@PathVariable UUID id, @Valid @RequestBody LeadRequest request) {
        if (request.version() == null) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "version is required when updating");
        }
        return leads.update(organizationId(), id, request.details(), request.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Delete a lead. Requires LEAD_DELETE.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        leads.delete(organizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/custom-field-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAN_CREATE_OR_UPDATE)
    @Operation(summary = "Upload a file for one of a service's FILE fields while entering a lead. Returns the "
            + "reference to send as that field's answer in customFields. Requires LEAD_CREATE or LEAD_UPDATE.")
    public ResponseEntity<FileReference> uploadFieldFile(
            @RequestParam UUID serviceId, @RequestParam String fieldKey, @RequestParam("file") MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return ResponseEntity.status(201).body(leads.storeFieldFile(
                    serviceId, fieldKey, file.getOriginalFilename(), file.getContentType(), file.getSize(), content));
        } catch (IOException unreadable) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The uploaded file could not be read", unreadable);
        }
    }

    // ------------------------------------------------------------------ activity

    @GetMapping("/{id}/activities")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "What has been done about the lead, newest first. Requires LEAD_VIEW.")
    public List<ActivityView> activities(@PathVariable UUID id) {
        return leads.activities(organizationId(), id);
    }

    @PostMapping("/{id}/activities")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Log a call, meeting, email or visit. The lead is rescored. Requires LEAD_UPDATE.")
    public ResponseEntity<ActivityView> logActivity(@PathVariable UUID id, @Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.status(201).body(leads.logActivity(
                CurrentUser.require(), id, request.type(), request.occurredAt(), request.subject().trim()));
    }

    // ------------------------------------------------------------------ qualification

    @GetMapping("/{id}/qualification")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Why this score: the lead's score with every factor that added or removed points, its "
            + "priority, health and qualification status with the reason for each, and the qualification "
            + "questions with the answers so far. Worked out as of now. Requires LEAD_VIEW.")
    public QualificationResponse qualification(@PathVariable UUID id) {
        return QualificationResponse.from(qualification.evaluation(organizationId(), id));
    }

    @PutMapping("/{id}/qualification/answers")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Answer qualification questions (question key to option value; null clears). Questions not "
            + "mentioned keep their answers. Returns the new assessment. Requires LEAD_UPDATE.")
    public QualificationResponse answer(@PathVariable UUID id, @Valid @RequestBody AnswersRequest request) {
        return QualificationResponse.from(qualification.answer(organizationId(), id, request.answers()));
    }

    @GetMapping("/{id}/score-history")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "How the lead's assessment has changed, newest first. Requires LEAD_VIEW.")
    public List<HistoryEntry> scoreHistory(@PathVariable UUID id) {
        return qualification.history(organizationId(), id);
    }

    private static UUID organizationId() {
        return CurrentUser.require().organizationId();
    }
}
