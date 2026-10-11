package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** The option one lead chose for one qualification question. */
@Entity
@Table(name = "lead_qualification_answers")
public class LeadQualificationAnswer extends AuditableEntity {

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "option_value", nullable = false, length = 60)
    private String optionValue;

    protected LeadQualificationAnswer() {
        // for JPA
    }

    public LeadQualificationAnswer(UUID leadId, UUID questionId, String optionValue) {
        this.leadId = leadId;
        this.questionId = questionId;
        this.optionValue = optionValue;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public void setOptionValue(String optionValue) {
        this.optionValue = optionValue;
    }
}
