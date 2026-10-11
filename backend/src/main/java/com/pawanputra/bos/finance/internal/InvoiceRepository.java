package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.finance.api.InvoiceStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    /** Always scope by organization: an invoice of another organization must look like it does not exist. */
    Optional<Invoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * The invoice, locked until the transaction ends. Everything that moves an invoice's amounts
     * takes this lock first, so two payments can never both be told the same money is still owing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.organizationId = :organizationId")
    Optional<Invoice> lockByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    /** A customer's invoices in the given statuses, locked, in a fixed order so two transactions cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.organizationId = :organizationId and i.customerId = :customerId "
            + "and i.status in :statuses order by i.id")
    List<Invoice> lockAllByCustomer(
            @Param("organizationId") UUID organizationId, @Param("customerId") UUID customerId,
            @Param("statuses") Collection<InvoiceStatus> statuses);

    List<Invoice> findAllByOrganizationIdAndCustomerIdAndStatusInOrderByDueDateAscInvoiceNumberAsc(
            UUID organizationId, UUID customerId, Collection<InvoiceStatus> statuses);

    /** A customer's issued invoices, most recently issued first. For the customer timeline. */
    List<Invoice> findAllByOrganizationIdAndCustomerIdAndIssuedAtLessThanEqualOrderByIssuedAtDescIdDesc(
            UUID organizationId, UUID customerId, Instant notAfter, Pageable pageable);
}
