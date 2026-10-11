package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ServiceCategoryRepository
        extends JpaRepository<ServiceCategory, UUID>, JpaSpecificationExecutor<ServiceCategory> {

    @Override
    @EntityGraph(attributePaths = "vertical")
    Page<ServiceCategory> findAll(Specification<ServiceCategory> spec, Pageable pageable);

    @EntityGraph(attributePaths = "vertical")
    Optional<ServiceCategory> findWithVerticalById(UUID id);

    List<ServiceCategory> findAllByVerticalCodeOrderByDisplayOrderAscNameAsc(ServiceVerticalCode verticalCode);

    Optional<ServiceCategory> findByVerticalCodeAndCode(ServiceVerticalCode verticalCode, String code);

    boolean existsByVerticalIdAndCode(UUID verticalId, String code);

    boolean existsByVerticalIdAndNameIgnoreCase(UUID verticalId, String name);

    boolean existsByVerticalIdAndNameIgnoreCaseAndIdNot(UUID verticalId, String name, UUID id);
}
