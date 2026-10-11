package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.SQLRestriction;

/**
 * A question an organization asks to qualify its leads, defined by its administrators. The answer
 * is always one of the options, and each option carries its points and whether it rules a lead out.
 */
@Entity
@Audited(module = "leads", entity = "QualificationQuestion", label = "label")
@Table(name = "qualification_questions")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class QualificationQuestion extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "question_key", nullable = false, updatable = false, length = 60)
    private String questionKey;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "help_text", length = 300)
    private String helpText;

    /** JSON array of options; read and written through {@link QuestionOptions}. */
    @Column(name = "options", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String optionsJson;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected QualificationQuestion() {
        // for JPA
    }

    public QualificationQuestion(UUID organizationId, String questionKey) {
        this.organizationId = organizationId;
        this.questionKey = questionKey;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getQuestionKey() {
        return questionKey;
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

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
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
