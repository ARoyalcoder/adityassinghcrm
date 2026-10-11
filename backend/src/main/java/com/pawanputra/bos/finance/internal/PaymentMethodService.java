package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ways an organization takes and returns money. Each organization defines its own; the first
 * time it needs them it is given the standard ones from configuration, which it can then change.
 */
@Service
@Transactional
public class PaymentMethodService {

    private final PaymentMethodRepository methods;
    private final JdbcTemplate jdbc;
    private final FinanceProperties properties;
    private final Clock clock;

    PaymentMethodService(
            PaymentMethodRepository methods, JdbcTemplate jdbc, FinanceProperties properties, Clock clock) {
        this.methods = methods;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** What an administrator sets. The code is given once, when the method is created. */
    public record Details(String code, String name, boolean requiresReference, boolean active, int displayOrder) {
    }

    public record View(
            UUID id, String code, String name, boolean requiresReference, boolean active, int displayOrder,
            long version) {
    }

    public List<View> list(UUID organizationId, boolean includeInactive) {
        ensureDefaults(organizationId);
        return methods.findAllByOrganizationIdOrderByDisplayOrderAscNameAsc(organizationId).stream()
                .filter(method -> includeInactive || method.isActive())
                .map(PaymentMethodService::view).toList();
    }

    public View create(UUID organizationId, Details details) {
        ensureDefaults(organizationId);
        if (methods.existsByOrganizationIdAndCode(organizationId, details.code())) {
            throw new FieldValidationException("code", "Another payment method already has this code");
        }
        if (methods.existsByOrganizationIdAndNameIgnoreCase(organizationId, details.name())) {
            throw new FieldValidationException("name", "Another payment method already has this name");
        }
        PaymentMethod method = new PaymentMethod(organizationId, details.code());
        apply(method, details);
        return view(methods.saveAndFlush(method));
    }

    public View update(UUID organizationId, UUID id, Details details, long expectedVersion) {
        PaymentMethod method = load(organizationId, id);
        if (method.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This payment method was changed by someone else. Reload it and apply your changes again.");
        }
        if (methods.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, details.name(), id)) {
            throw new FieldValidationException("name", "Another payment method already has this name");
        }
        apply(method, details);
        return view(methods.saveAndFlush(method));
    }

    /** Payments already taken by this method keep its name; it just cannot be chosen any more. */
    public void delete(UUID organizationId, UUID id) {
        load(organizationId, id).markDeleted(clock.instant());
    }

    /** A method that can be used for a new payment or refund, or a validation error on {@code methodId}. */
    PaymentMethod usable(UUID organizationId, UUID id) {
        return methods.findByIdAndOrganizationId(id, organizationId).filter(PaymentMethod::isActive)
                .orElseThrow(() -> new FieldValidationException("methodId", "Choose a payment method"));
    }

    PaymentMethod usableByCode(UUID organizationId, String code) {
        ensureDefaults(organizationId);
        return methods.findByOrganizationIdAndCode(organizationId, code).filter(PaymentMethod::isActive)
                .orElseThrow(() -> new FieldValidationException("methodCode", "No active payment method has the code " + code));
    }

    /**
     * Gives an organization the standard payment methods the first time it needs any. An
     * organization that has ever had a method, even one since removed, is left alone: its
     * administrator has decided what the list should be.
     */
    private void ensureDefaults(UUID organizationId) {
        Boolean configured = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payment_methods WHERE organization_id = ?)", Boolean.class, organizationId);
        if (Boolean.TRUE.equals(configured)) {
            return;
        }
        List<FinanceProperties.DefaultMethod> defaults = properties.defaultPaymentMethods();
        for (int index = 0; index < defaults.size(); index++) {
            FinanceProperties.DefaultMethod method = defaults.get(index);
            // Two first requests at the same moment both get here; the unique index lets one of them win.
            jdbc.update("""
                    INSERT INTO payment_methods (organization_id, code, name, requires_reference, display_order,
                                                 created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, 'system', 'system')
                    ON CONFLICT DO NOTHING
                    """, organizationId, method.code(), method.name(), method.requiresReference(), index + 1);
        }
    }

    private PaymentMethod load(UUID organizationId, UUID id) {
        return methods.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method", id));
    }

    private static void apply(PaymentMethod method, Details details) {
        method.setName(details.name());
        method.setRequiresReference(details.requiresReference());
        method.setActive(details.active());
        method.setDisplayOrder(details.displayOrder());
    }

    private static View view(PaymentMethod method) {
        return new View(method.getId(), method.getCode(), method.getName(), method.isRequiresReference(),
                method.isActive(), method.getDisplayOrder(), method.getVersion());
    }
}
