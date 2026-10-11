package com.pawanputra.bos.project.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    Optional<Project> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    boolean existsByOrganizationIdAndProjectNumber(UUID organizationId, String projectNumber);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);

    @Query("SELECT COALESCE(SUM(p.budget), 0) FROM Project p WHERE p.organizationId = :orgId AND p.deletedAt IS NULL AND p.status != 'CANCELLED'")
    BigDecimal sumBudgetByOrganizationId(@Param("orgId") UUID organizationId);

    @Query("SELECT COALESCE(SUM(p.revenue), 0) FROM Project p WHERE p.organizationId = :orgId AND p.deletedAt IS NULL AND p.status != 'CANCELLED'")
    BigDecimal sumRevenueByOrganizationId(@Param("orgId") UUID organizationId);

    List<Project> findByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);

    Optional<Project> findByOrganizationIdAndOrderId(UUID organizationId, UUID orderId);
}
