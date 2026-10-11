package com.pawanputra.bos.activity.internal;

import com.pawanputra.bos.activity.api.ActivityType;
import com.pawanputra.bos.activity.api.ReminderStatus;
import com.pawanputra.bos.activity.api.TargetEntityType;
import com.pawanputra.bos.activity.api.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<Activity, UUID>, JpaSpecificationExecutor<Activity> {

    Optional<Activity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Activity> findByOrganizationIdAndEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(
            UUID organizationId, TargetEntityType entityType, UUID entityId);

    List<Activity> findByEntityTypeAndEntityIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
            TargetEntityType entityType, UUID entityId, Instant notAfter, Pageable pageable);

    @Query("SELECT a FROM Activity a WHERE a.reminderStatus = :reminderStatus AND a.reminderAt <= :now AND a.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<Activity> findDueReminders(@Param("reminderStatus") ReminderStatus reminderStatus, @Param("now") Instant now);

    long countByOrganizationIdAndOwnerIdAndStatusNot(UUID organizationId, UUID ownerId, TaskStatus status);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.organizationId = :orgId AND a.status NOT IN ('COMPLETED', 'CANCELLED') AND a.dueDate < :now")
    long countOverdue(@Param("orgId") UUID orgId, @Param("now") Instant now);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.organizationId = :orgId AND a.status NOT IN ('COMPLETED', 'CANCELLED') AND a.dueDate >= :startOfDay AND a.dueDate <= :endOfDay")
    long countDueToday(@Param("orgId") UUID orgId, @Param("startOfDay") Instant startOfDay, @Param("endOfDay") Instant endOfDay);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.organizationId = :orgId AND a.status NOT IN ('COMPLETED', 'CANCELLED') AND a.dueDate > :endOfDay")
    long countUpcoming(@Param("orgId") UUID orgId, @Param("endOfDay") Instant endOfDay);

    long countByOrganizationIdAndStatus(UUID organizationId, TaskStatus status);
}
