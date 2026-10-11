package com.pawanputra.bos.project.internal;

import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectStatusService {

    private final ProjectStatusConfigRepository statusRepo;

    public ProjectStatusService(ProjectStatusConfigRepository statusRepo) {
        this.statusRepo = statusRepo;
    }

    public record CreateStatusCmd(
            String code,
            String name,
            int displayOrder,
            String color,
            String category,
            boolean isDefault,
            boolean isCompleted,
            boolean isCancelled) {
    }

    public record UpdateStatusCmd(
            String name,
            String color,
            String category,
            boolean isDefault,
            boolean isCompleted,
            boolean isCancelled) {
    }

    public List<ProjectViews.StatusView> listStatuses(UUID orgId) {
        ensureDefaultStatuses(orgId);
        return statusRepo.findByOrganizationIdOrderByDisplayOrderAsc(orgId)
                .stream()
                .map(ProjectViews.StatusView::from)
                .toList();
    }

    public ProjectViews.StatusView createStatus(UUID orgId, CreateStatusCmd cmd, String actor) {
        ensureDefaultStatuses(orgId);
        String code = cmd.code().toUpperCase().trim().replace(" ", "_");

        if (statusRepo.existsByOrganizationIdAndCode(orgId, code)) {
            throw new IllegalArgumentException("Project status with code '" + code + "' already exists");
        }

        if (cmd.isDefault()) {
            clearOtherDefaults(orgId);
        }

        ProjectStatusConfig entity = new ProjectStatusConfig(
                orgId,
                code,
                cmd.name().trim(),
                cmd.displayOrder(),
                cmd.color(),
                cmd.category(),
                cmd.isDefault(),
                cmd.isCompleted(),
                cmd.isCancelled(),
                actor);

        return ProjectViews.StatusView.from(statusRepo.save(entity));
    }

    public ProjectViews.StatusView updateStatus(UUID orgId, UUID statusId, UpdateStatusCmd cmd, String actor) {
        ProjectStatusConfig status = statusRepo.findById(statusId)
                .filter(s -> s.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("ProjectStatus", statusId));

        if (cmd.isDefault() && !status.isDefault()) {
            clearOtherDefaults(orgId);
        }

        status.setName(cmd.name().trim());
        status.setColor(cmd.color());
        status.setCategory(cmd.category());
        status.setDefault(cmd.isDefault());
        status.setCompleted(cmd.isCompleted());
        status.setCancelled(cmd.isCancelled());
        status.setUpdatedAt(Instant.now());
        status.setUpdatedBy(actor);

        return ProjectViews.StatusView.from(statusRepo.save(status));
    }

    public void reorderStatuses(UUID orgId, List<UUID> statusIds, String actor) {
        for (int i = 0; i < statusIds.size(); i++) {
            UUID id = statusIds.get(i);
            int order = i + 1;
            statusRepo.findById(id)
                    .filter(s -> s.getOrganizationId().equals(orgId))
                    .ifPresent(s -> {
                        s.setDisplayOrder(order);
                        s.setUpdatedAt(Instant.now());
                        s.setUpdatedBy(actor);
                        statusRepo.save(s);
                    });
        }
    }

    public void deleteStatus(UUID orgId, UUID statusId) {
        ProjectStatusConfig status = statusRepo.findById(statusId)
                .filter(s -> s.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("ProjectStatus", statusId));

        if (status.isDefault()) {
            throw new IllegalStateException("Cannot delete the default project status");
        }

        statusRepo.delete(status);
    }

    public void ensureDefaultStatuses(UUID orgId) {
        if (!statusRepo.findByOrganizationIdOrderByDisplayOrderAsc(orgId).isEmpty()) {
            return;
        }

        List<ProjectStatusConfig> defaults = List.of(
                new ProjectStatusConfig(orgId, "PLANNING", "Planning", 1, "blue", "NOT_STARTED", true, false, false, "system"),
                new ProjectStatusConfig(orgId, "IN_PROGRESS", "In Progress", 2, "amber", "IN_PROGRESS", false, false, false, "system"),
                new ProjectStatusConfig(orgId, "ON_HOLD", "On Hold", 3, "orange", "ON_HOLD", false, false, false, "system"),
                new ProjectStatusConfig(orgId, "IN_REVIEW", "In Review", 4, "purple", "IN_PROGRESS", false, false, false, "system"),
                new ProjectStatusConfig(orgId, "COMPLETED", "Completed", 5, "emerald", "COMPLETED", false, true, false, "system"),
                new ProjectStatusConfig(orgId, "CANCELLED", "Cancelled", 6, "rose", "CANCELLED", false, false, true, "system"));

        statusRepo.saveAll(defaults);
    }

    private void clearOtherDefaults(UUID orgId) {
        statusRepo.findFirstByOrganizationIdAndIsDefaultTrue(orgId).ifPresent(s -> {
            s.setDefault(false);
            statusRepo.save(s);
        });
    }
}
