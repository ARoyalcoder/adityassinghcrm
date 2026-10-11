package com.pawanputra.bos.project.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    List<TaskDependency> findByTaskId(UUID taskId);

    List<TaskDependency> findByDependsOnTaskId(UUID dependsOnTaskId);

    void deleteByTaskIdAndDependsOnTaskId(UUID taskId, UUID dependsOnTaskId);
}
