package com.pawanputra.bos.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * How other modules refer to people. A module that assigns work to an employee stores only the
 * user's id and asks this directory for the name to display and whether the id is valid.
 * Every lookup is limited to one organization.
 */
public interface UserDirectory {

    /** @param active whether the person can currently sign in */
    record UserSummary(UUID id, String fullName, String email, boolean active) {
    }

    Optional<UserSummary> find(UUID organizationId, UUID userId);

    /** Ids that do not exist in the organization are simply absent from the result. */
    Map<UUID, UserSummary> findAll(UUID organizationId, Collection<UUID> userIds);

    /** People who can be given work: active users of the organization, by name. */
    List<UserSummary> listActive(UUID organizationId);
}
