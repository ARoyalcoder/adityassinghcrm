package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.catalog.api.CustomFieldType;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.SQLRestriction;

/**
 * One extra question a service asks on its lead form, as configured by an administrator.
 *
 * <p>Removing a field is a soft delete, so answers already given keep the field they refer to.
 */
@Entity
@Audited(module = "catalog", entity = "ServiceField", label = "label")
@Table(name = "service_custom_fields")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class ServiceCustomField extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private ServiceOffering service;

    @Column(name = "field_key", nullable = false, updatable = false, length = 60)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, updatable = false, length = 20)
    private CustomFieldType fieldType;

    @Column(name = "help_text", length = 300)
    private String helpText;

    @Column(name = "required", nullable = false)
    private boolean required;

    /** JSON array of {@code {"value", "label"}}; read and written through {@link CustomFieldJson}. */
    @Column(name = "options", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String optionsJson;

    @Column(name = "min_value", precision = 18, scale = 4)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 18, scale = 4)
    private BigDecimal maxValue;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "unit", length = 30)
    private String unit;

    /** Comma-separated lower-case extensions; {@code null} means any. */
    @Column(name = "allowed_file_types", length = 200)
    private String allowedFileTypes;

    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ServiceCustomField() {
        // for JPA
    }

    public ServiceCustomField(ServiceOffering service, String fieldKey, CustomFieldType fieldType, String label) {
        this.service = service;
        this.fieldKey = fieldKey;
        this.fieldType = fieldType;
        this.label = label;
    }

    public ServiceOffering getService() {
        return service;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public CustomFieldType getFieldType() {
        return fieldType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public BigDecimal getMinValue() {
        return minValue;
    }

    public void setMinValue(BigDecimal minValue) {
        this.minValue = minValue;
    }

    public BigDecimal getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(BigDecimal maxValue) {
        this.maxValue = maxValue;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getAllowedFileTypes() {
        return allowedFileTypes;
    }

    public void setAllowedFileTypes(String allowedFileTypes) {
        this.allowedFileTypes = allowedFileTypes;
    }

    public Integer getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(Integer maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
