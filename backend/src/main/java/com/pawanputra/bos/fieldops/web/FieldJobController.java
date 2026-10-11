package com.pawanputra.bos.fieldops.web;

import com.pawanputra.bos.fieldops.api.JobStatus;
import com.pawanputra.bos.fieldops.api.JobType;
import com.pawanputra.bos.fieldops.internal.FieldJobService;
import com.pawanputra.bos.fieldops.internal.FieldJobService.Actor;
import com.pawanputra.bos.fieldops.internal.FieldJobService.Bucket;
import com.pawanputra.bos.fieldops.internal.FieldJobService.Counts;
import com.pawanputra.bos.fieldops.internal.FieldJobService.Detail;
import com.pawanputra.bos.fieldops.internal.FieldJobService.PhotoContent;
import com.pawanputra.bos.fieldops.internal.FieldJobService.Summary;
import com.pawanputra.bos.fieldops.internal.FieldJobService.Technician;
import com.pawanputra.bos.identity.api.Permissions;
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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
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
 * Field jobs. A dispatcher schedules and assigns them; a technician works through them on site.
 *
 * <p>The permission on each endpoint says who may call it at all. Which jobs that person may then
 * see or act on (a technician: only their own) is decided in the service, from the same permissions.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/field-jobs")
@Tag(name = "Field jobs")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class FieldJobController {

    private static final String CAN_SEE_ALL = "hasAnyAuthority('" + Permissions.FIELD_JOB_VIEW + "', '"
            + Permissions.FIELD_JOB_MANAGE + "')";
    private static final String CAN_MANAGE = "hasAuthority('" + Permissions.FIELD_JOB_MANAGE + "')";
    private static final String CAN_WORK = "hasAuthority('" + Permissions.FIELD_JOB_WORK + "')";
    /** Anyone with any field-job permission; the service narrows it to the jobs they may see. */
    private static final String CAN_OPEN = "hasAnyAuthority('" + Permissions.FIELD_JOB_VIEW + "', '"
            + Permissions.FIELD_JOB_MANAGE + "', '" + Permissions.FIELD_JOB_WORK + "')";
    private static final String CAN_ACT = "hasAnyAuthority('" + Permissions.FIELD_JOB_MANAGE + "', '"
            + Permissions.FIELD_JOB_WORK + "')";

    private static final String PHONE_PATTERN = "^[+0-9][0-9 ()-]{5,28}$";
    private static final Map<String, String> SORTS = Map.of(
            "scheduledStart", "scheduledStart", "jobNumber", "jobNumber", "status", "status",
            "customer", "customerName", "technician", "technicianName");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("scheduledStart"), Sort.Order.asc("id"));

    private final FieldJobService jobs;

    public FieldJobController(FieldJobService jobs) {
        this.jobs = jobs;
    }

    /**
     * Create or edit a job.
     *
     * @param checklist   what the technician must tick off; on an edit, leave out to keep it as it is
     * @param technicianId on create: who to give the job to straight away (optional)
     * @param version     required when editing: the version that was loaded
     */
    record JobRequest(
            JobType jobType,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String instructions,
            UUID customerId,
            @NotBlank @Size(max = 200) String customerName,
            @NotBlank @Size(max = 500) String siteAddress,
            @Size(max = 200) String siteContactName,
            @Pattern(regexp = PHONE_PATTERN, message = "must be a phone number") String siteContactPhone,
            UUID projectId,
            @NotNull Instant scheduledStart,
            Instant scheduledEnd,
            UUID technicianId,
            @Size(max = 50) List<@NotBlank @Size(max = 300) String> checklist,
            Long version) {

        FieldJobService.Details details() {
            return new FieldJobService.Details(jobType, title.trim(), trimToNull(instructions), customerId,
                    customerName.trim(), siteAddress.trim(), trimToNull(siteContactName), trimToNull(siteContactPhone),
                    projectId, scheduledStart, scheduledEnd, technicianId,
                    checklist == null ? null : checklist.stream().map(String::trim).toList());
        }
    }

    record AssignRequest(@NotNull UUID technicianId) {
    }

    /**
     * Take the next step.
     *
     * @param to              the status to move to; must be the next one
     * @param completionNotes with COMPLETED
     * @param signedByName    with CUSTOMER_CONFIRMED: required
     * @param signature       with CUSTOMER_CONFIRMED: a PNG data URL of the signature
     */
    record AdvanceRequest(
            @NotNull JobStatus to,
            @Size(max = 4000) String completionNotes,
            @Size(max = 200) String signedByName,
            @Size(max = 1000) String remarks,
            String signature) {
    }

    record TickRequest(@NotNull Boolean done) {
    }

    record MaterialRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal quantity,
            @Size(max = 20) String unit,
            @Size(max = 500) String notes) {
    }

    record NoteRequest(@NotBlank @Size(max = 2000) String body) {
    }

    // ------------------------------------------------------------------ the technician's own jobs

    @GetMapping("/my")
    @PreAuthorize(CAN_WORK)
    @Operation(summary = "One of your own job lists. bucket: TODAY (scheduled today, still to do), UPCOMING, DELAYED "
            + "(still to do, scheduled for a day that has passed) or COMPLETED. Requires FIELD_JOB_WORK.")
    public List<Summary> myJobs(@RequestParam(defaultValue = "TODAY") Bucket bucket) {
        return jobs.myJobs(actor(), bucket);
    }

    @GetMapping("/my/summary")
    @PreAuthorize(CAN_WORK)
    @Operation(summary = "How many jobs you have in each list. Requires FIELD_JOB_WORK.")
    public Counts myCounts() {
        return jobs.myCounts(actor());
    }

    // ------------------------------------------------------------------ scheduling and assigning

    @GetMapping
    @PreAuthorize(CAN_SEE_ALL)
    @Operation(summary = "Search all jobs. Filters: search (title, customer, job number, address), status, "
            + "technicianId. Sort: scheduledStart (default, latest first), jobNumber, status, customer, technician. "
            + "Requires FIELD_JOB_VIEW or FIELD_JOB_MANAGE.")
    public PageResponse<Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) UUID technicianId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                jobs.search(actor(), new FieldJobService.Filter(search, status, technicianId),
                        Paging.of(page, size, sort, SORTS, DEFAULT_SORT)),
                Function.identity());
    }

    @GetMapping("/technicians")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "The people a job can be assigned to. Requires FIELD_JOB_MANAGE.")
    public List<Technician> technicians() {
        return jobs.technicians(actor());
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Create a job or site visit, with its checklist, and optionally assign it. "
            + "Requires FIELD_JOB_MANAGE.")
    public ResponseEntity<Detail> create(@Valid @RequestBody JobRequest request) {
        Detail created = jobs.create(actor(), request.details());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/field-jobs/" + created.job().id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Edit a job's details and schedule. Requires FIELD_JOB_MANAGE.")
    public Detail update(@PathVariable UUID id, @Valid @RequestBody JobRequest request) {
        if (request.version() == null) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "version is required when updating");
        }
        return jobs.update(actor(), id, request.details(), request.version());
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Give the job to a technician, or to another one while nobody has set off. "
            + "Requires FIELD_JOB_MANAGE.")
    public Detail assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        return jobs.assign(actor(), id, request.technicianId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Cancel a job that is not yet closed. Requires FIELD_JOB_MANAGE.")
    public Detail cancel(@PathVariable UUID id) {
        return jobs.cancel(actor(), id);
    }

    // ------------------------------------------------------------------ one job

    @GetMapping("/{id}")
    @PreAuthorize(CAN_OPEN)
    @Operation(summary = "One job with its customer and site, instructions, checklist, materials, photos, notes and "
            + "sign-off. A technician can open only their own jobs.")
    public Detail get(@PathVariable UUID id) {
        return jobs.get(actor(), id);
    }

    @PostMapping("/{id}/advance")
    @PreAuthorize(CAN_ACT)
    @Operation(summary = "Take the next step: ASSIGNED, ACCEPTED, EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED, "
            + "CUSTOMER_CONFIRMED. Completing needs the whole checklist ticked; customer confirmation needs the "
            + "name of the person confirming. By the assigned technician, or with FIELD_JOB_MANAGE.")
    public Detail advance(@PathVariable UUID id, @Valid @RequestBody AdvanceRequest request) {
        return jobs.advance(actor(), id, request.to(), trimToNull(request.completionNotes()),
                trimToNull(request.signedByName()), trimToNull(request.remarks()), trimToNull(request.signature()));
    }

    @PutMapping("/{id}/checklist/{itemId}")
    @PreAuthorize(CAN_ACT)
    @Operation(summary = "Tick or untick a checklist item.")
    public Detail tick(@PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody TickRequest request) {
        return jobs.tick(actor(), id, itemId, request.done());
    }

    @PostMapping("/{id}/materials")
    @PreAuthorize(CAN_ACT)
    @Operation(summary = "Record a material used on the job.")
    public Detail addMaterial(@PathVariable UUID id, @Valid @RequestBody MaterialRequest request) {
        return jobs.addMaterial(actor(), id, request.name().trim(), request.quantity(), trimToNull(request.unit()),
                trimToNull(request.notes()));
    }

    @DeleteMapping("/{id}/materials/{materialId}")
    @PreAuthorize(CAN_ACT)
    @Operation(summary = "Remove a material recorded by mistake.")
    public Detail removeMaterial(@PathVariable UUID id, @PathVariable UUID materialId) {
        return jobs.removeMaterial(actor(), id, materialId);
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize(CAN_ACT)
    @Operation(summary = "Add a note to the job.")
    public Detail addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return jobs.addNote(actor(), id, request.body().trim());
    }

    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAN_ACT)
    @Operation(summary = "Upload a photo (JPEG, PNG or WebP, at most 10 MB), with an optional caption.")
    public Detail addPhoto(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 300) String caption) {
        try (InputStream content = file.getInputStream()) {
            return jobs.addPhoto(actor(), id, file.getOriginalFilename(), file.getContentType(), file.getSize(),
                    trimToNull(caption), content);
        } catch (IOException unreadable) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The uploaded photo could not be read", unreadable);
        }
    }

    @GetMapping("/{id}/photos/{photoId}/content")
    @PreAuthorize(CAN_OPEN)
    @Operation(summary = "The photo itself.")
    public ResponseEntity<InputStreamResource> photo(@PathVariable UUID id, @PathVariable UUID photoId) {
        PhotoContent photo = jobs.photoContent(actor(), id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .contentLength(photo.sizeBytes())
                // A photo never changes once uploaded, but it is private to people who may see the job.
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(photo.content()));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize(CAN_ACT)
    @Operation(summary = "Remove a photo.")
    public Detail removePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        return jobs.removePhoto(actor(), id, photoId);
    }

    private static Actor actor() {
        CurrentUser user = CurrentUser.require();
        return new Actor(user.id(), user.organizationId(), user.email(),
                user.hasPermission(Permissions.FIELD_JOB_VIEW), user.hasPermission(Permissions.FIELD_JOB_MANAGE),
                user.hasPermission(Permissions.FIELD_JOB_WORK));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
