package com.pawanputra.bos.fieldops.internal;

import com.pawanputra.bos.fieldops.api.ProofAttachmentType;
import com.pawanputra.bos.fieldops.api.ProofCategory;
import com.pawanputra.bos.fieldops.api.ProofEntityType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProofOfWorkDtos {

    private ProofOfWorkDtos() {
    }

    public record AttachmentView(
            UUID id,
            UUID proofItemId,
            ProofAttachmentType attachmentType,
            String fileName,
            String contentType,
            long sizeBytes,
            String caption,
            String uploadedByName,
            Instant createdAt,
            String downloadUrl) {
    }

    public record ChecklistView(
            UUID id,
            UUID proofItemId,
            String itemTitle,
            boolean completed,
            Instant completedAt,
            String completedByName,
            int displayOrder,
            Instant createdAt) {
    }

    public record ProofItemSummary(
            UUID id,
            UUID organizationId,
            ProofEntityType entityType,
            UUID projectId,
            UUID jobId,
            String title,
            ProofCategory category,
            UUID technicianId,
            String technicianName,
            boolean customerConfirmed,
            String customerName,
            Instant confirmedAt,
            boolean verified,
            String verifiedByName,
            Instant verifiedAt,
            Instant recordedAt,
            int attachmentCount,
            int checklistCount,
            int completedChecklistCount,
            Instant createdAt) {
    }

    public record ProofItemDetail(
            UUID id,
            UUID organizationId,
            ProofEntityType entityType,
            UUID projectId,
            UUID jobId,
            String title,
            ProofCategory category,
            UUID technicianId,
            String technicianName,
            String technicianNotes,
            boolean customerConfirmed,
            String customerName,
            String customerRemarks,
            String customerSignature,
            Instant confirmedAt,
            boolean verified,
            UUID verifiedById,
            String verifiedByName,
            Instant verifiedAt,
            Instant recordedAt,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            List<AttachmentView> attachments,
            List<ChecklistView> checklists) {
    }

    public record CreateProofItemRequest(
            ProofEntityType entityType,
            UUID projectId,
            UUID jobId,
            String title,
            ProofCategory category,
            UUID technicianId,
            String technicianNotes,
            List<String> checklistTitles) {
    }

    public record CustomerConfirmationRequest(
            String customerName,
            String customerRemarks,
            String customerSignature) {
    }

    public record VerificationRequest(
            String remarks) {
    }

    public record ChecklistToggleRequest(
            boolean completed) {
    }

    public record DeleteProofRequest(
            String reason) {
    }

    public record TimelineView(
            UUID entityId,
            ProofEntityType entityType,
            long totalItems,
            long verifiedItems,
            long customerConfirmedItems,
            long photoCount,
            long documentCount,
            List<ProofItemDetail> items) {
    }
}
