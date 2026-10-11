package com.pawanputra.bos.customer.web;

import com.pawanputra.bos.audit.api.ActivityFeed.ActivityItem;
import com.pawanputra.bos.customer.api.CustomerSource;
import com.pawanputra.bos.customer.api.CustomerStatus;
import com.pawanputra.bos.customer.api.CustomerType;
import com.pawanputra.bos.customer.internal.CustomerInteractionService;
import com.pawanputra.bos.customer.internal.CustomerOverviewService;
import com.pawanputra.bos.customer.internal.CustomerOverviewService.Overview;
import com.pawanputra.bos.customer.internal.CustomerPartsService;
import com.pawanputra.bos.customer.internal.CustomerService;
import com.pawanputra.bos.customer.internal.CustomerTimelineService;
import com.pawanputra.bos.customer.internal.CustomerViews.Address;
import com.pawanputra.bos.customer.internal.CustomerViews.Assignee;
import com.pawanputra.bos.customer.internal.CustomerViews.Contact;
import com.pawanputra.bos.customer.internal.CustomerViews.Detail;
import com.pawanputra.bos.customer.internal.CustomerViews.DuplicateMatch;
import com.pawanputra.bos.customer.internal.CustomerViews.Note;
import com.pawanputra.bos.customer.internal.CustomerViews.Summary;
import com.pawanputra.bos.customer.internal.CustomerViews.Tag;
import com.pawanputra.bos.customer.web.CustomerDtos.AddressRequest;
import com.pawanputra.bos.customer.web.CustomerDtos.ContactRequest;
import com.pawanputra.bos.customer.web.CustomerDtos.CustomerRequest;
import com.pawanputra.bos.customer.web.CustomerDtos.DuplicateCheckRequest;
import com.pawanputra.bos.customer.web.CustomerDtos.InteractionRequest;
import com.pawanputra.bos.customer.web.CustomerDtos.NoteRequest;
import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelinePage;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

