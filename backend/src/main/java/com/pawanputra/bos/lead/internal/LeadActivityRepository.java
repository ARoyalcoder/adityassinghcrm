package com.pawanputra.bos.lead.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    List<LeadActivity> findTop50ByLeadIdOrderByOccurredAtDescIdDesc(UUID leadId);

    long countByLeadId(UUID leadId);

    @Query("select max(a.occurredAt) from LeadActivity a where a.leadId = :leadId")
    Instant lastActivityAt(UUID leadId);
}
