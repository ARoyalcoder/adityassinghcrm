package com.pawanputra.bos.activity.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityReminderRepository extends JpaRepository<ActivityReminder, UUID> {

    List<ActivityReminder> findByUserIdAndStatusOrderByRemindAtDesc(UUID userId, String status);

    List<ActivityReminder> findByActivityId(UUID activityId);
}
