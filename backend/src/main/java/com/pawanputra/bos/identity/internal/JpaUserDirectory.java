package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.identity.api.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaUserDirectory implements UserDirectory {

    private final UserRepository users;

    JpaUserDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserSummary> find(UUID organizationId, UUID userId) {
        return users.findByIdAndOrganizationId(userId, organizationId).map(JpaUserDirectory::summary);
    }

    @Override
    public Map<UUID, UserSummary> findAll(UUID organizationId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllByOrganizationIdAndIdIn(organizationId, userIds).stream()
                .collect(Collectors.toMap(User::getId, JpaUserDirectory::summary));
    }

    @Override
    public List<UserSummary> listActive(UUID organizationId) {
        return users.findAllByOrganizationIdAndStatusOrderByFullNameAsc(organizationId, UserStatus.ACTIVE).stream()
                .map(JpaUserDirectory::summary).toList();
    }

    private static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.isActive());
    }
}
