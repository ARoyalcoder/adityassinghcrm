package com.pawanputra.bos.fieldops.internal;

import com.pawanputra.bos.fieldops.api.JobStatus;
import com.pawanputra.bos.fieldops.api.JobType;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.identity.api.UserDirectory.UserSummary;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.persistence.Specs;
import com.pawanputra.bos.platform.storage.ObjectStorage;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field jobs: scheduling and assigning them, and what a technician does on one. The workflow is
 * one step at a time (see {@link JobStatus}); this class decides who may take a step and what
 * must be true before it is taken.
 */
@Service
@Transactional(readOnly = true)
public class FieldJobService {

    /** A job that is still to be done. */
    static final Set<JobStatus> OPEN = Set.of(
            JobStatus.ASSIGNED, JobStatus.ACCEPTED, JobStatus.EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS);
    static final Set<JobStatus> DONE = Set.of(JobStatus.COMPLETED, JobStatus.CUSTOMER_CONFIRMED);

    static final int MAX_CHECKLIST_ITEMS = 50;
    static final int MAX_PHOTOS = 30;
    static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    static final int MAX_SIGNATURE_LENGTH = 300_000;
    private static final Map<String, String> PHOTO_TYPES =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private static final Instant FAR_PAST = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final FieldJobRepository jobs;
    private final JdbcTemplate jdbc;
    private final UserDirectory users;
    private final ObjectStorage storage;
    private final Clock clock;

    FieldJobService(
            FieldJobRepository jobs, JdbcTemplate jdbc, UserDirectory users, ObjectStorage storage, Clock clock) {
        this.jobs = jobs;
        this.jdbc = jdbc;
        this.users = users;
        this.storage = storage;
        this.clock = clock;
    }

    /**
     * Who is asking, and what they may do with field jobs.
     *
     * @param viewAll sees every job of the organization (otherwise only their own)
     * @param manage  schedules, assigns and cancels jobs, and may act on any job
     * @param work    carries out the jobs assigned to them
     */
    public record Actor(UUID id, UUID organizationId, String email, boolean viewAll, boolean manage, boolean work) {
    }

    /** What a dispatcher sets. {@code checklist} of {@code null} on an edit leaves the checklist as it is. */
    public record Details(
            JobType jobType, String title, String instructions, UUID customerId, String customerName,
            String siteAddress, String siteContactName, String siteContactPhone, UUID projectId,
            Instant scheduledStart, Instant scheduledEnd, UUID technicianId, List<String> checklist) {
    }

    public record Filter(String search, JobStatus status, UUID technicianId) {
    }

    public enum Bucket { TODAY, UPCOMING, DELAYED, COMPLETED }

    /** @param delayed still to be done, and scheduled for a day that has passed */
    public record Summary(
            UUID id, String jobNumber, JobType jobType, String title, String customerName, String siteAddress,
            Instant scheduledStart, Instant scheduledEnd, UUID technicianId, String technicianName, JobStatus status,
            boolean delayed) {
    }

    public record ChecklistItem(UUID id, String label, boolean done, Instant doneAt, String doneByName) {
    }

    public record Material(UUID id, String name, BigDecimal quantity, String unit, String notes, String recordedByName,
            Instant createdAt) {
    }

    public record Photo(UUID id, String fileName, String contentType, long sizeBytes, String caption,
            String uploadedByName, Instant createdAt) {
    }

    public record Note(UUID id, String body, String authorName, Instant createdAt) {
    }

    public record SignOff(String signedByName, String remarks, String signature, Instant confirmedAt) {
    }

    /** The moment each step of the workflow was reached; {@code null} for steps not reached. */
    public record Progress(Instant assignedAt, Instant acceptedAt, Instant enRouteAt, Instant arrivedAt,
            Instant startedAt, Instant completedAt, Instant confirmedAt, Instant cancelledAt) {
    }

    /**
     * @param nextStatus the step that can be taken now; {@code null} if there is none
     * @param canAct     whether the person asking may take it and record work on this job
     */
    public record Detail(
            Summary job, String instructions, UUID customerId, String siteContactName, String siteContactPhone,
            UUID projectId, String completionNotes, Progress progress, SignOff signOff, List<ChecklistItem> checklist,
            List<Material> materials, List<Photo> photos, List<Note> notes, JobStatus nextStatus, boolean canAct,
            long version) {
    }

    public record Counts(long today, long upcoming, long delayed, long completed) {
    }

    public record PhotoContent(String contentType, long sizeBytes, InputStream content) {
    }

    public record Technician(UUID id, String name) {
    }

