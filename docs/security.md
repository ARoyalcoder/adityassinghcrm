# Authentication and authorization

**The backend is the only thing that enforces access.** Frontend route guards and hidden buttons
exist so people are not shown things that would fail; they protect nothing.

## Overview

```
login ──> access token (JWT, 15 min, in the response body, kept in browser memory)
     └──> refresh token (opaque, in an HttpOnly cookie, stored server-side as a hash)

API call ──> Authorization: Bearer <access token> ──> signature, expiry, issuer checked
                                                 └──> @PreAuthorize permission check ──> 200 / 403

access token expired ──> POST /auth/refresh (cookie) ──> new access token + NEW refresh cookie
```

| | Access token | Refresh token |
| --- | --- | --- |
| Format | Signed JWT (HS256) | 256 random bits, opaque |
| Lifetime | 15 minutes | 14 days idle, 30 days absolute per sign-in |
| Where the browser keeps it | JavaScript memory only | `HttpOnly; Secure; SameSite=Strict` cookie, path `/api/v1/auth` |
| Where the server keeps it | Nowhere (verified by signature) | SHA-256 hash in `refresh_tokens` |
| Carries | user id, session id, organization id, roles, permissions | Nothing |

Why two tokens: the access token makes API calls fast (no database lookup) but cannot be taken back
once issued, so it is short-lived. The refresh token is long-lived but checked against the database
on every use, so it can be revoked at any moment.

## Endpoints

All under `/api/v1`.

| Endpoint | Needs | Purpose |
| --- | --- | --- |
| `POST /auth/login` | nothing | Email + password. Returns the access token and user; sets the refresh cookie |
| `POST /auth/refresh` | refresh cookie | New access token and a new refresh cookie. Each cookie works once |
| `POST /auth/logout` | refresh cookie | Ends that session, deletes the cookie. Always `204` |
| `GET /auth/me` | access token | The signed-in user with current roles and permissions |
| `POST /auth/password/change` | access token | Needs the current password. Signs out every other device |
| `POST /auth/password/forgot` | nothing | Starts a reset. Always `202`, whether or not the email exists |
| `POST /auth/password/reset` | reset token | Sets a new password. Signs out every device |
| `GET /auth/sessions` | access token | The devices you are signed in on |
| `DELETE /auth/sessions/{id}` | access token | Sign one of your devices out |
| `POST /users/{id}/activate` | `USER_UPDATE` | Let a user sign in again |
| `POST /users/{id}/deactivate` | `USER_UPDATE` | Block a user and end all their sessions |

## Protections

| Threat | What stops it |
| --- | --- |
| Stolen database | Passwords are bcrypt hashes (stored with an algorithm prefix so the algorithm can be upgraded). Refresh and reset tokens are stored only as hashes |
| Password guessing | 5 wrong passwords in a row lock the account for 15 minutes. Nginx limits `/api/v1/auth/` to 5 requests/second per address |
| Finding out which emails have accounts | Wrong email, wrong password and locked account give the same `401 INVALID_CREDENTIALS`, and take the same time. "Forgot password" always answers `202` |
| XSS stealing the session | The refresh token is in an `HttpOnly` cookie that scripts cannot read. The access token is never written to `localStorage` |
| CSRF | API calls need a bearer header, which browsers never add by themselves. The cookie is `SameSite=Strict` and only sent to `/api/v1/auth` |
| Stolen refresh token | Tokens rotate on every use. If a used token is presented again, the whole session is revoked (`TOKEN_REUSE`), so a thief and the victim cannot both keep it |
| Forged or altered access token | Signature, expiry and issuer are verified on every request |
| Privilege escalation through naming | Roles and permissions live in separate authority namespaces (see below) |
| Covering tracks | Sign-ins (including failures), sign-outs, password changes and every change to audited data are written to an append-only audit log that the application cannot edit. See [audit.md](audit.md) |
| An admin locking out the owners | Only a `SUPER_ADMIN` can activate or deactivate a `SUPER_ADMIN`. Nobody can deactivate themselves |
| Reaching another organization's users | They are reported as `404`, never `403`, so their existence is not revealed |

