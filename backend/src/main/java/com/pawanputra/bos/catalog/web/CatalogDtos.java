package com.pawanputra.bos.catalog.web;

import com.pawanputra.bos.catalog.api.BillingType;
import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.catalog.internal.ServiceCategory;
import com.pawanputra.bos.catalog.internal.ServiceCategoryService;
import com.pawanputra.bos.catalog.internal.ServiceOffering;
import com.pawanputra.bos.catalog.internal.ServiceOfferingService;
import com.pawanputra.bos.catalog.internal.ServiceVertical;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the catalog administration endpoints. */
final class CatalogDtos {

    static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]*$";
    static final String CODE_MESSAGE =
            "must start with a capital letter and contain only capital letters, digits and underscores";

    private CatalogDtos() {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ------------------------------------------------------------------ verticals

    record VerticalAdminResponse(
            UUID id, ServiceVerticalCode code, String name, String description, int displayOrder, boolean active,
            long version, Instant updatedAt) {

        static VerticalAdminResponse from(ServiceVertical vertical) {
            return new VerticalAdminResponse(
                    vertical.getId(), vertical.getCode(), vertical.getName(), vertical.getDescription(),
                    vertical.getDisplayOrder(), vertical.isActive(), vertical.getVersion(), vertical.getUpdatedAt());
        }
    }

    /** The code is not here on purpose: it identifies the vertical and can never change. */
    record UpdateVerticalRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull @Min(1) @Max(999) Integer displayOrder,
            @NotNull Long version) {
    }

    // ------------------------------------------------------------------ categories

    record VerticalRef(ServiceVerticalCode code, String name) {

        static VerticalRef from(ServiceVertical vertical) {
            return new VerticalRef(vertical.getCode(), vertical.getName());
        }
    }

    record CategoryResponse(
            UUID id, String code, String name, String description, int displayOrder, boolean active,
            VerticalRef vertical, long version, Instant updatedAt) {

        static CategoryResponse from(ServiceCategory category) {
            return new CategoryResponse(
                    category.getId(), category.getCode(), category.getName(), category.getDescription(),
                    category.getDisplayOrder(), category.isActive(), VerticalRef.from(category.getVertical()),
                    category.getVersion(), category.getUpdatedAt());
        }
    }

    record CreateCategoryRequest(
            @NotBlank String vertical,
            @NotBlank @Size(max = 60) @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            @Min(0) @Max(9999) Integer displayOrder) {

        ServiceCategoryService.Details details() {
            return new ServiceCategoryService.Details(
                    name.trim(), trimToNull(description), displayOrder == null ? 0 : displayOrder);
        }
    }

    /** A category stays in its vertical and keeps its code; only the fields below can change. */
    record UpdateCategoryRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            @Min(0) @Max(9999) Integer displayOrder,
            @NotNull Long version) {

        ServiceCategoryService.Details details() {
            return new ServiceCategoryService.Details(
                    name.trim(), trimToNull(description), displayOrder == null ? 0 : displayOrder);
        }
    }

    // ------------------------------------------------------------------ services

    record CategoryRef(UUID id, String code, String name) {
    }

    record ServiceResponse(
            UUID id,
            String code,
            String name,
            String description,
            int displayOrder,
            boolean active,
            BillingType billingType,
            String unitLabel,
            BigDecimal basePrice,
            Boolean requiresSiteVisit,
            Integer estimatedDurationDays,
            CategoryRef category,
            VerticalRef vertical,
            long version,
            Instant updatedAt) {

        static ServiceResponse from(ServiceOffering service) {
            ServiceCategory category = service.getCategory();
            return new ServiceResponse(
                    service.getId(), service.getCode(), service.getName(), service.getDescription(),
                    service.getDisplayOrder(), service.isActive(), service.getBillingType(), service.getUnitLabel(),
                    service.getBasePrice(), service.isRequiresSiteVisit(), service.getEstimatedDurationDays(),
                    new CategoryRef(category.getId(), category.getCode(), category.getName()),
                    VerticalRef.from(category.getVertical()),
                    service.getVersion(), service.getUpdatedAt());
        }
    }

    record CreateServiceRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE) String code,
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Min(0) @Max(9999) Integer displayOrder,
            @NotNull BillingType billingType,
            @Size(max = 50) String unitLabel,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            Boolean requiresSiteVisit,
            @Min(1) @Max(3650) Integer estimatedDurationDays) {

        ServiceOfferingService.Details details() {
            return new ServiceOfferingService.Details(
                    categoryId, name.trim(), trimToNull(description), displayOrder == null ? 0 : displayOrder,
                    billingType, trimToNull(unitLabel), basePrice, Boolean.TRUE.equals(requiresSiteVisit),
                    estimatedDurationDays);
        }
    }

    /** Same as create without the code (which never changes) and with the version being edited. */
    record UpdateServiceRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Min(0) @Max(9999) Integer displayOrder,
            @NotNull BillingType billingType,
            @Size(max = 50) String unitLabel,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            Boolean requiresSiteVisit,
            @Min(1) @Max(3650) Integer estimatedDurationDays,
            @NotNull Long version) {

        ServiceOfferingService.Details details() {
            return new ServiceOfferingService.Details(
                    categoryId, name.trim(), trimToNull(description), displayOrder == null ? 0 : displayOrder,
                    billingType, trimToNull(unitLabel), basePrice, Boolean.TRUE.equals(requiresSiteVisit),
                    estimatedDurationDays);
        }
    }
}
