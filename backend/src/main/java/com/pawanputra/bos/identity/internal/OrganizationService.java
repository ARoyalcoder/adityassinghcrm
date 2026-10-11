package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own organization. There is no way to read or change another one. */
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /** The code is not editable: it identifies the organization. */
    public record Details(String name, String legalName, String taxId, String email, String phone) {
    }

    public Organization get(UUID organizationId) {
        return organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
    }

    @Transactional
    public Organization update(UUID organizationId, Details details, long expectedVersion) {
        Organization organization = get(organizationId);
        if (organization.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "The organization was changed by someone else. Reload it and apply your changes again.");
        }
        organization.setName(details.name());
        organization.setLegalName(details.legalName());
        organization.setTaxId(details.taxId());
        organization.setEmail(details.email());
        organization.setPhone(details.phone());
        return organizations.saveAndFlush(organization);
    }
}
