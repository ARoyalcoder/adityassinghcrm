package com.pawanputra.bos.catalog.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * How other modules refer to services. A module that records which service something is about
 * stores only the service id, and asks this directory for the name to display and whether the id
 * is valid.
 */
public interface ServiceDirectory {

    /** @param active whether the service is currently offered */
    record ServiceSummary(UUID id, String code, String name, String verticalName, boolean active) {
    }

    Optional<ServiceSummary> find(UUID serviceId);

    /** Ids that do not exist are simply absent from the result. */
    Map<UUID, ServiceSummary> findAll(Collection<UUID> serviceIds);
}
