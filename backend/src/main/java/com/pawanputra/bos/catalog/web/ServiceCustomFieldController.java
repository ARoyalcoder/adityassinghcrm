package com.pawanputra.bos.catalog.web;

import com.pawanputra.bos.catalog.api.LeadCustomFields;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FileReference;
import com.pawanputra.bos.catalog.internal.ServiceCustomField;
import com.pawanputra.bos.catalog.internal.ServiceCustomFieldService;
import com.pawanputra.bos.catalog.web.CustomFieldDtos.CreateFieldRequest;
import com.pawanputra.bos.catalog.web.CustomFieldDtos.FieldResponse;
import com.pawanputra.bos.catalog.web.CustomFieldDtos.UpdateFieldRequest;
import com.pawanputra.bos.catalog.web.CustomFieldDtos.ValidateRequest;
import com.pawanputra.bos.catalog.web.CustomFieldDtos.ValidateResponse;
import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * The custom lead fields of a service: administrators define them, and any form that collects a
 * lead reads them to know what to ask. Nothing here names a field; they are all data.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/catalog/services/{serviceId}/custom-fields")
@Tag(name = "Service custom fields")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ServiceCustomFieldController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.CATALOG_VIEW + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.CATALOG_UPDATE + "')";

    private final ServiceCustomFieldService fields;
    private final LeadCustomFields leadFields;

    public ServiceCustomFieldController(ServiceCustomFieldService fields, LeadCustomFields leadFields) {
        this.fields = fields;
        this.leadFields = leadFields;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The service's custom fields in display order. activeOnly=true gives the fields a lead "
            + "form should show. Requires CATALOG_VIEW.")
    public List<FieldResponse> list(
            @PathVariable UUID serviceId, @RequestParam(required = false) Boolean activeOnly) {
        return fields.list(serviceId, Boolean.TRUE.equals(activeOnly)).stream().map(this::response).toList();
    }

    @GetMapping("/{fieldId}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "One field. Requires CATALOG_VIEW.")
    public FieldResponse get(@PathVariable UUID serviceId, @PathVariable UUID fieldId) {
        return response(fields.get(serviceId, fieldId));
    }

    @PostMapping
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add a field to the service. The key and type cannot be changed later. "
            + "Requires CATALOG_UPDATE.")
    public ResponseEntity<FieldResponse> create(
            @PathVariable UUID serviceId, @Valid @RequestBody CreateFieldRequest request) {
        FieldResponse created = response(fields.create(serviceId, request.key(), request.type(), request.details()));
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/catalog/services/" + serviceId + "/custom-fields/"
                + created.field().id())).body(created);
    }

    @PutMapping("/{fieldId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Edit a field. Requires CATALOG_UPDATE.")
    public FieldResponse update(
            @PathVariable UUID serviceId, @PathVariable UUID fieldId, @Valid @RequestBody UpdateFieldRequest request) {
        return response(fields.update(serviceId, fieldId, request.details(), request.version()));
    }

    @PostMapping("/{fieldId}/activate")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Switch a field on. Requires CATALOG_UPDATE.")
    public FieldResponse activate(@PathVariable UUID serviceId, @PathVariable UUID fieldId) {
        return response(fields.setActive(serviceId, fieldId, true));
    }

    @PostMapping("/{fieldId}/deactivate")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Switch a field off: it is no longer asked; answers already given are kept. "
            + "Requires CATALOG_UPDATE.")
    public FieldResponse deactivate(@PathVariable UUID serviceId, @PathVariable UUID fieldId) {
        return response(fields.setActive(serviceId, fieldId, false));
    }

    @DeleteMapping("/{fieldId}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Remove a field (its key becomes reusable). Requires CATALOG_UPDATE.")
    public ResponseEntity<Void> delete(@PathVariable UUID serviceId, @PathVariable UUID fieldId) {
        fields.delete(serviceId, fieldId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Check a set of answers (field key to value) against the service's active fields without "
            + "saving anything. 400 VALIDATION_FAILED lists every field that is wrong. Requires CATALOG_VIEW.")
    public ValidateResponse validate(@PathVariable UUID serviceId, @RequestBody ValidateRequest request) {
        return new ValidateResponse(leadFields.validate(serviceId, request.values()));
    }

    @PostMapping(path = "/{fieldKey}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Upload a file for a FILE field. Returns the reference to send as the field's answer. "
            + "Requires CATALOG_UPDATE (used by the administrator's preview; lead forms will upload under the "
            + "lead permissions).")
    public ResponseEntity<FileReference> upload(
            @PathVariable UUID serviceId, @PathVariable String fieldKey, @RequestParam("file") MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return ResponseEntity.status(201).body(leadFields.storeFile(
                    serviceId, fieldKey, file.getOriginalFilename(), file.getContentType(), file.getSize(), content));
        } catch (IOException unreadable) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The uploaded file could not be read", unreadable);
        }
    }

    private FieldResponse response(ServiceCustomField field) {
        return new FieldResponse(fields.definition(field), field.getVersion(), field.getUpdatedAt());
    }
}
