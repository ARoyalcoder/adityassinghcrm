package com.pawanputra.bos.fieldops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawanputra.bos.audit.api.AuditAction;
import com.pawanputra.bos.audit.api.AuditEvent;
import com.pawanputra.bos.audit.api.AuditRecorder;
import com.pawanputra.bos.fieldops.api.ProofAttachmentType;
import com.pawanputra.bos.fieldops.api.ProofCategory;
import com.pawanputra.bos.fieldops.api.ProofEntityType;
import com.pawanputra.bos.fieldops.internal.ProofOfWorkDtos;
import com.pawanputra.bos.fieldops.internal.ProofOfWorkService;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

class ProofOfWorkServiceTest {

    private JdbcTemplate jdbc;
    private ObjectStorage storage;
    private AuditRecorder audit;
    private UserDirectory users;
    private ProofOfWorkService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    private final ProofOfWorkService.Actor technician = new ProofOfWorkService.Actor(
            userId, orgId, "tech@example.com", true, true, false, false);
    private final ProofOfWorkService.Actor verifier = new ProofOfWorkService.Actor(
            UUID.randomUUID(), orgId, "pm@example.com", true, false, true, false);
    private final ProofOfWorkService.Actor admin = new ProofOfWorkService.Actor(
            UUID.randomUUID(), orgId, "admin@example.com", true, true, true, true);
    private final ProofOfWorkService.Actor readOnlyUser = new ProofOfWorkService.Actor(
            UUID.randomUUID(), orgId, "viewer@example.com", true, false, false, false);

    @BeforeEach
    void setUp() {
        DataSource ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("pow_test_" + UUID.randomUUID())
                .addScript("classpath:pow-schema.sql")
                .build();
        jdbc = new JdbcTemplate(ds);

        storage = mock(ObjectStorage.class);
        when(storage.presignedDownloadUrl(anyString(), any(Duration.class)))
                .thenAnswer(inv -> URI.create("http://storage.test/" + inv.getArgument(0)));

        audit = mock(AuditRecorder.class);
        users = mock(UserDirectory.class);
        when(users.find(eq(orgId), any(UUID.class)))
                .thenReturn(Optional.of(new UserDirectory.UserSummary(userId, "John Tech", "tech@example.com", true)));

        service = new ProofOfWorkService(jdbc, storage, audit, users, clock);
    }

