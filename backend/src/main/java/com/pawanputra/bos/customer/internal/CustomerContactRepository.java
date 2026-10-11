package com.pawanputra.bos.customer.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerContactRepository extends JpaRepository<CustomerContact, UUID> {

    List<CustomerContact> findAllByCustomerIdOrderByPrimaryContactDescNameAsc(UUID customerId);

    Optional<CustomerContact> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<CustomerContact> findByCustomerIdAndPrimaryContactTrue(UUID customerId);

    long countByCustomerId(UUID customerId);

    // The join (rather than a plain property match) leaves out contacts of deleted customers.

    @Query("select ct from CustomerContact ct join fetch ct.customer c "
            + "where ct.organizationId = :organizationId and ct.phoneKey = :phoneKey")
    List<CustomerContact> findByPhoneKey(
            @Param("organizationId") UUID organizationId, @Param("phoneKey") String phoneKey);

    @Query("select ct from CustomerContact ct join fetch ct.customer c "
            + "where ct.organizationId = :organizationId and ct.email = :email")
    List<CustomerContact> findByEmail(@Param("organizationId") UUID organizationId, @Param("email") String email);
}
