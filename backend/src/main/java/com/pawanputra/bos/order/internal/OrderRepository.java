package com.pawanputra.bos.order.internal;

import com.pawanputra.bos.order.api.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    boolean existsByOrganizationIdAndOrderNumber(UUID organizationId, String orderNumber);

    long countByOrganizationIdAndStatus(UUID organizationId, OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.finalAmount), 0) FROM Order o WHERE o.organizationId = :orgId AND o.deletedAt IS NULL AND o.status != 'CANCELLED'")
    BigDecimal sumFinalAmountByOrganizationId(@Param("orgId") UUID organizationId);

    List<Order> findByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);

    Optional<Order> findByOrganizationIdAndQuotationId(UUID organizationId, UUID quotationId);
}