### Known limits

- **Revocation takes up to 15 minutes for API calls.** Deactivating a user, changing their roles, or
  revoking a session stops refresh immediately, but an access token already issued keeps working
  until it expires. Shorten `JWT_ACCESS_TOKEN_TTL` if that window is too long. Closing it entirely
  would need a per-request session lookup (in Redis), which is not built.
- **Password reset is not delivered yet.** Tokens are created and verified, but sending the email is
  the notification module's job (`PasswordResetNotifier`). Until it exists, the token is logged in
  the `local` profile only and dropped everywhere else.
- **No multi-factor authentication** and no "new device" alerts.
- **Two tabs refreshing at the very same moment** can trip reuse detection and sign the user out.
  The frontend shares one refresh per tab; across tabs it is rare but possible.
- Password rules are length only (10 to 128 characters). There is no breached-password check.

## Account states

| Status | Can sign in | How it gets there |
| --- | --- | --- |
| `INVITED` | No | Created without a password. Becomes `ACTIVE` when the user sets one through a reset link |
| `ACTIVE` | Yes | |
| `SUSPENDED` | No | `POST /users/{id}/deactivate`. Reversible with `/activate` |
| `DISABLED` | No | Reserved for permanent removal (set by user management, not built yet) |

Someone who types the correct password for an inactive account gets `403 ACCOUNT_INACTIVE`; anyone
else gets the generic `401`.

## Roles and permissions

- A **permission** is one thing a user may do, named `MODULE_ACTION` (`CUSTOMER_VIEW`,
  `FINANCE_APPROVE`). Code checks permissions.
- A **role** is a named bundle of permissions assigned to users. Roles are data.
- A user's permissions are the union over their active roles.

### Checking a permission

```java
@PreAuthorize("hasAuthority('" + Permissions.CUSTOMER_VIEW + "')")
public CustomerResponse get(@PathVariable UUID id) { ... }
```

- Put the check on the controller method (or the service method if the service is called from more
  than one place).
- An endpoint with no `@PreAuthorize` still requires a valid token; only the short lists in
  `SecurityConfig` are public.
- Missing or invalid token gives `401 UNAUTHENTICATED`. Valid token without the permission gives `403 FORBIDDEN`.
- Use `CurrentUser.require()` for the user's id and `organizationId`, and always filter data by
  organization: a permission says *what* a user may do, not *whose* data.
- Prefer permissions to `hasRole(...)`. Role authorities carry the prefix `ROLE:`, which permission
  codes cannot contain, so the permission `ROLE_VIEW` and a role named `VIEW` can never be confused.

### Adding a permission

1. Add the constant to `identity.api.Permissions` and to `Permissions.ALL`.
2. In a new migration, insert it into `permissions` and grant it to `SUPER_ADMIN` and any other roles.
3. `PermissionCatalogIT` fails if the constants and the table differ.

### Default role matrix

Seeded by `V6__seed_roles_and_permissions.sql` (`AUDIT_VIEW` by `V8`; `CUSTOMER_EXPORT` by `V9`, also held by
Super Admin, Admin and Director; the `LEAD_*` permissions by `V12`, see [leads.md](leads.md); `PROJECT_CONFIGURE`, held by Super Admin and
Admin, by `V19`, see [project-templates.md](project-templates.md); the `FIELD_JOB_*` permissions by `V22`, see
[field-operations.md](field-operations.md)). **This is a starting proposal; review it before go-live.**
Customer, project and finance permissions exist ahead of their modules so roles can be set up now.

