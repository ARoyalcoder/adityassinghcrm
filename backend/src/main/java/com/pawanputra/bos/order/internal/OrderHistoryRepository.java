package com.pawanputra.bos.order.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {

    List<OrderHistory> findByOrderIdOrderByPerformedAtDesc(UUID orderId);
}
