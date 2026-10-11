package com.pawanputra.bos.fieldops.internal;

import com.pawanputra.bos.audit.api.AuditAction;
import com.pawanputra.bos.audit.api.AuditEvent;
import com.pawanputra.bos.audit.api.AuditRecorder;
import com.pawanputra.bos.fieldops.api.ProofAttachmentType;
import com.pawanputra.bos.fieldops.api.ProofCategory;
import com.pawanputra.bos.fieldops.api.ProofEntityType;
import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.identity.api.UserDirectory.UserSummary;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.storage.ObjectStorage;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProofOfWorkService {

    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024; // 25 MB
    private static final Duration PRESIGNED_URL_TTL = Duration.ofHours(2);

    private final JdbcTemplate jdbc;
    private final ObjectStorage storage;
    private final AuditRecorder audit;
    private final UserDirectory users;
    private final Clock clock;

    public ProofOfWorkService(
            JdbcTemplate jdbc,
            ObjectStorage storage,
            AuditRecorder audit,
            UserDirectory users,
            Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.audit = audit;
        this.users = users;
        this.clock = clock;
    }

    public record Actor(
            UUID id,
            UUID organizationId,
            String email,
            boolean canView,
            boolean canCreate,
            boolean canVerify,
            boolean canDelete) {
    }

    // ------------------------------------------------------------------ Queries

    public ProofOfWorkDtos.TimelineView getProjectTimeline(Actor actor, UUID projectId) {
        return getTimeline(actor, ProofEntityType.PROJECT, projectId);
    }

    public ProofOfWorkDtos.TimelineView getJobTimeline(Actor actor, UUID jobId) {
        return getTimeline(actor, ProofEntityType.JOB, jobId);
    }

    private ProofOfWorkDtos.TimelineView getTimeline(Actor actor, ProofEntityType entityType, UUID entityId) {
        String column = entityType == ProofEntityType.PROJECT ? "project_id" : "job_id";
        List<UUID> itemIds = jdbc.queryForList(
                "SELECT id FROM proof_of_work_items WHERE organization_id = ? AND " + column + " = ? AND deleted_at IS NULL ORDER BY recorded_at DESC",
                UUID.class, actor.organizationId(), entityId);

        List<ProofOfWorkDtos.ProofItemDetail> items = new ArrayList<>();
        long verifiedCount = 0;
        long confirmedCount = 0;
        long photoCount = 0;
        long docCount = 0;

        for (UUID itemId : itemIds) {
            ProofOfWorkDtos.ProofItemDetail detail = getItem(actor, itemId);
            items.add(detail);
            if (detail.verified()) {
                verifiedCount++;
            }
            if (detail.customerConfirmed()) {
                confirmedCount++;
            }
            for (ProofOfWorkDtos.AttachmentView att : detail.attachments()) {
                if (att.attachmentType() == ProofAttachmentType.DOCUMENT) {
                    docCount++;
                } else {
                    photoCount++;
                }
            }
        }

        return new ProofOfWorkDtos.TimelineView(
                entityId,
                entityType,
                items.size(),
                verifiedCount,
                confirmedCount,
                photoCount,
                docCount,
                items);
    }

    public ProofOfWorkDtos.ProofItemDetail getItem(Actor actor, UUID itemId) {
        return jdbc.query(
                "SELECT id, organization_id, entity_type, project_id, job_id, title, category, technician_id, technician_name, "
                        + "technician_notes, customer_confirmed, customer_name, customer_remarks, customer_signature, "
                        + "confirmed_at, verified, verified_by_id, verified_by_name, verified_at, recorded_at, created_at, created_by, "
                        + "updated_at, updated_by FROM proof_of_work_items WHERE id = ? AND organization_id = ? AND deleted_at IS NULL",
                rs -> {
                    if (!rs.next()) {
                        throw new ResourceNotFoundException("ProofOfWorkItem", itemId);
                    }
                    UUID id = rs.getObject("id", UUID.class);
                    UUID orgId = rs.getObject("organization_id", UUID.class);
                    ProofEntityType entityType = ProofEntityType.valueOf(rs.getString("entity_type"));
                    UUID projectId = rs.getObject("project_id", UUID.class);
                    UUID jobId = rs.getObject("job_id", UUID.class);
                    String title = rs.getString("title");
                    ProofCategory category = ProofCategory.valueOf(rs.getString("category"));
                    UUID techId = rs.getObject("technician_id", UUID.class);
                    String techName = rs.getString("technician_name");
                    String techNotes = rs.getString("technician_notes");
                    boolean custConfirmed = rs.getBoolean("customer_confirmed");
                    String custName = rs.getString("customer_name");
                    String custRemarks = rs.getString("customer_remarks");
                    String custSig = rs.getString("customer_signature");
                    Instant confirmedAt = toInstant(rs.getTimestamp("confirmed_at"));
                    boolean verified = rs.getBoolean("verified");
                    UUID verifiedById = rs.getObject("verified_by_id", UUID.class);
                    String verifiedByName = rs.getString("verified_by_name");
                    Instant verifiedAt = toInstant(rs.getTimestamp("verified_at"));
                    Instant recordedAt = toInstant(rs.getTimestamp("recorded_at"));
                    Instant createdAt = toInstant(rs.getTimestamp("created_at"));
                    String createdBy = rs.getString("created_by");
                    Instant updatedAt = toInstant(rs.getTimestamp("updated_at"));
                    String updatedBy = rs.getString("updated_by");

                    List<ProofOfWorkDtos.AttachmentView> attachments = getAttachments(id);
                    List<ProofOfWorkDtos.ChecklistView> checklists = getChecklists(id);

                    return new ProofOfWorkDtos.ProofItemDetail(
                            id, orgId, entityType, projectId, jobId, title, category, techId, techName, techNotes,
                            custConfirmed, custName, custRemarks, custSig, confirmedAt, verified, verifiedById,
                            verifiedByName, verifiedAt, recordedAt, createdAt, createdBy, updatedAt, updatedBy,
                            attachments, checklists);
                },
                itemId, actor.organizationId());
    }

    private List<ProofOfWorkDtos.AttachmentView> getAttachments(UUID proofItemId) {
        return jdbc.query(
                "SELECT id, proof_item_id, attachment_type, file_name, content_type, size_bytes, storage_key, caption, "
                        + "uploaded_by_name, created_at FROM proof_of_work_attachments WHERE proof_item_id = ? ORDER BY created_at ASC",
                (rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String key = rs.getString("storage_key");
                    String downloadUrl = storage.presignedDownloadUrl(key, PRESIGNED_URL_TTL).toString();
                    return new ProofOfWorkDtos.AttachmentView(
                            id,
                            rs.getObject("proof_item_id", UUID.class),
                            ProofAttachmentType.valueOf(rs.getString("attachment_type")),
                            rs.getString("file_name"),
                            rs.getString("content_type"),
                            rs.getLong("size_bytes"),
                            rs.getString("caption"),
                            rs.getString("uploaded_by_name"),
                            toInstant(rs.getTimestamp("created_at")),
                            downloadUrl);
                },
                proofItemId);
    }

    private List<ProofOfWorkDtos.ChecklistView> getChecklists(UUID proofItemId) {
        return jdbc.query(
                "SELECT id, proof_item_id, item_title, completed, completed_at, completed_by_name, display_order, created_at "
                        + "FROM proof_of_work_checklists WHERE proof_item_id = ? ORDER BY display_order ASC",
                (rs, rowNum) -> new ProofOfWorkDtos.ChecklistView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("proof_item_id", UUID.class),
                        rs.getString("item_title"),
                        rs.getBoolean("completed"),
                        toInstant(rs.getTimestamp("completed_at")),
                        rs.getString("completed_by_name"),
                        rs.getInt("display_order"),
                        toInstant(rs.getTimestamp("created_at"))),
                proofItemId);
    }

    // ------------------------------------------------------------------ Mutations

    @Transactional
    public ProofOfWorkDtos.ProofItemDetail createProofItem(Actor actor, ProofOfWorkDtos.CreateProofItemRequest req) {
        if (!actor.canCreate()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Permission PROOF_OF_WORK_CREATE is required");
        }
        Objects.requireNonNull(req.title(), "title is required");
        if (req.title().trim().isEmpty()) {
            throw new FieldValidationException("title", "Title is required");
        }
        if (req.entityType() == ProofEntityType.PROJECT && req.projectId() == null) {
            throw new FieldValidationException("projectId", "Project ID is required for PROJECT proof");
        }
        if (req.entityType() == ProofEntityType.JOB && req.jobId() == null) {
            throw new FieldValidationException("jobId", "Job ID is required for JOB proof");
        }

        UUID id = UUID.randomUUID();
        ProofCategory category = req.category() != null ? req.category() : ProofCategory.GENERAL;
        String techName = resolveUserName(actor.organizationId(), req.technicianId(), actor.email());
        Instant now = clock.instant();

        jdbc.update(
                "INSERT INTO proof_of_work_items (id, organization_id, entity_type, project_id, job_id, title, category, "
                        + "technician_id, technician_name, technician_notes, recorded_at, created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, actor.organizationId(), req.entityType().name(), req.projectId(), req.jobId(), req.title().trim(),
                category.name(), req.technicianId() != null ? req.technicianId() : actor.id(), techName,
                req.technicianNotes(), Timestamp.from(now), Timestamp.from(now), actor.email(), Timestamp.from(now), actor.email());

        // Checklists
        if (req.checklistTitles() != null) {
            int order = 0;
            for (String title : req.checklistTitles()) {
                if (title != null && !title.trim().isEmpty()) {
                    jdbc.update(
                            "INSERT INTO proof_of_work_checklists (id, proof_item_id, item_title, display_order, created_at) "
                                    + "VALUES (?, ?, ?, ?, ?)",
                            UUID.randomUUID(), id, title.trim(), order++, Timestamp.from(now));
                }
            }
        }

        // Audit Log integration
        audit.record(AuditEvent.of(AuditAction.CREATE, "proof-of-work", "Created proof of work item: " + req.title().trim())
                .entity("ProofOfWorkItem", id, req.title().trim())
                .activityOwner(req.entityType() == ProofEntityType.PROJECT ? "Project" : "Job",
                        req.entityType() == ProofEntityType.PROJECT ? req.projectId() : req.jobId())
                .metadata("category", category.name())
                .metadata("technicianName", techName));

        return getItem(actor, id);
    }

    @Transactional
    public ProofOfWorkDtos.ProofItemDetail addAttachment(
            Actor actor,
            UUID proofItemId,
            ProofAttachmentType type,
            String fileName,
            String contentType,
            long sizeBytes,
            String caption,
            InputStream content) {
        if (!actor.canCreate()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Permission PROOF_OF_WORK_CREATE is required");
        }
        ProofOfWorkDtos.ProofItemDetail item = getItem(actor, proofItemId);
        if (sizeBytes <= 0 || sizeBytes > MAX_FILE_BYTES) {
            throw new FieldValidationException("file", "File size must be between 1 byte and 25 MB");
        }

        UUID attachmentId = UUID.randomUUID();
        String safeName = fileName != null && !fileName.isBlank()
                ? fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1)
                : "file";
        String storageKey = "proof-of-work/" + actor.organizationId() + "/" + proofItemId + "/" + attachmentId + "-" + safeName;

        try {
            storage.put(storageKey, content, sizeBytes, contentType != null ? contentType : "application/octet-stream");
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Storage service is currently unavailable", e);
        }

        Instant now = clock.instant();
        String uploaderName = resolveUserName(actor.organizationId(), actor.id(), actor.email());

        jdbc.update(
                "INSERT INTO proof_of_work_attachments (id, proof_item_id, attachment_type, file_name, content_type, "
                        + "size_bytes, storage_key, caption, uploaded_by_name, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                attachmentId, proofItemId, type.name(), safeName, contentType, sizeBytes, storageKey,
                caption != null ? caption.trim() : null, uploaderName, Timestamp.from(now));

        // Update parent updated_at
        jdbc.update("UPDATE proof_of_work_items SET updated_at = ?, updated_by = ? WHERE id = ?",
                Timestamp.from(now), actor.email(), proofItemId);

        // Audit Log integration
        audit.record(AuditEvent.of(AuditAction.UPDATE, "proof-of-work", "Added " + type.name() + " attachment: " + safeName)
                .entity("ProofOfWorkItem", proofItemId, item.title())
                .activityOwner(item.entityType() == ProofEntityType.PROJECT ? "Project" : "Job",
                        item.entityType() == ProofEntityType.PROJECT ? item.projectId() : item.jobId())
                .metadata("attachmentId", attachmentId.toString())
                .metadata("attachmentType", type.name())
                .metadata("fileName", safeName)
                .metadata("sizeBytes", sizeBytes));

        return getItem(actor, proofItemId);
    }

    @Transactional
    public ProofOfWorkDtos.ProofItemDetail toggleChecklistItem(Actor actor, UUID proofItemId, UUID checklistId, boolean completed) {
        if (!actor.canCreate()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Permission PROOF_OF_WORK_CREATE is required");
        }
        ProofOfWorkDtos.ProofItemDetail item = getItem(actor, proofItemId);
        Instant now = clock.instant();
        String name = resolveUserName(actor.organizationId(), actor.id(), actor.email());

        int updated = jdbc.update(
                "UPDATE proof_of_work_checklists SET completed = ?, completed_at = ?, completed_by_name = ? WHERE id = ? AND proof_item_id = ?",
                completed, completed ? Timestamp.from(now) : null, completed ? name : null, checklistId, proofItemId);

        if (updated == 0) {
            throw new ResourceNotFoundException("ProofOfWorkChecklist", checklistId);
        }

        jdbc.update("UPDATE proof_of_work_items SET updated_at = ?, updated_by = ? WHERE id = ?",
                Timestamp.from(now), actor.email(), proofItemId);

        audit.record(AuditEvent.of(AuditAction.UPDATE, "proof-of-work", "Checklist item " + (completed ? "completed" : "reopened"))
                .entity("ProofOfWorkItem", proofItemId, item.title())
                .activityOwner(item.entityType() == ProofEntityType.PROJECT ? "Project" : "Job",
                        item.entityType() == ProofEntityType.PROJECT ? item.projectId() : item.jobId())
                .metadata("checklistId", checklistId.toString())
                .metadata("completed", completed));

        return getItem(actor, proofItemId);
    }

    @Transactional
    public ProofOfWorkDtos.ProofItemDetail recordCustomerConfirmation(
            Actor actor,
            UUID proofItemId,
            ProofOfWorkDtos.CustomerConfirmationRequest req) {
        if (!actor.canCreate()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Permission PROOF_OF_WORK_CREATE is required");
        }
        if (req.customerName() == null || req.customerName().trim().isEmpty()) {
            throw new FieldValidationException("customerName", "Customer name is required for confirmation sign-off");
        }

        ProofOfWorkDtos.ProofItemDetail item = getItem(actor, proofItemId);
        Instant now = clock.instant();

        jdbc.update(
                "UPDATE proof_of_work_items SET customer_confirmed = true, customer_name = ?, customer_remarks = ?, "
                        + "customer_signature = ?, confirmed_at = ?, updated_at = ?, updated_by = ? WHERE id = ?",
                req.customerName().trim(), req.customerRemarks(), req.customerSignature(), Timestamp.from(now),
                Timestamp.from(now), actor.email(), proofItemId);

        audit.record(AuditEvent.of(AuditAction.APPROVAL, "proof-of-work", "Recorded customer confirmation by: " + req.customerName().trim())
                .entity("ProofOfWorkItem", proofItemId, item.title())
                .activityOwner(item.entityType() == ProofEntityType.PROJECT ? "Project" : "Job",
                        item.entityType() == ProofEntityType.PROJECT ? item.projectId() : item.jobId())
                .metadata("customerName", req.customerName().trim())
                .metadata("hasSignature", req.customerSignature() != null));

        return getItem(actor, proofItemId);
    }

    @Transactional
    public ProofOfWorkDtos.ProofItemDetail verifyProofItem(Actor actor, UUID proofItemId, ProofOfWorkDtos.VerificationRequest req) {
        if (!actor.canVerify()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Permission PROOF_OF_WORK_VERIFY is required");
        }
        ProofOfWorkDtos.ProofItemDetail item = getItem(actor, proofItemId);
        Instant now = clock.instant();
        String verifierName = resolveUserName(actor.organizationId(), actor.id(), actor.email());

        jdbc.update(
                "UPDATE proof_of_work_items SET verified = true, verified_by_id = ?, verified_by_name = ?, verified_at = ?, "
                        + "updated_at = ?, updated_by = ? WHERE id = ?",
                actor.id(), verifierName, Timestamp.from(now), Timestamp.from(now), actor.email(), proofItemId);

        audit.record(AuditEvent.of(AuditAction.APPROVAL, "proof-of-work", "Verified proof of work item: " + item.title())
                .entity("ProofOfWorkItem", proofItemId, item.title())
                .activityOwner(item.entityType() == ProofEntityType.PROJECT ? "Project" : "Job",
                        item.entityType() == ProofEntityType.PROJECT ? item.projectId() : item.jobId())
                .metadata("verifiedBy", verifierName)
                .metadata("remarks", req.remarks()));

        return getItem(actor, proofItemId);
    }

    @Transactional
    public void deleteProofItem(Actor actor, UUID proofItemId, String reason) {
        // Prevent unauthorized deletion: requires PROOF_OF_WORK_DELETE (Admin/SuperAdmin)
        if (!actor.canDelete()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Unauthorized deletion: PROOF_OF_WORK_DELETE permission is required to delete proof of work records");
        }
        ProofOfWorkDtos.ProofItemDetail item = getItem(actor, proofItemId);
        if (item.verified()) {
            throw new BusinessRuleException("A verified proof of work record cannot be deleted");
        }

        Instant now = clock.instant();
        jdbc.update(
                "UPDATE proof_of_work_items SET deleted_at = ?, deleted_by = ?, deletion_reason = ? WHERE id = ?",
                Timestamp.from(now), actor.email(), reason != null ? reason.trim() : "Deleted by admin", proofItemId);

        // Audit Log integration
        audit.record(AuditEvent.of(AuditAction.DELETE, "proof-of-work", "Deleted proof of work item: " + item.title())
                .entity("ProofOfWorkItem", proofItemId, item.title())
                .activityOwner(item.entityType() == ProofEntityType.PROJECT ? "Project" : "Job",
                        item.entityType() == ProofEntityType.PROJECT ? item.projectId() : item.jobId())
                .metadata("deletedBy", actor.email())
                .metadata("reason", reason));
    }

    // ------------------------------------------------------------------ Helpers

    private String resolveUserName(UUID orgId, UUID userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        return users.find(orgId, userId).map(UserSummary::fullName).orElse(fallback);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
