package com.pawanputra.bos.project.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

    List<ChecklistItem> findByTaskIdOrderByDisplayOrderAsc(UUID taskId);
}
