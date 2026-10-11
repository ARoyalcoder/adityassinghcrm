package com.pawanputra.bos.order.internal;

import com.pawanputra.bos.order.api.OrderProjectBridge;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(OrderProjectBridge.class)
public class DefaultNoOpOrderProjectBridge implements OrderProjectBridge {

    private static final Logger log = LoggerFactory.getLogger(DefaultNoOpOrderProjectBridge.class);

    @Override
    public void onOrderConfirmed(UUID organizationId, OrderProjectContext context) {
        log.info(
                "Order confirmed extension point: order {} (id {}) for customer '{}' (amount {}). "
                        + "Future project management module can register OrderProjectBridge to automate project/milestone setup.",
                context.orderNumber(),
                context.orderId(),
                context.customerName(),
                context.finalAmount());
    }
}
