package com.pawanputra.bos.project.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliverableRepository extends JpaRepository<Deliverable, UUID> {

    List<Deliverable> findByProjectIdOrderByDueDateAsc(UUID projectId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, String status);
}
