package com.pawanputra.bos.project.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.project.internal.ProjectStatusService;
import com.pawanputra.bos.project.internal.ProjectViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/projects/statuses")
@Tag(name = "Project Statuses")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ProjectStatusController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.PROJECT_VIEW + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.PROJECT_UPDATE + "')";

    private final ProjectStatusService statusService;

    public ProjectStatusController(ProjectStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "List configurable project statuses for the organization")
    public List<ProjectViews.StatusView> list() {
        CurrentUser user = CurrentUser.require();
        return statusService.listStatuses(user.organizationId());
    }

    @PostMapping
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Create a new configurable project status")
    public ProjectViews.StatusView create(@Valid @RequestBody ProjectDtos.CreateStatusConfigRequest request) {
        CurrentUser user = CurrentUser.require();
        return statusService.createStatus(user.organizationId(), request.toCommand(), user.email());
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update an existing project status")
    public ProjectViews.StatusView update(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.UpdateStatusConfigRequest request) {
        CurrentUser user = CurrentUser.require();
        return statusService.updateStatus(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PutMapping("/reorder")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Reorder project statuses")
    public void reorder(@Valid @RequestBody ProjectDtos.ReorderStatusesRequest request) {
        CurrentUser user = CurrentUser.require();
        statusService.reorderStatuses(user.organizationId(), request.statusIds(), user.email());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Delete a project status")
    public void delete(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        statusService.deleteStatus(user.organizationId(), id);
    }
}
