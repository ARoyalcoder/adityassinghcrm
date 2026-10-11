package com.pawanputra.bos.platform.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A fact that already happened in one module and that other modules may react to.
 *
 * <p>Events are the only way modules influence each other without a direct call. Implement them as
 * immutable records made of ids and simple values (never entities), so the same payload can later be
 * serialised onto a message broker if the consumer is extracted into its own service.
 */
public interface DomainEvent {

    /** Unique per occurrence; lets consumers de-duplicate. */
    UUID eventId();

    Instant occurredAt();
}
