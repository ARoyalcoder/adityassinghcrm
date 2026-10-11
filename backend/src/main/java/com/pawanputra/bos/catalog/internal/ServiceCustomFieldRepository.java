package com.pawanputra.bos.catalog.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCustomFieldRepository extends JpaRepository<ServiceCustomField, UUID> {

    List<ServiceCustomField> findAllByServiceIdOrderByDisplayOrderAscLabelAsc(UUID serviceId);

    List<ServiceCustomField> findAllByServiceIdAndActiveTrueOrderByDisplayOrderAscLabelAsc(UUID serviceId);

    Optional<ServiceCustomField> findByIdAndServiceId(UUID id, UUID serviceId);

    Optional<ServiceCustomField> findByServiceIdAndFieldKey(UUID serviceId, String fieldKey);

    boolean existsByServiceIdAndFieldKey(UUID serviceId, String fieldKey);

    long countByServiceId(UUID serviceId);
}
