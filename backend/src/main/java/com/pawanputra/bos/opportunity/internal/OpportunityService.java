package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.catalog.api.ServiceDirectory;
import com.pawanputra.bos.catalog.api.ServiceDirectory.ServiceSummary;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.identity.api.UserDirectory.UserSummary;
import com.pawanputra.bos.opportunity.api.OpportunityEvents;
import com.pawanputra.bos.opportunity.api.OpportunityStatus;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OpportunityService {

    private final OpportunityRepository opportunities;
    private final OpportunityStageHistoryRepository stageHistories;
    private final OpportunityNoteRepository notes;
    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final PipelineService pipelineService;
    private final UserDirectory users;
    private final ServiceDirectory services;
    private final DomainEventPublisher events;
    private final Clock clock;

    public OpportunityService(
            OpportunityRepository opportunities,
            OpportunityStageHistoryRepository stageHistories,
            OpportunityNoteRepository notes,
            PipelineRepository pipelines,
            PipelineStageRepository stages,
            PipelineService pipelineService,
            UserDirectory users,
            ServiceDirectory services,
            DomainEventPublisher events,
            Clock clock) {
        this.opportunities = opportunities;
        this.stageHistories = stageHistories;
        this.notes = notes;
        this.pipelines = pipelines;
        this.stages = stages;
        this.pipelineService = pipelineService;
        this.users = users;
        this.services = services;
        this.events = events;
        this.clock = clock;
    }

    public record CreateCommand(
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID serviceId,
            String serviceName,
            String verticalCode,
            BigDecimal value,
            Integer probability,
            LocalDate expectedCloseDate,
            UUID ownerId,
            UUID pipelineId,
            UUID stageId) {
    }

    public record UpdateCommand(
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID serviceId,
            String serviceName,
            String verticalCode,
            BigDecimal value,
            Integer probability,
            LocalDate expectedCloseDate,
            UUID ownerId) {
    }

    public record ChangeStageCommand(UUID stageId, String notes, String lossReason) {
    }

    public record OpportunityFilter(
            UUID pipelineId,
            UUID stageId,
            UUID ownerId,
            OpportunityStatus status,
            UUID customerId,
            UUID leadId,
            UUID serviceId,
            String search) {
    }

    @Transactional
    public OpportunityViews.Detail create(
            UUID organizationId, CreateCommand cmd, UUID actorId, String actorName) {
        Pipeline pipeline = cmd.pipelineId() != null
                ? pipelineService.getEntity(organizationId, cmd.pipelineId())
                : pipelineService.ensureDefaultPipeline(organizationId);

        PipelineStage initialStage;
        if (cmd.stageId() != null) {
            initialStage = pipelineService.getStageEntity(pipeline.getId(), cmd.stageId());
        } else {
            initialStage = pipeline.getStages().stream()
                    .filter(s -> !s.isWon() && !s.isLost())
                    .findFirst()
                    .orElse(pipeline.getStages().getFirst());
        }

        UUID ownerId = cmd.ownerId() != null ? cmd.ownerId() : actorId;
        String ownerName = resolveUserName(organizationId, ownerId, actorName);

        String serviceName = cmd.serviceName();
        String verticalCode = cmd.verticalCode();
        if (cmd.serviceId() != null) {
            Optional<ServiceSummary> svc = services.find(cmd.serviceId());
            if (svc.isPresent()) {
                serviceName = svc.get().name();
                verticalCode = svc.get().verticalName();
            }
        }

        int prob = cmd.probability() != null ? cmd.probability() : initialStage.getDefaultProbability();

        Opportunity opp = new Opportunity(
                organizationId,
                cmd.title(),
                cmd.customerId(),
                cmd.customerName(),
                cmd.leadId(),
                cmd.leadName(),
                cmd.serviceId(),
                serviceName,
                verticalCode,
                cmd.value(),
                prob,
                cmd.expectedCloseDate(),
                ownerId,
                ownerName,
                pipeline,
                initialStage);

        Opportunity saved = opportunities.save(opp);

        // Record initial history entry
        Instant now = clock.instant();
        OpportunityStageHistory history = new OpportunityStageHistory(
                saved, null, null, initialStage, initialStage.getName(), now, actorName, "Opportunity created");
        stageHistories.save(history);

        events.publish(OpportunityEvents.OpportunityCreatedEvent.of(
                saved.getId(),
                organizationId,
                saved.getTitle(),
                saved.getValue(),
                saved.getCustomerId(),
                saved.getLeadId(),
                saved.getOwnerId()));

        return get(organizationId, saved.getId());
    }

    @Transactional
    public OpportunityViews.Detail update(
            UUID organizationId, UUID opportunityId, UpdateCommand cmd, String actorName) {
        Opportunity opp = getEntity(organizationId, opportunityId);

        if (cmd.title() != null && !cmd.title().isBlank()) {
            opp.setTitle(cmd.title());
        }
        opp.setCustomerId(cmd.customerId());
        if (cmd.customerName() != null) {
            opp.setCustomerName(cmd.customerName());
        }
        opp.setLeadId(cmd.leadId());
        if (cmd.leadName() != null) {
            opp.setLeadName(cmd.leadName());
        }
        opp.setServiceId(cmd.serviceId());
        if (cmd.serviceName() != null) {
            opp.setServiceName(cmd.serviceName());
        }
        if (cmd.verticalCode() != null) {
            opp.setVerticalCode(cmd.verticalCode());
        }
        if (cmd.value() != null) {
            opp.setValue(cmd.value());
        }
        if (cmd.probability() != null) {
            opp.setProbability(cmd.probability());
        }
        opp.setExpectedCloseDate(cmd.expectedCloseDate());

        if (cmd.ownerId() != null && !cmd.ownerId().equals(opp.getOwnerId())) {
            opp.setOwnerId(cmd.ownerId());
            opp.setOwnerName(resolveUserName(organizationId, cmd.ownerId(), actorName));
        }

        return get(organizationId, opp.getId());
    }

    @Transactional
    public OpportunityViews.Detail changeStage(
            UUID organizationId, UUID opportunityId, ChangeStageCommand cmd, String actorName) {
        Opportunity opp = getEntity(organizationId, opportunityId);
        PipelineStage oldStage = opp.getStage();

        if (oldStage.getId() != null && oldStage.getId().equals(cmd.stageId())) {
            return get(organizationId, opp.getId());
        }

        PipelineStage newStage = pipelineService.getStageEntity(opp.getPipeline().getId(), cmd.stageId());
        Instant now = clock.instant();

        // Close current stage history
        Optional<OpportunityStageHistory> currentHist =
                stageHistories.findTopByOpportunityIdAndExitedAtIsNullOrderByEnteredAtDesc(opp.getId());
        if (currentHist.isPresent()) {
            OpportunityStageHistory h = currentHist.get();
            h.setExitedAt(now);
            long secs = Math.max(0, Duration.between(h.getEnteredAt(), now).getSeconds());
            h.setDurationSeconds(secs);
        }

        // Apply stage transition
        opp.transitionToStage(newStage, now, cmd.lossReason());

        // Create new history record
        OpportunityStageHistory nextHist = new OpportunityStageHistory(
                opp,
                oldStage,
                oldStage.getName(),
                newStage,
                newStage.getName(),
                now,
                actorName,
                cmd.notes() != null && !cmd.notes().isBlank() ? cmd.notes() : "Stage moved to " + newStage.getName());
        stageHistories.save(nextHist);

        events.publish(OpportunityEvents.OpportunityStageChangedEvent.of(
                opp.getId(),
                organizationId,
                oldStage.getId(),
                oldStage.getName(),
                newStage.getId(),
                newStage.getName(),
                opp.getStatus()));

        return get(organizationId, opp.getId());
    }

    @Transactional
    public OpportunityViews.NoteView addNote(
            UUID organizationId, UUID opportunityId, String body, UUID actorId, String actorName) {
        Opportunity opp = getEntity(organizationId, opportunityId);
        OpportunityNote note = new OpportunityNote(opp, body.trim(), actorId, actorName);
        OpportunityNote saved = notes.save(note);
        return OpportunityViews.NoteView.from(saved);
    }

    public OpportunityViews.Detail get(UUID organizationId, UUID opportunityId) {
        Opportunity opp = getEntity(organizationId, opportunityId);
        List<OpportunityViews.StageHistoryView> history = stageHistories
                .findByOpportunityIdOrderByEnteredAtDesc(opp.getId())
                .stream()
                .map(OpportunityViews.StageHistoryView::from)
                .toList();

        List<OpportunityViews.NoteView> noteList = notes
                .findByOpportunityIdOrderByCreatedAtDesc(opp.getId())
                .stream()
                .map(OpportunityViews.NoteView::from)
                .toList();

        return OpportunityViews.Detail.from(opp, history, noteList);
    }

    public Page<OpportunityViews.Summary> list(
            UUID organizationId, OpportunityFilter filter, Pageable pageable) {
        Specification<Opportunity> spec = buildSpecification(organizationId, filter);
        return opportunities.findAll(spec, pageable).map(OpportunityViews.Summary::from);
    }

    public OpportunityViews.KanbanBoard getKanbanBoard(UUID organizationId, UUID requestedPipelineId) {
        Pipeline pipeline = requestedPipelineId != null
                ? pipelineService.getEntity(organizationId, requestedPipelineId)
                : pipelineService.ensureDefaultPipeline(organizationId);

        List<Opportunity> all = opportunities.findKanbanOpportunities(organizationId, pipeline.getId());

        Map<UUID, List<OpportunityViews.Summary>> grouped = new LinkedHashMap<>();
        for (PipelineStage stage : pipeline.getStages()) {
            grouped.put(stage.getId(), new ArrayList<>());
        }

        BigDecimal totalValue = BigDecimal.ZERO;
        for (Opportunity o : all) {
            OpportunityViews.Summary sum = OpportunityViews.Summary.from(o);
            List<OpportunityViews.Summary> list = grouped.get(o.getStage().getId());
            if (list != null) {
                list.add(sum);
            }
            if (o.getStatus() == OpportunityStatus.OPEN) {
                totalValue = totalValue.add(o.getValue());
            }
        }

        List<OpportunityViews.KanbanColumn> columns = new ArrayList<>();
        for (PipelineStage stage : pipeline.getStages()) {
            List<OpportunityViews.Summary> colOpps = grouped.getOrDefault(stage.getId(), Collections.emptyList());
            BigDecimal colTotal = colOpps.stream()
                    .map(OpportunityViews.Summary::value)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            columns.add(new OpportunityViews.KanbanColumn(
                    OpportunityViews.PipelineStageView.from(stage),
                    colOpps.size(),
                    colTotal,
                    colOpps));
        }

        return new OpportunityViews.KanbanBoard(
                OpportunityViews.PipelineDetail.from(pipeline),
                columns,
                all.size(),
                totalValue);
    }

    public OpportunityViews.PipelineAnalytics getAnalytics(UUID organizationId, UUID requestedPipelineId) {
        Pipeline pipeline = requestedPipelineId != null
                ? pipelineService.getEntity(organizationId, requestedPipelineId)
                : pipelineService.ensureDefaultPipeline(organizationId);

        List<Opportunity> list = opportunities.findByOrganizationIdAndPipelineId(organizationId, pipeline.getId());

        long total = list.size();
        long open = 0;
        long won = 0;
        long lost = 0;
        BigDecimal pipelineValue = BigDecimal.ZERO;
        BigDecimal weightedValue = BigDecimal.ZERO;

        for (Opportunity o : list) {
            switch (o.getStatus()) {
                case OPEN -> {
                    open++;
                    pipelineValue = pipelineValue.add(o.getValue());
                    BigDecimal weight = o.getValue()
                            .multiply(BigDecimal.valueOf(o.getProbability()))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    weightedValue = weightedValue.add(weight);
                }
                case WON -> won++;
                case LOST -> lost++;
            }
        }

        double winRate = (won + lost > 0)
                ? ((double) won / (double) (won + lost)) * 100.0
                : 0.0;

        // Stage durations
        List<Object[]> rawAvg = stageHistories.findAverageDurationPerStage(organizationId);
        Map<UUID, Double> avgMap = new LinkedHashMap<>();
        for (Object[] row : rawAvg) {
            UUID sId = (UUID) row[0];
            Double avgSecs = (Double) row[2];
            if (sId != null && avgSecs != null) {
                avgMap.put(sId, avgSecs);
            }
        }

        List<OpportunityViews.StageTimeMetric> timeMetrics = new ArrayList<>();
        for (PipelineStage stage : pipeline.getStages()) {
            double avgSec = avgMap.getOrDefault(stage.getId(), 0.0);
            double avgHours = avgSec / 3600.0;
            double avgDays = avgSec / 86400.0;
            timeMetrics.add(new OpportunityViews.StageTimeMetric(
                    stage.getId(), stage.getName(), Math.round(avgDays * 10.0) / 10.0, Math.round(avgHours * 10.0) / 10.0));
        }

        return new OpportunityViews.PipelineAnalytics(
                total,
                open,
                won,
                lost,
                pipelineValue,
                weightedValue,
                Math.round(winRate * 10.0) / 10.0,
                timeMetrics);
    }

    @Transactional
    public void delete(UUID organizationId, UUID opportunityId) {
        Opportunity opp = getEntity(organizationId, opportunityId);
        opp.markDeleted(clock.instant());
    }

    private Opportunity getEntity(UUID organizationId, UUID opportunityId) {
        return opportunities.findByIdAndOrganizationId(opportunityId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity", opportunityId));
    }

    private String resolveUserName(UUID organizationId, UUID userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        return users.find(organizationId, userId)
                .map(UserSummary::fullName)
                .orElse(fallback != null ? fallback : "Unassigned");
    }

    private Specification<Opportunity> buildSpecification(UUID organizationId, OpportunityFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));

            if (filter.pipelineId() != null) {
                predicates.add(cb.equal(root.get("pipeline").get("id"), filter.pipelineId()));
            }
            if (filter.stageId() != null) {
                predicates.add(cb.equal(root.get("stage").get("id"), filter.stageId()));
            }
            if (filter.ownerId() != null) {
                predicates.add(cb.equal(root.get("ownerId"), filter.ownerId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
            }
            if (filter.leadId() != null) {
                predicates.add(cb.equal(root.get("leadId"), filter.leadId()));
            }
            if (filter.serviceId() != null) {
                predicates.add(cb.equal(root.get("serviceId"), filter.serviceId()));
            }

            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                Predicate matchTitle = cb.like(cb.lower(root.get("title")), pattern);
                Predicate matchCustomer = cb.like(cb.lower(root.get("customerName")), pattern);
                Predicate matchLead = cb.like(cb.lower(root.get("leadName")), pattern);
                Predicate matchService = cb.like(cb.lower(root.get("serviceName")), pattern);
                Predicate matchOwner = cb.like(cb.lower(root.get("ownerName")), pattern);
                predicates.add(cb.or(matchTitle, matchCustomer, matchLead, matchService, matchOwner));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
