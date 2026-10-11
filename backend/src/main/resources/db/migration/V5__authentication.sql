-- Authentication: login protection, sessions (devices), rotating refresh tokens, password reset.
-- No token is ever stored in clear text: only its SHA-256 hash (64 hex characters).

-- Permission codes follow MODULE_ACTION (e.g. CUSTOMER_VIEW); the table is still empty at this point.
ALTER TABLE permissions DROP CONSTRAINT ck_permissions_code_format;
ALTER TABLE permissions ADD CONSTRAINT ck_permissions_code_format
    CHECK (code ~ '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$');

ALTER TABLE users
    ADD COLUMN failed_login_attempts integer NOT NULL DEFAULT 0,
    -- Sign-in is refused until this time after too many wrong passwords.
    ADD COLUMN locked_until        timestamptz,
    ADD COLUMN password_changed_at timestamptz;

-- One row per sign-in (browser/device). Revoking the row signs that device out.
CREATE TABLE user_sessions (
    id             uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id        uuid         NOT NULL,
    ip_address     varchar(45),
    user_agent     varchar(500),
    last_used_at   timestamptz  NOT NULL DEFAULT now(),
    -- Absolute limit: the session cannot be refreshed past this, however active it is.
    expires_at     timestamptz  NOT NULL,
    revoked_at     timestamptz,
    revoked_reason varchar(30),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_sessions_revoked_reason CHECK (revoked_reason IN (
        'LOGOUT', 'REVOKED_BY_USER', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'ACCOUNT_DEACTIVATED', 'TOKEN_REUSE')),
    CONSTRAINT ck_user_sessions_revocation CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

CREATE INDEX ix_user_sessions_user ON user_sessions (user_id);
CREATE INDEX ix_user_sessions_expires_at ON user_sessions (expires_at);

-- Every refresh issues a new token and marks the old one used. A used token that shows up again
-- means it was copied, so the whole session is revoked (TOKEN_REUSE).
CREATE TABLE refresh_tokens (
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    session_id uuid        NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES user_sessions (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id);

-- Single-use, short-lived. Also used to let an invited user set their first password.
CREATE TABLE password_reset_tokens (
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
CREATE INDEX ix_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