    @Test
    @DisplayName("Technician can create a Proof of Work record with checklists and audit event is recorded")
    void createProofItemWithAudit() {
        ProofOfWorkDtos.CreateProofItemRequest req = new ProofOfWorkDtos.CreateProofItemRequest(
                ProofEntityType.PROJECT,
                projectId,
                null,
                "Inverter Installation Inspection",
                ProofCategory.BEFORE_PHOTO,
                userId,
                "Site foundation is solid and clear of debris.",
                List.of("Check earthing resistance", "Inspect breaker panel", "Confirm cable routing"));

        ProofOfWorkDtos.ProofItemDetail created = service.createProofItem(technician, req);

        assertThat(created.title()).isEqualTo("Inverter Installation Inspection");
        assertThat(created.category()).isEqualTo(ProofCategory.BEFORE_PHOTO);
        assertThat(created.checklists()).hasSize(3);
        assertThat(created.checklists().get(0).itemTitle()).isEqualTo("Check earthing resistance");
        assertThat(created.checklists().get(0).completed()).isFalse();

        // Verify audit log call
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.CREATE);
        assertThat(event.module()).isEqualTo("proof-of-work");
        assertThat(event.entityType()).isEqualTo("ProofOfWorkItem");
    }

    @Test
    @DisplayName("Upload before/after photos and documents via ObjectStorage abstraction")
    void uploadAttachmentsThroughStorageAbstraction() {
        ProofOfWorkDtos.ProofItemDetail item = service.createProofItem(technician,
                new ProofOfWorkDtos.CreateProofItemRequest(
                        ProofEntityType.JOB, null, jobId, "Solar Panel Mount",
                        ProofCategory.DURING_PHOTO, userId, "Panel alignment", List.of()));

        byte[] photoBytes = new byte[]{1, 2, 3, 4};
        ByteArrayInputStream is = new ByteArrayInputStream(photoBytes);

        ProofOfWorkDtos.ProofItemDetail updated = service.addAttachment(
                technician,
                item.id(),
                ProofAttachmentType.BEFORE_PHOTO,
                "site-before.jpg",
                "image/jpeg",
                photoBytes.length,
                "Roof condition before work began",
                is);

        assertThat(updated.attachments()).hasSize(1);
        ProofOfWorkDtos.AttachmentView att = updated.attachments().get(0);
        assertThat(att.attachmentType()).isEqualTo(ProofAttachmentType.BEFORE_PHOTO);
        assertThat(att.fileName()).isEqualTo("site-before.jpg");
        assertThat(att.downloadUrl()).contains("proof-of-work/");

        // Storage was invoked through ObjectStorage abstraction (never direct local disk coupling)
        verify(storage).put(anyString(), any(), eq((long) photoBytes.length), eq("image/jpeg"));
    }

    @Test
    @DisplayName("Checklist completion updates with timestamp and user sign-off")
    void checklistCompletion() {
        ProofOfWorkDtos.ProofItemDetail item = service.createProofItem(technician,
                new ProofOfWorkDtos.CreateProofItemRequest(
                        ProofEntityType.PROJECT, projectId, null, "Electrical Safety Inspection",
                        ProofCategory.CHECKLIST, userId, "Notes", List.of("Verify isolator switch")));

        UUID checklistId = item.checklists().get(0).id();
        ProofOfWorkDtos.ProofItemDetail updated = service.toggleChecklistItem(technician, item.id(), checklistId, true);

        assertThat(updated.checklists().get(0).completed()).isTrue();
        assertThat(updated.checklists().get(0).completedAt()).isNotNull();
        assertThat(updated.checklists().get(0).completedByName()).isEqualTo("John Tech");
    }

    @Test
    @DisplayName("Customer confirmation records signature and remarks")
    void customerConfirmation() {
        ProofOfWorkDtos.ProofItemDetail item = service.createProofItem(technician,
                new ProofOfWorkDtos.CreateProofItemRequest(
                        ProofEntityType.PROJECT, projectId, null, "Final Handover",
                        ProofCategory.CUSTOMER_CONFIRMATION, userId, "Testing done", List.of()));

        ProofOfWorkDtos.ProofItemDetail confirmed = service.recordCustomerConfirmation(
                technician,
                item.id(),
                new ProofOfWorkDtos.CustomerConfirmationRequest(
                        "Aditya Verma", "Everything is working smoothly, great job!", "data:image/png;base64,sample-signature"));

        assertThat(confirmed.customerConfirmed()).isTrue();
        assertThat(confirmed.customerName()).isEqualTo("Aditya Verma");
        assertThat(confirmed.customerRemarks()).isEqualTo("Everything is working smoothly, great job!");
        assertThat(confirmed.customerSignature()).isEqualTo("data:image/png;base64,sample-signature");
        assertThat(confirmed.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("PM verification locks the proof item")
    void verificationSignOff() {
        ProofOfWorkDtos.ProofItemDetail item = service.createProofItem(technician,
                new ProofOfWorkDtos.CreateProofItemRequest(
                        ProofEntityType.PROJECT, projectId, null, "Roof Mounting Structural Check",
                        ProofCategory.GENERAL, userId, "Checked anchors", List.of()));

        ProofOfWorkDtos.ProofItemDetail verifiedItem = service.verifyProofItem(
                verifier,
                item.id(),
                new ProofOfWorkDtos.VerificationRequest("Passed structural standards review"));

        assertThat(verifiedItem.verified()).isTrue();
        assertThat(verifiedItem.verifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Unauthorized deletion is prevented; verified proof cannot be deleted")
    void deletionProtection() {
        ProofOfWorkDtos.ProofItemDetail item = service.createProofItem(technician,
                new ProofOfWorkDtos.CreateProofItemRequest(
                        ProofEntityType.PROJECT, projectId, null, "Wiring Check",
                        ProofCategory.GENERAL, userId, "Wires checked", List.of()));

        // Non-admin cannot delete
        assertThatThrownBy(() -> service.deleteProofItem(technician, item.id(), "Mistake"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PROOF_OF_WORK_DELETE");

        // Verifying the item prevents even admin from deleting it
        service.verifyProofItem(verifier, item.id(), new ProofOfWorkDtos.VerificationRequest("Approved"));
        assertThatThrownBy(() -> service.deleteProofItem(admin, item.id(), "Try delete"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("verified proof of work record cannot be deleted");
    }

    @Test
    @DisplayName("Timeline aggregation yields complete categorized proof timeline")
    void projectProofTimeline() {
        service.createProofItem(technician, new ProofOfWorkDtos.CreateProofItemRequest(
                ProofEntityType.PROJECT, projectId, null, "Initial Site Survey",
                ProofCategory.BEFORE_PHOTO, userId, "Survey notes", List.of()));
        service.createProofItem(technician, new ProofOfWorkDtos.CreateProofItemRequest(
                ProofEntityType.PROJECT, projectId, null, "Structure Assembled",
                ProofCategory.DURING_PHOTO, userId, "Mounting complete", List.of()));

        ProofOfWorkDtos.TimelineView timeline = service.getProjectTimeline(technician, projectId);

        assertThat(timeline.totalItems()).isEqualTo(2);
        assertThat(timeline.entityId()).isEqualTo(projectId);
        assertThat(timeline.items()).hasSize(2);
    }
}
