package com.pawanputra.bos.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {

    List<Branch> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<Branch> findByOrganizationIdAndCode(UUID organizationId, String code);

    /** Always scope by organization: a branch id from another organization must look like it does not exist. */
    Optional<Branch> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    Optional<Branch> findByOrganizationIdAndHeadOfficeTrue(UUID organizationId);

    /** One row per distinct place the organization has a branch in, for the location filters. */
    @Query("select distinct new com.pawanputra.bos.identity.internal.BranchLocation(b.state, b.city) "
            + "from Branch b where b.organization.id = :organizationId order by b.state, b.city")
    List<BranchLocation> findLocations(@Param("organizationId") UUID organizationId);
}
