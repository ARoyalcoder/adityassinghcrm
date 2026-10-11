package com.pawanputra.bos.finance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

    Optional<PaymentAllocation> findByIdAndPaymentId(UUID id, UUID paymentId);

    boolean existsByReversesId(UUID reversesId);

    /** A payment's allocations that have not been released, in the order their invoices must be locked. */
    @Query("select a from PaymentAllocation a where a.paymentId = :paymentId and a.amount > 0 "
            + "and not exists (select 1 from PaymentAllocation r where r.reversesId = a.id) order by a.invoiceId")
    List<PaymentAllocation> findActiveByPaymentId(@Param("paymentId") UUID paymentId);
}
