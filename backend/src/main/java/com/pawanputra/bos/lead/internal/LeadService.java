package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.catalog.api.LeadCustomFields;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FieldValue;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FileReference;
import com.pawanputra.bos.catalog.api.ServiceDirectory;
import com.pawanputra.bos.catalog.api.ServiceDirectory.ServiceSummary;
import com.pawanputra.bos.customer.api.InteractionType;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.lead.api.LeadHealth;
import com.pawanputra.bos.lead.api.LeadPriority;
import com.pawanputra.bos.lead.api.LeadStatus;
import com.pawanputra.bos.lead.api.QualificationStatus;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.persistence.Specs;
import com.pawanputra.bos.platform.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leads: creating and editing them, and logging what is done about them. Every change ends by
 * reassessing the lead, so its stored score is never older than its last change.
 */
@Service
@Transactional(readOnly = true)
public class LeadService {

    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private final LeadRepository leads;
    private final LeadActivityRepository activities;
    private final LeadQualificationService qualification;
    private final LeadCustomFields customFields;
    private final ServiceDirectory services;
    private final UserDirectory users;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public LeadService(
            LeadRepository leads,
            LeadActivityRepository activities,
            LeadQualificationService qualification,
            LeadCustomFields customFields,
            ServiceDirectory services,
            UserDirectory users,
            Clock clock) {
        this.leads = leads;
        this.activities = activities;
        this.qualification = qualification;
        this.customFields = customFields;
        this.services = services;
        this.users = users;
        this.clock = clock;
    }

    /** Every filter is optional; {@code null} means "any". */
    public record Filter(
            String search, LeadStatus status, LeadPriority priority, LeadHealth health,
            QualificationStatus qualificationStatus, UUID serviceId) {
    }

    /**
     * What a user may set.
     *
     * @param customFields the answers to the service's own fields, field key to value
     */
    public record Details(
            String contactName, String companyName, String email, String phone, UUID serviceId,
            BigDecimal estimatedValue, LeadStatus status, Map<String, Object> customFields) {
    }

    public record ServiceRef(UUID id, String name, String verticalName) {
    }

    /** A lead as shown in lists. */
    public record Summary(
            UUID id, String leadNumber, String contactName, String companyName, String email, String phone,
            ServiceRef service, BigDecimal estimatedValue, LeadStatus status, int score, LeadPriority priority,
            LeadHealth health, QualificationStatus qualificationStatus, Instant createdAt) {
    }

    /** A lead with everything needed to show and edit it. */
    public record Detail(
            UUID id, String leadNumber, String contactName, String companyName, String email, String phone,
            ServiceRef service, BigDecimal estimatedValue, LeadStatus status, Instant closedAt, int score,
            LeadPriority priority, LeadHealth health, QualificationStatus qualificationStatus,
            List<FieldValue> customFields, long version, Instant createdAt, Instant updatedAt) {
    }

    public record ActivityView(UUID id, InteractionType type, Instant occurredAt, String subject, String loggedByName) {

        static ActivityView from(LeadActivity activity) {
            return new ActivityView(activity.getId(), activity.getType(), activity.getOccurredAt(),
                    activity.getSubject(), activity.getLoggedByName());
        }
    }

    // ------------------------------------------------------------------ reading

    public Page<Summary> search(UUID organizationId, Filter filter, Pageable pageable) {
        Specification<Lead> spec = Specs.<Lead>equalTo("organizationId", organizationId)
                .and(Specs.search(filter.search(), "contactName", "companyName", "leadNumber", "email", "phone"))
                .and(Specs.equalTo("status", filter.status()))
                .and(Specs.equalTo("priority", filter.priority()))
                .and(Specs.equalTo("health", filter.health()))
                .and(Specs.equalTo("qualificationStatus", filter.qualificationStatus()))
                .and(Specs.equalTo("serviceId", filter.serviceId()));
        Page<Lead> page = leads.findAll(spec, pageable);
        Map<UUID, ServiceSummary> named = services.findAll(
                page.getContent().stream().map(Lead::getServiceId).collect(Collectors.toSet()));
        return page.map(lead -> new Summary(
                lead.getId(), lead.getLeadNumber(), lead.getContactName(), lead.getCompanyName(), lead.getEmail(),
                lead.getPhone(), serviceRef(lead.getServiceId(), named.get(lead.getServiceId())),
                lead.getEstimatedValue(), lead.getStatus(), lead.getScore(), lead.getPriority(), lead.getHealth(),
                lead.getQualificationStatus(), lead.getCreatedAt()));
    }

    public Detail get(UUID organizationId, UUID id) {
        Lead lead = load(organizationId, id);
        return detail(lead, lead.getScore(), lead.getPriority(), lead.getHealth(), lead.getQualificationStatus());
    }

    public List<ActivityView> activities(UUID organizationId, UUID id) {
        load(organizationId, id);
        return activities.findTop50ByLeadIdOrderByOccurredAtDescIdDesc(id).stream().map(ActivityView::from).toList();
    }

    // ------------------------------------------------------------------ writing

    @Transactional
    public Detail create(UUID organizationId, Details details) {
        validate(details, null);
        Lead lead = new Lead(organizationId, "LD-%06d".formatted(leads.nextLeadNumber()));
        apply(lead, details);
        lead = leads.saveAndFlush(lead);
        saveCustomFields(lead, details.customFields());
        return detail(lead, qualification.reassess(lead));
    }

