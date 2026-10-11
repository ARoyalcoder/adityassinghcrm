package com.pawanputra.bos.project.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {

    List<ProjectTask> findByProjectIdOrderByDisplayOrderAsc(UUID projectId);

    List<ProjectTask> findByMilestoneIdOrderByDisplayOrderAsc(UUID milestoneId);

    List<ProjectTask> findByAssignedToId(UUID assignedToId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, String status);
}
