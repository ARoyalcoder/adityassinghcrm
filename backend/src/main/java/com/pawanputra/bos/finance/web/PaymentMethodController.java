package com.pawanputra.bos.finance.web;

import com.pawanputra.bos.finance.internal.PaymentMethodService;
import com.pawanputra.bos.finance.internal.PaymentMethodService.View;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The ways the organization takes and returns money. Configuration, kept by whoever runs finance. */
@RestController
@RequestMapping(ApiPaths.V1 + "/payment-methods")
@Tag(name = "Payment methods")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class PaymentMethodController {

    private final PaymentMethodService methods;

    public PaymentMethodController(PaymentMethodService methods) {
        this.methods = methods;
    }

    /**
     * @param code    capitals, digits and underscores; set when the method is created and never changed
     * @param version required when editing: the version that was loaded
     */
    record MethodRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$",
                    message = "must be 2 to 30 capital letters, digits or underscores, starting with a letter")
            String code,
            @NotBlank @Size(max = 100) String name,
            Boolean requiresReference,
            Boolean active,
            @Min(0) @Max(9999) Integer displayOrder,
            Long version) {

        PaymentMethodService.Details details() {
            return new PaymentMethodService.Details(code, name.trim(), Boolean.TRUE.equals(requiresReference),
                    !Boolean.FALSE.equals(active), displayOrder == null ? 0 : displayOrder);
        }
    }

    @GetMapping
    @PreAuthorize(FinanceWeb.CAN_VIEW)
    @Operation(summary = "The organization's payment methods, in display order. Only active ones unless "
            + "includeInactive=true. Requires FINANCE_VIEW.")
    public List<View> list(@RequestParam(required = false) Boolean includeInactive) {
        return methods.list(CurrentUser.require().organizationId(), Boolean.TRUE.equals(includeInactive));
    }

    @PostMapping
    @PreAuthorize(FinanceWeb.CAN_CONFIGURE)
    @Operation(summary = "Add a payment method. Requires FINANCE_CONFIGURE.")
    public ResponseEntity<View> create(@Valid @RequestBody MethodRequest request) {
        View created = methods.create(CurrentUser.require().organizationId(), request.details());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/payment-methods/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(FinanceWeb.CAN_CONFIGURE)
    @Operation(summary = "Rename a payment method, switch it on or off, or change whether it needs a reference. "
            + "The code cannot be changed. Requires FINANCE_CONFIGURE.")
    public View update(@PathVariable UUID id, @Valid @RequestBody MethodRequest request) {
        if (request.version() == null) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "version is required when updating");
        }
        return methods.update(CurrentUser.require().organizationId(), id, request.details(), request.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(FinanceWeb.CAN_CONFIGURE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a payment method. Payments already taken by it keep its name. "
            + "Requires FINANCE_CONFIGURE.")
    public void delete(@PathVariable UUID id) {
        methods.delete(CurrentUser.require().organizationId(), id);
    }
}
