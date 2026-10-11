package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.persistence.Specs;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branches of one organization. Every method takes the organization id of the caller and never
 * looks outside it, so a branch of another organization behaves exactly like one that does not exist.
 */
@Service
@Transactional(readOnly = true)
public class BranchService {

    private final BranchRepository branches;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final Clock clock;

    public BranchService(
            BranchRepository branches, OrganizationRepository organizations, UserRepository users, Clock clock) {
        this.branches = branches;
        this.organizations = organizations;
        this.users = users;
        this.clock = clock;
    }

    /** Every filter is optional; {@code null} means "any". */
    public record Filter(String search, String state, String city, Boolean active) {
    }

    /** The fields an administrator may set on create and on edit. The code is set on creation only. */
    public record Details(
            String name,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String countryCode,
            String phone,
            String email,
            boolean headOffice) {
    }

    public Page<Branch> search(UUID organizationId, Filter filter, Pageable pageable) {
        Specification<Branch> spec = Specs.<Branch>equalTo("organization.id", organizationId)
                .and(Specs.search(filter.search(), "name", "code", "city"))
                .and(Specs.equalIgnoreCase("state", filter.state()))
                .and(Specs.equalIgnoreCase("city", filter.city()))
                .and(Specs.equalTo("active", filter.active()));
        return branches.findAll(spec, pageable);
    }

    /** The states and cities the organization is present in. */
    public List<BranchLocation> locations(UUID organizationId) {
        return branches.findLocations(organizationId);
    }

    public Branch get(UUID organizationId, UUID id) {
        return branches.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
    }

    @Transactional
    public Branch create(UUID organizationId, String code, Details details) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
        if (branches.existsByOrganizationIdAndCode(organizationId, code)) {
            throw new ConflictException("A branch with code '" + code + "' already exists");
        }
        Branch branch = new Branch(organization, code, details.name(), details.city(), details.state());
        apply(branch, details);
        return branches.saveAndFlush(branch);
    }

    @Transactional
    public Branch update(UUID organizationId, UUID id, Details details, long expectedVersion) {
        Branch branch = get(organizationId, id);
        if (branch.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This branch was changed by someone else. Reload it and apply your changes again.");
        }
        apply(branch, details);
        return branches.saveAndFlush(branch);
    }

    @Transactional
    public Branch setActive(UUID organizationId, UUID id, boolean active) {
        Branch branch = get(organizationId, id);
        if (!active && branch.isHeadOffice()) {
            throw new BusinessRuleException(
                    "The head office cannot be deactivated. Make another branch the head office first.");
        }
        branch.setActive(active);
        return branches.saveAndFlush(branch);
    }

    /** Soft delete. Refused while people are still assigned to the branch or it is the head office. */
    @Transactional
    public void delete(UUID organizationId, UUID id) {
        Branch branch = get(organizationId, id);
        if (branch.isHeadOffice()) {
            throw new BusinessRuleException(
                    "The head office cannot be deleted. Make another branch the head office first.");
        }
        if (users.existsByBranchId(id)) {
            throw new BusinessRuleException(
                    "People are still assigned to this branch. Move them first, or deactivate the branch instead.");
        }
        branch.markDeleted(clock.instant());
    }

    private void apply(Branch branch, Details details) {
        if (details.headOffice() && !branch.isHeadOffice()) {
            if (!branch.isActive()) {
                throw new BusinessRuleException("An inactive branch cannot be the head office");
            }
            // An organization has at most one head office: the title moves, it is never shared.
            branches.findByOrganizationIdAndHeadOfficeTrue(branch.getOrganization().getId())
                    .ifPresent(current -> {
                        current.setHeadOffice(false);
                        branches.saveAndFlush(current);
                    });
        }
        branch.setHeadOffice(details.headOffice());
        branch.setName(details.name());
        branch.setAddressLine1(details.addressLine1());
        branch.setAddressLine2(details.addressLine2());
        branch.setCity(details.city());
        branch.setState(details.state());
        branch.setPostalCode(details.postalCode());
        branch.setCountryCode(details.countryCode());
        branch.setPhone(details.phone());
        branch.setEmail(details.email());
    }
}
