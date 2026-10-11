package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.opportunity.api.OpportunityStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID>, JpaSpecificationExecutor<Opportunity> {

    Optional<Opportunity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Opportunity> findByOrganizationIdAndPipelineId(UUID organizationId, UUID pipelineId);

    List<Opportunity> findByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);

    List<Opportunity> findByOrganizationIdAndLeadId(UUID organizationId, UUID leadId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, OpportunityStatus status);

    @Query("SELECT o FROM Opportunity o WHERE o.organizationId = :orgId AND o.pipeline.id = :pipelineId ORDER BY o.createdAt DESC")
    List<Opportunity> findKanbanOpportunities(@Param("orgId") UUID orgId, @Param("pipelineId") UUID pipelineId);
}
