package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.api.AddressType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findAllByCustomerIdOrderByTypeAscDefaultAddressDescCityAsc(UUID customerId);

    Optional<CustomerAddress> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<CustomerAddress> findByCustomerIdAndTypeAndDefaultAddressTrue(UUID customerId, AddressType type);

    long countByCustomerId(UUID customerId);

    long countByCustomerIdAndType(UUID customerId, AddressType type);
}
