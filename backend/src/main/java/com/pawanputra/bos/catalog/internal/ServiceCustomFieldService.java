package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.CustomFieldType;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FieldDefinition;
import com.pawanputra.bos.catalog.api.LeadCustomFields.Option;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.FieldValidationException.Violation;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration of a service's custom lead fields. The rules are about field <em>types</em>; no
 * rule here is about a particular service or a particular field.
 */
@Service
@Transactional(readOnly = true)
public class ServiceCustomFieldService {

    static final int MAX_FIELDS_PER_SERVICE = 50;
    static final int MAX_OPTIONS = 100;
    static final int MAX_TEXT_LENGTH = 1000;
    static final int MAX_TEXTAREA_LENGTH = 10000;
    /** Uploads are capped by the server's multipart limit (spring.servlet.multipart.max-file-size). */
    static final int MAX_FILE_SIZE_MB = 25;

    private final ServiceCustomFieldRepository fields;
    private final ServiceOfferingRepository services;
    private final CustomFieldJson json;
    private final Clock clock;

    public ServiceCustomFieldService(
            ServiceCustomFieldRepository fields, ServiceOfferingRepository services, CustomFieldJson json, Clock clock) {
        this.fields = fields;
        this.services = services;
        this.json = json;
        this.clock = clock;
    }

    /**
     * What an administrator may set on create and on edit. The key and the type are set on creation
     * only. Properties that do not apply to the field's type must be {@code null}.
     *
     * @param displayOrder {@code null} on create puts the field last
     */
    public record Details(
            String label,
            String helpText,
            boolean required,
            List<Option> options,
            BigDecimal minValue,
            BigDecimal maxValue,
            Integer maxLength,
            String unit,
            List<String> allowedFileTypes,
            Integer maxFileSizeMb,
            Integer displayOrder) {
    }

    /** The field in the form the API and other modules use. */
    public FieldDefinition definition(ServiceCustomField field) {
        return json.definition(field);
    }

    public List<ServiceCustomField> list(UUID serviceId, boolean activeOnly) {
        requireService(serviceId);
        return activeOnly
                ? fields.findAllByServiceIdAndActiveTrueOrderByDisplayOrderAscLabelAsc(serviceId)
                : fields.findAllByServiceIdOrderByDisplayOrderAscLabelAsc(serviceId);
    }

