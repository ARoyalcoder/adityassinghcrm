package com.pawanputra.bos.project.internal;

import com.pawanputra.bos.customer.api.CustomerTimelineSource;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import com.pawanputra.bos.platform.timeline.TimelineEventTypes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ProjectTimelineSource implements CustomerTimelineSource {

    private final ProjectRepository projects;
    private final MilestoneRepository milestones;

    public ProjectTimelineSource(ProjectRepository projects, MilestoneRepository milestones) {
        this.projects = projects;
        this.milestones = milestones;
    }

    @Override
    public Set<TimelineCategory> categories() {
        return Set.of(TimelineCategory.PROJECT);
    }

    @Override
    public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
        Instant cutoff = notAfter != null ? notAfter : Instant.now();
        List<Project> list = projects.findByOrganizationIdAndCustomerId(organizationId, customerId);

        List<TimelineEvent> events = new ArrayList<>();
        for (Project p : list) {
            if (p.getDeletedAt() == null && !p.getCreatedAt().isAfter(cutoff)) {
                events.add(new TimelineEvent(
                        "project-started:" + p.getId(),
                        TimelineEventTypes.PROJECT_STARTED,
                        TimelineCategory.PROJECT,
                        p.getCreatedAt(),
                        "Project started: " + p.getProjectNumber(),
                        p.getTitle() + " - PM: " + p.getProjectManagerName() + " (Status: " + p.getStatus() + ")",
                        p.getCreatedBy(),
                        new TimelineEvent.Reference("Project", p.getId().toString()),
                        null));

                // Check completed milestones
                List<Milestone> mList = milestones.findByProjectIdOrderByDisplayOrderAsc(p.getId());
                for (Milestone m : mList) {
                    if ("COMPLETED".equals(m.getStatus()) && m.getCompletedDate() != null) {
                        Instant completedInstant = m.getCompletedDate().atStartOfDay().toInstant(ZoneOffset.UTC);
                        if (!completedInstant.isAfter(cutoff)) {
                            events.add(new TimelineEvent(
                                    "milestone-completed:" + m.getId(),
                                    TimelineEventTypes.MILESTONE_COMPLETED,
                                    TimelineCategory.PROJECT,
                                    completedInstant,
                                    "Milestone completed: " + m.getTitle(),
                                    "Project " + p.getProjectNumber() + " (" + p.getTitle() + ")",
                                    m.getUpdatedBy(),
                                    new TimelineEvent.Reference("Project", p.getId().toString()),
                                    null));
                        }
                    }
                }
            }
        }

        events.sort((a, b) -> b.occurredAt().compareTo(a.occurredAt()));
        if (events.size() > limit) {
            return events.subList(0, limit);
        }
        return events;
    }
}
