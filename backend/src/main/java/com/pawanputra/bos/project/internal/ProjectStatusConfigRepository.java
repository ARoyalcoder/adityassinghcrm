package com.pawanputra.bos.project.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectStatusConfigRepository extends JpaRepository<ProjectStatusConfig, UUID> {

    List<ProjectStatusConfig> findByOrganizationIdOrderByDisplayOrderAsc(UUID organizationId);

    Optional<ProjectStatusConfig> findByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    Optional<ProjectStatusConfig> findFirstByOrganizationIdAndIsDefaultTrue(UUID organizationId);
}
