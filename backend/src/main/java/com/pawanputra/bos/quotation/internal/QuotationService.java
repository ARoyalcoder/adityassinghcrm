package com.pawanputra.bos.quotation.internal;

import com.pawanputra.bos.catalog.api.ServiceDirectory;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import com.pawanputra.bos.platform.storage.ObjectStorage;
import com.pawanputra.bos.quotation.api.QuotationDirectory;
import com.pawanputra.bos.quotation.api.QuotationEvents;
import com.pawanputra.bos.quotation.api.QuotationStatus;
import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QuotationService implements QuotationDirectory {

    private final QuotationRepository quotations;
    private final QuotationVersionRepository versions;
    private final QuotationItemRepository items;
    private final QuotationAuditLogRepository auditLogs;
    private final QuotationPdfRenderer pdfRenderer;
    private final ObjectStorage storage;
    private final UserDirectory users;
    private final ServiceDirectory services;
    private final DomainEventPublisher events;
    private final Clock clock;

    public QuotationService(
            QuotationRepository quotations,
            QuotationVersionRepository versions,
            QuotationItemRepository items,
            QuotationAuditLogRepository auditLogs,
            QuotationPdfRenderer pdfRenderer,
            ObjectStorage storage,
            UserDirectory users,
            ServiceDirectory services,
            DomainEventPublisher events,
            Clock clock) {
        this.quotations = quotations;
        this.versions = versions;
        this.items = items;
        this.auditLogs = auditLogs;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        this.users = users;
        this.services = services;
        this.events = events;
        this.clock = clock;
    }

    // Command records
    public record ItemInput(
            UUID serviceId,
            String serviceName,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercentage,
            BigDecimal taxPercentage) {
    }

    public record CreateCommand(
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID opportunityId,
            String opportunityTitle,
            UUID ownerId,
            BigDecimal discountPercentage,
            BigDecimal taxPercentage,
            LocalDate validUntil,
            String terms,
            String notes,
            LocalDate nextFollowUpDate,
            List<ItemInput> items) {
    }

    public record UpdateDraftCommand(
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID opportunityId,
            String opportunityTitle,
            UUID ownerId,
            BigDecimal discountPercentage,
            BigDecimal taxPercentage,
            LocalDate validUntil,
            String terms,
            String notes,
            LocalDate nextFollowUpDate,
            List<ItemInput> items) {
    }

    public record CreateVersionCommand(
            BigDecimal discountPercentage,
            BigDecimal taxPercentage,
            LocalDate validUntil,
            String terms,
            String notes,
            String changeSummary,
            LocalDate nextFollowUpDate,
            List<ItemInput> items) {
    }

    public record QuotationFilter(
            QuotationStatus status,
            UUID customerId,
            UUID leadId,
            UUID opportunityId,
            UUID ownerId,
            LocalDate followUpDate,
            String search) {
    }

    public QuotationViews.Detail create(UUID orgId, CreateCommand cmd, UUID actorId, String actorName) {
        String ownerName = actorName;
        if (cmd.ownerId() != null) {
            ownerName = users.find(orgId, cmd.ownerId())
                    .map(UserDirectory.UserSummary::fullName)
                    .orElse(actorName);
        }

        // Generate unique quotation number
        String qNum = generateQuotationNumber(orgId);

        Quotation q = new Quotation(
                orgId, qNum, cmd.title(),
                cmd.customerId(), cmd.customerName(),
                cmd.leadId(), cmd.leadName(),
                cmd.opportunityId(), cmd.opportunityTitle(),
                cmd.ownerId() != null ? cmd.ownerId() : actorId, ownerName,
                actorName);

        q.setNextFollowUpDate(cmd.nextFollowUpDate());

        QuotationVersion v1 = new QuotationVersion(
                q, 1, cmd.discountPercentage(), cmd.taxPercentage(),
                cmd.terms(), cmd.validUntil(), cmd.notes(),
                "Initial version", actorName);

        if (cmd.items() != null && !cmd.items().isEmpty()) {
            int order = 1;
            for (ItemInput itm : cmd.items()) {
                String sName = itm.serviceName();
                if (itm.serviceId() != null) {
                    sName = services.find(itm.serviceId())
                            .map(ServiceDirectory.ServiceSummary::name)
                            .orElse(sName);
                }
                QuotationItem item = new QuotationItem(
                        v1, order++, itm.serviceId(), sName, itm.description(),
                        itm.quantity(), itm.unitPrice(), itm.discountPercentage(),
                        itm.taxPercentage(), actorName);
                v1.addItem(item);
            }
        }

        v1.recalculateTotals();
        q.addVersion(v1);

        q.logAudit("CREATED", null, QuotationStatus.DRAFT, 1, "Quotation created as draft", actorName);

        Quotation saved = quotations.save(q);

        events.publish(QuotationEvents.QuotationCreatedEvent.of(
                saved.getId(), orgId, saved.getQuotationNumber(), saved.getTitle(),
                saved.getFinalAmount(), saved.getCustomerId(), saved.getLeadId(),
                saved.getOpportunityId(), saved.getOwnerId()));

        return toDetail(saved);
    }

    public QuotationViews.Detail updateDraft(UUID orgId, UUID quotationId, UpdateDraftCommand cmd, String actorName) {
        Quotation q = getEntity(orgId, quotationId);

        if (q.getStatus() != QuotationStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT quotations can be directly edited. Create a new version instead.");
        }

        if (cmd.title() != null) q.setTitle(cmd.title());
        if (cmd.customerId() != null) {
            q.setCustomerId(cmd.customerId());
            q.setCustomerName(cmd.customerName());
        }
        if (cmd.leadId() != null) {
            q.setLeadId(cmd.leadId());
            q.setLeadName(cmd.leadName());
        }
        if (cmd.opportunityId() != null) {
            q.setOpportunityId(cmd.opportunityId());
            q.setOpportunityTitle(cmd.opportunityTitle());
        }
        if (cmd.ownerId() != null) {
            q.setOwnerId(cmd.ownerId());
            users.find(orgId, cmd.ownerId()).ifPresent(u -> q.setOwnerName(u.fullName()));
        }
        if (cmd.nextFollowUpDate() != null) {
            q.setNextFollowUpDate(cmd.nextFollowUpDate());
        }

        QuotationVersion cur = getCurrentVersion(q);
        if (cmd.discountPercentage() != null) cur.setDiscountPercentage(cmd.discountPercentage());
        if (cmd.taxPercentage() != null) cur.setTaxPercentage(cmd.taxPercentage());
        if (cmd.validUntil() != null) cur.setValidUntil(cmd.validUntil());
        if (cmd.terms() != null) cur.setTerms(cmd.terms());
        if (cmd.notes() != null) cur.setNotes(cmd.notes());

        if (cmd.items() != null) {
            cur.getItems().clear();
            int order = 1;
            for (ItemInput itm : cmd.items()) {
                String sName = itm.serviceName();
                if (itm.serviceId() != null) {
                    sName = services.find(itm.serviceId())
                            .map(ServiceDirectory.ServiceSummary::name)
                            .orElse(sName);
                }
                QuotationItem item = new QuotationItem(
                        cur, order++, itm.serviceId(), sName, itm.description(),
                        itm.quantity(), itm.unitPrice(), itm.discountPercentage(),
                        itm.taxPercentage(), actorName);
                cur.addItem(item);
            }
        }

        cur.recalculateTotals();
        q.setSubtotal(cur.getSubtotal());
        q.setDiscountAmount(cur.getDiscountAmount());
        q.setTaxAmount(cur.getTaxAmount());
        q.setFinalAmount(cur.getFinalAmount());
        q.setValidUntil(cur.getValidUntil());
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("UPDATED", QuotationStatus.DRAFT, QuotationStatus.DRAFT, cur.getVersionNumber(), "Draft updated", actorName);

        return toDetail(q);
    }

    public QuotationViews.Detail createNewVersion(UUID orgId, UUID quotationId, CreateVersionCommand cmd, String actorName) {
        Quotation q = getEntity(orgId, quotationId);

        int nextVerNumber = q.getCurrentVersionNumber() + 1;

        QuotationVersion nextVer = new QuotationVersion(
                q, nextVerNumber, cmd.discountPercentage(), cmd.taxPercentage(),
                cmd.terms(), cmd.validUntil(), cmd.notes(),
                cmd.changeSummary() != null ? cmd.changeSummary() : "Version " + nextVerNumber,
                actorName);

        if (cmd.items() != null && !cmd.items().isEmpty()) {
            int order = 1;
            for (ItemInput itm : cmd.items()) {
                String sName = itm.serviceName();
                if (itm.serviceId() != null) {
                    sName = services.find(itm.serviceId())
                            .map(ServiceDirectory.ServiceSummary::name)
                            .orElse(sName);
                }
                QuotationItem item = new QuotationItem(
                        nextVer, order++, itm.serviceId(), sName, itm.description(),
                        itm.quantity(), itm.unitPrice(), itm.discountPercentage(),
                        itm.taxPercentage(), actorName);
                nextVer.addItem(item);
            }
        }

        nextVer.recalculateTotals();
        q.addVersion(nextVer);

        // When a new version is created on a sent/rejected quotation, reset status to DRAFT
        QuotationStatus prevStatus = q.getStatus();
        q.setStatus(QuotationStatus.DRAFT);
        q.setApprovalRequired(false);
        q.setApprovedAt(null);
        q.setApprovedBy(null);
        q.setSentAt(null);
        q.setSentBy(null);

        if (cmd.nextFollowUpDate() != null) {
            q.setNextFollowUpDate(cmd.nextFollowUpDate());
        }

        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("VERSION_CREATED", prevStatus, QuotationStatus.DRAFT, nextVerNumber,
                "Created new Version " + nextVerNumber + ": " + (cmd.changeSummary() != null ? cmd.changeSummary() : ""),
                actorName);

        events.publish(QuotationEvents.QuotationVersionCreatedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), nextVerNumber,
                nextVer.getFinalAmount(), cmd.changeSummary(), actorName));

        return toDetail(q);
    }

    public QuotationViews.Detail requestApproval(UUID orgId, UUID quotationId, String notes, String actorName) {
        Quotation q = getEntity(orgId, quotationId);
        if (q.getStatus() != QuotationStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT quotations can be submitted for approval");
        }

        QuotationStatus prev = q.getStatus();
        q.setStatus(QuotationStatus.PENDING_APPROVAL);
        q.setApprovalRequired(true);
        q.setApprovalRequestedAt(Instant.now(clock));
        q.setApprovalRequestedBy(actorName);
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("APPROVAL_REQUESTED", prev, QuotationStatus.PENDING_APPROVAL, q.getCurrentVersionNumber(), notes, actorName);

        events.publish(QuotationEvents.QuotationStatusChangedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), prev, QuotationStatus.PENDING_APPROVAL, q.getCurrentVersionNumber(), actorName));

        return toDetail(q);
    }

    public QuotationViews.Detail approve(UUID orgId, UUID quotationId, String notes, String actorName) {
        Quotation q = getEntity(orgId, quotationId);
        if (q.getStatus() != QuotationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Quotation is not pending approval");
        }

        QuotationStatus prev = q.getStatus();
        q.setStatus(QuotationStatus.APPROVED);
        q.setApprovedAt(Instant.now(clock));
        q.setApprovedBy(actorName);
        q.setRejectedAt(null);
        q.setRejectedBy(null);
        q.setRejectionReason(null);
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("APPROVED", prev, QuotationStatus.APPROVED, q.getCurrentVersionNumber(), notes, actorName);

        events.publish(QuotationEvents.QuotationStatusChangedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), prev, QuotationStatus.APPROVED, q.getCurrentVersionNumber(), actorName));

        return toDetail(q);
    }

    public QuotationViews.Detail rejectApproval(UUID orgId, UUID quotationId, String reason, String actorName) {
        Quotation q = getEntity(orgId, quotationId);
        if (q.getStatus() != QuotationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Quotation is not pending approval");
        }

        QuotationStatus prev = q.getStatus();
        q.setStatus(QuotationStatus.DRAFT); // returns to draft for editing
        q.setRejectedAt(Instant.now(clock));
        q.setRejectedBy(actorName);
        q.setRejectionReason(reason);
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("APPROVAL_REJECTED", prev, QuotationStatus.DRAFT, q.getCurrentVersionNumber(),
                "Approval rejected: " + reason, actorName);

        events.publish(QuotationEvents.QuotationStatusChangedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), prev, QuotationStatus.DRAFT, q.getCurrentVersionNumber(), actorName));

        return toDetail(q);
    }

    public QuotationViews.Detail markSent(UUID orgId, UUID quotationId, LocalDate nextFollowUpDate, String notes, String actorName) {
        Quotation q = getEntity(orgId, quotationId);
        if (q.getStatus() != QuotationStatus.DRAFT && q.getStatus() != QuotationStatus.APPROVED) {
            throw new IllegalStateException("Only DRAFT or APPROVED quotations can be sent to client");
        }

        QuotationStatus prev = q.getStatus();
        q.setStatus(QuotationStatus.SENT);
        q.setSentAt(Instant.now(clock));
        q.setSentBy(actorName);
        if (nextFollowUpDate != null) {
            q.setNextFollowUpDate(nextFollowUpDate);
        }
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("SENT", prev, QuotationStatus.SENT, q.getCurrentVersionNumber(), notes, actorName);

        events.publish(QuotationEvents.QuotationStatusChangedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), prev, QuotationStatus.SENT, q.getCurrentVersionNumber(), actorName));

        return toDetail(q);
    }

    public QuotationViews.Detail markViewed(UUID orgId, UUID quotationId, String actorName) {
        Quotation q = getEntity(orgId, quotationId);
        if (q.getStatus() != QuotationStatus.SENT) {
            return toDetail(q);
        }

        QuotationStatus prev = q.getStatus();
        q.setStatus(QuotationStatus.VIEWED);
        q.setViewedAt(Instant.now(clock));
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("VIEWED", prev, QuotationStatus.VIEWED, q.getCurrentVersionNumber(), "Quotation viewed by client", actorName);

        events.publish(QuotationEvents.QuotationStatusChangedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), prev, QuotationStatus.VIEWED, q.getCurrentVersionNumber(), actorName));

        return toDetail(q);
    }

    public QuotationViews.Detail markAccepted(UUID orgId, UUID quotationId, String notes, String actorName) {
        Quotation q = getEntity(orgId, quotationId);
        if (q.getStatus() != QuotationStatus.SENT && q.getStatus() != QuotationStatus.VIEWED && q.getStatus() != QuotationStatus.APPROVED) {
            throw new IllegalStateException("Quotation is not in an active sent state to be accepted");
        }

        QuotationStatus prev = q.getStatus();
        q.setStatus(QuotationStatus.ACCEPTED);
        q.setAcceptedAt(Instant.now(clock));
        q.setAcceptedBy(actorName);
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("ACCEPTED", prev, QuotationStatus.ACCEPTED, q.getCurrentVersionNumber(), notes, actorName);

        events.publish(QuotationEvents.QuotationStatusChangedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), prev, QuotationStatus.ACCEPTED, q.getCurrentVersionNumber(), actorName));

        return toDetail(q);
    }

    public QuotationViews.Detail markRejected(UUID orgId, UUID quotationId, String reason, String actorName) {
        Quotation q = getEntity(orgId, quotationId);

        QuotationStatus prev = q.getStatus();
        q.setStatus(QuotationStatus.REJECTED);
        q.setClientRejectionReason(reason);
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("REJECTED_BY_CLIENT", prev, QuotationStatus.REJECTED, q.getCurrentVersionNumber(), reason, actorName);

        events.publish(QuotationEvents.QuotationStatusChangedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), prev, QuotationStatus.REJECTED, q.getCurrentVersionNumber(), actorName));

        return toDetail(q);
    }

    public QuotationViews.Detail cancel(UUID orgId, UUID quotationId, String reason, String actorName) {
        Quotation q = getEntity(orgId, quotationId);

        QuotationStatus prev = q.getStatus();
        q.setStatus(QuotationStatus.CANCELLED);
        q.setCancelledAt(Instant.now(clock));
        q.setCancellationReason(reason);
        q.setUpdatedAt(Instant.now(clock));
        q.setUpdatedBy(actorName);

        q.logAudit("CANCELLED", prev, QuotationStatus.CANCELLED, q.getCurrentVersionNumber(), reason, actorName);

        events.publish(QuotationEvents.QuotationStatusChangedEvent.of(
                q.getId(), orgId, q.getQuotationNumber(), prev, QuotationStatus.CANCELLED, q.getCurrentVersionNumber(), actorName));

        return toDetail(q);
    }

    public URI generateAndDownloadPdf(UUID orgId, UUID quotationId, Integer versionNumber, String actorName) {
        Quotation q = getEntity(orgId, quotationId);
        int vNum = versionNumber != null ? versionNumber : q.getCurrentVersionNumber();

        QuotationVersion v = versions.findByQuotationIdAndVersionNumber(q.getId(), vNum)
                .orElseThrow(() -> new ResourceNotFoundException("QuotationVersion", vNum));

        String storageKey = "quotations/" + orgId + "/" + q.getQuotationNumber() + "-v" + vNum + ".pdf";

        byte[] pdfBytes = pdfRenderer.render(q, v, "Pawan Putra Business OS", "Customer Quotation Document");

        storage.put(storageKey, new ByteArrayInputStream(pdfBytes), pdfBytes.length, "application/pdf");

        v.setPdfStorageKey(storageKey);
        v.setPdfGeneratedAt(Instant.now(clock));
        versions.save(v);

        q.logAudit("PDF_GENERATED", q.getStatus(), q.getStatus(), vNum, "PDF generated for Version " + vNum, actorName);

        return storage.presignedDownloadUrl(storageKey, Duration.ofHours(2));
    }

    @Transactional(readOnly = true)
    public Page<QuotationViews.Summary> list(UUID orgId, QuotationFilter filter, Pageable pageable) {
        Specification<Quotation> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("organizationId"), orgId));
            preds.add(cb.isNull(root.get("deletedAt")));

            if (filter.status() != null) {
                preds.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.customerId() != null) {
                preds.add(cb.equal(root.get("customerId"), filter.customerId()));
            }
            if (filter.leadId() != null) {
                preds.add(cb.equal(root.get("leadId"), filter.leadId()));
            }
            if (filter.opportunityId() != null) {
                preds.add(cb.equal(root.get("opportunityId"), filter.opportunityId()));
            }
            if (filter.ownerId() != null) {
                preds.add(cb.equal(root.get("ownerId"), filter.ownerId()));
            }
            if (filter.followUpDate() != null) {
                preds.add(cb.equal(root.get("nextFollowUpDate"), filter.followUpDate()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().toLowerCase().trim() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("quotationNumber")), pattern),
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(cb.lower(root.get("leadName")), pattern)
                ));
            }

            return cb.and(preds.toArray(new Predicate[0]));
        };

        return quotations.findAll(spec, pageable).map(QuotationViews.Summary::from);
    }

    @Transactional(readOnly = true)
    public QuotationViews.Detail get(UUID orgId, UUID quotationId) {
        Quotation q = getEntity(orgId, quotationId);
        return toDetail(q);
    }

    @Transactional(readOnly = true)
    public QuotationViews.VersionDetail getVersion(UUID orgId, UUID quotationId, int versionNumber) {
        Quotation q = getEntity(orgId, quotationId);
        QuotationVersion v = versions.findByQuotationIdAndVersionNumber(q.getId(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("QuotationVersion", versionNumber));
        return QuotationViews.VersionDetail.from(v);
    }

    @Transactional(readOnly = true)
    public QuotationViews.Dashboard getDashboard(UUID orgId) {
        long total = quotations.countByOrganizationId(orgId);
        BigDecimal totalValue = quotations.sumFinalAmountByOrganizationId(orgId);
        long draft = quotations.countByOrganizationIdAndStatus(orgId, QuotationStatus.DRAFT);
        long pending = quotations.countByOrganizationIdAndStatus(orgId, QuotationStatus.PENDING_APPROVAL);
        long sent = quotations.countByOrganizationIdAndStatus(orgId, QuotationStatus.SENT);
        long accepted = quotations.countByOrganizationIdAndStatus(orgId, QuotationStatus.ACCEPTED);
        long rejected = quotations.countByOrganizationIdAndStatus(orgId, QuotationStatus.REJECTED);
        long expired = quotations.countByOrganizationIdAndStatus(orgId, QuotationStatus.EXPIRED);

        LocalDate today = LocalDate.now(clock);
        long followUpsTodayCount = quotations.countByOrganizationIdAndNextFollowUpDate(orgId, today);
        List<QuotationViews.Summary> followUpsToday = quotations.findByOrganizationIdAndNextFollowUpDate(orgId, today)
                .stream().map(QuotationViews.Summary::from).toList();

        return new QuotationViews.Dashboard(
                total,
                totalValue != null ? totalValue : BigDecimal.ZERO,
                draft,
                pending,
                sent,
                accepted,
                rejected,
                expired,
                followUpsTodayCount,
                followUpsToday);
    }

    public void delete(UUID orgId, UUID quotationId) {
        Quotation q = getEntity(orgId, quotationId);
        q.setDeletedAt(Instant.now(clock));
        quotations.save(q);
    }

    private Quotation getEntity(UUID orgId, UUID id) {
        return quotations.findByIdAndOrganizationId(id, orgId)
                .filter(q -> q.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
    }

    private QuotationVersion getCurrentVersion(Quotation q) {
        return q.getVersions().stream()
                .filter(v -> v.getVersionNumber() == q.getCurrentVersionNumber())
                .findFirst()
                .orElseGet(() -> q.getVersions().get(0));
    }

    private String generateQuotationNumber(UUID orgId) {
        long count = quotations.countByOrganizationId(orgId) + 1;
        String candidate = String.format("Q-%04d", count);
        int attempts = 0;
        while (quotations.existsByOrganizationIdAndQuotationNumber(orgId, candidate) && attempts < 100) {
            count++;
            candidate = String.format("Q-%04d", count);
            attempts++;
        }
        return candidate;
    }

    private QuotationViews.Detail toDetail(Quotation q) {
        List<QuotationVersion> allVers = versions.findByQuotationIdOrderByVersionNumberDesc(q.getId());
        QuotationVersion cur = allVers.stream()
                .filter(v -> v.getVersionNumber() == q.getCurrentVersionNumber())
                .findFirst()
                .orElse(allVers.isEmpty() ? null : allVers.get(0));

        QuotationViews.VersionDetail curDetail = cur != null ? QuotationViews.VersionDetail.from(cur) : null;
        List<QuotationViews.VersionSummary> versSummary = allVers.stream().map(QuotationViews.VersionSummary::from).toList();
        List<QuotationViews.AuditLogView> audits = auditLogs.findByQuotationIdOrderByPerformedAtDesc(q.getId())
                .stream().map(QuotationViews.AuditLogView::from).toList();

        return QuotationViews.Detail.from(q, curDetail, versSummary, audits);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuotationForOrder> findQuotationForOrder(UUID orgId, UUID quotationId) {
        return quotations.findByIdAndOrganizationId(quotationId, orgId)
                .filter(q -> q.getDeletedAt() == null)
                .map(q -> {
                    QuotationVersion cur = getCurrentVersion(q);
                    List<QuotationDirectory.QuotationItemSummary> itemSummaries = cur.getItems().stream()
                            .map(item -> new QuotationDirectory.QuotationItemSummary(
                                    item.getId(),
                                    item.getItemOrder(),
                                    item.getServiceId(),
                                    item.getServiceName(),
                                    item.getDescription(),
                                    item.getQuantity(),
                                    item.getUnitPrice(),
                                    item.getDiscountPercentage(),
                                    item.getDiscountAmount(),
                                    item.getTaxPercentage(),
                                    item.getTaxAmount(),
                                    item.getSubtotal(),
                                    item.getTotalAmount()))
                            .toList();

                    return new QuotationForOrder(
                            q.getId(),
                            q.getQuotationNumber(),
                            q.getCurrentVersionNumber(),
                            q.getStatus(),
                            q.getCustomerId(),
                            q.getCustomerName(),
                            q.getLeadId(),
                            q.getLeadName(),
                            q.getOpportunityId(),
                            q.getOpportunityTitle(),
                            q.getOwnerId(),
                            q.getOwnerName(),
                            cur.getSubtotal(),
                            cur.getDiscountAmount(),
                            cur.getTaxAmount(),
                            cur.getFinalAmount(),
                            cur.getTerms(),
                            cur.getNotes(),
                            itemSummaries);
                });
    }
}
