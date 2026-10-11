package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PipelineService {

    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;

    public PipelineService(PipelineRepository pipelines, PipelineStageRepository stages) {
        this.pipelines = pipelines;
        this.stages = stages;
    }

    @Transactional
    public Pipeline ensureDefaultPipeline(UUID organizationId) {
        return pipelines.findByOrganizationIdAndDefaultPipelineTrue(organizationId)
                .orElseGet(() -> {
                    Pipeline p = new Pipeline(
                            organizationId,
                            "Standard Sales Pipeline",
                            "STANDARD",
                            "Default company sales and deals pipeline",
                            true,
                            true);
                    Pipeline saved = pipelines.save(p);

                    List<PipelineStage> defaultStages = List.of(
                            new PipelineStage(saved, "New", "NEW", 1, 10, false, false, "slate"),
                            new PipelineStage(saved, "Qualified", "QUALIFIED", 2, 25, false, false, "indigo"),
                            new PipelineStage(saved, "Requirement Confirmed", "REQUIREMENT_CONFIRMED", 3, 40, false, false, "cyan"),
                            new PipelineStage(saved, "Site Visit", "SITE_VISIT", 4, 50, false, false, "amber"),
                            new PipelineStage(saved, "Proposal", "PROPOSAL", 5, 65, false, false, "purple"),
                            new PipelineStage(saved, "Negotiation", "NEGOTIATION", 6, 80, false, false, "blue"),
                            new PipelineStage(saved, "Won", "WON", 7, 100, true, false, "emerald"),
                            new PipelineStage(saved, "Lost", "LOST", 8, 0, false, true, "rose"));

                    for (PipelineStage stage : defaultStages) {
                        saved.addStage(stage);
                    }
                    return pipelines.save(saved);
                });
    }

    public List<OpportunityViews.PipelineDetail> list(UUID organizationId) {
        ensureDefaultPipeline(organizationId);
        return pipelines.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId)
                .stream()
                .map(OpportunityViews.PipelineDetail::from)
                .toList();
    }

    public OpportunityViews.PipelineDetail get(UUID organizationId, UUID pipelineId) {
        Pipeline p = getEntity(organizationId, pipelineId);
        return OpportunityViews.PipelineDetail.from(p);
    }

    public Pipeline getEntity(UUID organizationId, UUID pipelineId) {
        return pipelines.findByIdAndOrganizationId(pipelineId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline", pipelineId));
    }

    public PipelineStage getStageEntity(UUID pipelineId, UUID stageId) {
        return stages.findByIdAndPipelineId(stageId, pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException("PipelineStage", stageId));
    }
}
