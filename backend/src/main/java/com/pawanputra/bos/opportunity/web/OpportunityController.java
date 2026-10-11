package com.pawanputra.bos.opportunity.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.opportunity.api.OpportunityStatus;
import com.pawanputra.bos.opportunity.internal.OpportunityService;
import com.pawanputra.bos.opportunity.internal.OpportunityService.OpportunityFilter;
import com.pawanputra.bos.opportunity.internal.OpportunityViews;
import com.pawanputra.bos.opportunity.web.OpportunityDtos.AddNoteRequest;
import com.pawanputra.bos.opportunity.web.OpportunityDtos.ChangeStageRequest;
import com.pawanputra.bos.opportunity.web.OpportunityDtos.CreateOpportunityRequest;
import com.pawanputra.bos.opportunity.web.OpportunityDtos.UpdateOpportunityRequest;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import com.pawanputra.bos.platform.web.PageResponse;
import com.pawanputra.bos.platform.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/opportunities")
@Tag(name = "Opportunities")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class OpportunityController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.OPPORTUNITY_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.OPPORTUNITY_CREATE + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.OPPORTUNITY_UPDATE + "')";
    private static final String CAN_DELETE = "hasAuthority('" + Permissions.OPPORTUNITY_DELETE + "')";

    private static final Map<String, String> SORTS = Map.of(
            "value", "value",
            "probability", "probability",
            "expectedCloseDate", "expectedCloseDate",
            "title", "title",
            "createdAt", "createdAt",
            "stageEnteredAt", "stageEnteredAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OpportunityService opportunities;

    public OpportunityController(OpportunityService opportunities) {
        this.opportunities = opportunities;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "List opportunities with filters and pagination")
    public PageResponse<OpportunityViews.Summary> list(
            @RequestParam(required = false) UUID pipelineId,
            @RequestParam(required = false) UUID stageId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) OpportunityStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser user = CurrentUser.require();
        Pageable pageable = Paging.of(page, size, sort, SORTS, DEFAULT_SORT);
        OpportunityFilter filter = new OpportunityFilter(
                pipelineId, stageId, ownerId, status, customerId, leadId, serviceId, search);

        Page<OpportunityViews.Summary> result = opportunities.list(user.organizationId(), filter, pageable);
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/kanban")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get Kanban board grouped by stage for a pipeline")
    public OpportunityViews.KanbanBoard kanban(@RequestParam(required = false) UUID pipelineId) {
        CurrentUser user = CurrentUser.require();
        return opportunities.getKanbanBoard(user.organizationId(), pipelineId);
    }

    @GetMapping("/analytics")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get pipeline analytics (metrics, weighted value, conversion rate, time per stage)")
    public OpportunityViews.PipelineAnalytics analytics(@RequestParam(required = false) UUID pipelineId) {
        CurrentUser user = CurrentUser.require();
        return opportunities.getAnalytics(user.organizationId(), pipelineId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get opportunity details including notes and stage history")
    public OpportunityViews.Detail get(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return opportunities.get(user.organizationId(), id);
    }

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create an opportunity")
    public ResponseEntity<OpportunityViews.Detail> create(@Valid @RequestBody CreateOpportunityRequest request) {
        CurrentUser user = CurrentUser.require();
        OpportunityViews.Detail created = opportunities.create(
                user.organizationId(), request.toCommand(), user.id(), user.email());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/opportunities/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update an opportunity")
    public OpportunityViews.Detail update(
            @PathVariable UUID id, @Valid @RequestBody UpdateOpportunityRequest request) {
        CurrentUser user = CurrentUser.require();
        return opportunities.update(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PostMapping("/{id}/stage")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Move opportunity to a different stage and record history")
    public OpportunityViews.Detail changeStage(
            @PathVariable UUID id, @Valid @RequestBody ChangeStageRequest request) {
        CurrentUser user = CurrentUser.require();
        return opportunities.changeStage(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Add an internal note to an opportunity")
    public OpportunityViews.NoteView addNote(
            @PathVariable UUID id, @Valid @RequestBody AddNoteRequest request) {
        CurrentUser user = CurrentUser.require();
        return opportunities.addNote(user.organizationId(), id, request.body(), user.id(), user.email());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Soft delete an opportunity")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        opportunities.delete(user.organizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
