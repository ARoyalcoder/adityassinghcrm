package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.identity.api.OrganizationDirectory;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaOrganizationDirectory implements OrganizationDirectory {

    private final OrganizationRepository organizations;
    private final BranchRepository branches;

    JpaOrganizationDirectory(OrganizationRepository organizations, BranchRepository branches) {
        this.organizations = organizations;
        this.branches = branches;
    }

    @Override
    public Optional<Letterhead> letterhead(UUID organizationId) {
        return organizations.findById(organizationId).map(organization -> {
            Optional<Branch> headOffice = branches.findByOrganizationIdAndHeadOfficeTrue(organizationId);
            return new Letterhead(
                    organization.getLegalName() != null ? organization.getLegalName() : organization.getName(),
                    organization.getTaxId(),
                    organization.getEmail() != null ? organization.getEmail() : headOffice.map(Branch::getEmail).orElse(null),
                    organization.getPhone() != null ? organization.getPhone() : headOffice.map(Branch::getPhone).orElse(null),
                    headOffice.map(JpaOrganizationDirectory::lines).orElse(null));
        });
    }

    private static String lines(Branch branch) {
        String street = join(", ", branch.getAddressLine1(), branch.getAddressLine2());
        String region = join(" ", join(", ", branch.getCity(), branch.getState()), branch.getPostalCode());
        return join("\n", street, region);
    }

    private static String join(String separator, String... parts) {
        String joined = Stream.of(parts).filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }
}
