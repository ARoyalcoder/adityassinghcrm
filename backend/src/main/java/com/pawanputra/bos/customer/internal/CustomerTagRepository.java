package com.pawanputra.bos.customer.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerTagRepository extends JpaRepository<CustomerTag, UUID> {

    List<CustomerTag> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<CustomerTag> findByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);
}
