package com.pawanputra.bos.identity.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.identity.internal.BranchService;
import com.pawanputra.bos.identity.internal.OrganizationService;
import com.pawanputra.bos.identity.web.OrganizationDtos.BranchResponse;
import com.pawanputra.bos.identity.web.OrganizationDtos.CreateBranchRequest;
import com.pawanputra.bos.identity.web.OrganizationDtos.OrganizationResponse;
import com.pawanputra.bos.identity.web.OrganizationDtos.StateLocations;
import com.pawanputra.bos.identity.web.OrganizationDtos.UpdateBranchRequest;
import com.pawanputra.bos.identity.web.OrganizationDtos.UpdateOrganizationRequest;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
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
 * The signed-in user's organization and its branches. The organization is always taken from the
 * access token, never from the request, so one organization cannot reach into another.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@Tag(name = "Organization and branches")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class OrganizationController {

    private static final Map<String, String> BRANCH_SORTS = Map.of(
            "name", "name",
            "code", "code",
            "city", "city",
            "state", "state",
            "updatedAt", "updatedAt");
    private static final Sort BRANCH_DEFAULT_SORT = Sort.by("state", "city", "name", "id");

    private final OrganizationService organizations;
    private final BranchService branches;

    public OrganizationController(OrganizationService organizations, BranchService branches) {
        this.organizations = organizations;
        this.branches = branches;
    }

    // ------------------------------------------------------------------ organization

    @GetMapping("/organization")
    @PreAuthorize("hasAuthority('" + Permissions.ORGANIZATION_VIEW + "')")
    @Operation(summary = "Your organization. Requires ORGANIZATION_VIEW.")
    public OrganizationResponse getOrganization() {
        return OrganizationResponse.from(organizations.get(organizationId()));
    }

    @PutMapping("/organization")
    @PreAuthorize("hasAuthority('" + Permissions.ORGANIZATION_UPDATE + "')")
    @Operation(summary = "Edit your organization's details. Requires ORGANIZATION_UPDATE.")
    public OrganizationResponse updateOrganization(@Valid @RequestBody UpdateOrganizationRequest request) {
        return OrganizationResponse.from(
                organizations.update(organizationId(), request.details(), request.version()));
    }

    // ------------------------------------------------------------------ branches

    @GetMapping("/branches")
    @PreAuthorize("hasAuthority('" + Permissions.BRANCH_VIEW + "')")
    @Operation(summary = "Search branches. Filters: search (name, code or city), state, city, active. "
            + "Sort: name, code, city, state, updatedAt. Requires BRANCH_VIEW.")
    public PageResponse<BranchResponse> listBranches(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                branches.search(
                        organizationId(),
                        new BranchService.Filter(search, state, city, active),
                        Paging.of(page, size, sort, BRANCH_SORTS, BRANCH_DEFAULT_SORT)),
                BranchResponse::from);
    }

    @GetMapping("/branches/locations")
    @PreAuthorize("hasAuthority('" + Permissions.BRANCH_VIEW + "')")
    @Operation(summary = "The states and cities in which your organization has branches. Requires BRANCH_VIEW.")
    public List<StateLocations> branchLocations() {
        return StateLocations.from(branches.locations(organizationId()));
    }

    @GetMapping("/branches/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.BRANCH_VIEW + "')")
    @Operation(summary = "One branch. Requires BRANCH_VIEW.")
    public BranchResponse getBranch(@PathVariable UUID id) {
        return BranchResponse.from(branches.get(organizationId(), id));
    }

    @PostMapping("/branches")
    @PreAuthorize("hasAuthority('" + Permissions.BRANCH_CREATE + "')")
    @Operation(summary = "Open a branch. Requires BRANCH_CREATE.")
    public ResponseEntity<BranchResponse> createBranch(@Valid @RequestBody CreateBranchRequest request) {
        BranchResponse created = BranchResponse.from(
                branches.create(organizationId(), request.code(), request.details()));
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/branches/" + created.id())).body(created);
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.BRANCH_UPDATE + "')")
    @Operation(summary = "Edit a branch. Marking it as head office moves that title from the current one. "
            + "Requires BRANCH_UPDATE.")
    public BranchResponse updateBranch(@PathVariable UUID id, @Valid @RequestBody UpdateBranchRequest request) {
        return BranchResponse.from(branches.update(organizationId(), id, request.details(), request.version()));
    }

    @PostMapping("/branches/{id}/activate")
    @PreAuthorize("hasAuthority('" + Permissions.BRANCH_UPDATE + "')")
    @Operation(summary = "Switch a branch on. Requires BRANCH_UPDATE.")
    public BranchResponse activateBranch(@PathVariable UUID id) {
        return BranchResponse.from(branches.setActive(organizationId(), id, true));
    }

    @PostMapping("/branches/{id}/deactivate")
    @PreAuthorize("hasAuthority('" + Permissions.BRANCH_UPDATE + "')")
    @Operation(summary = "Switch a branch off. The head office cannot be switched off. Requires BRANCH_UPDATE.")
    public BranchResponse deactivateBranch(@PathVariable UUID id) {
        return BranchResponse.from(branches.setActive(organizationId(), id, false));
    }

    @DeleteMapping("/branches/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.BRANCH_DELETE + "')")
    @Operation(summary = "Delete a branch that has no people assigned and is not the head office. "
            + "Requires BRANCH_DELETE.")
    public ResponseEntity<Void> deleteBranch(@PathVariable UUID id) {
        branches.delete(organizationId(), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID organizationId() {
        return CurrentUser.require().organizationId();
    }
}
