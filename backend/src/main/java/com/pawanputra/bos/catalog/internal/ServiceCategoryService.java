package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.persistence.Specs;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceCategoryService {

    private final ServiceCategoryRepository categories;
    private final ServiceOfferingRepository services;
    private final ServiceVerticalService verticals;
    private final Clock clock;

    public ServiceCategoryService(
            ServiceCategoryRepository categories,
            ServiceOfferingRepository services,
            ServiceVerticalService verticals,
            Clock clock) {
        this.categories = categories;
        this.services = services;
        this.verticals = verticals;
        this.clock = clock;
    }

    /** Every filter is optional; {@code null} means "any". */
    public record Filter(String search, ServiceVerticalCode vertical, Boolean active) {
    }

    /** The fields an administrator may set. {@code code} and {@code vertical} are used on creation only. */
    public record Details(String name, String description, int displayOrder) {
    }

    public Page<ServiceCategory> search(Filter filter, Pageable pageable) {
        Specification<ServiceCategory> spec = Specs.<ServiceCategory>search(filter.search(), "name", "code")
                .and(Specs.equalTo("vertical.code", filter.vertical()))
                .and(Specs.equalTo("active", filter.active()));
        return categories.findAll(spec, pageable);
    }

    public ServiceCategory get(UUID id) {
        return categories.findWithVerticalById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service category", id));
    }

    @Transactional
    public ServiceCategory create(String verticalCode, String code, Details details) {
        ServiceVertical vertical = verticals.getByCode(verticalCode);
        if (categories.existsByVerticalIdAndCode(vertical.getId(), code)) {
            throw new ConflictException(
                    "%s already has a category with code '%s'".formatted(vertical.getName(), code));
        }
        if (categories.existsByVerticalIdAndNameIgnoreCase(vertical.getId(), details.name())) {
            throw nameTaken(vertical, details.name());
        }
        ServiceCategory category = new ServiceCategory(vertical, code, details.name());
        category.setDescription(details.description());
        category.setDisplayOrder(details.displayOrder());
        return categories.saveAndFlush(category);
    }

    @Transactional
    public ServiceCategory update(UUID id, Details details, long expectedVersion) {
        ServiceCategory category = get(id);
        requireVersion(category, expectedVersion);
        ServiceVertical vertical = category.getVertical();
        if (categories.existsByVerticalIdAndNameIgnoreCaseAndIdNot(vertical.getId(), details.name(), id)) {
            throw nameTaken(vertical, details.name());
        }
        category.setName(details.name());
        category.setDescription(details.description());
        category.setDisplayOrder(details.displayOrder());
        return categories.saveAndFlush(category);
    }

    /** Switching a category off does not change its services; they can be switched off separately. */
    @Transactional
    public ServiceCategory setActive(UUID id, boolean active) {
        ServiceCategory category = get(id);
        category.setActive(active);
        return categories.saveAndFlush(category);
    }

    /** A category can only be deleted once it is empty, so no service is ever left without one. */
    @Transactional
    public void delete(UUID id) {
        ServiceCategory category = get(id);
        if (services.existsByCategoryId(id)) {
            throw new BusinessRuleException(
                    "This category still has services. Move or delete them first, or deactivate the category instead.");
        }
        category.markDeleted(clock.instant());
    }

    private static void requireVersion(ServiceCategory category, long expectedVersion) {
        if (category.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This category was changed by someone else. Reload it and apply your changes again.");
        }
    }

    private static ConflictException nameTaken(ServiceVertical vertical, String name) {
        return new ConflictException("%s already has a category called '%s'".formatted(vertical.getName(), name));
    }
}
