package com.pawanputra.bos.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawanputra.bos.identity.api.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private final Organization organization = new Organization("PPG", "Pawan Putra Group");

    @Test
    void emailIsTrimmedAndLowerCased() {
        User user = new User(organization, "  Asha.Verma@Example.COM ", "Asha Verma");
        assertThat(user.getEmail()).isEqualTo("asha.verma@example.com");

        user.setEmail("NEW@Example.com");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void newUserIsInvitedWithNoPasswordAndNoRoles() {
        User user = new User(organization, "asha@example.com", "Asha");

        assertThat(user.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    void assigningTheSameRoleTwiceKeepsOneAssignment() {
        User user = new User(organization, "asha@example.com", "Asha");
        Role manager = new Role("BRANCH_MANAGER", "Branch manager", false);
        Role sales = new Role("SALES_EXECUTIVE", "Sales executive", false);

        user.assignRole(manager);
        user.assignRole(manager);
        user.assignRole(sales);
        assertThat(user.getRoles()).containsExactlyInAnyOrder(manager, sales);

        user.removeRole(manager);
        assertThat(user.getRoles()).containsExactly(sales);
    }

    @Test
    void markDeletedKeepsTheFirstDeletionTime() {
        User user = new User(organization, "asha@example.com", "Asha");
        Instant first = Instant.parse("2026-01-01T00:00:00Z");

        user.markDeleted(first);
        user.markDeleted(first.plusSeconds(60));

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isEqualTo(first);
    }
}
