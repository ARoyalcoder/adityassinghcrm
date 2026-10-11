package com.pawanputra.bos.customer.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    /** Always scope by organization: a customer of another organization must look like it does not exist. */
    Optional<Customer> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(value = "select nextval('customer_number_seq')", nativeQuery = true)
    long nextCustomerNumber();
}
