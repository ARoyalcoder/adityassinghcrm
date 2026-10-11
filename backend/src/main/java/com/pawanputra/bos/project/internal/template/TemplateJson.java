package com.pawanputra.bos.project.internal.template;

import com.pawanputra.bos.project.api.ProjectTemplates.DocumentSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.StageSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.TaskSpec;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Converts between a stage as the code sees it and as it is stored. */
@Component
class TemplateJson {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<TaskSpec>> TASKS = new TypeReference<>() {
    };
    private static final TypeReference<List<DocumentSpec>> DOCUMENTS = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    TemplateJson(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    StageSpec read(ProjectTemplateStage stage) {
        return new StageSpec(
                stage.getStageKey(), stage.getName(), stage.getDescription(), stage.getDurationDays(),
                stage.getResponsibleRole(),
                jsonMapper.readValue(stage.getDependsOnJson(), STRINGS),
                jsonMapper.readValue(stage.getMilestonesJson(), STRINGS),
                jsonMapper.readValue(stage.getTasksJson(), TASKS),
                jsonMapper.readValue(stage.getDocumentsJson(), DOCUMENTS));
    }

    ProjectTemplateStage write(UUID templateId, int position, StageSpec stage) {
        return new ProjectTemplateStage(
                templateId, stage.key(), stage.name(), stage.description(), position, stage.durationDays(),
                stage.responsibleRole(),
                jsonMapper.writeValueAsString(stage.dependsOn()),
                jsonMapper.writeValueAsString(stage.milestones()),
                jsonMapper.writeValueAsString(stage.tasks()),
                jsonMapper.writeValueAsString(stage.documents()));
    }
}
