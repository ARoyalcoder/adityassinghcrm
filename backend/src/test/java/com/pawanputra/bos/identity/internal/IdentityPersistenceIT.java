package com.pawanputra.bos.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawanputra.bos.identity.api.UserStatus;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class IdentityPersistenceIT extends AbstractIntegrationTest {

    @Autowired OrganizationRepository organizations;
    @Autowired BranchRepository branches;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsUserWithOrganizationBranchRoleAndPermission() {
        Organization org = organizations.save(new Organization("PPG", "Pawan Putra Group"));
        Branch headOffice = new Branch(org, "HO", "Head Office", "Lucknow", "Uttar Pradesh");
        headOffice.setHeadOffice(true);
        branches.save(headOffice);

        Permission permission = permissions.save(new Permission("TESTMODULE_WRITE", "Test permission"));
        Role role = new Role("TEST_CATALOG_MANAGER", "Test role", false);
        role.grant(permission);
        roles.save(role);

        User user = new User(org, "  Asha.Verma@Example.COM ", "Asha Verma");
        user.setBranch(headOffice);
        user.assignRole(role);
        user.assignRole(role); // idempotent
        users.saveAndFlush(user);
        em.clear();

        User loaded = users.findWithRolesByEmail("asha.verma@example.com").orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(loaded.getOrganization().getCode()).isEqualTo("PPG");
        assertThat(loaded.getBranch().getName()).isEqualTo("Head Office");
        assertThat(loaded.getRoles()).extracting(Role::getCode).containsExactly("TEST_CATALOG_MANAGER");
        assertThat(loaded.getRoles().iterator().next().getPermissions())
                .extracting(Permission::getCode).containsExactly("TESTMODULE_WRITE");

        // Audit columns are filled in by JPA auditing; with no signed-in user the actor is "system".
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getCreatedBy()).isEqualTo("system");
        assertThat(loaded.getUpdatedBy()).isEqualTo("system");
        assertThat(loaded.getVersion()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT created_by FROM user_roles WHERE user_id = ?", String.class, loaded.getId()))
                .isEqualTo("system");
    }

    @Test
    void emailIsUniqueAcrossLiveUsersRegardlessOfCase() {
        Organization org = organizations.save(new Organization("PPG", "Pawan Putra Group"));
        users.saveAndFlush(new User(org, "asha@example.com", "Asha"));

        assertThatThrownBy(() -> users.saveAndFlush(new User(org, "ASHA@example.com", "Another Asha")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_users_email");
    }

    @Test
    void softDeletedUserIsHiddenButKeptAndFreesTheEmail() {
        Organization org = organizations.save(new Organization("PPG", "Pawan Putra Group"));
        User user = users.saveAndFlush(new User(org, "asha@example.com", "Asha"));

        user.markDeleted(Instant.now());
        users.saveAndFlush(user);
        em.clear();

        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(users.findByEmail("asha@example.com")).isEmpty();
        assertThat(users.existsByEmail("asha@example.com")).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE id = ? AND deleted_at IS NOT NULL", Long.class, user.getId()))
                .isEqualTo(1);

        User replacement = users.saveAndFlush(new User(org, "asha@example.com", "Asha (rejoined)"));
        assertThat(replacement.getId()).isNotEqualTo(user.getId());
    }

    @Test
    void userCannotBePlacedInABranchOfAnotherOrganization() {
        Organization own = organizations.save(new Organization("PPG", "Pawan Putra Group"));
        Organization other = organizations.save(new Organization("OTH", "Other Company"));
        Branch foreignBranch = branches.save(new Branch(other, "HO", "Other HQ", "Lucknow", "Uttar Pradesh"));

        User user = new User(own, "asha@example.com", "Asha");
        user.setBranch(foreignBranch);

        assertThatThrownBy(() -> users.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_users_branch");
    }

    @Test
    void organizationHasAtMostOneHeadOffice() {
        Organization org = organizations.save(new Organization("PPG", "Pawan Putra Group"));
        Branch first = new Branch(org, "HO", "Head Office", "Lucknow", "Uttar Pradesh");
        first.setHeadOffice(true);
        branches.saveAndFlush(first);

        Branch second = new Branch(org, "HO2", "Second Head Office", "Lucknow", "Uttar Pradesh");
        second.setHeadOffice(true);

        assertThatThrownBy(() -> branches.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_branches_head_office");
    }

    @Test
    void branchCodeIsUniqueWithinAnOrganizationOnly() {
        Organization first = organizations.save(new Organization("PPG", "Pawan Putra Group"));
        Organization second = organizations.save(new Organization("OTH", "Other Company"));
        branches.saveAndFlush(new Branch(first, "DEL", "Delhi", "Lucknow", "Uttar Pradesh"));
        branches.saveAndFlush(new Branch(second, "DEL", "Delhi", "Lucknow", "Uttar Pradesh")); // same code, different organization: fine

        assertThatThrownBy(() -> branches.saveAndFlush(new Branch(first, "DEL", "Delhi again", "Lucknow", "Uttar Pradesh")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_branches_organization_code");
    }

    @Test
    void roleThatIsStillAssignedCannotBeDeleted() {
        Organization org = organizations.save(new Organization("PPG", "Pawan Putra Group"));
        Role role = roles.findByCode("SALES_EXECUTIVE").orElseThrow();
        User user = new User(org, "asha@example.com", "Asha");
        user.assignRole(role);
        users.saveAndFlush(user);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM roles WHERE id = ?", role.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_user_roles_role");
    }

    @Test
    void databaseRejectsUnknownUserStatus() {
        Organization org = organizations.saveAndFlush(new Organization("PPG", "Pawan Putra Group"));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (organization_id, email, full_name, status, created_by, updated_by) "
                        + "VALUES (?, 'x@example.com', 'X', 'BANNED', 'test', 'test')", org.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_status");
    }

    @Test
    void permissionCodeMustBeModuleAction() {
        assertThatThrownBy(() -> permissions.saveAndFlush(new Permission("catalog.service.write", "Bad code")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_permissions_code_format");
    }
}
