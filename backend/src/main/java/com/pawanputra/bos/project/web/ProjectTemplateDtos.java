package com.pawanputra.bos.project.web;

import com.pawanputra.bos.project.api.ProjectTemplates.DocumentSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.StageSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.TaskSpec;
import com.pawanputra.bos.project.internal.template.ProjectTemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request bodies of the project template endpoints. They check each value on its own; what must
 * hold between stages (distinct keys, sound dependencies) is checked by {@code TemplatePlanner}.
 */
final class ProjectTemplateDtos {

    private static final String KEY_PATTERN = "^[a-z][a-z0-9_]*$";
    private static final String KEY_MESSAGE =
            "must be lower_snake_case: start with a letter, then letters, digits or underscores";

    private ProjectTemplateDtos() {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    record TaskRequest(
            @NotBlank @Size(max = 250) String title,
            @Size(max = 1000) String description,
            @Size(max = 50) String responsibleRole,
            @DecimalMin("0.00") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2) BigDecimal estimatedHours,
            @Size(max = 30) List<@NotBlank @Size(max = 500) String> checklist) {

        TaskSpec spec() {
            return new TaskSpec(title.trim(), trimToNull(description), trimToNull(responsibleRole), estimatedHours,
                    orEmpty(checklist).stream().map(String::trim).toList());
        }
    }

    record DocumentRequest(@NotBlank @Size(max = 200) String name, Boolean required, @Size(max = 500) String description) {

        DocumentSpec spec() {
            return new DocumentSpec(name.trim(), Boolean.TRUE.equals(required), trimToNull(description));
        }
    }

    /** @param dependsOn keys of the stages that must finish before this one starts */
    record StageRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String key,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotNull @Min(0) @Max(3650) Integer durationDays,
            @Size(max = 50) String responsibleRole,
            @Size(max = 40) List<@NotBlank @Size(max = 60) String> dependsOn,
            @Size(max = 20) List<@NotBlank @Size(max = 200) String> milestones,
            @Size(max = 50) List<@NotNull @Valid TaskRequest> tasks,
            @Size(max = 30) List<@NotNull @Valid DocumentRequest> documents) {

        StageSpec spec() {
            return new StageSpec(key, name.trim(), trimToNull(description), durationDays, trimToNull(responsibleRole),
                    orEmpty(dependsOn), orEmpty(milestones).stream().map(String::trim).toList(),
                    orEmpty(tasks).stream().map(TaskRequest::spec).toList(),
                    orEmpty(documents).stream().map(DocumentRequest::spec).toList());
        }
    }

    /** Create a template for a service. The service cannot be changed afterwards. */
    record CreateTemplateRequest(
            @NotNull UUID serviceId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull @Size(max = 40) List<@NotNull @Valid StageRequest> stages) {

        ProjectTemplateService.Details details() {
            return new ProjectTemplateService.Details(name.trim(), trimToNull(description),
                    stages.stream().map(StageRequest::spec).toList());
        }
    }

    /** Replaces the template's name, description and all of its stages. */
    record UpdateTemplateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull @Size(max = 40) List<@NotNull @Valid StageRequest> stages,
            @NotNull Long version) {

        ProjectTemplateService.Details details() {
            return new ProjectTemplateService.Details(name.trim(), trimToNull(description),
                    stages.stream().map(StageRequest::spec).toList());
        }
    }
}
