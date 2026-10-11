package com.pawanputra.bos.platform.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The seam between modules. Today events are delivered in-process; consumers should listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so they only see committed facts.
 * Swapping this implementation for an outbox/broker publisher is what extracting a module means.
 */
@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    public DomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(DomainEvent event) {
        delegate.publishEvent(event);
    }
}
