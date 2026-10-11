package com.pawanputra.bos.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ServiceOfferingRepository
        extends JpaRepository<ServiceOffering, UUID>, JpaSpecificationExecutor<ServiceOffering> {

    /** Loads each service with its category and vertical in the same query (no N+1 on list screens). */
    @Override
    @EntityGraph(attributePaths = {"category", "category.vertical"})
    Page<ServiceOffering> findAll(Specification<ServiceOffering> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "category.vertical"})
    Optional<ServiceOffering> findWithCategoryById(UUID id);

    @EntityGraph(attributePaths = {"category", "category.vertical"})
    List<ServiceOffering> findAllWithCategoryByIdIn(Collection<UUID> ids);

    Optional<ServiceOffering> findByCode(String code);

    List<ServiceOffering> findAllByCategoryIdOrderByDisplayOrderAscNameAsc(UUID categoryId);

    boolean existsByCode(String code);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByCategoryIdAndNameIgnoreCase(UUID categoryId, String name);

    boolean existsByCategoryIdAndNameIgnoreCaseAndIdNot(UUID categoryId, String name, UUID id);
}
