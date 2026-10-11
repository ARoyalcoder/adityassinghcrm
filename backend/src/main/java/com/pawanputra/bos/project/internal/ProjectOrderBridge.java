package com.pawanputra.bos.project.internal;

import com.pawanputra.bos.order.api.OrderProjectBridge;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
public class ProjectOrderBridge implements OrderProjectBridge {

    private static final Logger log = LoggerFactory.getLogger(ProjectOrderBridge.class);

    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final Clock clock;

    public ProjectOrderBridge(
            ProjectService projectService,
            ProjectRepository projects,
            Clock clock) {
        this.projectService = projectService;
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void onOrderConfirmed(UUID organizationId, OrderProjectContext context) {
        log.info("ProjectOrderBridge triggered for order {} (id {})", context.orderNumber(), context.orderId());

        // Check if a project already exists for this order
        if (projects.findByOrganizationIdAndOrderId(organizationId, context.orderId()).isPresent()) {
            log.info("Project already exists for order {}, skipping creation", context.orderNumber());
            return;
        }

        UUID primaryServiceId = null;
        String primaryServiceName = null;
        if (context.items() != null && !context.items().isEmpty()) {
            primaryServiceId = context.items().get(0).serviceId();
            primaryServiceName = context.items().get(0).serviceName();
        }

        LocalDate startDate = LocalDate.now(clock);
        LocalDate expectedCompletion = context.deliveryDate() != null
                ? context.deliveryDate()
                : startDate.plusMonths(1);

        String title = "Project for " + context.orderNumber() + " - " + context.customerName();

        ProjectService.CreateProjectCmd cmd = new ProjectService.CreateProjectCmd(
                title,
                "Provisioned from confirmed Order " + context.orderNumber() + ". " + (context.notes() != null ? context.notes() : ""),
                context.customerId(),
                context.customerName(),
                context.orderId(),
                context.orderNumber(),
                primaryServiceId,
                primaryServiceName,
                context.ownerId(),
                startDate,
                expectedCompletion,
                "PLANNING",
                "MEDIUM",
                context.finalAmount() != null ? context.finalAmount().multiply(BigDecimal.valueOf(0.70)) : BigDecimal.ZERO, // Target budget
                context.finalAmount() != null ? context.finalAmount() : BigDecimal.ZERO, // Contract revenue
                context.notes());

        ProjectViews.ProjectDetail created = projectService.createProject(
                organizationId,
                cmd,
                context.ownerId() != null ? context.ownerId() : UUID.randomUUID(),
                context.ownerName() != null ? context.ownerName() : "System");

        log.info("Successfully provisioned project {} ({}) from order {}",
                created.projectNumber(), created.id(), context.orderNumber());

        // Create default kick-off milestone
        projectService.addMilestone(
                organizationId,
                created.id(),
                new ProjectService.AddMilestoneCmd(
                        "Project Kickoff & Requirements Finalization",
                        "Initial kickoff meeting with " + context.customerName() + " and site survey",
                        startDate.plusDays(7),
                        1),
                "System");

        // Create completion & delivery milestone
        projectService.addMilestone(
                organizationId,
                created.id(),
                new ProjectService.AddMilestoneCmd(
                        "Final Testing & Client Signoff",
                        "Delivery handover and signoff for " + context.orderNumber(),
                        expectedCompletion,
                        2),
                "System");
    }
}
