package com.pawanputra.bos.opportunity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunityStageHistoryRepository extends JpaRepository<OpportunityStageHistory, UUID> {

    List<OpportunityStageHistory> findByOpportunityIdOrderByEnteredAtDesc(UUID opportunityId);

    Optional<OpportunityStageHistory> findTopByOpportunityIdAndExitedAtIsNullOrderByEnteredAtDesc(UUID opportunityId);

    @Query("SELECT h.toStage.id, h.toStageName, AVG(h.durationSeconds) FROM OpportunityStageHistory h "
            + "WHERE h.opportunity.organizationId = :orgId AND h.durationSeconds IS NOT NULL "
            + "GROUP BY h.toStage.id, h.toStageName")
    List<Object[]> findAverageDurationPerStage(@Param("orgId") UUID orgId);
}