    // ------------------------------------------------------------------ reading

    public Page<Summary> search(Actor actor, Filter filter, Pageable pageable) {
        Specification<FieldJob> spec = Specs.<FieldJob>equalTo("organizationId", actor.organizationId())
                .and(Specs.search(filter.search(), "title", "customerName", "jobNumber", "siteAddress"))
                .and(Specs.equalTo("status", filter.status()))
                .and(Specs.equalTo("technicianId", filter.technicianId()));
        Instant dayStart = dayStart();
        return jobs.findAll(spec, pageable).map(job -> summary(job, dayStart));
    }

    /** One of the technician's own lists. */
    public List<Summary> myJobs(Actor actor, Bucket bucket) {
        Instant dayStart = dayStart();
        Instant dayEnd = dayEnd();
        List<FieldJob> found = switch (bucket) {
            case TODAY -> scheduled(actor.id(), dayStart, dayEnd);
            case UPCOMING -> scheduled(actor.id(), dayEnd, FAR_FUTURE);
            case DELAYED -> scheduled(actor.id(), FAR_PAST, dayStart);
            case COMPLETED -> jobs.findAllByTechnicianIdAndStatusInOrderByCompletedAtDesc(
                    actor.id(), DONE, PageRequest.of(0, 50));
        };
        return found.stream().map(job -> summary(job, dayStart)).toList();
    }