| Role | Permissions |
| --- | --- |
| `SUPER_ADMIN` | All |
| `ADMIN` | All of user, organization, branch, catalog, customer, lead, project; `ROLE_VIEW`; `FINANCE_VIEW`; `AUDIT_VIEW`; `FIELD_JOB_VIEW`, `FIELD_JOB_MANAGE`. Not `ROLE_UPDATE`, not `FINANCE_APPROVE`, not `FIELD_JOB_WORK` |
| `DIRECTOR` | View everything, including `LEAD_VIEW`, `FIELD_JOB_VIEW` and `AUDIT_VIEW`; `PROJECT_UPDATE`; `FINANCE_APPROVE` |
| `SALES_MANAGER` | Customer view/create/update/delete/export; lead view/create/update/delete and `LEAD_CONFIGURE`; `PROJECT_VIEW`; `CATALOG_VIEW` |
| `SALES_EXECUTIVE` | Customer view/create/update; lead view/create/update; `CATALOG_VIEW` |
| `PROJECT_MANAGER` | `PROJECT_VIEW`, `PROJECT_UPDATE`; `CUSTOMER_VIEW`; `CATALOG_VIEW`; `FIELD_JOB_VIEW`, `FIELD_JOB_MANAGE` |
| `DESIGNER` | `PROJECT_VIEW`; `CATALOG_VIEW` |
| `TECHNICIAN` | `PROJECT_VIEW`; `FIELD_JOB_WORK` (their own field jobs, see [field-operations.md](field-operations.md)) |
| `FINANCE` | `FINANCE_VIEW`, `FINANCE_APPROVE`; `CUSTOMER_VIEW`; `PROJECT_VIEW` |
| `HR` | User view/create/update; `ORGANIZATION_VIEW`; `BRANCH_VIEW` |
| `SUPPORT_AGENT` | `CUSTOMER_VIEW`; `PROJECT_VIEW`; `FIELD_JOB_VIEW` |
| `VENDOR`, `PARTNER` | None yet |

## First administrator

Nothing is seeded, so a new database has nobody who can sign in. Set `BOOTSTRAP_ADMIN_EMAIL` and
`BOOTSTRAP_ADMIN_PASSWORD` (10+ characters) and start the application: if, and only if, the `users`
table is empty, it creates one organization and one active `SUPER_ADMIN`. Then sign in, change the
password, and remove the two variables.

The `local` profile has built-in bootstrap values (see `application-local.yml`) so a developer can
sign in to a fresh local database straight away. They exist in that profile only.

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `JWT_SECRET` | none (required) | Signing key, 32+ characters. Start-up fails without it |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Access token lifetime |
| `AUTH_REFRESH_TOKEN_TTL` | `14d` | Idle timeout of a session |
| `AUTH_SESSION_MAX_LIFETIME` | `30d` | Absolute limit of a session |
| `AUTH_PASSWORD_RESET_TOKEN_TTL` | `30m` | How long a reset link works |
| `AUTH_MAX_FAILED_LOGINS` / `AUTH_LOCK_DURATION` | `5` / `15m` | Account lock |
| `AUTH_COOKIE_SECURE` | `true` | Refresh cookie over HTTPS only. `false` only for plain-http local use |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | empty | First administrator (see above) |

Production must be served over HTTPS; with `AUTH_COOKIE_SECURE=true` the browser will not send the
refresh cookie over plain http and nobody could stay signed in.

## Frontend

- `features/auth/session.ts` holds the session outside React. On page load it calls `/auth/refresh`
  to find out whether the cookie still holds a session.
- `lib/api/client.ts` adds the bearer header. On a `401` it refreshes once and repeats the request;
  if the refresh fails the user is returned to the login page.
- `RequireAuth` guards every route except `/login`. `RequirePermission` and `useHasPermission` hide
  pages and controls. Both are convenience only.

## Tests

| Test | Proves |
| --- | --- |
| `AuthorizationEnforcementTest` | Real tokens through the real filter chain: no token, garbage, tampered, wrong key, expired, wrong issuer all give 401; missing permission or role gives 403; roles and permissions cannot stand in for each other |
| `AuthFlowIT` | Login, lockout, inactive accounts, refresh rotation, reuse detection, logout, sessions, password change and reset, activation and deactivation, organization isolation, over real HTTP and PostgreSQL |
| `PermissionCatalogIT` | Seeded roles and permissions match the constants and the matrix above |
| `e2e/auth.spec.ts` | Login redirect and return, validation, silent token renewal, session loss, sign out |
