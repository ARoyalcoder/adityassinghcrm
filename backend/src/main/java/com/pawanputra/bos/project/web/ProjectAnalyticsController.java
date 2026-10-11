package com.pawanputra.bos.project.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.project.internal.analytics.ProjectAnalyticsService;
import com.pawanputra.bos.project.internal.analytics.ProjectAnalyticsService.Analytics;
import com.pawanputra.bos.project.internal.analytics.ProjectAnalyticsService.Feedback;
import com.pawanputra.bos.project.internal.analytics.ProjectAnalyticsService.PipelineProject;
import com.pawanputra.bos.project.internal.analytics.ProjectStageService;
import com.pawanputra.bos.project.internal.analytics.ProjectStageService.ProjectStage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Project analytics, and the two things they are built on: a project's pipeline stage and its client's rating. */
@RestController
@RequestMapping(ApiPaths.V1)
@Tag(name = "Project analytics")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ProjectAnalyticsController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.PROJECT_VIEW + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.PROJECT_UPDATE + "')";

    private final ProjectAnalyticsService analytics;
    private final ProjectStageService stages;

    public ProjectAnalyticsController(ProjectAnalyticsService analytics, ProjectStageService stages) {
        this.analytics = analytics;
        this.stages = stages;
    }

    record MoveStageRequest(@NotBlank @Size(max = 60) String stageKey) {
    }

    /** @param rating 1 (very unhappy) to 5 (very happy) */
    record FeedbackRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {
    }

    @GetMapping("/project-analytics")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Dashboard cards, the pipeline by stage, delivery performance and productivity, worked out "
            + "from the project records as of today. A figure that cannot be worked out is null. Requires PROJECT_VIEW.")
    public Analytics analytics() {
        return analytics.analytics(organizationId());
    }

    @GetMapping("/project-analytics/pipeline-projects")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The active projects, most urgent first, each with its current stage and the stages of its "
            + "template. Requires PROJECT_VIEW.")
    public List<PipelineProject> pipelineProjects() {
        return analytics.pipelineProjects(organizationId());
    }

    @GetMapping("/projects/{projectId}/stage")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The pipeline stage a project is in, and the stages of its template. Requires PROJECT_VIEW.")
    public ProjectStage stage(@PathVariable UUID projectId) {
        return stages.stageOf(organizationId(), projectId);
    }

    @PutMapping("/projects/{projectId}/stage")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Move a project to another stage of its template. Requires PROJECT_UPDATE.")
    public ProjectStage moveStage(@PathVariable UUID projectId, @Valid @RequestBody MoveStageRequest request) {
        CurrentUser user = CurrentUser.require();
        return stages.move(user.organizationId(), projectId, request.stageKey(), user.email());
    }

    @PutMapping("/projects/{projectId}/satisfaction")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Record the client's rating of a project, 1 to 5 (replaces an earlier one). "
            + "Requires PROJECT_UPDATE.")
    public Feedback rate(@PathVariable UUID projectId, @Valid @RequestBody FeedbackRequest request) {
        CurrentUser user = CurrentUser.require();
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        return analytics.rate(user.organizationId(), projectId, request.rating(), comment, user.email());
    }

    private static UUID organizationId() {
        return CurrentUser.require().organizationId();
    }
}
