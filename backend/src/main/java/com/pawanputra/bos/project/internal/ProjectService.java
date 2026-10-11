package com.pawanputra.bos.project.internal;

import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import com.pawanputra.bos.project.api.ProjectDirectory;
import com.pawanputra.bos.project.api.ProjectEvents;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectService implements ProjectDirectory {

    private final ProjectRepository projects;
    private final ProjectStatusConfigRepository statusConfigs;
    private final ProjectMemberRepository members;
    private final MilestoneRepository milestones;
    private final ProjectTaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final ChecklistItemRepository checklists;
    private final DeliverableRepository deliverables;
    private final ProjectStatusService statusService;
    private final UserDirectory users;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ProjectService(
            ProjectRepository projects,
            ProjectStatusConfigRepository statusConfigs,
            ProjectMemberRepository members,
            MilestoneRepository milestones,
            ProjectTaskRepository tasks,
            TaskDependencyRepository dependencies,
            ChecklistItemRepository checklists,
            DeliverableRepository deliverables,
            ProjectStatusService statusService,
            UserDirectory users,
            DomainEventPublisher events,
            Clock clock) {
        this.projects = projects;
        this.statusConfigs = statusConfigs;
        this.members = members;
        this.milestones = milestones;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.checklists = checklists;
        this.deliverables = deliverables;
        this.statusService = statusService;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    // Command records
    public record CreateProjectCmd(
            String title,
            String description,
            UUID customerId,
            String customerName,
            UUID orderId,
            String orderNumber,
            UUID serviceId,
            String serviceName,
            UUID projectManagerId,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            String status,
            String priority,
            BigDecimal budget,
            BigDecimal revenue,
            String notes) {
    }

    public record UpdateProjectCmd(
            String title,
            String description,
            UUID projectManagerId,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            LocalDate actualCompletionDate,
            String priority,
            BigDecimal budget,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal materialCost,
            BigDecimal labourCost,
            BigDecimal vendorCost,
            BigDecimal travelCost,
            BigDecimal otherExpenses,
            BigDecimal expectedCost,
            String notes) {
    }

    public record UpdateProfitabilityCmd(
            BigDecimal revenue,
            BigDecimal materialCost,
            BigDecimal labourCost,
            BigDecimal vendorCost,
            BigDecimal travelCost,
            BigDecimal otherExpenses,
            BigDecimal expectedCost) {
    }

    public record ProjectFilter(
            String status,
            UUID customerId,
            UUID projectManagerId,
            String priority,
            String search) {
    }

    public record AddMemberCmd(
            UUID userId,
            String role) {
    }

    public record AddMilestoneCmd(
            String title,
            String description,
            LocalDate dueDate,
            int displayOrder) {
    }

    public record UpdateMilestoneCmd(
            String title,
            String description,
            LocalDate dueDate,
            int displayOrder,
            String status) {
    }

    public record AddTaskCmd(
            UUID milestoneId,
            String title,
            String description,
            UUID assignedToId,
            String priority,
            LocalDate startDate,
            LocalDate dueDate,
            BigDecimal estimatedHours,
            int displayOrder,
            List<UUID> dependsOnTaskIds) {
    }

    public record UpdateTaskCmd(
            UUID milestoneId,
            String title,
            String description,
            UUID assignedToId,
            String priority,
            LocalDate startDate,
            LocalDate dueDate,
            BigDecimal estimatedHours,
            BigDecimal actualHours,
            String status) {
    }

    public record AddDeliverableCmd(
            UUID milestoneId,
            String title,
            String description,
            LocalDate dueDate,
            String acceptanceCriteria,
            String fileUrl) {
    }

    // --- Project Operations ---

    public ProjectViews.ProjectDetail createProject(
            UUID orgId, CreateProjectCmd cmd, UUID actorId, String actorName) {
        statusService.ensureDefaultStatuses(orgId);

        String pmName = actorName;
        if (cmd.projectManagerId() != null) {
            pmName = users.find(orgId, cmd.projectManagerId())
                    .map(UserDirectory.UserSummary::fullName)
                    .orElse(actorName);
        }

        String status = cmd.status();
        if (status == null || status.isBlank()) {
            status = statusConfigs.findFirstByOrganizationIdAndIsDefaultTrue(orgId)
                    .map(ProjectStatusConfig::getCode)
                    .orElse("PLANNING");
        }

        String projectNumber = generateProjectNumber(orgId);
        Project project = new Project(
                orgId,
                projectNumber,
                cmd.title().trim(),
                cmd.description(),
                cmd.customerId(),
                cmd.customerName(),
                cmd.orderId(),
                cmd.orderNumber(),
                cmd.serviceId(),
                cmd.serviceName(),
                cmd.projectManagerId() != null ? cmd.projectManagerId() : actorId,
                pmName,
                cmd.startDate() != null ? cmd.startDate() : LocalDate.now(clock),
                cmd.expectedCompletionDate() != null ? cmd.expectedCompletionDate() : LocalDate.now(clock).plusMonths(1),
                status,
                cmd.priority(),
                cmd.budget(),
                cmd.revenue(),
                actorName);

        project.setNotes(cmd.notes());

        Project saved = projects.save(project);

        // Add PM as initial project member
        ProjectMember pmMember = new ProjectMember(
                saved,
                saved.getProjectManagerId(),
                saved.getProjectManagerName(),
                null,
                "PROJECT_MANAGER",
                actorName);
        saved.addMember(pmMember);
        members.save(pmMember);

        // Publish event
        events.publish(ProjectEvents.ProjectCreatedEvent.of(
                saved.getId(),
                orgId,
                saved.getProjectNumber(),
                saved.getTitle(),
                saved.getCustomerId(),
                saved.getCustomerName(),
                saved.getOrderId(),
                saved.getOrderNumber(),
                saved.getProjectManagerId(),
                saved.getBudget(),
                saved.getRevenue(),
                saved.getStatus(),
                actorName));

        return toDetail(saved);
    }

    public ProjectViews.ProjectDetail updateProject(
            UUID orgId, UUID projectId, UpdateProjectCmd cmd, String actor) {
        Project p = getEntity(orgId, projectId);

        if (cmd.title() != null && !cmd.title().isBlank()) {
            p.setTitle(cmd.title().trim());
        }
        p.setDescription(cmd.description());
        if (cmd.projectManagerId() != null) {
            p.setProjectManagerId(cmd.projectManagerId());
            users.find(orgId, cmd.projectManagerId())
                    .ifPresent(u -> p.setProjectManagerName(u.fullName()));
        }
        if (cmd.startDate() != null) {
            p.setStartDate(cmd.startDate());
        }
        if (cmd.expectedCompletionDate() != null) {
            p.setExpectedCompletionDate(cmd.expectedCompletionDate());
        }
        p.setActualCompletionDate(cmd.actualCompletionDate());
        if (cmd.priority() != null) {
            p.setPriority(cmd.priority());
        }
        if (cmd.budget() != null) {
            p.setBudget(cmd.budget());
        }
        if (cmd.revenue() != null) {
            p.setRevenue(cmd.revenue());
        }
        if (cmd.materialCost() != null) {
            p.setMaterialCost(cmd.materialCost());
        }
        if (cmd.labourCost() != null) {
            p.setLabourCost(cmd.labourCost());
        }
        if (cmd.vendorCost() != null) {
            p.setVendorCost(cmd.vendorCost());
        }
        if (cmd.travelCost() != null) {
            p.setTravelCost(cmd.travelCost());
        }
        if (cmd.otherExpenses() != null) {
            p.setOtherExpenses(cmd.otherExpenses());
        }
        if (cmd.expectedCost() != null) {
            p.setExpectedCost(cmd.expectedCost());
        }
        if (cmd.cost() != null) {
            p.setCost(cmd.cost());
        } else {
            p.recalculateActualCost();
        }
        p.setNotes(cmd.notes());
        p.setUpdatedAt(Instant.now(clock));
        p.setUpdatedBy(actor);

        return toDetail(projects.save(p));
    }

    public ProjectViews.ProjectDetail updateProfitability(
            UUID orgId, UUID projectId, UpdateProfitabilityCmd cmd, String actor) {
        Project p = getEntity(orgId, projectId);
        if (cmd.revenue() != null) {
            p.setRevenue(cmd.revenue());
        }
        if (cmd.materialCost() != null) {
            p.setMaterialCost(cmd.materialCost());
        }
        if (cmd.labourCost() != null) {
            p.setLabourCost(cmd.labourCost());
        }
        if (cmd.vendorCost() != null) {
            p.setVendorCost(cmd.vendorCost());
        }
        if (cmd.travelCost() != null) {
            p.setTravelCost(cmd.travelCost());
        }
        if (cmd.otherExpenses() != null) {
            p.setOtherExpenses(cmd.otherExpenses());
        }
        if (cmd.expectedCost() != null) {
            p.setExpectedCost(cmd.expectedCost());
        }
        p.recalculateActualCost();
        p.setUpdatedAt(Instant.now(clock));
        p.setUpdatedBy(actor);

        return toDetail(projects.save(p));
    }

    public ProjectViews.ProjectDetail updateProjectStatus(
            UUID orgId, UUID projectId, String targetStatus, String notes, String actor) {
        Project p = getEntity(orgId, projectId);
        String fromStatus = p.getStatus();

        if (fromStatus.equalsIgnoreCase(targetStatus)) {
            return toDetail(p);
        }

        // Validate status exists
        ProjectStatusConfig config = statusConfigs.findByOrganizationIdAndCode(orgId, targetStatus)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project status: " + targetStatus));

        p.setStatus(config.getCode());
        p.setUpdatedAt(Instant.now(clock));
        p.setUpdatedBy(actor);

        if (config.isCompleted()) {
            p.setActualCompletionDate(LocalDate.now(clock));
            p.setProgressPercentage(100);
        } else if (p.getActualCompletionDate() != null && !config.isCompleted()) {
            p.setActualCompletionDate(null);
        }

        Project saved = projects.save(p);

        events.publish(ProjectEvents.ProjectStatusChangedEvent.of(
                saved.getId(),
                orgId,
                saved.getProjectNumber(),
                fromStatus,
                saved.getStatus(),
                notes,
                actor));

        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public ProjectViews.ProjectDetail getProject(UUID orgId, UUID projectId) {
        Project p = getEntity(orgId, projectId);
        return toDetail(p);
    }

    @Transactional(readOnly = true)
    public ProjectViews.ProjectHealth getHealth(UUID orgId, UUID projectId) {
        Project p = getEntity(orgId, projectId);
        List<ProjectTask> tList = tasks.findByProjectIdOrderByDisplayOrderAsc(p.getId());
        int memberCount = members.findByProjectId(p.getId()).size();
        List<Deliverable> dList = deliverables.findByProjectIdOrderByDueDateAsc(p.getId());
        return ProjectHealthCalculator.calculate(p, tList, memberCount, dList, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public Page<ProjectViews.ProjectSummary> findPage(UUID orgId, ProjectFilter filter, Pageable pageable) {
        Specification<Project> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (filter != null) {
                if (filter.status() != null && !filter.status().isBlank() && !"ALL".equalsIgnoreCase(filter.status())) {
                    predicates.add(cb.equal(root.get("status"), filter.status()));
                }
                if (filter.customerId() != null) {
                    predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
                }
                if (filter.projectManagerId() != null) {
                    predicates.add(cb.equal(root.get("projectManagerId"), filter.projectManagerId()));
                }
                if (filter.priority() != null && !filter.priority().isBlank()) {
                    predicates.add(cb.equal(root.get("priority"), filter.priority()));
                }
                if (filter.search() != null && !filter.search().isBlank()) {
                    String pattern = "%" + filter.search().toLowerCase().trim() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("projectNumber")), pattern),
                            cb.like(cb.lower(root.get("title")), pattern),
                            cb.like(cb.lower(root.get("customerName")), pattern),
                            cb.like(cb.lower(root.get("serviceName")), pattern)));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return projects.findAll(spec, pageable).map(ProjectViews.ProjectSummary::from);
    }

    @Transactional(readOnly = true)
    public ProjectViews.KanbanBoard getKanbanBoard(UUID orgId) {
        statusService.ensureDefaultStatuses(orgId);
        List<ProjectStatusConfig> statuses = statusConfigs.findByOrganizationIdOrderByDisplayOrderAsc(orgId);

        List<Project> allProjects = projects.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("organizationId"), orgId),
                        cb.isNull(root.get("deletedAt"))));

        List<ProjectViews.KanbanColumn> columns = new ArrayList<>();
        for (ProjectStatusConfig s : statuses) {
            List<ProjectViews.ProjectSummary> matching = allProjects.stream()
                    .filter(p -> p.getStatus().equalsIgnoreCase(s.getCode()))
                    .map(ProjectViews.ProjectSummary::from)
                    .toList();

            BigDecimal colBudget = matching.stream()
                    .map(ProjectViews.ProjectSummary::budget)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal colRevenue = matching.stream()
                    .map(ProjectViews.ProjectSummary::revenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            columns.add(new ProjectViews.KanbanColumn(
                    ProjectViews.StatusView.from(s),
                    matching,
                    colBudget,
                    colRevenue));
        }

        return new ProjectViews.KanbanBoard(columns);
    }

    @Transactional(readOnly = true)
    public ProjectViews.ProjectTimeline getTimeline(UUID orgId, UUID projectId) {
        Project p = getEntity(orgId, projectId);
        List<Milestone> mList = milestones.findByProjectIdOrderByDisplayOrderAsc(projectId);
        List<ProjectTask> tList = tasks.findByProjectIdOrderByDisplayOrderAsc(projectId);

        List<ProjectViews.TimelineItem> items = new ArrayList<>();

        for (Milestone m : mList) {
            items.add(new ProjectViews.TimelineItem(
                    m.getId().toString(),
                    "MILESTONE",
                    m.getTitle(),
                    m.getStatus(),
                    m.getDueDate(),
                    m.getDueDate(),
                    m.getProgressPercentage(),
                    null,
                    List.of()));
        }

        for (ProjectTask t : tList) {
            List<UUID> depIds = dependencies.findByTaskId(t.getId()).stream()
                    .map(d -> d.getDependsOnTask().getId())
                    .toList();

            items.add(new ProjectViews.TimelineItem(
                    t.getId().toString(),
                    "TASK",
                    t.getTitle(),
                    t.getStatus(),
                    t.getStartDate() != null ? t.getStartDate() : t.getDueDate(),
                    t.getDueDate() != null ? t.getDueDate() : t.getStartDate(),
                    "COMPLETED".equals(t.getStatus()) ? 100 : ("IN_PROGRESS".equals(t.getStatus()) ? 50 : 0),
                    t.getAssignedToName(),
                    depIds));
        }

        return new ProjectViews.ProjectTimeline(
                p.getId(),
                p.getProjectNumber(),
                p.getTitle(),
                p.getStartDate(),
                p.getExpectedCompletionDate(),
                items);
    }

    @Transactional(readOnly = true)
    public ProjectViews.Dashboard getDashboard(UUID orgId) {
        long total = projects.countByOrganizationId(orgId);
        BigDecimal budget = projects.sumBudgetByOrganizationId(orgId);
        BigDecimal revenue = projects.sumRevenueByOrganizationId(orgId);

        long active = projects.countByOrganizationIdAndStatus(orgId, "IN_PROGRESS")
                + projects.countByOrganizationIdAndStatus(orgId, "PLANNING")
                + projects.countByOrganizationIdAndStatus(orgId, "IN_REVIEW");

        long completed = projects.countByOrganizationIdAndStatus(orgId, "COMPLETED");

        LocalDate today = LocalDate.now(clock);
        long delayed = projects.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.isNull(root.get("deletedAt")),
                cb.notEqual(root.get("status"), "COMPLETED"),
                cb.notEqual(root.get("status"), "CANCELLED"),
                cb.lessThan(root.get("expectedCompletionDate"), today))).size();

        PageRequest recentReq = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ProjectViews.ProjectSummary> recent = findPage(orgId, null, recentReq).getContent();

        return new ProjectViews.Dashboard(total, budget, revenue, active, completed, delayed, recent);
    }

    public void deleteProject(UUID orgId, UUID projectId, String actor) {
        Project p = getEntity(orgId, projectId);
        p.setDeletedAt(Instant.now(clock));
        p.setUpdatedBy(actor);
        projects.save(p);
    }

    // --- Member Operations ---

    public ProjectViews.MemberView addMember(UUID orgId, UUID projectId, AddMemberCmd cmd, String actor) {
        Project p = getEntity(orgId, projectId);

        if (members.findByProjectIdAndUserId(projectId, cmd.userId()).isPresent()) {
            throw new IllegalArgumentException("User is already a member of this project");
        }

        UserDirectory.UserSummary u = users.find(orgId, cmd.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", cmd.userId()));

        ProjectMember member = new ProjectMember(
                p,
                cmd.userId(),
                u.fullName(),
                u.email(),
                cmd.role(),
                actor);

        p.addMember(member);
        return ProjectViews.MemberView.from(members.save(member));
    }

    public void removeMember(UUID orgId, UUID projectId, UUID userId, String actor) {
        Project p = getEntity(orgId, projectId);
        if (p.getProjectManagerId().equals(userId)) {
            throw new IllegalStateException("Cannot remove the primary project manager from project members");
        }
        members.deleteByProjectIdAndUserId(projectId, userId);
    }

    // --- Milestone Operations ---

    public ProjectViews.MilestoneView addMilestone(UUID orgId, UUID projectId, AddMilestoneCmd cmd, String actor) {
        Project p = getEntity(orgId, projectId);
        int order = cmd.displayOrder() > 0 ? cmd.displayOrder() : (int) milestones.countByProjectId(projectId) + 1;

        Milestone m = new Milestone(p, cmd.title().trim(), cmd.description(), cmd.dueDate(), order, actor);
        p.addMilestone(m);
        Milestone saved = milestones.save(m);
        return ProjectViews.MilestoneView.from(saved, 0, 0);
    }

    public ProjectViews.MilestoneView updateMilestone(
            UUID orgId, UUID projectId, UUID milestoneId, UpdateMilestoneCmd cmd, String actor) {
        Project p = getEntity(orgId, projectId);
        Milestone m = milestones.findById(milestoneId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));

        m.setTitle(cmd.title().trim());
        m.setDescription(cmd.description());
        m.setDueDate(cmd.dueDate());
        m.setDisplayOrder(cmd.displayOrder());
        if (cmd.status() != null) {
            m.setStatus(cmd.status());
            if ("COMPLETED".equals(cmd.status())) {
                m.setCompletedDate(LocalDate.now(clock));
                m.setProgressPercentage(100);
            }
        }
        m.setUpdatedAt(Instant.now(clock));
        m.setUpdatedBy(actor);

        Milestone saved = milestones.save(m);
        recalculateProjectProgress(p);
        long taskCount = tasks.countByProjectId(projectId);
        long compCount = tasks.countByProjectIdAndStatus(projectId, "COMPLETED");
        return ProjectViews.MilestoneView.from(saved, taskCount, compCount);
    }

    public ProjectViews.MilestoneView completeMilestone(UUID orgId, UUID projectId, UUID milestoneId, String actor) {
        Project p = getEntity(orgId, projectId);
        Milestone m = milestones.findById(milestoneId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));

        m.setStatus("COMPLETED");
        m.setCompletedDate(LocalDate.now(clock));
        m.setProgressPercentage(100);
        m.setUpdatedAt(Instant.now(clock));
        m.setUpdatedBy(actor);

        Milestone saved = milestones.save(m);
        recalculateProjectProgress(p);

        events.publish(ProjectEvents.MilestoneCompletedEvent.of(
                m.getId(), projectId, orgId, m.getTitle(), actor));

        long taskCount = tasks.countByProjectId(projectId);
        long compCount = tasks.countByProjectIdAndStatus(projectId, "COMPLETED");
        return ProjectViews.MilestoneView.from(saved, taskCount, compCount);
    }

    public void deleteMilestone(UUID orgId, UUID projectId, UUID milestoneId) {
        getEntity(orgId, projectId);
        milestones.deleteById(milestoneId);
    }

    // --- Task Operations & Dependencies ---

    public ProjectViews.TaskView addTask(UUID orgId, UUID projectId, AddTaskCmd cmd, String actor) {
        Project p = getEntity(orgId, projectId);
        Milestone m = null;
        if (cmd.milestoneId() != null) {
            m = milestones.findById(cmd.milestoneId())
                    .filter(item -> item.getProject().getId().equals(projectId))
                    .orElse(null);
        }

        String assignedName = null;
        if (cmd.assignedToId() != null) {
            assignedName = users.find(orgId, cmd.assignedToId())
                    .map(UserDirectory.UserSummary::fullName)
                    .orElse(null);
        }

        int order = cmd.displayOrder() > 0 ? cmd.displayOrder() : (int) tasks.countByProjectId(projectId) + 1;

        ProjectTask t = new ProjectTask(
                p,
                m,
                cmd.title().trim(),
                cmd.description(),
                cmd.assignedToId(),
                assignedName,
                cmd.priority(),
                cmd.startDate(),
                cmd.dueDate(),
                cmd.estimatedHours(),
                order,
                actor);

        p.addTask(t);
        ProjectTask saved = tasks.save(t);

        // Add dependencies if provided
        if (cmd.dependsOnTaskIds() != null) {
            for (UUID depId : cmd.dependsOnTaskIds()) {
                tasks.findById(depId)
                        .filter(dep -> dep.getProject().getId().equals(projectId))
                        .ifPresent(depTask -> {
                            TaskDependency dep = new TaskDependency(saved, depTask, "FINISH_TO_START", actor);
                            saved.addDependency(dep);
                            dependencies.save(dep);
                        });
            }
        }

        recalculateProjectProgress(p);
        return toTaskView(saved);
    }

    public ProjectViews.TaskView updateTask(
            UUID orgId, UUID projectId, UUID taskId, UpdateTaskCmd cmd, String actor) {
        Project p = getEntity(orgId, projectId);
        ProjectTask t = tasks.findById(taskId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("ProjectTask", taskId));

        if (cmd.milestoneId() != null) {
            milestones.findById(cmd.milestoneId())
                    .filter(item -> item.getProject().getId().equals(projectId))
                    .ifPresent(t::setMilestone);
        } else {
            t.setMilestone(null);
        }

        t.setTitle(cmd.title().trim());
        t.setDescription(cmd.description());

        if (cmd.assignedToId() != null) {
            t.setAssignedToId(cmd.assignedToId());
            users.find(orgId, cmd.assignedToId()).ifPresent(u -> t.setAssignedToName(u.fullName()));
        } else {
            t.setAssignedToId(null);
            t.setAssignedToName(null);
        }

        if (cmd.priority() != null) {
            t.setPriority(cmd.priority());
        }
        t.setStartDate(cmd.startDate());
        t.setDueDate(cmd.dueDate());
        if (cmd.estimatedHours() != null) {
            t.setEstimatedHours(cmd.estimatedHours());
        }
        if (cmd.actualHours() != null) {
            t.setActualHours(cmd.actualHours());
        }

        if (cmd.status() != null) {
            t.setStatus(cmd.status());
            if ("COMPLETED".equals(cmd.status())) {
                t.setCompletedAt(Instant.now(clock));
            } else {
                t.setCompletedAt(null);
            }
        }

        t.setUpdatedAt(Instant.now(clock));
        t.setUpdatedBy(actor);

        ProjectTask saved = tasks.save(t);
        recalculateProjectProgress(p);
        return toTaskView(saved);
    }

    public ProjectViews.TaskView assignTask(
            UUID orgId, UUID projectId, UUID taskId, UUID assignedToId, String actor) {
        getEntity(orgId, projectId);
        ProjectTask t = tasks.findById(taskId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("ProjectTask", taskId));

        if (assignedToId != null) {
            UserDirectory.UserSummary u = users.find(orgId, assignedToId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", assignedToId));
            t.setAssignedToId(assignedToId);
            t.setAssignedToName(u.fullName());
        } else {
            t.setAssignedToId(null);
            t.setAssignedToName(null);
        }

        t.setUpdatedAt(Instant.now(clock));
        t.setUpdatedBy(actor);
        return toTaskView(tasks.save(t));
    }

    public ProjectViews.TaskView updateTaskStatus(
            UUID orgId, UUID projectId, UUID taskId, String targetStatus, String actor) {
        Project p = getEntity(orgId, projectId);
        ProjectTask t = tasks.findById(taskId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("ProjectTask", taskId));

        t.setStatus(targetStatus);
        if ("COMPLETED".equals(targetStatus)) {
            t.setCompletedAt(Instant.now(clock));
        } else {
            t.setCompletedAt(null);
        }
        t.setUpdatedAt(Instant.now(clock));
        t.setUpdatedBy(actor);

        ProjectTask saved = tasks.save(t);
        recalculateProjectProgress(p);
        return toTaskView(saved);
    }

    public void deleteTask(UUID orgId, UUID projectId, UUID taskId) {
        Project p = getEntity(orgId, projectId);
        tasks.deleteById(taskId);
        recalculateProjectProgress(p);
    }

    public void addDependency(
            UUID orgId, UUID projectId, UUID taskId, UUID dependsOnTaskId, String type, String actor) {
        getEntity(orgId, projectId);
        ProjectTask task = tasks.findById(taskId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("ProjectTask", taskId));

        ProjectTask depTask = tasks.findById(dependsOnTaskId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("ProjectTask", dependsOnTaskId));

        if (taskId.equals(dependsOnTaskId)) {
            throw new IllegalArgumentException("A task cannot depend on itself");
        }

        TaskDependency dep = new TaskDependency(task, depTask, type, actor);
        dependencies.save(dep);
    }

    public void removeDependency(UUID orgId, UUID projectId, UUID taskId, UUID dependsOnTaskId) {
        getEntity(orgId, projectId);
        dependencies.deleteByTaskIdAndDependsOnTaskId(taskId, dependsOnTaskId);
    }

    // --- Checklist Operations ---

    public ProjectViews.ChecklistItemView addChecklistItem(
            UUID orgId, UUID projectId, UUID taskId, String itemText, String actor) {
        getEntity(orgId, projectId);
        ProjectTask task = tasks.findById(taskId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("ProjectTask", taskId));

        int order = checklists.findByTaskIdOrderByDisplayOrderAsc(taskId).size() + 1;
        ChecklistItem item = new ChecklistItem(task, itemText.trim(), order, actor);
        return ProjectViews.ChecklistItemView.from(checklists.save(item));
    }

    public ProjectViews.ChecklistItemView toggleChecklistItem(
            UUID orgId, UUID projectId, UUID taskId, UUID itemId, boolean completed, String actor) {
        getEntity(orgId, projectId);
        ChecklistItem item = checklists.findById(itemId)
                .filter(c -> c.getTask().getId().equals(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("ChecklistItem", itemId));

        item.setCompleted(completed, actor);
        return ProjectViews.ChecklistItemView.from(checklists.save(item));
    }

    public void deleteChecklistItem(UUID orgId, UUID projectId, UUID taskId, UUID itemId) {
        getEntity(orgId, projectId);
        checklists.deleteById(itemId);
    }

    // --- Deliverable Operations ---

    public ProjectViews.DeliverableView addDeliverable(
            UUID orgId, UUID projectId, AddDeliverableCmd cmd, String actor) {
        Project p = getEntity(orgId, projectId);
        Milestone m = null;
        if (cmd.milestoneId() != null) {
            m = milestones.findById(cmd.milestoneId())
                    .filter(item -> item.getProject().getId().equals(projectId))
                    .orElse(null);
        }

        Deliverable d = new Deliverable(p, m, cmd.title().trim(), cmd.description(), cmd.dueDate(), cmd.acceptanceCriteria(), actor);
        d.setFileUrl(cmd.fileUrl());
        p.addDeliverable(d);
        return ProjectViews.DeliverableView.from(deliverables.save(d));
    }

    public ProjectViews.DeliverableView updateDeliverableStatus(
            UUID orgId, UUID projectId, UUID deliverableId, String status, String actor) {
        getEntity(orgId, projectId);
        Deliverable d = deliverables.findById(deliverableId)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Deliverable", deliverableId));

        d.setStatus(status);
        if ("SUBMITTED".equals(status) && d.getDeliveredDate() == null) {
            d.setDeliveredDate(LocalDate.now(clock));
        }
        if ("ACCEPTED".equals(status) || "REJECTED".equals(status)) {
            d.setReviewedBy(actor);
            d.setReviewedAt(Instant.now(clock));
        }
        d.setUpdatedAt(Instant.now(clock));
        d.setUpdatedBy(actor);

        return ProjectViews.DeliverableView.from(deliverables.save(d));
    }

    public void deleteDeliverable(UUID orgId, UUID projectId, UUID deliverableId) {
        getEntity(orgId, projectId);
        deliverables.deleteById(deliverableId);
    }

    // --- Directory implementation ---

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectDirectory.ProjectSummary> findProject(UUID orgId, UUID projectId) {
        return projects.findByIdAndOrganizationId(projectId, orgId)
                .filter(p -> p.getDeletedAt() == null)
                .map(p -> new ProjectDirectory.ProjectSummary(
                        p.getId(),
                        p.getProjectNumber(),
                        p.getTitle(),
                        p.getCustomerId(),
                        p.getCustomerName(),
                        p.getOrderId(),
                        p.getOrderNumber(),
                        p.getProjectManagerId(),
                        p.getProjectManagerName(),
                        p.getStartDate(),
                        p.getExpectedCompletionDate(),
                        p.getActualCompletionDate(),
                        p.getStatus(),
                        p.getPriority(),
                        p.getBudget(),
                        p.getRevenue(),
                        p.getProgressPercentage()));
    }

    // --- Helpers ---

    private Project getEntity(UUID orgId, UUID projectId) {
        return projects.findByIdAndOrganizationId(projectId, orgId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private String generateProjectNumber(UUID orgId) {
        long count = projects.countByOrganizationId(orgId) + 1;
        String candidate = String.format("PRJ-%04d", count);
        int attempts = 0;
        while (projects.existsByOrganizationIdAndProjectNumber(orgId, candidate) && attempts < 100) {
            count++;
            candidate = String.format("PRJ-%04d", count);
            attempts++;
        }
        return candidate;
    }

    private void recalculateProjectProgress(Project p) {
        List<ProjectTask> taskList = tasks.findByProjectIdOrderByDisplayOrderAsc(p.getId());
        if (taskList.isEmpty()) {
            return;
        }

        long completed = taskList.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        int pct = (int) Math.round(((double) completed / taskList.size()) * 100);
        p.setProgressPercentage(pct);
        projects.save(p);
    }

    private ProjectViews.TaskView toTaskView(ProjectTask t) {
        List<ProjectViews.ChecklistItemView> clViews = checklists.findByTaskIdOrderByDisplayOrderAsc(t.getId())
                .stream().map(ProjectViews.ChecklistItemView::from).toList();
        List<ProjectViews.TaskDependencyView> depViews = dependencies.findByTaskId(t.getId())
                .stream().map(ProjectViews.TaskDependencyView::from).toList();

        return ProjectViews.TaskView.from(t, clViews, depViews);
    }

    private ProjectViews.ProjectDetail toDetail(Project p) {
        List<ProjectViews.MemberView> memViews = members.findByProjectId(p.getId())
                .stream().map(ProjectViews.MemberView::from).toList();

        List<Milestone> mList = milestones.findByProjectIdOrderByDisplayOrderAsc(p.getId());
        List<ProjectViews.MilestoneView> mViews = mList.stream().map(m -> {
            long tCount = tasks.findByMilestoneIdOrderByDisplayOrderAsc(m.getId()).size();
            long cCount = tasks.findByMilestoneIdOrderByDisplayOrderAsc(m.getId()).stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus())).count();
            return ProjectViews.MilestoneView.from(m, tCount, cCount);
        }).toList();

        List<ProjectTask> tList = tasks.findByProjectIdOrderByDisplayOrderAsc(p.getId());
        List<ProjectViews.TaskView> tViews = tList.stream().map(this::toTaskView).toList();

        List<Deliverable> dList = deliverables.findByProjectIdOrderByDueDateAsc(p.getId());
        List<ProjectViews.DeliverableView> dViews = dList.stream().map(ProjectViews.DeliverableView::from).toList();

        ProjectViews.ProjectHealth health = ProjectHealthCalculator.calculate(
                p, tList, memViews.size(), dList, LocalDate.now(clock));

        return new ProjectViews.ProjectDetail(
                p.getId(),
                p.getProjectNumber(),
                p.getTitle(),
                p.getDescription(),
                p.getCustomerId(),
                p.getCustomerName(),
                p.getOrderId(),
                p.getOrderNumber(),
                p.getServiceId(),
                p.getServiceName(),
                p.getProjectManagerId(),
                p.getProjectManagerName(),
                p.getStartDate(),
                p.getExpectedCompletionDate(),
                p.getActualCompletionDate(),
                p.getStatus(),
                p.getPriority(),
                p.getBudget(),
                p.getRevenue(),
                p.getCost(),
                p.getProgressPercentage(),
                p.getNotes(),
                p.getCreatedAt(),
                p.getCreatedBy(),
                p.getUpdatedAt(),
                p.getUpdatedBy(),
                memViews,
                mViews,
                tViews,
                dViews,
                health);
    }
}
