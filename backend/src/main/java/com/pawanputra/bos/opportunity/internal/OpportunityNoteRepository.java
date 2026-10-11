package com.pawanputra.bos.opportunity.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityNoteRepository extends JpaRepository<OpportunityNote, UUID> {

    List<OpportunityNote> findByOpportunityIdOrderByCreatedAtDesc(UUID opportunityId);
}
