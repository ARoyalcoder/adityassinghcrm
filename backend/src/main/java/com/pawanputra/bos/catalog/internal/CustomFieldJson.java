package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.LeadCustomFields.FieldDefinition;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FileReference;
import com.pawanputra.bos.catalog.api.LeadCustomFields.Option;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Converts between the JSON columns of the custom-field tables and the types the code works with. */
@Component
class CustomFieldJson {

    private static final TypeReference<List<Option>> OPTIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    CustomFieldJson(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    String write(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    List<Option> options(String json) {
        return json == null ? null : jsonMapper.readValue(json, OPTIONS);
    }

    List<String> strings(String json) {
        return jsonMapper.readValue(json, STRINGS);
    }

    FileReference file(String json) {
        return jsonMapper.readValue(json, FileReference.class);
    }

    static List<String> fileTypes(String commaSeparated) {
        return commaSeparated == null ? null : Arrays.asList(commaSeparated.split(","));
    }

    FieldDefinition definition(ServiceCustomField field) {
        return new FieldDefinition(
                field.getId(), field.getFieldKey(), field.getLabel(), field.getFieldType(), field.getHelpText(),
                field.isRequired(), options(field.getOptionsJson()),
                plain(field.getMinValue()), plain(field.getMaxValue()), field.getMaxLength(), field.getUnit(),
                fileTypes(field.getAllowedFileTypes()), field.getMaxFileSizeMb(), field.getDisplayOrder(),
                field.isActive());
    }

    /** 500.0000 as stored becomes 500, so limits read the way the administrator typed them. */
    static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
