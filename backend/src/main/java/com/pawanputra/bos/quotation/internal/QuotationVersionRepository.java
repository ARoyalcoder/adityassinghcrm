package com.pawanputra.bos.quotation.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationVersionRepository extends JpaRepository<QuotationVersion, UUID> {

    List<QuotationVersion> findByQuotationIdOrderByVersionNumberDesc(UUID quotationId);

    Optional<QuotationVersion> findByQuotationIdAndVersionNumber(UUID quotationId, int versionNumber);

    @Query("SELECT MAX(v.versionNumber) FROM QuotationVersion v WHERE v.quotation.id = :quotationId")
    Integer findMaxVersionNumber(@Param("quotationId") UUID quotationId);
}