/**
 * Customers and everything attached to them. The organization always comes from the access token.
 * This class only translates HTTP to service calls; the rules live in the services.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/customers")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Customers")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class CustomerController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.CUSTOMER_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.CUSTOMER_CREATE + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.CUSTOMER_UPDATE + "')";
    private static final String CAN_DELETE = "hasAuthority('" + Permissions.CUSTOMER_DELETE + "')";
    private static final String CAN_EXPORT = "hasAuthority('" + Permissions.CUSTOMER_EXPORT + "')";
    /** Checking for duplicates is part of entering or editing a customer. */
    private static final String CAN_CREATE_OR_UPDATE = "hasAnyAuthority('" + Permissions.CUSTOMER_CREATE + "', '"
            + Permissions.CUSTOMER_UPDATE + "')";

    private static final Map<String, String> SORTS = Map.of(
            "name", "displayName",
            "customerNumber", "customerNumber",
            "status", "status",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by("displayName", "id");

    private final CustomerService customers;
    private final CustomerPartsService parts;
    private final CustomerInteractionService interactions;
    private final CustomerTimelineService timeline;
    private final CustomerOverviewService overview;

    public CustomerController(
            CustomerService customers,
            CustomerPartsService parts,
            CustomerInteractionService interactions,
            CustomerTimelineService timeline,
            CustomerOverviewService overview) {
        this.customers = customers;
        this.parts = parts;
        this.interactions = interactions;
        this.timeline = timeline;
        this.overview = overview;
    }

    // ------------------------------------------------------------------ list, export, lookups

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Search customers. Filters: search (name, customer number, email, phone, company), type, "
            + "status, source, assignedTo (user id), tagId. Sort: name, customerNumber, status, createdAt, updatedAt. "
            + "Requires CUSTOMER_VIEW.")
    public PageResponse<Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerType type,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) CustomerSource source,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                customers.search(
                        organizationId(),
                        new CustomerService.Filter(search, type, status, source, assignedTo, tagId),
                        Paging.of(page, size, sort, SORTS, DEFAULT_SORT)),
                Function.identity());
    }

    @GetMapping("/export")
    @PreAuthorize(CAN_EXPORT)
    @Operation(summary = "Download the customers matching the same filters and sort as the list, as a CSV file. "
            + "Recorded in the audit log. Requires CUSTOMER_EXPORT.")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerType type,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) CustomerSource source,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String sort) {
        Sort order = Paging.of(0, 1, sort, SORTS, DEFAULT_SORT).getSort();
        byte[] csv = customers.exportCsv(
                organizationId(), new CustomerService.Filter(search, type, status, source, assignedTo, tagId), order);
        String fileName = "customers-" + LocalDate.now(ZoneOffset.UTC) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
    }

    @GetMapping("/tags")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The tags your organization has used on customers. Requires CUSTOMER_VIEW.")
    public List<Tag> tags() {
        return customers.tags(organizationId());
    }

    @GetMapping("/assignees")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Employees a customer can be assigned to. Requires CUSTOMER_VIEW.")
    public List<Assignee> assignees() {
        return customers.assignees(organizationId());
    }

    @PostMapping("/duplicate-check")
    @PreAuthorize(CAN_CREATE_OR_UPDATE)
    @Operation(summary = "Existing customers sharing the given phone, email or company name. Call it before saving "
            + "to show the user what they may be duplicating. Requires CUSTOMER_CREATE or CUSTOMER_UPDATE.")
    public List<DuplicateMatch> duplicateCheck(@Valid @RequestBody DuplicateCheckRequest request) {
        return customers.findDuplicates(
                organizationId(), request.phone(), request.email(), request.companyName(), request.excludeCustomerId());
    }

    // ------------------------------------------------------------------ customer

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create a customer. Answers 409 POSSIBLE_DUPLICATE if an existing customer shares the "
            + "phone, email or company name, unless confirmDuplicates is true. Requires CUSTOMER_CREATE.")
    public ResponseEntity<Detail> create(@Valid @RequestBody CustomerRequest request) {
        Detail created = customers.create(organizationId(), request.details(), request.duplicatesConfirmed());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/customers/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "One customer: the Overview of its profile. Requires CUSTOMER_VIEW.")
    public Detail get(@PathVariable UUID id) {
        return customers.get(organizationId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Edit a customer, including its status, tags and assigned employee. Same duplicate rule "
            + "as create, applied when the phone, email or company name changes. Requires CUSTOMER_UPDATE.")
    public Detail update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(
                organizationId(), id, request.details(), requireVersion(request.version()),
                request.duplicatesConfirmed());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Delete a customer (kept for history, hidden from lists). Requires CUSTOMER_DELETE.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customers.delete(organizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The customer's history, newest first, including changes to its contacts, addresses and "
            + "notes. Requires CUSTOMER_VIEW.")
    public PageResponse<ActivityItem> activity(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.from(customers.activity(organizationId(), id, unsorted(page, size)), Function.identity());
    }

    // ------------------------------------------------------------------ contacts

    @GetMapping("/{id}/contacts")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The customer's contacts, primary first. Requires CUSTOMER_VIEW.")
    public List<Contact> contacts(@PathVariable UUID id) {
        return parts.contacts(organizationId(), id);
    }

    @PostMapping("/{id}/contacts")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add a contact. The first one becomes the primary contact. Requires CUSTOMER_UPDATE.")
    public ResponseEntity<Contact> addContact(@PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        Contact created = parts.addContact(organizationId(), id, request.details());
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}/contacts/{contactId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Edit a contact. Marking it primary moves that title from the current one. "
            + "Requires CUSTOMER_UPDATE.")
    public Contact updateContact(
            @PathVariable UUID id, @PathVariable UUID contactId, @Valid @RequestBody ContactRequest request) {
        return parts.updateContact(organizationId(), id, contactId, request.details(), requireVersion(request.version()));
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Remove a contact. Requires CUSTOMER_UPDATE.")
    public ResponseEntity<Void> removeContact(@PathVariable UUID id, @PathVariable UUID contactId) {
        parts.removeContact(organizationId(), id, contactId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ addresses

    @GetMapping("/{id}/addresses")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The customer's billing and service addresses. Requires CUSTOMER_VIEW.")
    public List<Address> addresses(@PathVariable UUID id) {
        return parts.addresses(organizationId(), id);
    }

    @PostMapping("/{id}/addresses")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add an address. The first of each type becomes that type's default. "
            + "Requires CUSTOMER_UPDATE.")
    public ResponseEntity<Address> addAddress(@PathVariable UUID id, @Valid @RequestBody AddressRequest request) {
        Address created = parts.addAddress(organizationId(), id, request.details());
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}/addresses/{addressId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Edit an address. Requires CUSTOMER_UPDATE.")
    public Address updateAddress(
            @PathVariable UUID id, @PathVariable UUID addressId, @Valid @RequestBody AddressRequest request) {
        return parts.updateAddress(organizationId(), id, addressId, request.details(), requireVersion(request.version()));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Remove an address. Requires CUSTOMER_UPDATE.")
    public ResponseEntity<Void> removeAddress(@PathVariable UUID id, @PathVariable UUID addressId) {
        parts.removeAddress(organizationId(), id, addressId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ notes

    @GetMapping("/{id}/notes")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Notes about the customer, newest first. Requires CUSTOMER_VIEW.")
    public PageResponse<Note> notes(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.from(parts.notes(organizationId(), id, unsorted(page, size)), Function.identity());
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Write a note about the customer. Requires CUSTOMER_UPDATE.")
    public ResponseEntity<Note> addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(201).body(parts.addNote(CurrentUser.require(), id, request.body().trim()));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Remove a note. Requires CUSTOMER_UPDATE.")
    public ResponseEntity<Void> removeNote(@PathVariable UUID id, @PathVariable UUID noteId) {
        parts.removeNote(organizationId(), id, noteId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ customer 360

    @GetMapping("/{id}/overview")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The customer's health (with the reasons for it) and summary facts, worked out from "
            + "current records. Requires CUSTOMER_VIEW.")
    public Overview overview(@PathVariable UUID id) {
        return overview.overview(organizationId(), id);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Everything that has happened with the customer, from every module, newest first. "
            + "Page with the returned nextCursor (cursor=...); limit up to 100; category may be repeated to "
            + "filter (COMMUNICATION, SALES, PROJECT, FINANCE, SERVICE, AMC, RECORD). Requires CUSTOMER_VIEW.")
    public TimelinePage timeline(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Set<TimelineCategory> category) {
        int size = limit == null ? CustomerTimelineService.DEFAULT_LIMIT : limit;
        if (size < 1 || size > CustomerTimelineService.MAX_LIMIT) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST,
                    "limit must be between 1 and " + CustomerTimelineService.MAX_LIMIT);
        }
        return timeline.timeline(organizationId(), id, cursor, size, category == null ? Set.of() : category);
    }

    @GetMapping("/{id}/interactions")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Calls, meetings, emails and visits logged for the customer, newest first. "
            + "Requires CUSTOMER_VIEW.")
    public PageResponse<CustomerInteractionService.View> interactions(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.from(interactions.list(organizationId(), id, unsorted(page, size)), Function.identity());
    }

    @PostMapping("/{id}/interactions")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Log a call, meeting, email or visit. It appears on the customer timeline. "
            + "Requires CUSTOMER_UPDATE.")
    public ResponseEntity<CustomerInteractionService.View> logInteraction(
            @PathVariable UUID id, @Valid @RequestBody InteractionRequest request) {
        return ResponseEntity.status(201).body(interactions.log(CurrentUser.require(), id, request.toDetails()));
    }

    @DeleteMapping("/{id}/interactions/{interactionId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Remove an interaction that was logged by mistake. Requires CUSTOMER_UPDATE.")
    public ResponseEntity<Void> removeInteraction(@PathVariable UUID id, @PathVariable UUID interactionId) {
        interactions.remove(organizationId(), id, interactionId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ helpers

    private static long requireVersion(Long version) {
        if (version == null) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "version is required when updating");
        }
        return version;
    }

    private static Pageable unsorted(Integer page, Integer size) {
        return Paging.of(page, size, null, Map.of(), Sort.unsorted());
    }

    private static UUID organizationId() {
        return CurrentUser.require().organizationId();
    }
}
