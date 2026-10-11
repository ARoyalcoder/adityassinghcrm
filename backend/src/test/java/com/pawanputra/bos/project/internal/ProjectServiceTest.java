package com.pawanputra.bos.project.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import com.pawanputra.bos.project.api.ProjectEvents;
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

class ProjectServiceTest {

    private ProjectRepository projects;
    private ProjectStatusConfigRepository statusConfigs;
    private ProjectMemberRepository members;
    private MilestoneRepository milestones;
    private ProjectTaskRepository tasks;
    private TaskDependencyRepository dependencies;
    private ChecklistItemRepository checklists;
    private DeliverableRepository deliverables;
    private ProjectStatusService statusService;
    private UserDirectory users;
    private DomainEventPublisher events;
    private Clock clock;
    private ProjectService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-10T10:00:00Z");

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        statusConfigs = mock(ProjectStatusConfigRepository.class);
        members = mock(ProjectMemberRepository.class);
        milestones = mock(MilestoneRepository.class);
        tasks = mock(ProjectTaskRepository.class);
        dependencies = mock(TaskDependencyRepository.class);
        checklists = mock(ChecklistItemRepository.class);
        deliverables = mock(DeliverableRepository.class);
        statusService = mock(ProjectStatusService.class);
        users = mock(UserDirectory.class);
        events = mock(DomainEventPublisher.class);
        clock = Clock.fixed(now, ZoneOffset.UTC);

        service = new ProjectService(
                projects,
                statusConfigs,
                members,
                milestones,
                tasks,
                dependencies,
                checklists,
                deliverables,
                statusService,
                users,
                events,
                clock);
    }

    private Project createMockProject() {
        return new Project(
                orgId,
                "PRJ-0001",
                "Solar Installation",
                "Installation description",
                UUID.randomUUID(),
                "Acme Corp",
                UUID.randomUUID(),
                "ORD-0001",
                UUID.randomUUID(),
                "Solar Setup",
                userId,
                "Alice PM",
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 11, 15),
                "PLANNING",
                "HIGH",
                new BigDecimal("50000.00"),
                new BigDecimal("65000.00"),
                "admin");
    }

    @Test
    void createProject_generatesProjectNumberAndPublishesEvent() {
        ProjectStatusConfig planningConfig = new ProjectStatusConfig(
                orgId, "PLANNING", "Planning", 0, "#64748b", "PLANNING", true, false, false, "Initial stage");

        when(statusConfigs.findFirstByOrganizationIdAndIsDefaultTrue(orgId))
                .thenReturn(Optional.of(planningConfig));
        when(projects.countByOrganizationId(orgId)).thenReturn(0L);
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectService.CreateProjectCmd cmd = new ProjectService.CreateProjectCmd(
                "Solar Roof Installation",
                "Installation for client rooftop",
                UUID.randomUUID(),
                "Acme Corp",
                UUID.randomUUID(),
                "ORD-2026-0001",
                UUID.randomUUID(),
                "Solar Setup",
                userId,
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 11, 15),
                "PLANNING",
                "HIGH",
                new BigDecimal("50000.00"),
                new BigDecimal("65000.00"),
                "Some notes");

        ProjectViews.ProjectDetail detail = service.createProject(orgId, cmd, userId, "admin");

        assertThat(detail).isNotNull();
        assertThat(detail.title()).isEqualTo("Solar Roof Installation");
        assertThat(detail.projectNumber()).isEqualTo("PRJ-0001");
        assertThat(detail.status()).isEqualTo("PLANNING");
        assertThat(detail.budget()).isEqualByComparingTo("50000.00");
        assertThat(detail.revenue()).isEqualByComparingTo("65000.00");
        assertThat(detail.priority()).isEqualTo("HIGH");

        verify(projects).save(any(Project.class));
        verify(events).publish(any(ProjectEvents.ProjectCreatedEvent.class));
    }

    @Test
    void updateProjectStatus_updatesStatusAndPublishesEvent() {
        Project project = createMockProject();

        ProjectStatusConfig inProgressStatus = new ProjectStatusConfig(
                orgId, "IN_PROGRESS", "In Progress", 1, "#3b82f6", "IN_PROGRESS", false, false, false, "Active");

        when(projects.findByIdAndOrganizationId(project.getId(), orgId)).thenReturn(Optional.of(project));
        when(statusConfigs.findByOrganizationIdAndCode(orgId, "IN_PROGRESS")).thenReturn(Optional.of(inProgressStatus));
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectViews.ProjectDetail updated = service.updateProjectStatus(
                orgId, project.getId(), "IN_PROGRESS", "Started work", "admin");

        assertThat(updated.status()).isEqualTo("IN_PROGRESS");
        verify(events).publish(any(ProjectEvents.ProjectStatusChangedEvent.class));
    }

    @Test
    void addMilestone_savesAndReturnsMilestoneView() {
        Project project = createMockProject();

        when(projects.findByIdAndOrganizationId(project.getId(), orgId)).thenReturn(Optional.of(project));
        when(milestones.countByProjectId(project.getId())).thenReturn(0L);
        when(milestones.save(any(Milestone.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectService.AddMilestoneCmd cmd = new ProjectService.AddMilestoneCmd(
                "Phase 1 Design",
                "Design phase signoff",
                LocalDate.of(2026, 10, 20),
                1);

        ProjectViews.MilestoneView view = service.addMilestone(orgId, project.getId(), cmd, "admin");

        assertThat(view.title()).isEqualTo("Phase 1 Design");
        assertThat(view.status()).isEqualTo("PENDING");
        verify(milestones).save(any(Milestone.class));
    }

    @Test
    void completeMilestone_updatesProgressAndPublishesEvent() {
        Project project = createMockProject();
        Milestone m1 = new Milestone(project, "M1", "Desc", LocalDate.now(), 1, "admin");

        when(projects.findByIdAndOrganizationId(project.getId(), orgId)).thenReturn(Optional.of(project));
        when(milestones.findById(m1.getId())).thenReturn(Optional.of(m1));
        when(milestones.save(any(Milestone.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectViews.MilestoneView completed = service.completeMilestone(orgId, project.getId(), m1.getId(), "admin");

        assertThat(completed.status()).isEqualTo("COMPLETED");
        verify(events).publish(any(ProjectEvents.MilestoneCompletedEvent.class));
    }

    @Test
    void addDependency_detectsSelfDependency() {
        Project project = createMockProject();
        ProjectTask t1 = new ProjectTask(project, null, "Task 1", null, null, null, "MEDIUM", null, null, BigDecimal.ZERO, 1, "admin");

        when(projects.findByIdAndOrganizationId(project.getId(), orgId)).thenReturn(Optional.of(project));
        when(tasks.findById(t1.getId())).thenReturn(Optional.of(t1));

        assertThatThrownBy(() -> service.addDependency(orgId, project.getId(), t1.getId(), t1.getId(), "FINISH_TO_START", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A task cannot depend on itself");
    }
}
