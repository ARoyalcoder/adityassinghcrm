package com.pawanputra.bos.lead.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualificationQuestionRepository extends JpaRepository<QualificationQuestion, UUID> {

    List<QualificationQuestion> findAllByOrganizationIdOrderByDisplayOrderAscLabelAsc(UUID organizationId);

    List<QualificationQuestion> findAllByOrganizationIdAndActiveTrueOrderByDisplayOrderAscLabelAsc(UUID organizationId);

    Optional<QualificationQuestion> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndQuestionKey(UUID organizationId, String questionKey);

    long countByOrganizationId(UUID organizationId);
}
