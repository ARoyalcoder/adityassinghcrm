package com.pawanputra.bos.customer.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInteractionRepository extends JpaRepository<CustomerInteraction, UUID> {

    Page<CustomerInteraction> findAllByCustomerIdOrderByOccurredAtDescIdDesc(UUID customerId, Pageable pageable);

    Optional<CustomerInteraction> findByIdAndCustomerId(UUID id, UUID customerId);

    /** For the timeline: the newest interactions that happened at or before the given moment. */
    List<CustomerInteraction> findByCustomerIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
            UUID customerId, Instant notAfter, Pageable pageable);

    /** The most recent contact with the customer, if there has been any. */
    Optional<CustomerInteraction> findFirstByCustomerIdOrderByOccurredAtDescIdDesc(UUID customerId);

    long countByCustomerId(UUID customerId);
}
