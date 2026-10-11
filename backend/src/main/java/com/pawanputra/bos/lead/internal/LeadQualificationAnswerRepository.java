package com.pawanputra.bos.lead.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadQualificationAnswerRepository extends JpaRepository<LeadQualificationAnswer, UUID> {

    List<LeadQualificationAnswer> findAllByLeadId(UUID leadId);
}
