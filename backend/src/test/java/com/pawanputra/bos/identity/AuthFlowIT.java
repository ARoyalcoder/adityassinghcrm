package com.pawanputra.bos.identity;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.identity.api.UserStatus;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.CapturingPasswordResetNotifier;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** The authentication flows end to end: real HTTP, real security filters, real PostgreSQL. */
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String COOKIE = "bos_refresh";
    private static final String NEW_PASSWORD = "A-Brand-New-Password-456";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired CapturingPasswordResetNotifier resetNotifier;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginReturnsAccessTokenUserAndHardenedRefreshCookie() {
        Account account = accounts.activeUser(RoleCodes.SALES_EXECUTIVE);

        Response response = login(account.email(), account.password());

        response.then().statusCode(200)
                .header("Cache-Control", equalTo("no-store"))
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(900))
                .body("user.id", equalTo(account.id().toString()))
                .body("user.email", equalTo(account.email()))
                .body("user.status", equalTo("ACTIVE"))
                .body("user.roles", containsInAnyOrder("SALES_EXECUTIVE"))
                .body("user.permissions", containsInAnyOrder(
                        "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CATALOG_VIEW",
                        "LEAD_VIEW", "LEAD_CREATE", "LEAD_UPDATE",
                        "ACTIVITY_VIEW", "ACTIVITY_CREATE", "ACTIVITY_UPDATE",
                        "OPPORTUNITY_VIEW", "OPPORTUNITY_CREATE", "OPPORTUNITY_UPDATE",
                        "QUOTATION_VIEW", "QUOTATION_CREATE", "QUOTATION_UPDATE",
                        "ORDER_VIEW", "ORDER_CREATE", "ORDER_UPDATE"));

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith(COOKIE + "=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
        // The raw token is never stored: the database holds only a 64-character hash of it.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Long.class, response.getCookie(COOKIE)))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, account.id()))
                .startsWith("{bcrypt}").doesNotContain(account.password());
    }

    @Test
    void emailIsMatchedCaseInsensitively() {
        Account account = accounts.activeUser(RoleCodes.HR);

        login(account.email().toUpperCase(), account.password()).then().statusCode(200);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() {
        Account account = accounts.activeUser(RoleCodes.HR);

        Response wrongPassword = login(account.email(), "definitely-wrong-password");
        Response unknownEmail = login("nobody-" + UUID.randomUUID() + "@example.com", "whatever-password");

        for (Response response : new Response[] {wrongPassword, unknownEmail}) {
            response.then().statusCode(401)
                    .contentType("application/problem+json")
                    .body("code", equalTo("INVALID_CREDENTIALS"))
                    .body("detail", equalTo("Invalid email or password"));
            assertThat(response.getCookie(COOKIE)).isNull();
        }
    }

    @Test
    void malformedLoginRequestIsAValidationError() {
        given().contentType(ContentType.JSON).body(Map.of("email", "not-an-email", "password", ""))
                .post("/api/v1/auth/login")
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void accountLocksAfterFiveWrongPasswordsEvenForTheRightOne() {
        Account account = accounts.activeUser(RoleCodes.HR);

        for (int attempt = 0; attempt < 5; attempt++) {
            login(account.email(), "wrong-password-" + attempt).then().statusCode(401);
        }

        login(account.email(), account.password()).then().statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
        assertThat(jdbc.queryForObject(
                "SELECT locked_until > now() FROM users WHERE id = ?", Boolean.class, account.id())).isTrue();
    }

    @Test
    void fourWrongPasswordsDoNotLockAndASuccessResetsTheCounter() {
        Account account = accounts.activeUser(RoleCodes.HR);
        for (int attempt = 0; attempt < 4; attempt++) {
            login(account.email(), "wrong-password-" + attempt).then().statusCode(401);
        }

        login(account.email(), account.password()).then().statusCode(200);

        assertThat(jdbc.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, account.id())).isZero();
    }

    @Test
    void deactivatedAccountCannotSignInEvenWithTheRightPassword() {
        Account account = accounts.suspendedUser(RoleCodes.HR);

        login(account.email(), account.password()).then().statusCode(403)
                .body("code", equalTo("ACCOUNT_INACTIVE"));
        // ...but someone guessing passwords learns nothing about the account's state.
        login(account.email(), "wrong-password").then().statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    void invitedUserWithoutPasswordCannotSignIn() {
        Account account = accounts.invitedUser(RoleCodes.HR);

        login(account.email(), account.password()).then().statusCode(401);
    }

    // ------------------------------------------------------------------ current user

    @Test
    void currentUserEndpointRequiresAValidToken() {
        given().get("/api/v1/auth/me").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
        given().header("Authorization", "Bearer garbage").get("/api/v1/auth/me").then().statusCode(401);
    }

    @Test
    void currentUserEndpointReturnsRolesAndPermissions() {
        Account account = accounts.activeUser(RoleCodes.FINANCE, RoleCodes.TECHNICIAN);
        String accessToken = login(account.email(), account.password()).path("accessToken");

        bearer(accessToken).get("/api/v1/auth/me")
                .then().statusCode(200)
                .body("id", equalTo(account.id().toString()))
                .body("organizationId", equalTo(account.organizationId().toString()))
                .body("roles", containsInAnyOrder("FINANCE", "TECHNICIAN"))
                .body("permissions", containsInAnyOrder(
                        "FINANCE_VIEW", "FINANCE_MANAGE", "FINANCE_APPROVE", "FINANCE_CONFIGURE", "CUSTOMER_VIEW", "PROJECT_VIEW",
                        "FIELD_JOB_WORK", "PROOF_OF_WORK_VIEW", "PROOF_OF_WORK_CREATE",
                        "ORDER_VIEW", "QUOTATION_VIEW", "QUOTATION_CREATE", "QUOTATION_UPDATE", "QUOTATION_APPROVE"))
                .body("passwordHash", nullValue());
    }

    // ------------------------------------------------------------------ refresh / logout

    @Test
    void refreshRotatesTheTokenAndTheOldOneStopsWorking() {
        Account account = accounts.activeUser(RoleCodes.HR);
        String firstCookie = login(account.email(), account.password()).getCookie(COOKIE);

        Response refreshed = refresh(firstCookie);

        refreshed.then().statusCode(200).body("accessToken", notNullValue())
                .body("user.id", equalTo(account.id().toString()));
        String secondCookie = refreshed.getCookie(COOKIE);
        assertThat(secondCookie).isNotBlank().isNotEqualTo(firstCookie);
        bearer(refreshed.path("accessToken")).get("/api/v1/auth/me").then().statusCode(200);
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() {
        Account account = accounts.activeUser(RoleCodes.HR);
        String stolen = login(account.email(), account.password()).getCookie(COOKIE);
        String current = refresh(stolen).getCookie(COOKIE);

        // The old token shows up again: someone has a copy.
        refresh(stolen).then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));

        // The newest token is dead too, so neither party keeps the session.
        refresh(current).then().statusCode(401);
        assertThat(jdbc.queryForObject(
                "SELECT revoked_reason FROM user_sessions WHERE user_id = ?", String.class, account.id()))
                .isEqualTo("TOKEN_REUSE");
    }

    @Test
    void refreshWithoutOrWithUnknownCookieIsUnauthorized() {
        given().post("/api/v1/auth/refresh").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
        refresh("made-up-token").then().statusCode(401);
    }

    @Test
    void accessTokenCannotBeUsedAsARefreshToken() {
        Account account = accounts.activeUser(RoleCodes.HR);
        String accessToken = login(account.email(), account.password()).path("accessToken");

        refresh(accessToken).then().statusCode(401);
    }

    @Test
    void logoutEndsTheSessionAndClearsTheCookie() {
        Account account = accounts.activeUser(RoleCodes.HR);
        String cookie = login(account.email(), account.password()).getCookie(COOKIE);

        Response logout = given().cookie(COOKIE, cookie).post("/api/v1/auth/logout");

        logout.then().statusCode(204);
        assertThat(logout.getHeader("Set-Cookie")).startsWith(COOKIE + "=;").contains("Max-Age=0");
        refresh(cookie).then().statusCode(401);
        // Logging out twice, or with no cookie at all, is not an error.
        given().cookie(COOKIE, cookie).post("/api/v1/auth/logout").then().statusCode(204);
        given().post("/api/v1/auth/logout").then().statusCode(204);
    }

    // ------------------------------------------------------------------ sessions

    @Test
    void userSeesTheirDevicesAndCanSignOneOut() {
        Account account = accounts.activeUser(RoleCodes.HR);
        Response laptop = given().contentType(ContentType.JSON).header("User-Agent", "Laptop-Browser")
                .body(credentials(account.email(), account.password())).post("/api/v1/auth/login");
        Response phone = given().contentType(ContentType.JSON).header("User-Agent", "Phone-Browser")
                .body(credentials(account.email(), account.password())).post("/api/v1/auth/login");
        String laptopToken = laptop.path("accessToken");

        Response sessions = bearer(laptopToken).get("/api/v1/auth/sessions");
        sessions.then().statusCode(200).body("$", hasSize(2))
                .body("userAgent", containsInAnyOrder("Laptop-Browser", "Phone-Browser"));
        String phoneSessionId = sessions.path("find { it.userAgent == 'Phone-Browser' }.id");
        assertThat(sessions.<Boolean>path("find { it.userAgent == 'Laptop-Browser' }.current")).isTrue();

        bearer(laptopToken).delete("/api/v1/auth/sessions/" + phoneSessionId).then().statusCode(204);

        refresh(phone.getCookie(COOKIE)).then().statusCode(401);
        refresh(laptop.getCookie(COOKIE)).then().statusCode(200);
    }

    @Test
    void userCannotSignOutSomeoneElsesSession() {
        Account victim = accounts.activeUser(RoleCodes.HR);
        Account other = accounts.activeUser(RoleCodes.HR);
        String victimToken = login(victim.email(), victim.password()).path("accessToken");
        String victimSessionId = bearer(victimToken).get("/api/v1/auth/sessions").path("[0].id");
        String otherToken = login(other.email(), other.password()).path("accessToken");

        bearer(otherToken).delete("/api/v1/auth/sessions/" + victimSessionId)
                .then().statusCode(404).body("code", equalTo("RESOURCE_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ password change

    @Test
    void passwordChangeNeedsAuthenticationAndTheCurrentPassword() {
        Account account = accounts.activeUser(RoleCodes.HR);
        String accessToken = login(account.email(), account.password()).path("accessToken");

        given().contentType(ContentType.JSON)
                .body(Map.of("currentPassword", account.password(), "newPassword", NEW_PASSWORD))
                .post("/api/v1/auth/password/change").then().statusCode(401);

        bearer(accessToken).contentType(ContentType.JSON)
                .body(Map.of("currentPassword", "not-my-password", "newPassword", NEW_PASSWORD))
                .post("/api/v1/auth/password/change")
                .then().statusCode(422).body("code", equalTo("BUSINESS_RULE_VIOLATION"));

        bearer(accessToken).contentType(ContentType.JSON)
                .body(Map.of("currentPassword", account.password(), "newPassword", "short"))
                .post("/api/v1/auth/password/change")
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", hasItem("newPassword"));

        login(account.email(), account.password()).then().statusCode(200);
    }

    @Test
    void passwordChangeKeepsThisDeviceAndSignsOutTheOthers() {
        Account account = accounts.activeUser(RoleCodes.HR);
        Response thisDevice = login(account.email(), account.password());
        Response otherDevice = login(account.email(), account.password());

        bearer(thisDevice.path("accessToken")).contentType(ContentType.JSON)
                .body(Map.of("currentPassword", account.password(), "newPassword", NEW_PASSWORD))
                .post("/api/v1/auth/password/change").then().statusCode(204);

        login(account.email(), account.password()).then().statusCode(401);
        login(account.email(), NEW_PASSWORD).then().statusCode(200);
        refresh(otherDevice.getCookie(COOKIE)).then().statusCode(401);
        refresh(thisDevice.getCookie(COOKIE)).then().statusCode(200);
    }

    // ------------------------------------------------------------------ password reset

    @Test
    void forgotPasswordAnswersTheSameForKnownAndUnknownEmails() {
        Account account = accounts.activeUser(RoleCodes.HR);
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";

        forgot(account.email()).then().statusCode(202);
        forgot(unknown).then().statusCode(202);

        assertThat(resetNotifier.lastTokenFor(account.email())).isNotBlank();
        assertThat(resetNotifier.lastTokenFor(unknown)).isNull();
    }

    @Test
    void resetSetsANewPasswordOnceAndSignsOutEveryDevice() {
        Account account = accounts.activeUser(RoleCodes.HR);
        String cookie = login(account.email(), account.password()).getCookie(COOKIE);
        forgot(account.email());
        String token = resetNotifier.lastTokenFor(account.email());

        reset(token, NEW_PASSWORD).then().statusCode(204);

        login(account.email(), account.password()).then().statusCode(401);
        login(account.email(), NEW_PASSWORD).then().statusCode(200);
        refresh(cookie).then().statusCode(401);
        // A reset link works once.
        reset(token, "Yet-Another-Password-789").then().statusCode(422)
                .body("code", equalTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void onlyTheNewestResetLinkWorksAndUnknownTokensAreRejected() {
        Account account = accounts.activeUser(RoleCodes.HR);
        forgot(account.email());
        String older = resetNotifier.lastTokenFor(account.email());
        forgot(account.email());
        String newer = resetNotifier.lastTokenFor(account.email());

        reset(older, NEW_PASSWORD).then().statusCode(422);
        reset("made-up-token", NEW_PASSWORD).then().statusCode(422);
        reset(newer, NEW_PASSWORD).then().statusCode(204);
    }

    @Test
    void resetUnlocksALockedAccount() {
        Account account = accounts.activeUser(RoleCodes.HR);
        for (int attempt = 0; attempt < 5; attempt++) {
            login(account.email(), "wrong-password-" + attempt);
        }
        forgot(account.email());

        reset(resetNotifier.lastTokenFor(account.email()), NEW_PASSWORD).then().statusCode(204);

        login(account.email(), NEW_PASSWORD).then().statusCode(200);
    }

    @Test
    void invitedUserActivatesTheirAccountBySettingAPassword() {
        Account account = accounts.invitedUser(RoleCodes.SALES_EXECUTIVE);
        forgot(account.email());

        reset(resetNotifier.lastTokenFor(account.email()), NEW_PASSWORD).then().statusCode(204);

        assertThat(accounts.statusOf(account)).isEqualTo(UserStatus.ACTIVE);
        login(account.email(), NEW_PASSWORD).then().statusCode(200);
    }

    @Test
    void deactivatedUserCannotStartAReset() {
        Account account = accounts.suspendedUser(RoleCodes.HR);

        forgot(account.email()).then().statusCode(202);

        assertThat(resetNotifier.lastTokenFor(account.email())).isNull();
    }

    // ------------------------------------------------------------------ activation / deactivation (RBAC)

    @Test
    void deactivationRequiresAuthentication() {
        Account target = accounts.activeUser(RoleCodes.HR);

        given().post("/api/v1/users/" + target.id() + "/deactivate")
                .then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void userWithoutThePermissionIsForbidden() {
        Account salesExecutive = accounts.activeUser(RoleCodes.SALES_EXECUTIVE);
        Account target = accounts.activeColleagueOf(salesExecutive, RoleCodes.TECHNICIAN);
        String token = login(salesExecutive.email(), salesExecutive.password()).path("accessToken");

        bearer(token).post("/api/v1/users/" + target.id() + "/deactivate")
                .then().statusCode(403).contentType("application/problem+json").body("code", equalTo("FORBIDDEN"));
        bearer(token).post("/api/v1/users/" + target.id() + "/activate").then().statusCode(403);

        assertThat(accounts.statusOf(target)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void adminDeactivatesAUserWhoIsThenSignedOutAndCanBeReactivated() {
        Account admin = accounts.activeUser(RoleCodes.ADMIN);
        Account target = accounts.activeColleagueOf(admin, RoleCodes.SALES_EXECUTIVE);
        String adminToken = login(admin.email(), admin.password()).path("accessToken");
        String targetCookie = login(target.email(), target.password()).getCookie(COOKIE);

        bearer(adminToken).post("/api/v1/users/" + target.id() + "/deactivate")
                .then().statusCode(200).body("status", equalTo("SUSPENDED"));

        refresh(targetCookie).then().statusCode(401);
        login(target.email(), target.password()).then().statusCode(403).body("code", equalTo("ACCOUNT_INACTIVE"));

        bearer(adminToken).post("/api/v1/users/" + target.id() + "/activate")
                .then().statusCode(200).body("status", equalTo("ACTIVE"));
        login(target.email(), target.password()).then().statusCode(200);
    }

    @Test
    void nobodyCanDeactivateThemselves() {
        Account admin = accounts.activeUser(RoleCodes.ADMIN);
        String token = login(admin.email(), admin.password()).path("accessToken");

        bearer(token).post("/api/v1/users/" + admin.id() + "/deactivate")
                .then().statusCode(422).body("code", equalTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void adminCannotDeactivateASuperAdmin() {
        Account admin = accounts.activeUser(RoleCodes.ADMIN);
        Account superAdmin = accounts.activeColleagueOf(admin, RoleCodes.SUPER_ADMIN);
        String adminToken = login(admin.email(), admin.password()).path("accessToken");

        bearer(adminToken).post("/api/v1/users/" + superAdmin.id() + "/deactivate")
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        assertThat(accounts.statusOf(superAdmin)).isEqualTo(UserStatus.ACTIVE);

        String superToken = login(superAdmin.email(), superAdmin.password()).path("accessToken");
        bearer(superToken).post("/api/v1/users/" + admin.id() + "/deactivate").then().statusCode(200);
    }

    @Test
    void usersOfAnotherOrganizationAreInvisible() {
        Account admin = accounts.activeUser(RoleCodes.ADMIN);
        Account outsider = accounts.activeUser(RoleCodes.SALES_EXECUTIVE);
        String adminToken = login(admin.email(), admin.password()).path("accessToken");

        bearer(adminToken).post("/api/v1/users/" + outsider.id() + "/deactivate")
                .then().statusCode(404).body("code", equalTo("RESOURCE_NOT_FOUND"));
        assertThat(accounts.statusOf(outsider)).isEqualTo(UserStatus.ACTIVE);
    }

    // ------------------------------------------------------------------ helpers

    private static Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(credentials(email, password)).post("/api/v1/auth/login");
    }

    private static Map<String, String> credentials(String email, String password) {
        return Map.of("email", email, "password", password);
    }

    private static Response refresh(String refreshCookie) {
        return given().cookie(COOKIE, refreshCookie).post("/api/v1/auth/refresh");
    }

    private static Response forgot(String email) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email)).post("/api/v1/auth/password/forgot");
    }

    private static Response reset(String token, String newPassword) {
        return given().contentType(ContentType.JSON).body(Map.of("token", token, "newPassword", newPassword))
                .post("/api/v1/auth/password/reset");
    }

    private static RequestSpecification bearer(String accessToken) {
        return given().header("Authorization", "Bearer " + accessToken);
    }
}
