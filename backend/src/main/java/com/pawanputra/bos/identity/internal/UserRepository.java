package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.identity.api.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Pass the address through {@link User#normalizeEmail(String)} first; emails are stored lower-case. */
    Optional<User> findByEmail(String email);

    /** For sign-in and authorisation: loads the user with roles and their permissions in one query. */
    @EntityGraph(attributePaths = {"roleAssignments.role.permissions"})
    Optional<User> findWithRolesByEmail(String email);

    @EntityGraph(attributePaths = {"roleAssignments.role.permissions"})
    Optional<User> findWithRolesById(UUID id);

    boolean existsByEmail(String email);

    boolean existsByBranchId(UUID branchId);

    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<User> findAllByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    List<User> findAllByOrganizationIdAndStatusOrderByFullNameAsc(UUID organizationId, UserStatus status);

    Page<User> findAllByOrganizationId(UUID organizationId, Pageable pageable);
}
