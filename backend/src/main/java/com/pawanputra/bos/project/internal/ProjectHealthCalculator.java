package com.pawanputra.bos.project.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ProjectHealthCalculator {

    private ProjectHealthCalculator() {
    }

    public static ProjectViews.ProjectHealth calculate(Project project, LocalDate today) {
        return calculate(
                project,
                project.getTasks() != null ? project.getTasks() : List.of(),
                project.getMembers() != null ? project.getMembers().size() : 0,
                project.getDeliverables() != null ? project.getDeliverables() : List.of(),
                today
        );
    }

    public static ProjectViews.ProjectHealth calculate(
            Project project,
            List<ProjectTask> tasks,
            int memberCount,
            List<Deliverable> deliverables,
            LocalDate today) {
        // 1. Schedule Health
        int scheduleScore = calculateScheduleHealth(project, today);

        // 2. Budget Health
        int budgetScore = calculateBudgetHealth(project);

        // 3. Task Health
        int taskScore = calculateTaskHealth(tasks, today);

        // 4. Client Approval Health
        int approvalScore = calculateApprovalHealth(deliverables);

        // 5. Team Capacity Health
        int capacityScore = calculateTeamCapacityHealth(memberCount, tasks);

        // Overall Score (Weighted: Schedule 25%, Budget 25%, Task 20%, Approval 15%, Capacity 15%)
        int overallScore = (int) Math.round(
                (scheduleScore * 0.25) +
                (budgetScore * 0.25) +
                (taskScore * 0.20) +
                (approvalScore * 0.15) +
                (capacityScore * 0.15)
        );
        overallScore = Math.max(0, Math.min(100, overallScore));

        // Risk alerts
        List<ProjectViews.RiskAlert> alerts = detectRisks(project, today, budgetScore, scheduleScore, approvalScore, tasks, deliverables);

        // Profitability metrics
        ProjectViews.ProjectProfitability profitability = calculateProfitability(project);

        return new ProjectViews.ProjectHealth(
                overallScore,
                scheduleScore,
                budgetScore,
                taskScore,
                approvalScore,
                capacityScore,
                profitability,
                alerts
        );
    }

    private static int calculateScheduleHealth(Project project, LocalDate today) {
        if ("COMPLETED".equalsIgnoreCase(project.getStatus())) {
            return 100;
        }

        LocalDate start = project.getStartDate();
        LocalDate due = project.getExpectedCompletionDate();

        if (start == null || due == null || !due.isAfter(start)) {
            return 100;
        }

        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, due);
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(start, today);

        if (elapsedDays <= 0) {
            return 100; // Not yet started
        }

        if (today.isAfter(due)) {
            // Already past due date and not completed
            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(due, today);
            int penalty = (int) Math.min(60, overdueDays * 5);
            return Math.max(10, 60 - penalty);
        }

        double expectedProgress = Math.min(100.0, ((double) elapsedDays / totalDays) * 100.0);
        double actualProgress = project.getProgressPercentage();

        double diff = actualProgress - expectedProgress;
        if (diff >= 0) {
            return Math.min(100, 90 + (int) (diff * 0.2));
        } else {
            // Falling behind expected timeline
            int score = (int) Math.round(100.0 + (diff * 1.5));
            return Math.max(15, Math.min(100, score));
        }
    }

    private static int calculateBudgetHealth(Project project) {
        BigDecimal budget = project.getBudget();
        BigDecimal actualCost = project.getCost();

        if (budget == null || budget.compareTo(BigDecimal.ZERO) <= 0) {
            return 100; // No budget defined
        }

        if (actualCost == null || actualCost.compareTo(BigDecimal.ZERO) <= 0) {
            return 100;
        }

        BigDecimal costRatio = actualCost.divide(budget, 4, RoundingMode.HALF_UP);
        if (costRatio.compareTo(BigDecimal.ONE) <= 0) {
            // Within budget: e.g. 80% consumed -> 90% health
            double remainingPct = 1.0 - costRatio.doubleValue();
            return (int) Math.round(80.0 + (remainingPct * 20.0));
        } else {
            // Over budget
            double overrunPct = (costRatio.doubleValue() - 1.0) * 100.0;
            int score = (int) Math.round(75.0 - (overrunPct * 2.0));
            return Math.max(10, Math.min(75, score));
        }
    }

    private static int calculateTaskHealth(List<ProjectTask> tasks, LocalDate today) {
        if (tasks == null || tasks.isEmpty()) {
            return 100;
        }

        int total = tasks.size();
        int completed = 0;
        int blocked = 0;
        int overdue = 0;

        for (ProjectTask t : tasks) {
            if ("COMPLETED".equalsIgnoreCase(t.getStatus())) {
                completed++;
            } else if ("BLOCKED".equalsIgnoreCase(t.getStatus())) {
                blocked++;
            } else if (t.getDueDate() != null && today.isAfter(t.getDueDate())) {
                overdue++;
            }
        }

        double completionRatio = (double) completed / total;
        double overdueRatio = (double) overdue / total;
        double blockedRatio = (double) blocked / total;

        double baseScore = 70.0 + (completionRatio * 30.0);
        baseScore -= (overdueRatio * 40.0);
        baseScore -= (blockedRatio * 30.0);

        return Math.max(10, Math.min(100, (int) Math.round(baseScore)));
    }

    private static int calculateApprovalHealth(List<Deliverable> deliverables) {
        if (deliverables == null || deliverables.isEmpty()) {
            return 100; // No deliverables waiting approval
        }

        int total = deliverables.size();
        int accepted = 0;
        int rejected = 0;
        int pending = 0;

        for (Deliverable d : deliverables) {
            if ("ACCEPTED".equalsIgnoreCase(d.getStatus())) {
                accepted++;
            } else if ("REJECTED".equalsIgnoreCase(d.getStatus())) {
                rejected++;
            } else if ("SUBMITTED".equalsIgnoreCase(d.getStatus()) || "PENDING".equalsIgnoreCase(d.getStatus())) {
                pending++;
            }
        }

        double acceptedRatio = (double) accepted / total;
        double rejectedRatio = (double) rejected / total;
        double pendingRatio = (double) pending / total;

        double score = 60.0 + (acceptedRatio * 40.0) - (rejectedRatio * 50.0) - (pendingRatio * 15.0);
        return Math.max(15, Math.min(100, (int) Math.round(score)));
    }

    private static int calculateTeamCapacityHealth(int memberCount, List<ProjectTask> tasks) {
        if (memberCount <= 0) {
            return 80;
        }
        if (tasks == null || tasks.isEmpty()) {
            return 95;
        }

        long activeTasks = tasks.stream()
                .filter(t -> !"COMPLETED".equalsIgnoreCase(t.getStatus()))
                .count();

        // Tasks per member
        double ratio = (double) activeTasks / memberCount;
        if (ratio <= 4.0) {
            return 95; // Well-balanced workload
        } else if (ratio <= 8.0) {
            return 80; // Moderate workload
        } else if (ratio <= 12.0) {
            return 65; // High workload
        } else {
            return 45; // Overloaded capacity
        }
    }

    public static ProjectViews.ProjectProfitability calculateProfitability(Project project) {
        BigDecimal revenue = project.getRevenue() != null ? project.getRevenue() : BigDecimal.ZERO;
        BigDecimal materialCost = project.getMaterialCost() != null ? project.getMaterialCost() : BigDecimal.ZERO;
        BigDecimal labourCost = project.getLabourCost() != null ? project.getLabourCost() : BigDecimal.ZERO;
        BigDecimal vendorCost = project.getVendorCost() != null ? project.getVendorCost() : BigDecimal.ZERO;
        BigDecimal travelCost = project.getTravelCost() != null ? project.getTravelCost() : BigDecimal.ZERO;
        BigDecimal otherExpenses = project.getOtherExpenses() != null ? project.getOtherExpenses() : BigDecimal.ZERO;

        BigDecimal itemizedCost = materialCost.add(labourCost).add(vendorCost).add(travelCost).add(otherExpenses);
        BigDecimal actualCost = itemizedCost.compareTo(BigDecimal.ZERO) > 0
                ? itemizedCost
                : (project.getCost() != null ? project.getCost() : BigDecimal.ZERO);
        BigDecimal expectedCost = project.getExpectedCost() != null && project.getExpectedCost().compareTo(BigDecimal.ZERO) > 0
                ? project.getExpectedCost()
                : (project.getBudget() != null && project.getBudget().compareTo(BigDecimal.ZERO) > 0 ? project.getBudget() : actualCost);

        BigDecimal grossProfit = revenue.subtract(actualCost);
        BigDecimal grossMargin = BigDecimal.ZERO;
        if (revenue.compareTo(BigDecimal.ZERO) > 0) {
            grossMargin = grossProfit.divide(revenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Projected final cost: if in progress, interpolate remaining expected cost
        int progress = Math.max(1, project.getProgressPercentage());
        BigDecimal projectedFinalCost = actualCost;
        if (progress < 100 && actualCost.compareTo(BigDecimal.ZERO) > 0) {
            projectedFinalCost = actualCost.divide(BigDecimal.valueOf(progress), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            if (projectedFinalCost.compareTo(expectedCost) < 0) {
                projectedFinalCost = expectedCost;
            }
        } else if (actualCost.compareTo(BigDecimal.ZERO) == 0) {
            projectedFinalCost = expectedCost;
        }

        return new ProjectViews.ProjectProfitability(
                revenue,
                materialCost,
                labourCost,
                vendorCost,
                travelCost,
                otherExpenses,
                actualCost,
                expectedCost,
                grossProfit,
                grossMargin,
                projectedFinalCost
        );
    }

    private static List<ProjectViews.RiskAlert> detectRisks(
            Project project,
            LocalDate today,
            int budgetScore,
            int scheduleScore,
            int approvalScore,
            List<ProjectTask> tasks,
            List<Deliverable> deliverables) {
        List<ProjectViews.RiskAlert> alerts = new ArrayList<>();

        // 1. Budget overrun alert
        if (project.getBudget() != null && project.getBudget().compareTo(BigDecimal.ZERO) > 0) {
            if (project.getCost().compareTo(project.getBudget()) > 0) {
                BigDecimal overrun = project.getCost().subtract(project.getBudget());
                alerts.add(new ProjectViews.RiskAlert(
                        "BUDGET_OVERRUN",
                        "CRITICAL",
                        "Budget Overrun",
                        String.format("Actual expenditure exceeds allocated budget by ₹%s.", overrun.toPlainString())
                ));
            } else if (budgetScore < 70) {
                alerts.add(new ProjectViews.RiskAlert(
                        "BUDGET_WARNING",
                        "WARNING",
                        "High Budget Consumption",
                        "Expenditure rate is approaching allocated project budget threshold."
                ));
            }
        }

        // 2. Schedule delay alert
        if (project.getExpectedCompletionDate() != null && today.isAfter(project.getExpectedCompletionDate())
                && !"COMPLETED".equalsIgnoreCase(project.getStatus())) {
            alerts.add(new ProjectViews.RiskAlert(
                    "SCHEDULE_DELAY",
                    "CRITICAL",
                    "Schedule Delay",
                    "Project has passed its target completion date without completion."
            ));
        } else if (scheduleScore < 60) {
            alerts.add(new ProjectViews.RiskAlert(
                    "SCHEDULE_BEHIND",
                    "WARNING",
                    "Schedule Behind Target",
                    "Actual completion progress is lagging significantly behind the expected timeline."
            ));
        }

        // 3. Margin decline alert
        ProjectViews.ProjectProfitability prof = calculateProfitability(project);
        if (prof.revenue().compareTo(BigDecimal.ZERO) > 0 && prof.grossMargin().compareTo(BigDecimal.valueOf(15.00)) < 0) {
            alerts.add(new ProjectViews.RiskAlert(
                    "MARGIN_DECLINE",
                    "WARNING",
                    "Margin Decline",
                    String.format("Gross margin has declined to %s%% (below target 15%%).", prof.grossMargin().toPlainString())
            ));
        }

        // 4. Task bottleneck alert
        List<ProjectTask> taskList = tasks != null ? tasks : List.of();
        long blockedTasks = taskList.stream()
                .filter(t -> "BLOCKED".equalsIgnoreCase(t.getStatus()))
                .count();
        long overdueTasks = taskList.stream()
                .filter(t -> t.getDueDate() != null && today.isAfter(t.getDueDate()) && !"COMPLETED".equalsIgnoreCase(t.getStatus()))
                .count();

        if (blockedTasks > 0 || overdueTasks >= 2) {
            alerts.add(new ProjectViews.RiskAlert(
                    "TASK_BOTTLENECK",
                    "WARNING",
                    "Task Bottleneck",
                    String.format("Found %d blocked tasks and %d overdue tasks obstructing execution.", blockedTasks, overdueTasks)
            ));
        }

        // 5. Approval pending alert
        List<Deliverable> delivList = deliverables != null ? deliverables : List.of();
        long pendingApprovals = delivList.stream()
                .filter(d -> "SUBMITTED".equalsIgnoreCase(d.getStatus()))
                .count();
        if (pendingApprovals > 0) {
            alerts.add(new ProjectViews.RiskAlert(
                    "APPROVAL_PENDING",
                    "INFO",
                    "Client Approval Pending",
                    String.format("%d deliverables submitted and awaiting client sign-off.", pendingApprovals)
            ));
        }

        return alerts;
    }
}
