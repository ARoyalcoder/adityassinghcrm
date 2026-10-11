package com.pawanputra.bos.activity.web;

import com.pawanputra.bos.activity.api.ActivityType;
import com.pawanputra.bos.activity.api.TargetEntityType;
import com.pawanputra.bos.activity.api.TaskPriority;
import com.pawanputra.bos.activity.api.TaskStatus;
import com.pawanputra.bos.activity.internal.ActivityService;
import com.pawanputra.bos.activity.internal.ActivityService.ActivityFilter;
import com.pawanputra.bos.activity.internal.ActivityViews;
import com.pawanputra.bos.activity.web.ActivityDtos.CompleteActivityRequest;
import com.pawanputra.bos.activity.web.ActivityDtos.CreateActivityRequest;
import com.pawanputra.bos.activity.web.ActivityDtos.RescheduleActivityRequest;
import com.pawanputra.bos.activity.web.ActivityDtos.UpdateActivityRequest;
import com.pawanputra.bos.identity.api.Permissions;
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
@RequestMapping(ApiPaths.V1 + "/activities")
@Tag(name = "Activities")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ActivityController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.ACTIVITY_VIEW + "')";
    private static final String CAN_CREATE = "hasAuthority('" + Permissions.ACTIVITY_CREATE + "')";
    private static final String CAN_UPDATE = "hasAuthority('" + Permissions.ACTIVITY_UPDATE + "')";
    private static final String CAN_DELETE = "hasAuthority('" + Permissions.ACTIVITY_DELETE + "')";

    private static final Map<String, String> SORTS = Map.of(
            "dueDate", "dueDate",
            "occurredAt", "occurredAt",
            "priority", "priority",
            "status", "status",
            "title", "title",
            "createdAt", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final ActivityService activities;

    public ActivityController(ActivityService activities) {
        this.activities = activities;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "List activities and tasks with filtering and pagination")
    public PageResponse<ActivityViews.Detail> list(
            @RequestParam(required = false) ActivityType type,
            @RequestParam(required = false) TargetEntityType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser user = CurrentUser.require();
        Pageable pageable = Paging.of(page, size, sort, SORTS, DEFAULT_SORT);
        ActivityFilter filter = new ActivityFilter(
                type, entityType, entityId, ownerId, priority, status, view, search);

        Page<ActivityViews.Detail> result = activities.list(user.organizationId(), user.id(), filter, pageable);
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/counts")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get counts for today, my tasks, overdue, upcoming and completed")
    public ActivityViews.Counts counts() {
        CurrentUser user = CurrentUser.require();
        return activities.getCounts(user.organizationId(), user.id());
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get activity details by id")
    public ActivityViews.Detail get(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return activities.get(user.organizationId(), id);
    }

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    @Operation(summary = "Create an activity, task, or follow-up")
    public ResponseEntity<ActivityViews.Detail> create(@Valid @RequestBody CreateActivityRequest request) {
        CurrentUser user = CurrentUser.require();
        ActivityViews.Detail detail = activities.create(
                user.organizationId(), request.toCommand(), user.id(), user.email());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/activities/" + detail.id())).body(detail);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Update an activity or task")
    public ActivityViews.Detail update(
            @PathVariable UUID id, @Valid @RequestBody UpdateActivityRequest request) {
        CurrentUser user = CurrentUser.require();
        return activities.update(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Mark an activity or task as complete")
    public ActivityViews.Detail complete(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CompleteActivityRequest request) {
        CurrentUser user = CurrentUser.require();
        CompleteActivityRequest req = request != null ? request : new CompleteActivityRequest(null);
        return activities.complete(user.organizationId(), id, req.toCommand(), user.email());
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Reschedule an activity or task")
    public ActivityViews.Detail reschedule(
            @PathVariable UUID id, @Valid @RequestBody RescheduleActivityRequest request) {
        CurrentUser user = CurrentUser.require();
        return activities.reschedule(user.organizationId(), id, request.toCommand(), user.email());
    }

    @PostMapping("/{id}/reminders/dismiss")
    @PreAuthorize(CAN_UPDATE)
    @Operation(summary = "Dismiss an activity reminder")
    public ActivityViews.Detail dismissReminder(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return activities.dismissReminder(user.organizationId(), id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_DELETE)
    @Operation(summary = "Delete an activity or task")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        activities.delete(user.organizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