    @Transactional
    public Detail update(UUID organizationId, UUID id, Details details, long expectedVersion) {
        Lead lead = load(organizationId, id);
        if (lead.getVersion() != expectedVersion) {
            throw new ConflictException("This lead was changed by someone else. Reload it and apply your changes again.");
        }
        validate(details, lead);
        if (!lead.getServiceId().equals(details.serviceId())) {
            // The old service's answers mean nothing for the new one.
            customFields.delete(lead.getId());
        }
        apply(lead, details);
        lead = leads.saveAndFlush(lead);
        saveCustomFields(lead, details.customFields());
        if (lead.getVersion() == expectedVersion) {
            // Nothing on the lead row itself changed, but its service-specific answers may have. They
            // are part of what the user edited, so the version must still move: otherwise two people
            // editing those answers at once would overwrite each other without either being told.
            entityManager.lock(lead, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        return detail(lead, qualification.reassess(lead));
    }

    /** Soft delete. */
    @Transactional
    public void delete(UUID organizationId, UUID id) {
        load(organizationId, id).markDeleted(clock.instant());
    }

    /**
     * @param occurredAt when it happened; {@code null} means now. Not in the future: this is a
     *                   record of what was done
     */
    @Transactional
    public ActivityView logActivity(CurrentUser author, UUID id, InteractionType type, Instant occurredAt, String subject) {
        Lead lead = load(author.organizationId(), id);
        Instant now = clock.instant();
        Instant when = occurredAt == null ? now : occurredAt;
        if (when.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new BusinessRuleException("An activity cannot be logged for a time in the future");
        }
        String loggedBy = users.find(author.organizationId(), author.id())
                .map(UserDirectory.UserSummary::fullName).orElse(author.email());
        LeadActivity activity = activities.saveAndFlush(new LeadActivity(id, type, when, subject, loggedBy));
        qualification.reassess(lead);
        return ActivityView.from(activity);
    }

    /**
     * Stores a file for one of a service's FILE fields, to be attached to a lead being created or
     * edited. The catalog checks it against the field's rules; the result is the field's answer.
     */
    public FileReference storeFieldFile(
            UUID serviceId, String fieldKey, String fileName, String contentType, long sizeBytes, InputStream content) {
        return customFields.storeFile(serviceId, fieldKey, fileName, contentType, sizeBytes, content);
    }

    // ------------------------------------------------------------------ helpers

    Lead load(UUID organizationId, UUID id) {
        return leads.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));
    }

    /** @param existing the lead being edited, or {@code null} when creating */
    private void validate(Details details, Lead existing) {
        if (details.phone() == null && details.email() == null) {
            throw new FieldValidationException("phone", "Enter a phone number or an email address");
        }
        // A lead may keep a service that has since been switched off, but cannot be moved to one.
        boolean sameService = existing != null && existing.getServiceId().equals(details.serviceId());
        boolean usable = services.find(details.serviceId())
                .map(service -> service.active() || sameService).orElse(false);
        if (!usable) {
            throw new FieldValidationException("serviceId", "Choose a service that is currently offered");
        }
    }

    private void apply(Lead lead, Details details) {
        lead.setContactName(details.contactName());
        lead.setCompanyName(details.companyName());
        lead.setEmail(details.email() == null ? null : details.email().toLowerCase(Locale.ROOT));
        lead.setPhone(details.phone());
        lead.setServiceId(details.serviceId());
        lead.setEstimatedValue(details.estimatedValue());
        lead.setStatus(details.status() == null ? LeadStatus.OPEN : details.status(), clock.instant());
    }

    /** The catalog validates the answers against the service's fields; its errors are reported under "customFields.". */
    private void saveCustomFields(Lead lead, Map<String, Object> answers) {
        try {
            customFields.save(lead.getId(), lead.getServiceId(), answers == null ? Map.of() : answers);
        } catch (FieldValidationException invalid) {
            throw new FieldValidationException(invalid.getViolations().stream()
                    .map(v -> new FieldValidationException.Violation("customFields." + v.field(), v.message()))
                    .toList());
        }
    }

    private ServiceRef serviceRef(UUID serviceId, ServiceSummary summary) {
        // A service that was removed from the catalog is still shown, as what it was.
        return summary == null
                ? new ServiceRef(serviceId, "Removed service", null)
                : new ServiceRef(summary.id(), summary.name(), summary.verticalName());
    }

    private Detail detail(Lead lead, LeadAssessment assessment) {
        return detail(lead, assessment.score().score(), assessment.priority(), assessment.health(),
                assessment.qualificationStatus());
    }

    private Detail detail(
            Lead lead, int score, LeadPriority priority, LeadHealth health, QualificationStatus qualificationStatus) {
        return new Detail(
                lead.getId(), lead.getLeadNumber(), lead.getContactName(), lead.getCompanyName(), lead.getEmail(),
                lead.getPhone(), serviceRef(lead.getServiceId(), services.find(lead.getServiceId()).orElse(null)),
                lead.getEstimatedValue(), lead.getStatus(), lead.getClosedAt(), score, priority, health,
                qualificationStatus, customFields.values(lead.getId()), lead.getVersion(), lead.getCreatedAt(),
                lead.getUpdatedAt());
    }
}
