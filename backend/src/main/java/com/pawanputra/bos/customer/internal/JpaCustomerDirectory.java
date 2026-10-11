package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.api.AddressType;
import com.pawanputra.bos.customer.api.CustomerDirectory;
import com.pawanputra.bos.customer.api.CustomerStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaCustomerDirectory implements CustomerDirectory {

    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;

    JpaCustomerDirectory(CustomerRepository customers, CustomerAddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    @Override
    public Optional<CustomerSummary> find(UUID organizationId, UUID customerId) {
        return customers.findByIdAndOrganizationId(customerId, organizationId).map(customer -> new CustomerSummary(
                customer.getId(), customer.getCustomerNumber(), customer.getDisplayName(), customer.getTaxId(),
                customer.getEmail(), customer.getPhone(),
                addresses.findByCustomerIdAndTypeAndDefaultAddressTrue(customerId, AddressType.BILLING)
                        .map(JpaCustomerDirectory::lines).orElse(null),
                customer.getStatus() == CustomerStatus.BLOCKED));
    }

    /** "12 MG Road, Hazratganj" over "Lucknow, Uttar Pradesh 226001". */
    private static String lines(CustomerAddress address) {
        String street = join(", ", address.getLine1(), address.getLine2());
        String region = join(" ", join(", ", address.getCity(), address.getState()), address.getPostalCode());
        return join("\n", street, region);
    }

    private static String join(String separator, String... parts) {
        String joined = Stream.of(parts).filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }
}
