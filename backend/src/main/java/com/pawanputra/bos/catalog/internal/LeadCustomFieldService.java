package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.CustomFieldType;
import com.pawanputra.bos.catalog.api.LeadCustomFields;
import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.storage.ObjectStorage;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validating and storing the answers to a service's custom fields. Driven entirely by the field
 * definitions in the database.
 */
@Service
@Transactional(readOnly = true)
public class LeadCustomFieldService implements LeadCustomFields {

    private final ServiceCustomFieldRepository fields;
    private final LeadCustomFieldValueRepository values;
    private final ServiceOfferingRepository services;
    private final CustomFieldJson json;
    private final ObjectStorage storage;

    public LeadCustomFieldService(
            ServiceCustomFieldRepository fields,
            LeadCustomFieldValueRepository values,
            ServiceOfferingRepository services,
            CustomFieldJson json,
            ObjectStorage storage) {
        this.fields = fields;
        this.values = values;
        this.services = services;
        this.json = json;
        this.storage = storage;
    }

    @Override
    public List<FieldDefinition> fields(UUID serviceId) {
        return activeFields(serviceId).stream().map(json::definition).toList();
    }

    @Override
    public Map<String, Object> validate(UUID serviceId, Map<String, Object> answers) {
        CustomFieldValidator.Result result = CustomFieldValidator.validate(fields(serviceId), answers);
        if (!result.violations().isEmpty()) {
            throw new FieldValidationException(result.violations());
        }
        return result.values();
    }

    @Override
    @Transactional
    public void save(UUID leadId, UUID serviceId, Map<String, Object> answers) {
        Map<String, Object> accepted = validate(serviceId, answers);

        Map<UUID, LeadCustomFieldValue> existing = new HashMap<>();
        // Loaded without the join to fields, so answers to removed fields are found (and left alone).
        for (LeadCustomFieldValue value : values.findAllByLeadId(leadId)) {
            existing.put(value.getField().getId(), value);
        }
        for (ServiceCustomField field : activeFields(serviceId)) {
            Object answer = accepted.get(field.getFieldKey());
            LeadCustomFieldValue stored = existing.get(field.getId());
            if (answer == null) {
                if (stored != null) {
                    values.delete(stored);
                }
                continue;
            }
            LeadCustomFieldValue value = stored != null ? stored : new LeadCustomFieldValue(leadId, field);
            store(value, field.getFieldType(), answer);
            values.save(value);
        }
        values.flush();
    }

    @Override
    public List<FieldValue> values(UUID leadId) {
        return values.findAllOfLead(leadId).stream()
                .map(value -> new FieldValue(json.definition(value.getField()), read(value)))
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID leadId) {
        values.deleteAllOfLead(leadId);
    }

    @Override
    public FileReference storeFile(
            UUID serviceId, String fieldKey, String fileName, String contentType, long sizeBytes, InputStream content) {
        requireService(serviceId);
        ServiceCustomField field = fields.findByServiceIdAndFieldKey(serviceId, fieldKey)
                .filter(candidate -> candidate.isActive() && candidate.getFieldType() == CustomFieldType.FILE)
                .orElseThrow(() -> new ResourceNotFoundException("File field", fieldKey));
        FieldDefinition definition = json.definition(field);
        // Only the last path segment: browsers may send a full path.
        String name = fileName == null ? "" : fileName.substring(Math.max(
                fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1).trim();
        try {
            CustomFieldValidator.checkFile(definition, name, sizeBytes);
        } catch (CustomFieldValidator.Rejected rejected) {
            throw new FieldValidationException(fieldKey, rejected.getMessage());
        }
        String extension = CustomFieldValidator.extensionOf(name);
        // The stored name is generated: nothing the user typed becomes part of the key.
        String key = CustomFieldValidator.fileKeyPrefix(definition) + UUID.randomUUID()
                + (extension.matches("[a-z0-9]{1,10}") ? "." + extension : "");
        String type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        try {
            storage.put(key, content, sizeBytes, type);
        } catch (RuntimeException storageFailure) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "File storage is not available right now. Try again later.", storageFailure);
        }
        return new FileReference(key, name, type, sizeBytes);
    }

    private List<ServiceCustomField> activeFields(UUID serviceId) {
        requireService(serviceId);
        return fields.findAllByServiceIdAndActiveTrueOrderByDisplayOrderAscLabelAsc(serviceId);
    }

    private void requireService(UUID serviceId) {
        if (!services.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service", serviceId);
        }
    }

    /** Puts a validated answer into the column that belongs to the field's type. */
    private void store(LeadCustomFieldValue value, CustomFieldType type, Object answer) {
        switch (type) {
            case TEXT, TEXTAREA, SELECT -> value.set((String) answer, null, null, null, null);
            case NUMBER, CURRENCY -> value.set(null, (BigDecimal) answer, null, null, null);
            case BOOLEAN -> value.set(null, null, (Boolean) answer, null, null);
            case DATE -> value.set(null, null, null, LocalDate.parse((String) answer), null);
            case MULTI_SELECT, FILE -> value.set(null, null, null, null, json.write(answer));
        }
    }

    private Object read(LeadCustomFieldValue value) {
        return switch (value.getField().getFieldType()) {
            case TEXT, TEXTAREA, SELECT -> value.getValueText();
            case NUMBER, CURRENCY -> CustomFieldJson.plain(value.getValueNumber());
            case BOOLEAN -> value.getValueBoolean();
            case DATE -> value.getValueDate().toString();
            case MULTI_SELECT -> json.strings(value.getValueJson());
            case FILE -> json.file(value.getValueJson());
        };
    }
}
