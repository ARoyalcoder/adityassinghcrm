package com.pawanputra.bos.lead.web;

import com.pawanputra.bos.customer.api.InteractionType;
import com.pawanputra.bos.lead.api.LeadScore;
import com.pawanputra.bos.lead.api.LeadStatus;
import com.pawanputra.bos.lead.internal.LeadAssessment;
import com.pawanputra.bos.lead.internal.LeadQualificationService.Evaluation;
import com.pawanputra.bos.lead.internal.LeadQualificationService.QuestionView;
import com.pawanputra.bos.lead.internal.LeadService;
import com.pawanputra.bos.lead.internal.QualificationQuestionService;
import com.pawanputra.bos.lead.internal.QuestionOptions.Option;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Requests and responses of the lead endpoints. */
final class LeadDtos {

    private static final String PHONE_PATTERN = "^[+0-9][0-9 ()-]{5,28}$";

    private LeadDtos() {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Create or update a lead.
     *
     * @param customFields answers to the service's own fields: field key to value
     * @param version      required when updating: the version that was loaded
     */
    record LeadRequest(
            @NotBlank @Size(max = 200) String contactName,
            @Size(max = 200) String companyName,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE_PATTERN, message = "must be a phone number") String phone,
            @NotNull UUID serviceId,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal estimatedValue,
            LeadStatus status,
            Map<String, Object> customFields,
            Long version) {

        LeadService.Details details() {
            return new LeadService.Details(contactName.trim(), trimToNull(companyName), trimToNull(email),
                    trimToNull(phone), serviceId, estimatedValue, status, customFields);
        }
    }

    /** @param occurredAt when it happened (ISO-8601); leave out for "just now" */
    record ActivityRequest(@NotNull InteractionType type, Instant occurredAt, @NotBlank @Size(max = 200) String subject) {
    }

    /** @param answers question key to the chosen option's value; null clears an answer */
    record AnswersRequest(@NotNull Map<String, String> answers) {
    }

    record FactorResponse(String code, LeadScore.Category category, String label, int points) {
    }

    /**
     * The "why" of a lead: its score with every reason, and the three conclusions with theirs.
     *
     * @param total  the sum of the factors; differs from {@code score} only when it fell outside 0..100
     * @param engine which scorer produced the score, e.g. {@code rules-v1}
     */
    record QualificationResponse(
            int score,
            int total,
            List<FactorResponse> factors,
            String engine,
            String priority,
            String priorityReason,
            String health,
            String healthReason,
            String qualificationStatus,
            String qualificationReason,
            Instant computedAt,
            List<QuestionView> questions) {

        static QualificationResponse from(Evaluation evaluation) {
            LeadAssessment a = evaluation.assessment();
            return new QualificationResponse(
                    a.score().score(), a.score().total(),
                    a.score().factors().stream()
                            .map(f -> new FactorResponse(f.code(), f.category(), f.label(), f.points())).toList(),
                    a.score().engine(), a.priority().name(), a.priorityReason(), a.health().name(), a.healthReason(),
                    a.qualificationStatus().name(), a.qualificationReason(), a.computedAt(), evaluation.questions());
        }
    }

    // ------------------------------------------------------------------ question administration

    /**
     * @param points       added to the score of a lead that gives this answer; negative takes away
     * @param disqualifies this answer rules the lead out
     */
    record OptionRequest(
            @NotBlank @Size(max = 60)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain only letters, digits, underscores and hyphens")
            String value,
            @NotBlank @Size(max = 100) String label,
            @NotNull @Min(-100) @Max(100) Integer points,
            Boolean disqualifies) {

        Option option() {
            return new Option(value, label.trim(), points, Boolean.TRUE.equals(disqualifies));
        }
    }

    /** Create a question. The key cannot be changed afterwards. */
    record CreateQuestionRequest(
            @NotBlank @Size(max = 60)
            @Pattern(regexp = "^[a-z][a-z0-9_]*$",
                    message = "must be lower_snake_case: start with a letter, then letters, digits or underscores")
            String key,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 300) String helpText,
            @NotNull @Size(max = 20) List<@NotNull @Valid OptionRequest> options,
            Boolean required,
            @Min(0) @Max(9999) Integer displayOrder) {

        QualificationQuestionService.Details details() {
            return LeadDtos.details(label, helpText, options, required, displayOrder);
        }
    }

    record UpdateQuestionRequest(
            @NotBlank @Size(max = 200) String label,
            @Size(max = 300) String helpText,
            @NotNull @Size(max = 20) List<@NotNull @Valid OptionRequest> options,
            Boolean required,
            @Min(0) @Max(9999) Integer displayOrder,
            @NotNull Long version) {

        QualificationQuestionService.Details details() {
            return LeadDtos.details(label, helpText, options, required, displayOrder);
        }
    }

    private static QualificationQuestionService.Details details(
            String label, String helpText, List<OptionRequest> options, Boolean required, Integer displayOrder) {
        return new QualificationQuestionService.Details(label.trim(), trimToNull(helpText),
                options.stream().map(OptionRequest::option).toList(), Boolean.TRUE.equals(required), displayOrder);
    }
}
