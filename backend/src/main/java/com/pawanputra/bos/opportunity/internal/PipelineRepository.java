package com.pawanputra.bos.opportunity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {

    List<Pipeline> findByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);

    Optional<Pipeline> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Pipeline> findByOrganizationIdAndDefaultPipelineTrue(UUID organizationId);

    boolean existsByOrganizationId(UUID organizationId);
}
