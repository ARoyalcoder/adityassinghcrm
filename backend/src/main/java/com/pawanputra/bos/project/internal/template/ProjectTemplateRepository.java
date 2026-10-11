package com.pawanputra.bos.project.internal.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, UUID> {

    List<ProjectTemplate> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    /** Always scope by organization: a template of another organization must look like it does not exist. */
    Optional<ProjectTemplate> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<ProjectTemplate> findByOrganizationIdAndServiceId(UUID organizationId, UUID serviceId);

    boolean existsByOrganizationIdAndServiceId(UUID organizationId, UUID serviceId);
}
