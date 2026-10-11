package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.lead.api.LeadStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    /** Always scope by organization: a lead of another organization must look like it does not exist. */
    Optional<Lead> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(value = "select nextval('lead_number_seq')", nativeQuery = true)
    long nextLeadNumber();

    /** Leads of every organization with the given status, in a stable order, for the rescoring job. */
    Slice<Lead> findAllByStatusOrderById(LeadStatus status, Pageable pageable);

    Slice<Lead> findAllByOrganizationIdAndStatusOrderById(UUID organizationId, LeadStatus status, Pageable pageable);

    /**
     * Stores an assessment. A direct update on purpose: it must not touch the version or the
     * "last changed" stamp, which belong to what users do to the lead.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "update leads set score = :score, priority = :priority, health = :health, "
            + "qualification_status = :qualificationStatus, scored_at = :scoredAt, score_engine = :engine "
            + "where id = :id", nativeQuery = true)
    void storeAssessment(
            UUID id, int score, String priority, String health, String qualificationStatus, Instant scoredAt,
            String engine);
}
