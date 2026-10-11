package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.opportunity.api.OpportunityStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OpportunityViews {

    private OpportunityViews() {
    }

    public record PipelineStageView(
            UUID id,
            String name,
            String code,
            int displayOrder,
            int defaultProbability,
            boolean isWon,
            boolean isLost,
            String color) {

        public static PipelineStageView from(PipelineStage s) {
            return new PipelineStageView(
                    s.getId(),
                    s.getName(),
                    s.getCode(),
                    s.getDisplayOrder(),
                    s.getDefaultProbability(),
                    s.isWon(),
                    s.isLost(),
                    s.getColor());
        }
    }

    public record PipelineDetail(
            UUID id,
            String name,
            String code,
            String description,
            boolean isDefault,
            boolean active,
            List<PipelineStageView> stages) {

        public static PipelineDetail from(Pipeline p) {
            return new PipelineDetail(
                    p.getId(),
                    p.getName(),
                    p.getCode(),
                    p.getDescription(),
                    p.isDefaultPipeline(),
                    p.isActive(),
                    p.getStages().stream().map(PipelineStageView::from).toList());
        }
    }

    public record Summary(
            UUID id,
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID serviceId,
            String serviceName,
            String verticalCode,
            BigDecimal value,
            int probability,
            LocalDate expectedCloseDate,
            UUID ownerId,
            String ownerName,
            UUID pipelineId,
            String pipelineName,
            UUID stageId,
            String stageName,
            String stageColor,
            OpportunityStatus status,
            String lossReason,
            Instant stageEnteredAt,
            Instant createdAt,
            Instant updatedAt) {

        public static Summary from(Opportunity o) {
            return new Summary(
                    o.getId(),
                    o.getTitle(),
                    o.getCustomerId(),
                    o.getCustomerName(),
                    o.getLeadId(),
                    o.getLeadName(),
                    o.getServiceId(),
                    o.getServiceName(),
                    o.getVerticalCode(),
                    o.getValue(),
                    o.getProbability(),
                    o.getExpectedCloseDate(),
                    o.getOwnerId(),
                    o.getOwnerName(),
                    o.getPipeline().getId(),
                    o.getPipeline().getName(),
                    o.getStage().getId(),
                    o.getStage().getName(),
                    o.getStage().getColor(),
                    o.getStatus(),
                    o.getLossReason(),
                    o.getStageEnteredAt(),
                    o.getCreatedAt(),
                    o.getUpdatedAt());
        }
    }

    public record StageHistoryView(
            UUID id,
            UUID fromStageId,
            String fromStageName,
            UUID toStageId,
            String toStageName,
            Instant enteredAt,
            Instant exitedAt,
            Long durationSeconds,
            Double durationDays,
            String changedBy,
            String notes) {

        public static StageHistoryView from(OpportunityStageHistory h) {
            Double days = h.getDurationSeconds() != null ? (double) h.getDurationSeconds() / 86400.0 : null;
            return new StageHistoryView(
                    h.getId(),
                    h.getFromStage() != null ? h.getFromStage().getId() : null,
                    h.getFromStageName(),
                    h.getToStage().getId(),
                    h.getToStageName(),
                    h.getEnteredAt(),
                    h.getExitedAt(),
                    h.getDurationSeconds(),
                    days,
                    h.getChangedBy(),
                    h.getNotes());
        }
    }

    public record NoteView(
            UUID id,
            String body,
            UUID authorId,
            String authorName,
            Instant createdAt) {

        public static NoteView from(OpportunityNote n) {
            return new NoteView(n.getId(), n.getBody(), n.getAuthorId(), n.getAuthorName(), n.getCreatedAt());
        }
    }

    public record Detail(
            UUID id,
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID serviceId,
            String serviceName,
            String verticalCode,
            BigDecimal value,
            int probability,
            LocalDate expectedCloseDate,
            UUID ownerId,
            String ownerName,
            PipelineDetail pipeline,
            PipelineStageView currentStage,
            OpportunityStatus status,
            String lossReason,
            Instant wonAt,
            Instant lostAt,
            Instant stageEnteredAt,
            List<StageHistoryView> stageHistory,
            List<NoteView> notes,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        public static Detail from(
                Opportunity o, List<StageHistoryView> history, List<NoteView> notes) {
            return new Detail(
                    o.getId(),
                    o.getTitle(),
                    o.getCustomerId(),
                    o.getCustomerName(),
                    o.getLeadId(),
                    o.getLeadName(),
                    o.getServiceId(),
                    o.getServiceName(),
                    o.getVerticalCode(),
                    o.getValue(),
                    o.getProbability(),
                    o.getExpectedCloseDate(),
                    o.getOwnerId(),
                    o.getOwnerName(),
                    PipelineDetail.from(o.getPipeline()),
                    PipelineStageView.from(o.getStage()),
                    o.getStatus(),
                    o.getLossReason(),
                    o.getWonAt(),
                    o.getLostAt(),
                    o.getStageEnteredAt(),
                    history,
                    notes,
                    o.getCreatedAt(),
                    o.getUpdatedAt(),
                    o.getVersion());
        }
    }

    public record KanbanColumn(
            PipelineStageView stage,
            int count,
            BigDecimal totalValue,
            List<Summary> opportunities) {
    }

    public record KanbanBoard(
            PipelineDetail pipeline,
            List<KanbanColumn> columns,
            int totalOpportunities,
            BigDecimal totalPipelineValue) {
    }

    public record StageTimeMetric(
            UUID stageId,
            String stageName,
            double averageDays,
            double averageHours) {
    }

    public record PipelineAnalytics(
            long totalOpportunities,
            long openOpportunities,
            long wonOpportunities,
            long lostOpportunities,
            BigDecimal pipelineValue,
            BigDecimal weightedPipelineValue,
            double winRatePercentage,
            List<StageTimeMetric> averageTimePerStage) {
    }
}
