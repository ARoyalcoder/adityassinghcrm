package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * The answer one lead gave to one custom field. Exactly one of the value columns is set, chosen by
 * the field's type (see {@link LeadCustomFieldService}).
 */
@Entity
@Table(name = "lead_custom_field_values")
public class LeadCustomFieldValue extends AuditableEntity {

    /** The lead's id. Not a relationship: leads belong to another module. */
    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false, updatable = false)
    private ServiceCustomField field;

    @Column(name = "value_text", columnDefinition = "text")
    private String valueText;

    @Column(name = "value_number", precision = 18, scale = 4)
    private BigDecimal valueNumber;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "value_json", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String valueJson;

    protected LeadCustomFieldValue() {
        // for JPA
    }

    public LeadCustomFieldValue(UUID leadId, ServiceCustomField field) {
        this.leadId = leadId;
        this.field = field;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public ServiceCustomField getField() {
        return field;
    }

    public String getValueText() {
        return valueText;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public Boolean getValueBoolean() {
        return valueBoolean;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public String getValueJson() {
        return valueJson;
    }

    /** Replaces the answer. Exactly one argument is non-null. */
    public void set(String text, BigDecimal number, Boolean bool, LocalDate date, String json) {
        this.valueText = text;
        this.valueNumber = number;
        this.valueBoolean = bool;
        this.valueDate = date;
        this.valueJson = json;
    }
}
