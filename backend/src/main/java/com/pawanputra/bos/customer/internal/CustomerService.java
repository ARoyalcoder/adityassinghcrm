package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.audit.api.ActivityFeed;
import com.pawanputra.bos.audit.api.ActivityFeed.ActivityItem;
import com.pawanputra.bos.audit.api.AuditAction;
import com.pawanputra.bos.audit.api.AuditEvent;
import com.pawanputra.bos.audit.api.AuditRecorder;
import com.pawanputra.bos.customer.api.CustomerSource;
import com.pawanputra.bos.customer.api.CustomerStatus;
import com.pawanputra.bos.customer.api.CustomerType;
import com.pawanputra.bos.customer.internal.CustomerViews.Assignee;
import com.pawanputra.bos.customer.internal.CustomerViews.Detail;
import com.pawanputra.bos.customer.internal.CustomerViews.DuplicateMatch;
import com.pawanputra.bos.customer.internal.CustomerViews.Summary;
import com.pawanputra.bos.customer.internal.CustomerViews.Tag;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.identity.api.UserDirectory.UserSummary;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.persistence.Specs;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers of one organization. Every method takes the caller's organization id and never looks
 * outside it. Results are {@link CustomerViews}, built here inside the transaction.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    /** More than this and the export is refused: narrow the filters instead of pulling the whole base. */
    static final int MAX_EXPORT_ROWS = 20_000;

    private static final String MODULE = "customers";

    private final CustomerRepository customers;
    private final CustomerContactRepository contacts;
    private final CustomerAddressRepository addresses;
    private final CustomerTagRepository tags;
    private final DuplicateDetector duplicateDetector;
    private final UserDirectory users;
    private final AuditRecorder audit;
    private final ActivityFeed activityFeed;
    private final Clock clock;

    public CustomerService(
            CustomerRepository customers,
            CustomerContactRepository contacts,
            CustomerAddressRepository addresses,
            CustomerTagRepository tags,
            DuplicateDetector duplicateDetector,
            UserDirectory users,
            AuditRecorder audit,
            ActivityFeed activityFeed,
            Clock clock) {
        this.customers = customers;
        this.contacts = contacts;
        this.addresses = addresses;
        this.tags = tags;
        this.duplicateDetector = duplicateDetector;
        this.users = users;
        this.audit = audit;
        this.activityFeed = activityFeed;
        this.clock = clock;
    }

    /** Every filter is optional; {@code null} means "any". */
    public record Filter(
            String search, CustomerType type, CustomerStatus status, CustomerSource source, UUID assignedUserId,
            UUID tagId) {
    }

    /** The fields a user may set. {@code tags} are names; unknown ones are created. */
    public record Details(
            CustomerType type,
            String firstName,
            String lastName,
            String companyName,
            String taxId,
            String email,
            String phone,
            CustomerStatus status,
            CustomerSource source,
            UUID assignedUserId,
            List<String> tags) {
    }

    // ------------------------------------------------------------------ reading

    public Page<Summary> search(UUID organizationId, Filter filter, Pageable pageable) {
        Page<Customer> page = customers.findAll(specification(organizationId, filter), pageable);
        Map<UUID, UserSummary> assignees = users.findAll(organizationId, page.getContent().stream()
                .map(Customer::getAssignedUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return page.map(customer -> summary(customer, assignees));
    }

    public Detail get(UUID organizationId, UUID id) {
        return detail(load(organizationId, id));
    }

    /** Tags the organization has used so far, for filters and suggestions. */
    public List<Tag> tags(UUID organizationId) {
        return tags.findAllByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(tag -> new Tag(tag.getId(), tag.getName())).toList();
    }

    /** Employees a customer can be assigned to. */
    public List<Assignee> assignees(UUID organizationId) {
        return users.listActive(organizationId).stream()
                .map(user -> new Assignee(user.id(), user.fullName())).toList();
    }

    /** The customer's history, including changes to its contacts, addresses and notes. */
    public Page<ActivityItem> activity(UUID organizationId, UUID id, Pageable pageable) {
        load(organizationId, id);
        return activityFeed.forEntity(organizationId, "Customer", id.toString(), pageable);
    }

    /**
     * Existing customers that may be the same as the one described.
     *
     * @param excludeCustomerId the customer being edited, or {@code null} when entering a new one
     */
    public List<DuplicateMatch> findDuplicates(
            UUID organizationId, String phone, String email, String companyName, UUID excludeCustomerId) {
        return duplicateDetector.find(organizationId, phone, email, companyName, excludeCustomerId);
    }

    // ------------------------------------------------------------------ writing

    /**
     * @param duplicatesConfirmed the user has seen the possible duplicates and wants to create the customer anyway
     */
    @Transactional
    public Detail create(UUID organizationId, Details details, boolean duplicatesConfirmed) {
        validate(organizationId, details);
        if (!duplicatesConfirmed) {
            rejectIfDuplicate(organizationId, details, null);
        }
        Customer customer = new Customer(organizationId, "CUS-%06d".formatted(customers.nextCustomerNumber()));
        apply(customer, details);
        customer.replaceTags(resolveTags(organizationId, details.tags()));
        return detail(customers.saveAndFlush(customer));
    }

    @Transactional
    public Detail update(
            UUID organizationId, UUID id, Details details, long expectedVersion, boolean duplicatesConfirmed) {
        Customer customer = load(organizationId, id);
        if (customer.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This customer was changed by someone else. Reload it and apply your changes again.");
        }
        validate(organizationId, details);
        // Only re-check when the identifying details change, so fixing a typo elsewhere on a customer
        // already known to share a phone number does not raise the warning every time.
        boolean identityChanged = !Objects.equals(CustomerKeys.phone(details.phone()), customer.getPhoneKey())
                || !Objects.equals(CustomerKeys.email(details.email()), customer.getEmail())
                || !Objects.equals(CustomerKeys.company(details.companyName()), customer.getCompanyNameKey());
        if (identityChanged && !duplicatesConfirmed) {
            rejectIfDuplicate(organizationId, details, id);
        }

        apply(customer, details);
        Set<String> tagsBefore = tagNames(customer);
        customer.replaceTags(resolveTags(organizationId, details.tags()));
        Set<String> tagsAfter = tagNames(customer);
        Customer saved = customers.saveAndFlush(customer);
        if (!tagsBefore.equals(tagsAfter)) {
            // Tags are a collection, which automatic entity auditing does not cover.
            audit.record(AuditEvent
                    .of(AuditAction.UPDATE, MODULE, "Updated Customer '" + saved.getDisplayName() + "': tags")
                    .entity("Customer", saved.getId(), saved.getDisplayName())
                    .before(Map.of("tags", tagsBefore))
                    .after(Map.of("tags", tagsAfter)));
        }
        return detail(saved);
    }

    /** Soft delete: the customer disappears from lists but stays for the records that refer to it. */
    @Transactional
    public void delete(UUID organizationId, UUID id) {
        load(organizationId, id).markDeleted(clock.instant());
    }

    // ------------------------------------------------------------------ export

    /**
     * The customers matching the filter as a CSV file, in the given order. Taking customer data out
     * of the system is always recorded in the audit log, with the filters used and the number of rows.
     */
    @Transactional
    public byte[] exportCsv(UUID organizationId, Filter filter, Sort sort) {
        Page<Customer> page = customers.findAll(
                specification(organizationId, filter), PageRequest.of(0, MAX_EXPORT_ROWS, sort));
        if (page.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new BusinessRuleException(
                    "%d customers match, which is more than the %d that can be exported at once. Narrow the filters."
                            .formatted(page.getTotalElements(), MAX_EXPORT_ROWS));
        }
        Map<UUID, UserSummary> assignees = users.findAll(organizationId, page.getContent().stream()
                .map(Customer::getAssignedUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        byte[] csv = CustomerCsv.write(page.getContent().stream().map(c -> summary(c, assignees)).toList());

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", filter.search());
        filters.put("type", filter.type());
        filters.put("status", filter.status());
        filters.put("source", filter.source());
        filters.put("assignedUserId", filter.assignedUserId());
        filters.put("tagId", filter.tagId());
        filters.values().removeIf(Objects::isNull);
        audit.record(AuditEvent.of(AuditAction.EXPORT, MODULE, "Exported " + page.getNumberOfElements() + " customers")
                .entity("Customer", null, null)
                .metadata("rowCount", page.getNumberOfElements())
                .metadata("format", "CSV")
                .metadata("filters", filters.isEmpty() ? null : filters));
        return csv;
    }

    // ------------------------------------------------------------------ helpers

    /** The entity itself, for the other services of this module. Throws 404 outside the organization. */
    public Customer load(UUID organizationId, UUID id) {
        return customers.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private static Specification<Customer> specification(UUID organizationId, Filter filter) {
        Specification<Customer> text = Specs.search(
                filter.search(), "displayName", "customerNumber", "email", "phone", "companyName");
        // "98765 43210" should find "+91-9876543210": also compare digits with the normalised phone.
        String digits = filter.search() == null ? "" : filter.search().replaceAll("\\D", "");
        if (digits.length() >= 4) {
            text = text.or(Specs.search(digits, "phoneKey"));
        }
        Specification<Customer> spec = Specs.<Customer>equalTo("organizationId", organizationId)
                .and(text)
                .and(Specs.equalTo("type", filter.type()))
                .and(Specs.equalTo("status", filter.status()))
                .and(Specs.equalTo("source", filter.source()))
                .and(Specs.equalTo("assignedUserId", filter.assignedUserId()));
        if (filter.tagId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.join("tags").get("id"), filter.tagId()));
        }
        return spec;
    }

    /** Rules that involve more than one field, or another module. Single-field rules are on the request. */
    private void validate(UUID organizationId, Details details) {
        if (details.type() == CustomerType.INDIVIDUAL && isBlank(details.firstName())) {
            throw new BusinessRuleException("A first name is required for an individual customer");
        }
        if (details.type() == CustomerType.BUSINESS && isBlank(details.companyName())) {
            throw new BusinessRuleException("A company name is required for a business customer");
        }
        if (details.assignedUserId() != null) {
            UserSummary assignee = users.find(organizationId, details.assignedUserId())
                    .orElseThrow(() -> new BusinessRuleException("The selected employee does not exist"));
            if (!assignee.active()) {
                throw new BusinessRuleException(assignee.fullName() + " is not active and cannot be assigned customers");
            }
        }
    }

    private void rejectIfDuplicate(UUID organizationId, Details details, UUID excludeId) {
        List<DuplicateMatch> matches = duplicateDetector.find(
                organizationId, details.phone(), details.email(), details.companyName(), excludeId);
        if (!matches.isEmpty()) {
            throw new ApiException(ErrorCode.POSSIBLE_DUPLICATE,
                    matches.size() == 1
                            ? "An existing customer has the same phone, email or company name: "
                                    + matches.getFirst().displayName() + " (" + matches.getFirst().customerNumber() + ")"
                            : matches.size() + " existing customers have the same phone, email or company name");
        }
    }

    private static void apply(Customer customer, Details details) {
        customer.identify(details.type(), details.firstName(), details.lastName(), details.companyName());
        customer.setTaxId(details.taxId());
        customer.setEmail(details.email());
        customer.setPhone(details.phone());
        customer.setStatus(details.status());
        customer.setSource(details.source());
        customer.setAssignedUserId(details.assignedUserId());
    }

    /** Finds each tag by name (ignoring case) or creates it. Blank and repeated names are dropped. */
    private Set<CustomerTag> resolveTags(UUID organizationId, List<String> names) {
        Map<String, CustomerTag> resolved = new LinkedHashMap<>();
        for (String raw : names == null ? List.<String>of() : names) {
            String name = raw == null ? "" : raw.trim();
            String key = name.toLowerCase(Locale.ROOT);
            if (name.isEmpty() || resolved.containsKey(key)) {
                continue;
            }
            resolved.put(key, tags.findByOrganizationIdAndNameIgnoreCase(organizationId, name)
                    .orElseGet(() -> tags.save(new CustomerTag(organizationId, name))));
        }
        return new LinkedHashSet<>(resolved.values());
    }

    private static Set<String> tagNames(Customer customer) {
        return customer.getTags().stream().map(CustomerTag::getName).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private Summary summary(Customer customer, Map<UUID, UserSummary> assignees) {
        return new Summary(
                customer.getId(), customer.getCustomerNumber(), customer.getType(), customer.getDisplayName(),
                customer.getEmail(), customer.getPhone(), customer.getStatus(), customer.getSource(),
                assignee(customer.getAssignedUserId(), assignees), List.copyOf(tagNames(customer)),
                customer.getCreatedAt());
    }

    private Detail detail(Customer customer) {
        UUID assignedUserId = customer.getAssignedUserId();
        Map<UUID, UserSummary> assignees = assignedUserId == null
                ? Map.of()
                : users.findAll(customer.getOrganizationId(), Set.of(assignedUserId));
        return new Detail(
                customer.getId(), customer.getCustomerNumber(), customer.getType(), customer.getDisplayName(),
                customer.getFirstName(), customer.getLastName(), customer.getCompanyName(), customer.getTaxId(),
                customer.getEmail(), customer.getPhone(), customer.getStatus(), customer.getSource(),
                assignee(assignedUserId, assignees), List.copyOf(tagNames(customer)),
                contacts.countByCustomerId(customer.getId()), addresses.countByCustomerId(customer.getId()),
                customer.getVersion(), customer.getCreatedAt(), customer.getUpdatedAt());
    }

    private static Assignee assignee(UUID userId, Map<UUID, UserSummary> known) {
        if (userId == null) {
            return null;
        }
        UserSummary user = known.get(userId);
        return new Assignee(userId, user != null ? user.fullName() : "Unknown user");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
