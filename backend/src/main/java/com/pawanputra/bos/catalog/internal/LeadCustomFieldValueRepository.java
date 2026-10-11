package com.pawanputra.bos.catalog.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LeadCustomFieldValueRepository extends JpaRepository<LeadCustomFieldValue, UUID> {

    /** Every stored answer of the lead, including answers to fields that have since been removed. */
    List<LeadCustomFieldValue> findAllByLeadId(UUID leadId);

    /** Answers to fields that still exist (a removed field drops out through the join), in form order. */
    @Query("select v from LeadCustomFieldValue v join fetch v.field f "
            + "where v.leadId = :leadId order by f.displayOrder, f.label")
    List<LeadCustomFieldValue> findAllOfLead(UUID leadId);

    @Modifying
    @Query("delete from LeadCustomFieldValue v where v.leadId = :leadId")
    void deleteAllOfLead(UUID leadId);
}
