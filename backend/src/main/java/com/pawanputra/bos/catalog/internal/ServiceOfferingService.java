package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.BillingType;
import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.persistence.Specs;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing the services the company sells. The rules here apply to every service alike; nothing in
 * this class (or anywhere else) may depend on which vertical or which service it is handling.
 */
@Service
@Transactional(readOnly = true)
public class ServiceOfferingService {

    private final ServiceOfferingRepository services;
    private final ServiceCategoryRepository categories;
    private final Clock clock;

    public ServiceOfferingService(
            ServiceOfferingRepository services, ServiceCategoryRepository categories, Clock clock) {
        this.services = services;
        this.categories = categories;
        this.clock = clock;
    }

    /** Every filter is optional; {@code null} means "any". */
    public record Filter(
            String search, ServiceVerticalCode vertical, UUID categoryId, Boolean active, BillingType billingType) {
    }

    /** The fields an administrator may set on create and on edit. The code is set on creation only. */
    public record Details(
            UUID categoryId,
            String name,
            String description,
            int displayOrder,
            BillingType billingType,
            String unitLabel,
            BigDecimal basePrice,
            boolean requiresSiteVisit,
            Integer estimatedDurationDays) {
    }

    public Page<ServiceOffering> search(Filter filter, Pageable pageable) {
        Specification<ServiceOffering> spec = Specs.<ServiceOffering>search(filter.search(), "name", "code")
                .and(Specs.equalTo("category.vertical.code", filter.vertical()))
                .and(Specs.equalTo("category.id", filter.categoryId()))
                .and(Specs.equalTo("active", filter.active()))
                .and(Specs.equalTo("billingType", filter.billingType()));
        return services.findAll(spec, pageable);
    }

    public ServiceOffering get(UUID id) {
        return services.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));
    }

    @Transactional
    public ServiceOffering create(String code, Details details) {
        ServiceCategory category = requireCategory(details.categoryId());
        if (services.existsByCode(code)) {
            throw new ConflictException("A service with code '" + code + "' already exists");
        }
        if (services.existsByCategoryIdAndNameIgnoreCase(category.getId(), details.name())) {
            throw nameTaken(category, details.name());
        }
        validate(details);
        ServiceOffering service = new ServiceOffering(category, code, details.name());
        apply(service, details);
        return services.saveAndFlush(service);
    }

    @Transactional
    public ServiceOffering update(UUID id, Details details, long expectedVersion) {
        ServiceOffering service = get(id);
        if (service.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This service was changed by someone else. Reload it and apply your changes again.");
        }
        ServiceCategory category = service.getCategory().getId().equals(details.categoryId())
                ? service.getCategory()
                : requireCategory(details.categoryId());
        if (services.existsByCategoryIdAndNameIgnoreCaseAndIdNot(category.getId(), details.name(), id)) {
            throw nameTaken(category, details.name());
        }
        validate(details);
        service.setCategory(category);
        service.setName(details.name());
        apply(service, details);
        return services.saveAndFlush(service);
    }

    @Transactional
    public ServiceOffering setActive(UUID id, boolean active) {
        ServiceOffering service = get(id);
        service.setActive(active);
        return services.saveAndFlush(service);
    }

    /** Soft delete: the row stays for history, and the code becomes available again. */
    @Transactional
    public void delete(UUID id) {
        get(id).markDeleted(clock.instant());
    }

    private ServiceCategory requireCategory(UUID categoryId) {
        // Reported as a rule violation, not 404: the URL is fine, the chosen category is not.
        return categories.findWithVerticalById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("The selected category does not exist"));
    }

    /** Rules that involve more than one field (single-field rules are on the request DTO). */
    private static void validate(Details details) {
        if (details.billingType() != BillingType.QUOTE_BASED && details.basePrice() == null) {
            throw new BusinessRuleException(
                    "A base price is required unless the service is quote-based");
        }
    }

    private static void apply(ServiceOffering service, Details details) {
        service.setDescription(details.description());
        service.setDisplayOrder(details.displayOrder());
        service.setBillingType(details.billingType());
        service.setUnitLabel(details.unitLabel());
        service.setBasePrice(details.basePrice());
        service.setRequiresSiteVisit(details.requiresSiteVisit());
        service.setEstimatedDurationDays(details.estimatedDurationDays());
    }

    private static ConflictException nameTaken(ServiceCategory category, String name) {
        return new ConflictException(
                "The category '%s' already has a service called '%s'".formatted(category.getName(), name));
    }
}
