package com.pawanputra.bos.customer.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerNoteRepository extends JpaRepository<CustomerNote, UUID> {

    Page<CustomerNote> findAllByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId, Pageable pageable);

    Optional<CustomerNote> findByIdAndCustomerId(UUID id, UUID customerId);

    /** For the timeline: the newest notes written at or before the given moment. */
    List<CustomerNote> findByCustomerIdAndCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(
            UUID customerId, Instant notAfter, Pageable pageable);

    long countByCustomerId(UUID customerId);
}
