package com.pawanputra.bos.project.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.project.internal.template.ProjectTemplateApplier;
import com.pawanputra.bos.project.internal.template.ProjectTemplateApplier.Applied;
import com.pawanputra.bos.project.internal.template.ProjectTemplateService;
import com.pawanputra.bos.project.internal.template.ProjectTemplateService.Detail;
import com.pawanputra.bos.project.internal.template.ProjectTemplateService.Summary;
import com.pawanputra.bos.project.web.ProjectTemplateDtos.CreateTemplateRequest;
import com.pawanputra.bos.project.web.ProjectTemplateDtos.UpdateTemplateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project templates: the plan each service's projects follow, defined by administrators. This
 * class only translates HTTP to service calls. It knows no stage of any template.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@Tag(name = "Project templates")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ProjectTemplateController {

    private static final String TEMPLATES = "/project-templates";
    private static final String CAN_VIEW = "hasAuthority('" + Permissions.PROJECT_VIEW + "')";
    private static final String CAN_CONFIGURE = "hasAuthority('" + Permissions.PROJECT_CONFIGURE + "')";
    private static final String CAN_UPDATE_PROJECT = "hasAuthority('" + Permissions.PROJECT_UPDATE + "')";

    private final ProjectTemplateService templates;
    private final ProjectTemplateApplier applier;

    public ProjectTemplateController(ProjectTemplateService templates, ProjectTemplateApplier applier) {
        this.templates = templates;
        this.applier = applier;
    }

    @GetMapping(TEMPLATES)
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "The organization's project templates, with each one's service, number of stages and "
            + "estimated duration. Requires PROJECT_VIEW.")
    public List<Summary> list() {
        return templates.list(organizationId());
    }

    @GetMapping(TEMPLATES + "/options")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "What a template editor offers to choose from: the role codes a stage or task can be made "
            + "the responsibility of. Requires PROJECT_VIEW.")
    public Map<String, List<String>> options() {
        return Map.of("roles", templates.assignableRoles());
    }

    @GetMapping(TEMPLATES + "/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "One template: its stages with their milestones, tasks, checklists and required documents, "
            + "each stage placed in time (startDay, endDay) from its duration and dependencies, and the total "
            + "estimated duration. Requires PROJECT_VIEW.")
    public Detail get(@PathVariable UUID id) {
        return templates.get(organizationId(), id);
    }

    @PostMapping(TEMPLATES)
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Create the template for a service (one per service). Problems are reported under the "
            + "path of the value, e.g. stages[2].dependsOn. Requires PROJECT_CONFIGURE.")
    public ResponseEntity<Detail> create(@Valid @RequestBody CreateTemplateRequest request) {
        Detail created = templates.create(organizationId(), request.serviceId(), request.details());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + TEMPLATES + "/" + created.id())).body(created);
    }

    @PutMapping(TEMPLATES + "/{id}")
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Replace a template's name, description and stages. Projects already planned from it are "
            + "not changed. Requires PROJECT_CONFIGURE.")
    public Detail update(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
        return templates.update(organizationId(), id, request.details(), request.version());
    }

    @PostMapping(TEMPLATES + "/{id}/activate")
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Switch a template on. Requires PROJECT_CONFIGURE.")
    public Detail activate(@PathVariable UUID id) {
        return templates.setActive(organizationId(), id, true);
    }

    @PostMapping(TEMPLATES + "/{id}/deactivate")
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Switch a template off: it is kept but no longer applied to projects. "
            + "Requires PROJECT_CONFIGURE.")
    public Detail deactivate(@PathVariable UUID id) {
        return templates.setActive(organizationId(), id, false);
    }

    @DeleteMapping(TEMPLATES + "/{id}")
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Remove a template (its service can then be given a new one). Requires PROJECT_CONFIGURE.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        templates.delete(organizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/apply-template")
    @PreAuthorize(CAN_UPDATE_PROJECT)
    @Operation(summary = "Plan a project from the active template of its service: creates the project's milestones, "
            + "tasks, checklists and deliverables, dated from the project's start date, and sets its expected "
            + "completion date. Only for a project that has no milestones or tasks yet. Requires PROJECT_UPDATE.")
    public Applied apply(@PathVariable UUID projectId) {
        CurrentUser user = CurrentUser.require();
        return applier.apply(user.organizationId(), projectId, user.email());
    }

    private static UUID organizationId() {
        return CurrentUser.require().organizationId();
    }
}
