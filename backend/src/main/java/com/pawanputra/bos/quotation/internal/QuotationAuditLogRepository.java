package com.pawanputra.bos.quotation.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationAuditLogRepository extends JpaRepository<QuotationAuditLog, UUID> {

    List<QuotationAuditLog> findByQuotationIdOrderByPerformedAtDesc(UUID quotationId);
}
