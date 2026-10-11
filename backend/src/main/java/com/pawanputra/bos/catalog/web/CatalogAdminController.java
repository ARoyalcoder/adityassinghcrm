package com.pawanputra.bos.catalog.web;

import com.pawanputra.bos.catalog.api.BillingType;
import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.catalog.internal.ServiceCategoryService;
import com.pawanputra.bos.catalog.internal.ServiceOfferingService;
import com.pawanputra.bos.catalog.internal.ServiceVerticalService;
import com.pawanputra.bos.catalog.web.CatalogDtos.CategoryResponse;
import com.pawanputra.bos.catalog.web.CatalogDtos.CreateCategoryRequest;
import com.pawanputra.bos.catalog.web.CatalogDtos.CreateServiceRequest;
import com.pawanputra.bos.catalog.web.CatalogDtos.ServiceResponse;
import com.pawanputra.bos.catalog.web.CatalogDtos.UpdateCategoryRequest;
import com.pawanputra.bos.catalog.web.CatalogDtos.UpdateServiceRequest;
import com.pawanputra.bos.catalog.web.CatalogDtos.UpdateVerticalRequest;
import com.pawanputra.bos.catalog.web.CatalogDtos.VerticalAdminResponse;
import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration of the service catalog: verticals, categories, services.
 *
 * <p>This controller only translates HTTP to service calls and back. It contains no rule about any
 * particular vertical or service; such differences are data on the service (see {@code ServiceOffering}).
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/catalog")
@Tag(name = "Catalog administration")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class CatalogAdminController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.CATALOG_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.CATALOG_CREATE + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.CATALOG_UPDATE + "')";
    private static final String CAN_DELETE = "hasAuthority('" + Permissions.CATALOG_DELETE + "')";

    private static final Map<String, String> CATEGORY_SORTS = Map.of(
            "name", "name",
            "code", "code",
            "displayOrder", "displayOrder",
            "vertical", "vertical.displayOrder",
            "updatedAt", "updatedAt");
    private static final Sort CATEGORY_DEFAULT_SORT = Sort.by("vertical.displayOrder", "displayOrder", "name", "id");

    private static final Map<String, String> SERVICE_SORTS = Map.of(
            "name", "name",
            "code", "code",
            "displayOrder", "displayOrder",
            "basePrice", "basePrice",
            "category", "category.name",
            "vertical", "category.vertical.displayOrder",
            "updatedAt", "updatedAt");
    private static final Sort SERVICE_DEFAULT_SORT =
            Sort.by("category.vertical.displayOrder", "category.name", "displayOrder", "name", "id");

    private final ServiceVerticalService verticals;
    private final ServiceCategoryService categories;
    private final ServiceOfferingService services;

    public CatalogAdminController(
            ServiceVerticalService verticals, ServiceCategoryService categories, ServiceOfferingService services) {
        this.verticals = verticals;
        this.categories = categories;
        this.services = services;
    }

    // ------------------------------------------------------------------ verticals (fixed set: no create, no delete)

    @GetMapping("/verticals")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "All six verticals, including inactive ones. Requires CATALOG_VIEW.")
    public List<VerticalAdminResponse> listVerticals() {
        return verticals.listAll().stream().map(VerticalAdminResponse::from).toList();
    }

    @GetMapping("/verticals/{code}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "One vertical by code. Requires CATALOG_VIEW.")
    public VerticalAdminResponse getVertical(@PathVariable String code) {
        return VerticalAdminResponse.from(verticals.getByCode(code));
    }

    @PutMapping("/verticals/{code}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Change a vertical's name, description and display order. Requires CATALOG_UPDATE.")
    public VerticalAdminResponse updateVertical(
            @PathVariable String code, @Valid @RequestBody UpdateVerticalRequest request) {
        return VerticalAdminResponse.from(verticals.update(
                code, request.name().trim(), blankToNull(request.description()), request.displayOrder(),
                request.version()));
    }

    @PostMapping("/verticals/{code}/activate")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Switch a vertical on. Requires CATALOG_UPDATE.")
    public VerticalAdminResponse activateVertical(@PathVariable String code) {
        return VerticalAdminResponse.from(verticals.setActive(code, true));
    }

    @PostMapping("/verticals/{code}/deactivate")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Switch a vertical off. Its categories and services are kept. Requires CATALOG_UPDATE.")
    public VerticalAdminResponse deactivateVertical(@PathVariable String code) {
        return VerticalAdminResponse.from(verticals.setActive(code, false));
    }

    // ------------------------------------------------------------------ categories

    @GetMapping("/categories")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Search categories. Filters: search (name or code), vertical, active. "
            + "Sort: name, code, displayOrder, vertical, updatedAt. Requires CATALOG_VIEW.")
    public PageResponse<CategoryResponse> listCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ServiceVerticalCode vertical,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                categories.search(
                        new ServiceCategoryService.Filter(search, vertical, active),
                        Paging.of(page, size, sort, CATEGORY_SORTS, CATEGORY_DEFAULT_SORT)),
                CategoryResponse::from);
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "One category. Requires CATALOG_VIEW.")
    public CategoryResponse getCategory(@PathVariable UUID id) {
        return CategoryResponse.from(categories.get(id));
    }

    @PostMapping("/categories")
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create a category in one of the six verticals. Requires CATALOG_CREATE.")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse created = CategoryResponse.from(
                categories.create(request.vertical(), request.code(), request.details()));
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/catalog/categories/" + created.id())).body(created);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Edit a category. Requires CATALOG_UPDATE.")
    public CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
        return CategoryResponse.from(categories.update(id, request.details(), request.version()));
    }

    @PostMapping("/categories/{id}/activate")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Switch a category on. Requires CATALOG_UPDATE.")
    public CategoryResponse activateCategory(@PathVariable UUID id) {
        return CategoryResponse.from(categories.setActive(id, true));
    }

    @PostMapping("/categories/{id}/deactivate")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Switch a category off. Requires CATALOG_UPDATE.")
    public CategoryResponse deactivateCategory(@PathVariable UUID id) {
        return CategoryResponse.from(categories.setActive(id, false));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Delete an empty category. Requires CATALOG_DELETE.")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categories.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ services

    @GetMapping("/services")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Search services. Filters: search (name or code), vertical, categoryId, active, billingType. "
            + "Sort: name, code, displayOrder, basePrice, category, vertical, updatedAt. Requires CATALOG_VIEW.")
    public PageResponse<ServiceResponse> listServices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ServiceVerticalCode vertical,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) BillingType billingType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                services.search(
                        new ServiceOfferingService.Filter(search, vertical, categoryId, active, billingType),
                        Paging.of(page, size, sort, SERVICE_SORTS, SERVICE_DEFAULT_SORT)),
                ServiceResponse::from);
    }

    @GetMapping("/services/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "One service. Requires CATALOG_VIEW.")
    public ServiceResponse getService(@PathVariable UUID id) {
        return ServiceResponse.from(services.get(id));
    }

    @PostMapping("/services")
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create a service in a category. Requires CATALOG_CREATE.")
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse created = ServiceResponse.from(services.create(request.code(), request.details()));
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/catalog/services/" + created.id())).body(created);
    }

    @PutMapping("/services/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Edit a service, including moving it to another category. Requires CATALOG_UPDATE.")
    public ServiceResponse updateService(@PathVariable UUID id, @Valid @RequestBody UpdateServiceRequest request) {
        return ServiceResponse.from(services.update(id, request.details(), request.version()));
    }

    @PostMapping("/services/{id}/activate")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Switch a service on. Requires CATALOG_UPDATE.")
    public ServiceResponse activateService(@PathVariable UUID id) {
        return ServiceResponse.from(services.setActive(id, true));
    }

    @PostMapping("/services/{id}/deactivate")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Switch a service off. Requires CATALOG_UPDATE.")
    public ServiceResponse deactivateService(@PathVariable UUID id) {
        return ServiceResponse.from(services.setActive(id, false));
    }

    @DeleteMapping("/services/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Delete a service (kept for history, its code becomes reusable). Requires CATALOG_DELETE.")
    public ResponseEntity<Void> deleteService(@PathVariable UUID id) {
        services.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