    public ServiceCustomField get(UUID serviceId, UUID fieldId) {
        return fields.findByIdAndServiceId(fieldId, serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));
    }

    @Transactional
    public ServiceCustomField create(UUID serviceId, String key, CustomFieldType type, Details details) {
        ServiceOffering service = requireService(serviceId);
        if (fields.existsByServiceIdAndFieldKey(serviceId, key)) {
            throw new ConflictException("This service already has a field with the key '" + key + "'");
        }
        if (fields.countByServiceId(serviceId) >= MAX_FIELDS_PER_SERVICE) {
            throw new BusinessRuleException("A service can have at most " + MAX_FIELDS_PER_SERVICE + " fields");
        }
        Details checked = validate(type, details);
        ServiceCustomField field = new ServiceCustomField(service, key, type, checked.label());
        apply(field, checked, nextDisplayOrder(serviceId));
        return fields.saveAndFlush(field);
    }

    @Transactional
    public ServiceCustomField update(UUID serviceId, UUID fieldId, Details details, long expectedVersion) {
        ServiceCustomField field = get(serviceId, fieldId);
        if (field.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This field was changed by someone else. Reload it and apply your changes again.");
        }
        Details checked = validate(field.getFieldType(), details);
        field.setLabel(checked.label());
        apply(field, checked, field.getDisplayOrder());
        return fields.saveAndFlush(field);
    }

    /** An inactive field is no longer asked or validated; answers already given are kept. */
    @Transactional
    public ServiceCustomField setActive(UUID serviceId, UUID fieldId, boolean active) {
        ServiceCustomField field = get(serviceId, fieldId);
        field.setActive(active);
        return fields.saveAndFlush(field);
    }

    /** Soft delete: answers already given keep their field, and the key becomes available again. */
    @Transactional
    public void delete(UUID serviceId, UUID fieldId) {
        get(serviceId, fieldId).markDeleted(clock.instant());
    }

    private ServiceOffering requireService(UUID serviceId) {
        return services.findById(serviceId).orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));
    }

    private int nextDisplayOrder(UUID serviceId) {
        return fields.findAllByServiceIdOrderByDisplayOrderAscLabelAsc(serviceId).stream()
                .mapToInt(ServiceCustomField::getDisplayOrder).max().orElse(0) + 10;
    }

    private void apply(ServiceCustomField field, Details details, int defaultDisplayOrder) {
        field.setHelpText(details.helpText());
        field.setRequired(details.required());
        field.setOptionsJson(details.options() == null ? null : json.write(details.options()));
        field.setMinValue(details.minValue());
        field.setMaxValue(details.maxValue());
        field.setMaxLength(details.maxLength());
        field.setUnit(details.unit());
        field.setAllowedFileTypes(details.allowedFileTypes() == null || details.allowedFileTypes().isEmpty()
                ? null
                : String.join(",", details.allowedFileTypes()));
        field.setMaxFileSizeMb(details.maxFileSizeMb());
        field.setDisplayOrder(details.displayOrder() == null ? defaultDisplayOrder : details.displayOrder());
    }

    /**
     * Checks that the settings make sense for the type, and returns them tidied (extensions in
     * lower case). Violations name the request property, so the admin form can show them in place.
     */
    static Details validate(CustomFieldType type, Details details) {
        List<Violation> violations = new ArrayList<>();

        List<Option> options = details.options();
        if (type.hasOptions()) {
            if (options == null || options.isEmpty()) {
                violations.add(new Violation("options", "Add at least one option"));
            } else if (options.size() > MAX_OPTIONS) {
                violations.add(new Violation("options", "At most " + MAX_OPTIONS + " options"));
            } else {
                Set<String> values = new HashSet<>();
                Set<String> labels = new HashSet<>();
                for (Option option : options) {
                    if (!values.add(option.value())) {
                        violations.add(new Violation("options", "The option value '" + option.value() + "' is used twice"));
                    }
                    if (!labels.add(option.label().toLowerCase(Locale.ROOT))) {
                        violations.add(new Violation("options", "The option '" + option.label() + "' is listed twice"));
                    }
                }
            }
        } else if (options != null && !options.isEmpty()) {
            violations.add(new Violation("options", "Only select and multi-select fields have options"));
        }

        if (!type.isNumeric() && (details.minValue() != null || details.maxValue() != null)) {
            violations.add(new Violation(details.minValue() != null ? "minValue" : "maxValue",
                    "Only number and currency fields have a range"));
        } else if (details.minValue() != null && details.maxValue() != null
                && details.minValue().compareTo(details.maxValue()) > 0) {
            violations.add(new Violation("maxValue", "Must not be less than the minimum"));
        }

        if (details.maxLength() != null) {
            if (!type.isText()) {
                violations.add(new Violation("maxLength", "Only text fields have a maximum length"));
            } else {
                int limit = type == CustomFieldType.TEXT ? MAX_TEXT_LENGTH : MAX_TEXTAREA_LENGTH;
                if (details.maxLength() > limit) {
                    violations.add(new Violation("maxLength", "At most " + limit + " for this type of field"));
                }
            }
        }

        if (details.unit() != null && type != CustomFieldType.NUMBER) {
            violations.add(new Violation("unit", "Only number fields have a unit"));
        }

        boolean hasFileTypes = details.allowedFileTypes() != null && !details.allowedFileTypes().isEmpty();
        if (type != CustomFieldType.FILE) {
            if (hasFileTypes) {
                violations.add(new Violation("allowedFileTypes", "Only file fields have allowed file types"));
            }
            if (details.maxFileSizeMb() != null) {
                violations.add(new Violation("maxFileSizeMb", "Only file fields have a maximum file size"));
            }
        } else if (details.maxFileSizeMb() != null && details.maxFileSizeMb() > MAX_FILE_SIZE_MB) {
            violations.add(new Violation("maxFileSizeMb", "At most " + MAX_FILE_SIZE_MB + " MB"));
        }

        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
        List<String> fileTypes = hasFileTypes
                ? details.allowedFileTypes().stream().map(t -> t.toLowerCase(Locale.ROOT)).distinct().toList()
                : null;
        return new Details(
                details.label(), details.helpText(), details.required(), type.hasOptions() ? options : null,
                details.minValue(), details.maxValue(), details.maxLength(), details.unit(), fileTypes,
                details.maxFileSizeMb(), details.displayOrder());
    }
}