    public Counts myCounts(Actor actor) {
        Timestamp dayStart = Timestamp.from(dayStart());
        Timestamp dayEnd = Timestamp.from(dayEnd());
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE is_open AND scheduled_start >= ? AND scheduled_start < ?),
                       count(*) FILTER (WHERE is_open AND scheduled_start >= ?),
                       count(*) FILTER (WHERE is_open AND scheduled_start < ?),
                       count(*) FILTER (WHERE status IN ('COMPLETED', 'CUSTOMER_CONFIRMED'))
                FROM (SELECT status, scheduled_start,
                             status IN ('ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS') AS is_open
                      FROM field_jobs WHERE technician_id = ? AND organization_id = ? AND deleted_at IS NULL) mine
                """, (row, index) -> new Counts(row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4)),
                dayStart, dayEnd, dayEnd, dayStart, actor.id(), actor.organizationId());
    }

    public Detail get(Actor actor, UUID id) {
        return detail(actor, load(actor, id));
    }

    /** The people a job can be given to: the organization's active users. */
    public List<Technician> technicians(Actor actor) {
        return users.listActive(actor.organizationId()).stream()
                .map(user -> new Technician(user.id(), user.fullName())).toList();
    }

    public PhotoContent photoContent(Actor actor, UUID jobId, UUID photoId) {
        load(actor, jobId);
        return jdbc.query("SELECT storage_key, content_type, size_bytes FROM field_job_photos WHERE id = ? AND job_id = ?",
                row -> {
                    if (!row.next()) {
                        throw new ResourceNotFoundException("Photo", photoId);
                    }
                    return new PhotoContent(row.getString(2), row.getLong(3), storage.get(row.getString(1)));
                }, photoId, jobId);
    }

    // ------------------------------------------------------------------ scheduling and assigning

    @Transactional
    public Detail create(Actor actor, Details details) {
        validate(details);
        FieldJob job = new FieldJob(actor.organizationId(), "JOB-%06d".formatted(jobs.nextJobNumber()));
        apply(job, details);
        if (details.technicianId() != null) {
            UserSummary technician = technician(actor, details.technicianId());
            job.assignTo(technician.id(), technician.fullName(), clock.instant());
        }
        job = jobs.saveAndFlush(job);
        replaceChecklist(job.getId(), details.checklist() == null ? List.of() : details.checklist());
        return detail(actor, job);
    }

    @Transactional
    public Detail update(Actor actor, UUID id, Details details, long expectedVersion) {
        FieldJob job = load(actor, id);
        if (job.getVersion() != expectedVersion) {
            throw new ConflictException("This job was changed by someone else. Reload it and apply your changes again.");
        }
        if (job.getStatus().isClosed()) {
            throw new BusinessRuleException("A job that is " + words(job.getStatus()) + " can no longer be changed");
        }
        validate(details);
        apply(job, details);
        if (details.checklist() != null) {
            if (!job.getStatus().canBeReassigned()) {
                throw new BusinessRuleException("The checklist cannot be replaced once the technician has set off");
            }
            replaceChecklist(id, details.checklist());
        }
        return detail(actor, jobs.saveAndFlush(job));
    }

    @Transactional
    public Detail assign(Actor actor, UUID id, UUID technicianId) {
        FieldJob job = load(actor, id);
        if (!job.getStatus().canBeReassigned()) {
            throw new BusinessRuleException(
                    "A job that is " + words(job.getStatus()) + " cannot be given to someone else");
        }
        UserSummary technician = technician(actor, technicianId);
        job.assignTo(technician.id(), technician.fullName(), clock.instant());
        return detail(actor, jobs.saveAndFlush(job));
    }

    @Transactional
    public Detail cancel(Actor actor, UUID id) {
        FieldJob job = load(actor, id);
        if (job.getStatus().isClosed()) {
            throw new BusinessRuleException("A job that is " + words(job.getStatus()) + " cannot be cancelled");
        }
        job.moveTo(JobStatus.CANCELLED, clock.instant());
        return detail(actor, jobs.saveAndFlush(job));
    }

    // ------------------------------------------------------------------ the technician's work

    /**
     * Takes the next step of the workflow.
     *
     * @param to              the step being taken; must be the one that follows the current status
     * @param completionNotes with COMPLETED: what was done (optional)
     * @param signedByName    with CUSTOMER_CONFIRMED: who confirmed the work (required)
     * @param signature       with CUSTOMER_CONFIRMED: their signature as a PNG data URL (optional)
     */
    @Transactional
    public Detail advance(
            Actor actor, UUID id, JobStatus to, String completionNotes, String signedByName, String remarks,
            String signature) {
        FieldJob job = load(actor, id);
        requireWorker(actor, job);
        JobStatus next = job.getStatus().next().orElseThrow(() -> new BusinessRuleException(
                "A job that is " + words(job.getStatus()) + " has no further step"));
        if (to != next) {
            throw new BusinessRuleException("A job that is " + words(job.getStatus()) + " can only move to "
                    + words(next) + ", not to " + words(to));
        }
        if (to == JobStatus.COMPLETED) {
            Integer open = jdbc.queryForObject(
                    "SELECT count(*) FROM field_job_checklist_items WHERE job_id = ? AND NOT done", Integer.class, id);
            if (open != null && open > 0) {
                throw new BusinessRuleException(open == 1
                        ? "1 checklist item is still open. Complete the checklist before completing the job."
                        : open + " checklist items are still open. Complete the checklist before completing the job.");
            }
            job.setCompletionNotes(completionNotes);
        }
        if (to == JobStatus.CUSTOMER_CONFIRMED) {
            if (signedByName == null) {
                throw new FieldValidationException("signedByName", "Enter the name of the person confirming the work");
            }
            if (signature != null
                    && (!signature.startsWith("data:image/png;base64,") || signature.length() > MAX_SIGNATURE_LENGTH)) {
                throw new FieldValidationException("signature", "must be a signature drawn on the screen");
            }
            job.signOff(signedByName, remarks, signature);
        }
        job.moveTo(to, clock.instant());
        return detail(actor, jobs.saveAndFlush(job));
    }

    @Transactional
    public Detail tick(Actor actor, UUID id, UUID itemId, boolean done) {
        FieldJob job = workable(actor, id, "The checklist");
        int changed = jdbc.update("UPDATE field_job_checklist_items SET done = ?, done_at = ?, done_by_name = ?, "
                        + "updated_at = now() WHERE id = ? AND job_id = ?",
                done, done ? Timestamp.from(clock.instant()) : null, done ? nameOf(actor) : null, itemId, id);
        if (changed == 0) {
            throw new ResourceNotFoundException("Checklist item", itemId);
        }
        return detail(actor, job);
    }

    @Transactional
    public Detail addMaterial(Actor actor, UUID id, String name, BigDecimal quantity, String unit, String notes) {
        FieldJob job = workable(actor, id, "Materials");
        jdbc.update("INSERT INTO field_job_materials (job_id, name, quantity, unit, notes, recorded_by_name) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, name, quantity, unit, notes, nameOf(actor));
        return detail(actor, job);
    }

    @Transactional
    public Detail removeMaterial(Actor actor, UUID id, UUID materialId) {
        FieldJob job = workable(actor, id, "Materials");
        if (jdbc.update("DELETE FROM field_job_materials WHERE id = ? AND job_id = ?", materialId, id) == 0) {
            throw new ResourceNotFoundException("Material", materialId);
        }
        return detail(actor, job);
    }

    /** Notes can be added at any time until the job is closed. */
    @Transactional
    public Detail addNote(Actor actor, UUID id, String body) {
        FieldJob job = load(actor, id);
        requireWorker(actor, job);
        if (job.getStatus().isClosed()) {
            throw new BusinessRuleException("A job that is " + words(job.getStatus()) + " can no longer be changed");
        }
        jdbc.update("INSERT INTO field_job_notes (job_id, body, author_name) VALUES (?, ?, ?)", id, body, nameOf(actor));
        return detail(actor, job);
    }

    @Transactional
    public Detail addPhoto(
            Actor actor, UUID id, String fileName, String contentType, long sizeBytes, String caption,
            InputStream content) {
        FieldJob job = workable(actor, id, "Photos");
        String extension = contentType == null ? null : PHOTO_TYPES.get(contentType.toLowerCase(Locale.ROOT));
        if (extension == null) {
            throw new FieldValidationException("file", "must be a JPEG, PNG or WebP image");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_PHOTO_BYTES) {
            throw new FieldValidationException("file", "must be at most 10 MB");
        }
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM field_job_photos WHERE job_id = ?", Integer.class, id);
        if (existing != null && existing >= MAX_PHOTOS) {
            throw new BusinessRuleException("A job can have at most " + MAX_PHOTOS + " photos");
        }
        // The stored name is generated: nothing typed or sent by the device becomes part of the key.
        String key = "field-jobs/" + id + "/" + UUID.randomUUID() + "." + extension;
        try {
            storage.put(key, content, sizeBytes, contentType);
        } catch (RuntimeException storageFailure) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Photo storage is not available right now. Try again later.", storageFailure);
        }
        String name = fileName == null || fileName.isBlank() ? "photo." + extension
                : fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        jdbc.update("INSERT INTO field_job_photos (job_id, storage_key, file_name, content_type, size_bytes, caption, "
                        + "uploaded_by_name) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, key, name.length() > 255 ? name.substring(name.length() - 255) : name, contentType, sizeBytes,
                caption, nameOf(actor));
        return detail(actor, job);
    }

    @Transactional
    public Detail removePhoto(Actor actor, UUID id, UUID photoId) {
        FieldJob job = workable(actor, id, "Photos");
        List<String> keys = jdbc.queryForList(
                "SELECT storage_key FROM field_job_photos WHERE id = ? AND job_id = ?", String.class, photoId, id);
        if (keys.isEmpty()) {
            throw new ResourceNotFoundException("Photo", photoId);
        }
        jdbc.update("DELETE FROM field_job_photos WHERE id = ?", photoId);
        storage.delete(keys.getFirst());
        return detail(actor, job);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The job, if the person asking may see it. Someone who only carries out jobs sees their own;
     * any other job looks to them as if it does not exist.
     */
    private FieldJob load(Actor actor, UUID id) {
        FieldJob job = jobs.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", id));
        if (!actor.viewAll() && !actor.manage() && !actor.id().equals(job.getTechnicianId())) {
            throw new ResourceNotFoundException("Job", id);
        }
        return job;
    }

    private static boolean canAct(Actor actor, FieldJob job) {
        return actor.manage() || (actor.work() && actor.id().equals(job.getTechnicianId()));
    }

    private static void requireWorker(Actor actor, FieldJob job) {
        if (!canAct(actor, job)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the technician this job is assigned to can do that");
        }
    }

    /** The job, for recording work on it: by its technician (or a manager), while it is under way. */
    private FieldJob workable(Actor actor, UUID id, String what) {
        FieldJob job = load(actor, id);
        requireWorker(actor, job);
        if (!job.getStatus().acceptsWork()) {
            throw new BusinessRuleException(what + " cannot be changed while the job is " + words(job.getStatus())
                    + (job.getStatus() == JobStatus.ASSIGNED ? ". Accept the job first." : ""));
        }
        return job;
    }

    private UserSummary technician(Actor actor, UUID technicianId) {
        return users.find(actor.organizationId(), technicianId).filter(UserSummary::active)
                .orElseThrow(() -> new FieldValidationException("technicianId", "Choose an active member of staff"));
    }

    private String nameOf(Actor actor) {
        return users.find(actor.organizationId(), actor.id()).map(UserSummary::fullName).orElse(actor.email());
    }

    private static void validate(Details details) {
        if (details.scheduledEnd() != null && details.scheduledEnd().isBefore(details.scheduledStart())) {
            throw new FieldValidationException("scheduledEnd", "must not be before the start");
        }
        if (details.checklist() != null && details.checklist().size() > MAX_CHECKLIST_ITEMS) {
            throw new FieldValidationException("checklist", "At most " + MAX_CHECKLIST_ITEMS + " items");
        }
    }

    private static void apply(FieldJob job, Details details) {
        job.setJobType(details.jobType() == null ? JobType.JOB : details.jobType());
        job.setTitle(details.title());
        job.setInstructions(details.instructions());
        job.setCustomerId(details.customerId());
        job.setCustomerName(details.customerName());
        job.setSiteAddress(details.siteAddress());
        job.setSiteContactName(details.siteContactName());
        job.setSiteContactPhone(details.siteContactPhone());
        job.setProjectId(details.projectId());
        job.setScheduledStart(details.scheduledStart());
        job.setScheduledEnd(details.scheduledEnd());
    }

    private void replaceChecklist(UUID jobId, List<String> labels) {
        jdbc.update("DELETE FROM field_job_checklist_items WHERE job_id = ?", jobId);
        for (int position = 0; position < labels.size(); position++) {
            jdbc.update("INSERT INTO field_job_checklist_items (job_id, label, position) VALUES (?, ?, ?)",
                    jobId, labels.get(position), position);
        }
    }

    private List<FieldJob> scheduled(UUID technicianId, Instant from, Instant to) {
        return jobs.findAllByTechnicianIdAndStatusInAndScheduledStartGreaterThanEqualAndScheduledStartLessThanOrderByScheduledStartAsc(
                technicianId, OPEN, from, to);
    }

    private Instant dayStart() {
        return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
    }

    private Instant dayEnd() {
        return LocalDate.now(clock).plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    }

    /** IN_PROGRESS as "in progress", for messages. */
    private static String words(JobStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static Summary summary(FieldJob job, Instant dayStart) {
        return new Summary(
                job.getId(), job.getJobNumber(), job.getJobType(), job.getTitle(), job.getCustomerName(),
                job.getSiteAddress(), job.getScheduledStart(), job.getScheduledEnd(), job.getTechnicianId(),
                job.getTechnicianName(), job.getStatus(),
                OPEN.contains(job.getStatus()) && job.getScheduledStart().isBefore(dayStart));
    }

    private Detail detail(Actor actor, FieldJob job) {
        UUID id = job.getId();
        List<ChecklistItem> checklist = jdbc.query(
                "SELECT id, label, done, done_at, done_by_name FROM field_job_checklist_items WHERE job_id = ? ORDER BY position",
                (row, index) -> new ChecklistItem(row.getObject(1, UUID.class), row.getString(2), row.getBoolean(3),
                        instant(row.getTimestamp(4)), row.getString(5)), id);
        List<Material> materials = jdbc.query(
                "SELECT id, name, quantity, unit, notes, recorded_by_name, created_at FROM field_job_materials "
                        + "WHERE job_id = ? ORDER BY created_at, id",
                (row, index) -> new Material(row.getObject(1, UUID.class), row.getString(2),
                        row.getBigDecimal(3).stripTrailingZeros().scale() < 0
                                ? row.getBigDecimal(3).setScale(0) : row.getBigDecimal(3).stripTrailingZeros(),
                        row.getString(4), row.getString(5), row.getString(6), instant(row.getTimestamp(7))), id);
        List<Photo> photos = jdbc.query(
                "SELECT id, file_name, content_type, size_bytes, caption, uploaded_by_name, created_at "
                        + "FROM field_job_photos WHERE job_id = ? ORDER BY created_at, id",
                (row, index) -> new Photo(row.getObject(1, UUID.class), row.getString(2), row.getString(3),
                        row.getLong(4), row.getString(5), row.getString(6), instant(row.getTimestamp(7))), id);
        List<Note> notes = jdbc.query(
                "SELECT id, body, author_name, created_at FROM field_job_notes WHERE job_id = ? ORDER BY created_at DESC, id",
                (row, index) -> new Note(row.getObject(1, UUID.class), row.getString(2), row.getString(3),
                        instant(row.getTimestamp(4))), id);
        boolean canAct = canAct(actor, job);
        return new Detail(
                summary(job, dayStart()), job.getInstructions(), job.getCustomerId(), job.getSiteContactName(),
                job.getSiteContactPhone(), job.getProjectId(), job.getCompletionNotes(),
                new Progress(job.getAssignedAt(), job.getAcceptedAt(), job.getEnRouteAt(), job.getArrivedAt(),
                        job.getStartedAt(), job.getCompletedAt(), job.getConfirmedAt(), job.getCancelledAt()),
                job.getSignedByName() == null ? null
                        : new SignOff(job.getSignedByName(), job.getSignoffRemarks(), job.getSignature(), job.getConfirmedAt()),
                checklist, materials, photos, notes, job.getStatus().next().orElse(null), canAct, job.getVersion());
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
