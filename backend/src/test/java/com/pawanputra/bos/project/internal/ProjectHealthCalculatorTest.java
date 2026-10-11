package com.pawanputra.bos.project.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectHealthCalculatorTest {

    private Project createBaseProject() {
        UUID orgId = UUID.randomUUID();
        return new Project(
                orgId,
                "PRJ-0001",
                "Solar Farm Installation",
                "Installation description",
                UUID.randomUUID(),
                "Customer A",
                UUID.randomUUID(),
                "ORD-001",
                UUID.randomUUID(),
                "Solar Engineering",
                UUID.randomUUID(),
                "Manager X",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                "IN_PROGRESS",
                "HIGH",
                new BigDecimal("100000.00"),
                new BigDecimal("150000.00"),
                "admin"
        );
    }

    @Test
    @DisplayName("Calculates health intelligence metrics and overall weighted score")
    void calculatesHealthScores() {
        Project project = createBaseProject();
        project.setProgressPercentage(70);
        project.setBudget(new BigDecimal("100000.00"));
        project.setCost(new BigDecimal("80000.00"));

        LocalDate today = LocalDate.of(2026, 10, 20);

        // Tasks
        ProjectTask t1 = new ProjectTask(project, null, "Task 1", null, null, null, "HIGH", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 25), BigDecimal.TEN, 1, "admin");
        t1.setStatus("COMPLETED");

        ProjectTask t2 = new ProjectTask(project, null, "Task 2", null, null, null, "HIGH", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 28), BigDecimal.TEN, 2, "admin");
        t2.setStatus("IN_PROGRESS");

        List<ProjectTask> tasks = List.of(t1, t2);
        int memberCount = 2;

        // Deliverables
        Deliverable d1 = new Deliverable(project, null, "D1", "Desc", LocalDate.of(2026, 10, 22), "Criteria", "admin");
        d1.setStatus("ACCEPTED");
        List<Deliverable> deliverables = List.of(d1);

        ProjectViews.ProjectHealth health = ProjectHealthCalculator.calculate(project, tasks, memberCount, deliverables, today);

        assertThat(health).isNotNull();
        assertThat(health.overallScore()).isBetween(0, 100);
        assertThat(health.scheduleHealth()).isGreaterThan(0);
        assertThat(health.budgetHealth()).isGreaterThan(0);
        assertThat(health.taskHealth()).isGreaterThan(0);
        assertThat(health.clientApprovalHealth()).isGreaterThan(0);
        assertThat(health.teamCapacityHealth()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Calculates profitability breakdown with gross profit, margin, and projected final cost")
    void calculatesProfitabilityBreakdown() {
        Project project = createBaseProject();
        project.setRevenue(new BigDecimal("200000.00"));
        project.setMaterialCost(new BigDecimal("50000.00"));
        project.setLabourCost(new BigDecimal("30000.00"));
        project.setVendorCost(new BigDecimal("20000.00"));
        project.setTravelCost(new BigDecimal("5000.00"));
        project.setOtherExpenses(new BigDecimal("5000.00"));
        project.setExpectedCost(new BigDecimal("120000.00"));
        project.setProgressPercentage(50);
        project.recalculateActualCost();

        ProjectViews.ProjectProfitability prof = ProjectHealthCalculator.calculateProfitability(project);

        assertThat(prof).isNotNull();
        assertThat(prof.revenue()).isEqualByComparingTo("200000.00");
        assertThat(prof.materialCost()).isEqualByComparingTo("50000.00");
        assertThat(prof.labourCost()).isEqualByComparingTo("30000.00");
        assertThat(prof.vendorCost()).isEqualByComparingTo("20000.00");
        assertThat(prof.travelCost()).isEqualByComparingTo("5000.00");
        assertThat(prof.otherExpenses()).isEqualByComparingTo("5000.00");
        // Actual cost = 50k + 30k + 20k + 5k + 5k = 110,000
        assertThat(prof.actualCost()).isEqualByComparingTo("110000.00");
        assertThat(prof.expectedCost()).isEqualByComparingTo("120000.00");
        // Gross Profit = 200,000 - 110,000 = 90,000
        assertThat(prof.grossProfit()).isEqualByComparingTo("90000.00");
        // Gross Margin = (90,000 / 200,000) * 100 = 45.00%
        assertThat(prof.grossMargin()).isEqualByComparingTo("45.00");
        // Projected Final Cost: at 50% progress with 110k spent => 110k / 0.5 = 220,000
        assertThat(prof.projectedFinalCost()).isEqualByComparingTo("220000.00");
    }

    @Test
    @DisplayName("Detects all risk alerts: budget overrun, schedule delay, margin decline, task bottleneck, approval pending")
    void detectsRiskAlerts() {
        Project project = createBaseProject();
        project.setBudget(new BigDecimal("100000.00"));
        // Actual cost > budget => BUDGET_OVERRUN
        project.setMaterialCost(new BigDecimal("110000.00"));
        project.setRevenue(new BigDecimal("115000.00")); // Margin = 5,000 / 115,000 = 4.3% (< 15%) => MARGIN_DECLINE
        project.recalculateActualCost();

        // Project past expected completion date => SCHEDULE_DELAY
        project.setExpectedCompletionDate(LocalDate.of(2026, 10, 10));
        LocalDate today = LocalDate.of(2026, 10, 15);

        // Blocked and overdue tasks => TASK_BOTTLENECK
        ProjectTask blockedTask = new ProjectTask(project, null, "Blocked Task", null, null, null, "HIGH", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 12), BigDecimal.TEN, 1, "admin");
        blockedTask.setStatus("BLOCKED");

        ProjectTask overdueTask = new ProjectTask(project, null, "Overdue Task", null, null, null, "HIGH", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 12), BigDecimal.TEN, 2, "admin");
        overdueTask.setStatus("IN_PROGRESS");

        List<ProjectTask> tasks = List.of(blockedTask, overdueTask);

        // Submitted deliverable => APPROVAL_PENDING
        Deliverable submittedDeliverable = new Deliverable(project, null, "Deliverable Pending", "Desc", LocalDate.of(2026, 10, 14), "Criteria", "admin");
        submittedDeliverable.setStatus("SUBMITTED");
        List<Deliverable> deliverables = List.of(submittedDeliverable);

        ProjectViews.ProjectHealth health = ProjectHealthCalculator.calculate(project, tasks, 1, deliverables, today);

        List<String> alertCodes = health.riskAlerts().stream().map(ProjectViews.RiskAlert::code).toList();
        assertThat(alertCodes).contains("BUDGET_OVERRUN");
        assertThat(alertCodes).contains("SCHEDULE_DELAY");
        assertThat(alertCodes).contains("MARGIN_DECLINE");
        assertThat(alertCodes).contains("TASK_BOTTLENECK");
        assertThat(alertCodes).contains("APPROVAL_PENDING");
    }
}
