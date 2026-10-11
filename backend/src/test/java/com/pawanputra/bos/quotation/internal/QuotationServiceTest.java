package com.pawanputra.bos.quotation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawanputra.bos.catalog.api.ServiceDirectory;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import com.pawanputra.bos.platform.storage.ObjectStorage;
import com.pawanputra.bos.quotation.api.QuotationStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotationServiceTest {

    private QuotationRepository quotations;
    private QuotationVersionRepository versions;
    private QuotationItemRepository items;
    private QuotationAuditLogRepository auditLogs;
    private QuotationPdfRenderer pdfRenderer;
    private ObjectStorage storage;
    private UserDirectory users;
    private ServiceDirectory services;
    private DomainEventPublisher events;
    private Clock clock;
    private QuotationService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-09T12:00:00Z");

    @BeforeEach
    void setUp() {
        quotations = mock(QuotationRepository.class);
        versions = mock(QuotationVersionRepository.class);
        items = mock(QuotationItemRepository.class);
        auditLogs = mock(QuotationAuditLogRepository.class);
        pdfRenderer = mock(QuotationPdfRenderer.class);
        storage = mock(ObjectStorage.class);
        users = mock(UserDirectory.class);
        services = mock(ServiceDirectory.class);
        events = mock(DomainEventPublisher.class);
        clock = Clock.fixed(now, ZoneOffset.UTC);

        service = new QuotationService(
                quotations, versions, items, auditLogs,
                pdfRenderer, storage, users, services, events, clock);
    }

    @Test
    void createQuotationCalculatesSubtotalTaxAndFinalAmount() {
        when(users.find(orgId, userId))
                .thenReturn(Optional.of(new UserDirectory.UserSummary(userId, "Aditya Singh", "aditya@example.com", true)));
        when(quotations.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        QuotationService.ItemInput itm1 = new QuotationService.ItemInput(
                UUID.randomUUID(), "CCTV Camera 4K", "Outdoor dome",
                BigDecimal.valueOf(2), BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.valueOf(18));
        QuotationService.ItemInput itm2 = new QuotationService.ItemInput(
                UUID.randomUUID(), "Installation Service", "Cabling and mount",
                BigDecimal.ONE, BigDecimal.valueOf(2000), BigDecimal.ZERO, BigDecimal.valueOf(18));

        QuotationService.CreateCommand cmd = new QuotationService.CreateCommand(
                "Security Setup", UUID.randomUUID(), "Sharma Retail",
                null, null, null, null, userId,
                BigDecimal.valueOf(10), // 10% discount
                BigDecimal.valueOf(18), // 18% tax
                LocalDate.now(clock).plusDays(15), "Net 30", "Urgent delivery",
                LocalDate.now(clock).plusDays(3), List.of(itm1, itm2));

        QuotationViews.Detail created = service.create(orgId, cmd, userId, "Aditya Singh");

        assertThat(created.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(created.currentVersionNumber()).isEqualTo(1);
        // itm1: 2 * 5000 = 10000 + 18% tax (1800) = 11800
        // itm2: 1 * 2000 = 2000 + 18% tax (360) = 2360
        // sum items total = 14160
        assertThat(created.subtotal()).isEqualByComparingTo(BigDecimal.valueOf(14160));
        // Discount 10%: 1416.00
        assertThat(created.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(1416.00));
        // After discount = 14160 - 1416 = 12744
        // Tax 18% = 2293.92
        assertThat(created.taxAmount()).isEqualByComparingTo(BigDecimal.valueOf(2293.92));
        // Final = 12744 + 2293.92 = 15037.92
        assertThat(created.finalAmount()).isEqualByComparingTo(BigDecimal.valueOf(15037.92));
        verify(quotations).save(any(Quotation.class));
        verify(events).publish(any());
    }

    @Test
    void approvalWorkflowTransitionsCorrectly() {
        Quotation q = new Quotation(orgId, "Q-0001", "Solar System", null, null, null, null, null, null, userId, "Aditya", "Aditya");
        when(quotations.findByIdAndOrganizationId(q.getId(), orgId)).thenReturn(Optional.of(q));

        // Submit for approval
        QuotationViews.Detail submitted = service.requestApproval(orgId, q.getId(), "Ready for review", "Aditya");
        assertThat(submitted.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
        assertThat(q.isApprovalRequired()).isTrue();

        // Approve
        QuotationViews.Detail approved = service.approve(orgId, q.getId(), "Looks good", "Director");
        assertThat(approved.status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(q.getApprovedBy()).isEqualTo("Director");

        // Send to client
        QuotationViews.Detail sent = service.markSent(orgId, q.getId(), LocalDate.now(clock).plusDays(5), "Sent via email", "Aditya");
        assertThat(sent.status()).isEqualTo(QuotationStatus.SENT);
        assertThat(q.getNextFollowUpDate()).isEqualTo(LocalDate.now(clock).plusDays(5));

        // Client accepts
        QuotationViews.Detail accepted = service.markAccepted(orgId, q.getId(), "Client accepted terms", "Aditya");
        assertThat(accepted.status()).isEqualTo(QuotationStatus.ACCEPTED);
    }

    @Test
    void createNewVersionNeverOverwritesPreviousVersion() {
        Quotation q = new Quotation(orgId, "Q-0002", "Office Cabling", null, null, null, null, null, null, userId, "Aditya", "Aditya");
        QuotationVersion v1 = new QuotationVersion(q, 1, BigDecimal.ZERO, BigDecimal.valueOf(18), "Terms v1", null, null, "Initial", "Aditya");
        q.addVersion(v1);
        q.setStatus(QuotationStatus.REJECTED);

        when(quotations.findByIdAndOrganizationId(q.getId(), orgId)).thenReturn(Optional.of(q));
        when(versions.findByQuotationIdOrderByVersionNumberDesc(q.getId())).thenAnswer(inv -> q.getVersions());

        QuotationService.CreateVersionCommand cmd = new QuotationService.CreateVersionCommand(
                BigDecimal.valueOf(5), BigDecimal.valueOf(18), LocalDate.now(clock).plusDays(20),
                "Terms v2", "Updated discount", "Added 5% discount after client feedback",
                LocalDate.now(clock).plusDays(2),
                List.of(new QuotationService.ItemInput(null, "Cable roll", "CAT6", BigDecimal.TEN, BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.valueOf(18))));

        QuotationViews.Detail updated = service.createNewVersion(orgId, q.getId(), cmd, "Aditya");

        assertThat(updated.currentVersionNumber()).isEqualTo(2);
        assertThat(updated.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(q.getVersions()).hasSize(2);
        assertThat(q.getVersions().get(0).getVersionNumber()).isEqualTo(1); // v1 intact
        assertThat(q.getVersions().get(1).getVersionNumber()).isEqualTo(2); // v2 added
        verify(events).publish(any());
    }

    @Test
    void generatePdfProducesDocumentAndPresignedUrl() {
        Quotation q = new Quotation(orgId, "Q-0003", "Fire Alarm", null, null, null, null, null, null, userId, "Aditya", "Aditya");
        QuotationVersion v1 = new QuotationVersion(q, 1, BigDecimal.ZERO, BigDecimal.valueOf(18), "Terms", null, null, "Initial", "Aditya");
        q.addVersion(v1);

        when(quotations.findByIdAndOrganizationId(q.getId(), orgId)).thenReturn(Optional.of(q));
        when(versions.findByQuotationIdAndVersionNumber(q.getId(), 1)).thenReturn(Optional.of(v1));
        when(pdfRenderer.render(any(), any(), anyString(), anyString())).thenReturn(new byte[] { 1, 2, 3 });
        when(storage.presignedDownloadUrl(anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://storage.example.com/quotations/Q-0003-v1.pdf?token=xyz"));

        URI url = service.generateAndDownloadPdf(orgId, q.getId(), 1, "Aditya");

        assertThat(url.toString()).contains("storage.example.com");
        verify(storage).put(anyString(), any(), anyLong(), anyString());
        verify(versions).save(v1);
    }
}
