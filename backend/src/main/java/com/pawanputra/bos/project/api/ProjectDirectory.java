package com.pawanputra.bos.project.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDirectory {

    record ProjectSummary(
            UUID id,
            String projectNumber,
            String title,
            UUID customerId,
            String customerName,
            UUID orderId,
            String orderNumber,
            UUID projectManagerId,
            String projectManagerName,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            LocalDate actualCompletionDate,
            String status,
            String priority,
            BigDecimal budget,
            BigDecimal revenue,
            int progressPercentage) {
    }

    Optional<ProjectSummary> findProject(UUID organizationId, UUID projectId);
}
