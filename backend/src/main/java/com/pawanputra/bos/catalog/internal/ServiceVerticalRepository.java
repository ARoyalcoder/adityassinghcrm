package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately not a {@code JpaRepository}: extending the marker {@link Repository} means there is
 * no delete method and no way to list-save new rows. Verticals can be read and updated, nothing else.
 */
public interface ServiceVerticalRepository extends Repository<ServiceVertical, UUID> {

    List<ServiceVertical> findAllByActiveTrueOrderByDisplayOrderAsc();

    List<ServiceVertical> findAllByOrderByDisplayOrderAsc();

    Optional<ServiceVertical> findByCode(ServiceVerticalCode code);

    boolean existsByNameIgnoreCaseAndCodeNot(String name, ServiceVerticalCode code);

    boolean existsByDisplayOrderAndCodeNot(int displayOrder, ServiceVerticalCode code);

    /** Updates an existing vertical; new instances cannot be constructed. */
    ServiceVertical saveAndFlush(ServiceVertical vertical);
}
