package com.pawanputra.bos.audit.web;

import com.pawanputra.bos.audit.api.AuditAction;
import com.pawanputra.bos.audit.internal.AuditQueryService;
import com.pawanputra.bos.audit.internal.AuditQueryService.Activity;
import com.pawanputra.bos.audit.internal.AuditQueryService.Detail;
import com.pawanputra.bos.audit.internal.AuditQueryService.Entry;
import com.pawanputra.bos.audit.internal.AuditQueryService.Facets;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to the audit log of the caller's organization. There is deliberately no endpoint
 * that writes, changes or deletes audit rows: they are produced by the system as things happen.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/audit")
@Tag(name = "Audit log")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping("/logs")
    @Operation(summary = "Search the audit log, newest first. Filters: actorId (user), module, action, entityType, "
            + "entityId, from / to (ISO-8601 instants; from inclusive, to exclusive), search (summary, user, "
            + "entity name). Requires AUDIT_VIEW.")
    public PageResponse<Entry> logs(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.from(
                audit.search(
                        organizationId(),
                        new AuditQueryService.Filter(actorId, module, action, entityType, entityId, from, to, search),
                        pageable(page, size)),
                Function.identity());
    }

    @GetMapping("/logs/{id}")
    @Operation(summary = "One audit event with its before and after values and metadata. Requires AUDIT_VIEW.")
    public Detail log(@PathVariable UUID id) {
        return audit.find(organizationId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event", id));
    }

    @GetMapping("/facets")
    @Operation(summary = "The modules, entity types and users that occur in the audit log, for filter choices. "
            + "Requires AUDIT_VIEW.")
    public Facets facets() {
        return audit.facets(organizationId());
    }

    @GetMapping("/activity")
    @Operation(summary = "The activity timeline of one record, newest first. Requires AUDIT_VIEW.")
    public PageResponse<Activity> activity(
            @RequestParam String entityType,
            @RequestParam String entityId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.from(
                audit.activity(organizationId(), entityType, entityId, pageable(page, size)), Function.identity());
    }

    /** The order is fixed (newest first), so only page and size are accepted. */
    private static Pageable pageable(Integer page, Integer size) {
        return Paging.of(page, size, null, Map.of(), Sort.unsorted());
    }

    private static UUID organizationId() {
        return CurrentUser.require().organizationId();
    }
}
