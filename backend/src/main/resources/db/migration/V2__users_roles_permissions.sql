-- Identity and access: users, roles, permissions and their assignments.
--   user --< user_roles >-- role --< role_permissions >-- permission

CREATE TABLE users (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid         NOT NULL,
    branch_id       uuid,
    email           varchar(254) NOT NULL,
    full_name       varchar(200) NOT NULL,
    phone           varchar(30),
    -- NULL until the user sets a password (status INVITED). Only ever a hash, never a password.
    password_hash   varchar(255),
    status          varchar(20)  NOT NULL DEFAULT 'INVITED',
    last_login_at   timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(100) NOT NULL,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(100) NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    deleted_at      timestamptz,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    -- Composite on purpose: a user can only be placed in a branch of their own organization.
    CONSTRAINT fk_users_branch FOREIGN KEY (branch_id, organization_id) REFERENCES branches (id, organization_id),
    CONSTRAINT ck_users_status CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

-- Email is the login identifier: unique across all live users.
CREATE UNIQUE INDEX uq_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_organization ON users (organization_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_branch ON users (branch_id) WHERE deleted_at IS NULL;

CREATE TABLE roles (
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    code        varchar(50)  NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(500),
    -- System roles ship with the product and cannot be edited or removed by administrators.
    system_role boolean      NOT NULL DEFAULT false,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  varchar(100) NOT NULL,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  varchar(100) NOT NULL,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

-- Permissions are defined by the application (added through migrations), not by administrators,
-- so they carry no actor columns and are never deleted, only deactivated.
CREATE TABLE permissions (
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    code        varchar(100) NOT NULL,
    name        varchar(150) NOT NULL,
    description varchar(500),
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code),
    -- e.g. catalog.service.write
    CONSTRAINT ck_permissions_code_format CHECK (code ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$')
);

CREATE TABLE role_permissions (
    role_id       uuid        NOT NULL,
    permission_id uuid        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

CREATE TABLE user_roles (
    id         uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id    uuid         NOT NULL,
    role_id    uuid         NOT NULL,
    -- Who granted the role and when: the assignment itself is the audit record.
    created_at timestamptz  NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (id),
    CONSTRAINT uq_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- A role that is still assigned to someone cannot be deleted.
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX ix_user_roles_role ON user_roles (role_id);
