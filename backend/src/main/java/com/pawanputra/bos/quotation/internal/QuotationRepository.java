package com.pawanputra.bos.quotation.internal;

import com.pawanputra.bos.quotation.api.QuotationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<Quotation, UUID>, JpaSpecificationExecutor<Quotation> {

    Optional<Quotation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndQuotationNumber(UUID organizationId, String quotationNumber);

    @Query("SELECT COUNT(q) FROM Quotation q WHERE q.organizationId = :orgId AND q.deletedAt IS NULL")
    long countByOrganizationId(@Param("orgId") UUID orgId);

    @Query("SELECT q FROM Quotation q WHERE q.organizationId = :orgId AND q.customerId = :customerId AND q.deletedAt IS NULL ORDER BY q.createdAt DESC")
    List<Quotation> findByOrganizationIdAndCustomerId(@Param("orgId") UUID orgId, @Param("customerId") UUID customerId);

    @Query("SELECT COUNT(q) FROM Quotation q WHERE q.organizationId = :orgId AND q.status = :status AND q.deletedAt IS NULL")
    long countByOrganizationIdAndStatus(@Param("orgId") UUID orgId, @Param("status") QuotationStatus status);

    @Query("SELECT COUNT(q) FROM Quotation q WHERE q.organizationId = :orgId AND q.nextFollowUpDate = :date AND q.deletedAt IS NULL")
    long countByOrganizationIdAndNextFollowUpDate(@Param("orgId") UUID orgId, @Param("date") LocalDate date);

    @Query("SELECT q FROM Quotation q WHERE q.organizationId = :orgId AND q.nextFollowUpDate = :date AND q.deletedAt IS NULL ORDER BY q.updatedAt DESC")
    List<Quotation> findByOrganizationIdAndNextFollowUpDate(@Param("orgId") UUID orgId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(q.finalAmount), 0) FROM Quotation q WHERE q.organizationId = :orgId AND q.deletedAt IS NULL")
    java.math.BigDecimal sumFinalAmountByOrganizationId(@Param("orgId") UUID orgId);
}
