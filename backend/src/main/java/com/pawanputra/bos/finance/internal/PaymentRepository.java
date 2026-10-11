package com.pawanputra.bos.finance.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** The payment, locked until the transaction ends. Taken before any invoice lock, always in that order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.organizationId = :organizationId")
    Optional<Payment> lockByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    /** A customer's payments, most recently recorded first. For the customer timeline. */
    List<Payment> findAllByOrganizationIdAndCustomerIdAndCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(
            UUID organizationId, UUID customerId, Instant notAfter, Pageable pageable);
}
