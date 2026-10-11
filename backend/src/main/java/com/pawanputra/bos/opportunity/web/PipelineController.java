package com.pawanputra.bos.opportunity.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.opportunity.internal.OpportunityViews;
import com.pawanputra.bos.opportunity.internal.PipelineService;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/pipelines")
@Tag(name = "Pipelines")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class PipelineController {

    private static final String CAN_VIEW = "hasAuthority('" + Permissions.OPPORTUNITY_VIEW + "')";

    private final PipelineService pipelines;

    public PipelineController(PipelineService pipelines) {
        this.pipelines = pipelines;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "List all active sales pipelines and stages")
    public List<OpportunityViews.PipelineDetail> list() {
        CurrentUser user = CurrentUser.require();
        return pipelines.list(user.organizationId());
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Get pipeline and its stages by id")
    public OpportunityViews.PipelineDetail get(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.require();
        return pipelines.get(user.organizationId(), id);
    }
}
