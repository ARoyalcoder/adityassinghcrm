package com.pawanputra.bos.opportunity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawanputra.bos.catalog.api.ServiceDirectory;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.opportunity.api.OpportunityStatus;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpportunityServiceTest {

    private OpportunityRepository opportunities;
    private OpportunityStageHistoryRepository stageHistories;
    private OpportunityNoteRepository notes;
    private PipelineRepository pipelines;
    private PipelineStageRepository stages;
    private PipelineService pipelineService;
    private UserDirectory users;
    private ServiceDirectory services;
    private DomainEventPublisher events;
    private Clock clock;
    private OpportunityService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-09T12:00:00Z");

    private Pipeline defaultPipeline;
    private PipelineStage stageNew;
    private PipelineStage stageWon;
    private PipelineStage stageLost;

    @BeforeEach
    void setUp() {
        opportunities = mock(OpportunityRepository.class);
        stageHistories = mock(OpportunityStageHistoryRepository.class);
        notes = mock(OpportunityNoteRepository.class);
        pipelines = mock(PipelineRepository.class);
        stages = mock(PipelineStageRepository.class);
        pipelineService = mock(PipelineService.class);
        users = mock(UserDirectory.class);
        services = mock(ServiceDirectory.class);
        events = mock(DomainEventPublisher.class);
        clock = Clock.fixed(now, ZoneOffset.UTC);

        defaultPipeline = new Pipeline(orgId, "Standard Sales Pipeline", "STANDARD", "Desc", true, true);
        stageNew = new PipelineStage(defaultPipeline, "New", "NEW", 1, 10, false, false, "slate");
        stageWon = new PipelineStage(defaultPipeline, "Won", "WON", 7, 100, true, false, "emerald");
        stageLost = new PipelineStage(defaultPipeline, "Lost", "LOST", 8, 0, false, true, "rose");
        defaultPipeline.addStage(stageNew);
        defaultPipeline.addStage(stageWon);
        defaultPipeline.addStage(stageLost);

        when(pipelineService.ensureDefaultPipeline(orgId)).thenReturn(defaultPipeline);
        when(pipelineService.getEntity(orgId, defaultPipeline.getId())).thenReturn(defaultPipeline);

        service = new OpportunityService(
                opportunities, stageHistories, notes, pipelines, stages,
                pipelineService, users, services, events, clock);
    }

    @Test
    void createOpportunityInitializesStageAndHistory() {
        when(users.find(orgId, userId))
                .thenReturn(Optional.of(new UserDirectory.UserSummary(userId, "Aditya Singh", "a@example.com", true)));
        when(opportunities.save(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        OpportunityService.CreateCommand cmd = new OpportunityService.CreateCommand(
                "Solar Rooftop 10kW",
                UUID.randomUUID(),
                "Sharma Residency",
                null,
                null,
                null,
                "Solar Installation",
                "Solar",
                BigDecimal.valueOf(450000),
                null,
                LocalDate.now(clock),
                userId,
                null,
                null);

        when(opportunities.findByIdAndOrganizationId(any(), any())).thenAnswer(inv -> {
            Opportunity o = new Opportunity(
                    orgId, cmd.title(), cmd.customerId(), cmd.customerName(), null, null,
                    null, cmd.serviceName(), cmd.verticalCode(), cmd.value(), 10,
                    cmd.expectedCloseDate(), userId, "Aditya Singh", defaultPipeline, stageNew);
            return Optional.of(o);
        });

        OpportunityViews.Detail created = service.create(orgId, cmd, userId, "Aditya Singh");

        assertThat(created.title()).isEqualTo("Solar Rooftop 10kW");
        assertThat(created.status()).isEqualTo(OpportunityStatus.OPEN);
        assertThat(created.currentStage().name()).isEqualTo("New");
        verify(opportunities).save(any(Opportunity.class));
        verify(stageHistories).save(any(OpportunityStageHistory.class));
        verify(events).publish(any());
    }

    @Test
    void changeStageUpdatesStatusToWonAndRecordsDuration() {
        Opportunity opp = new Opportunity(
                orgId, "CCTV Project", null, null, null, null,
                null, "CCTV", "CCTV & Security", BigDecimal.valueOf(150000), 10,
                LocalDate.now(clock), userId, "Aditya Singh", defaultPipeline, stageNew);

        when(opportunities.findByIdAndOrganizationId(opp.getId(), orgId)).thenReturn(Optional.of(opp));
        when(pipelineService.getStageEntity(defaultPipeline.getId(), stageWon.getId())).thenReturn(stageWon);

        OpportunityStageHistory prevHist = new OpportunityStageHistory(
                opp, null, null, stageNew, "New", now.minusSeconds(86400 * 3), "Aditya", "Created");
        when(stageHistories.findTopByOpportunityIdAndExitedAtIsNullOrderByEnteredAtDesc(opp.getId()))
                .thenReturn(Optional.of(prevHist));

        OpportunityViews.Detail detail = service.changeStage(
                orgId, opp.getId(), new OpportunityService.ChangeStageCommand(stageWon.getId(), "Client accepted quote", null), "Aditya Singh");

        assertThat(opp.getStatus()).isEqualTo(OpportunityStatus.WON);
        assertThat(opp.getStage().getName()).isEqualTo("Won");
        assertThat(prevHist.getDurationSeconds()).isEqualTo(86400 * 3);
        verify(stageHistories).save(any(OpportunityStageHistory.class));
        verify(events).publish(any());
    }

    @Test
    void calculateAnalyticsCorrectlyComputesWeightedPipelineAndWinRate() {
        Opportunity o1 = new Opportunity(
                orgId, "Deal 1", null, null, null, null,
                null, "S1", "V1", BigDecimal.valueOf(100000), 50,
                null, userId, "Aditya", defaultPipeline, stageNew);
        o1.setStatus(OpportunityStatus.OPEN);

        Opportunity o2 = new Opportunity(
                orgId, "Deal 2", null, null, null, null,
                null, "S2", "V2", BigDecimal.valueOf(200000), 100,
                null, userId, "Aditya", defaultPipeline, stageWon);
        o2.setStatus(OpportunityStatus.WON);

        Opportunity o3 = new Opportunity(
                orgId, "Deal 3", null, null, null, null,
                null, "S3", "V3", BigDecimal.valueOf(50000), 0,
                null, userId, "Aditya", defaultPipeline, stageLost);
        o3.setStatus(OpportunityStatus.LOST);

        when(opportunities.findByOrganizationIdAndPipelineId(orgId, defaultPipeline.getId()))
                .thenReturn(List.of(o1, o2, o3));

        OpportunityViews.PipelineAnalytics analytics = service.getAnalytics(orgId, defaultPipeline.getId());

        assertThat(analytics.totalOpportunities()).isEqualTo(3);
        assertThat(analytics.openOpportunities()).isEqualTo(1);
        assertThat(analytics.wonOpportunities()).isEqualTo(1);
        assertThat(analytics.lostOpportunities()).isEqualTo(1);
        assertThat(analytics.pipelineValue()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        // Weighted: 100000 * 50% = 50000
        assertThat(analytics.weightedPipelineValue()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        // Win rate: 1 won / (1 won + 1 lost) = 50%
        assertThat(analytics.winRatePercentage()).isEqualTo(50.0);
    }
}
