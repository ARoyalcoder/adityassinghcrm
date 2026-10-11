-- Audit and activity infrastructure.
--
--   audit_logs            the forensic record: who did what, when, from where, with before/after values.
--                         Append-only: the database itself refuses UPDATE and DELETE.
--   entity_activity_logs  the readable timeline of one record ("price changed from 100 to 120"),
--                         shown to users on that record's page.
--
-- Neither table has a foreign key to users or to the audited tables: an audit row must outlive the
-- thing it describes, and writing one must never be blocked by another table.

CREATE TABLE audit_logs (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    occurred_at     timestamptz  NOT NULL DEFAULT now(),
    -- The organization the event belongs to; decides who may see it. NULL for events with no
    -- organization (a sign-in attempt for an unknown email, start-up tasks).
    organization_id uuid,
    -- NULL when nobody was signed in (system jobs, anonymous requests).
    actor_id        uuid,
    -- Who it was in words, kept as it was at the time: the user's email, or 'system' / 'anonymous'.
    actor_label     varchar(254) NOT NULL,
    action          varchar(20)  NOT NULL,
    -- The module that owns the event: 'identity', 'catalog', ...
    module          varchar(50)  NOT NULL,
    entity_type     varchar(100),
    entity_id       varchar(100),
    -- A human-readable name of the entity at the time (a service's name, a user's email).
    entity_label    varchar(200),
    summary         varchar(500) NOT NULL,
    before_value    jsonb,
    after_value     jsonb,
    metadata        jsonb,
    ip_address      varchar(45),
    user_agent      varchar(500),
    -- Ties the row to the application log lines of the same request.
    request_id      varchar(64),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT ck_audit_logs_action CHECK (action IN (
        'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'STATUS_CHANGE',
        'APPROVAL', 'PAYMENT', 'EXPORT', 'IMPORT'))
);

-- The audit screen always lists newest first within an organization, optionally narrowed by one of these.
CREATE INDEX ix_audit_logs_org_time ON audit_logs (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_org_actor_time ON audit_logs (organization_id, actor_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_org_module_time ON audit_logs (organization_id, module, occurred_at DESC);
CREATE INDEX ix_audit_logs_org_action_time ON audit_logs (organization_id, action, occurred_at DESC);
CREATE INDEX ix_audit_logs_entity_time ON audit_logs (entity_type, entity_id, occurred_at DESC);

-- Tamper resistance: rows can be added, never changed or removed, whatever the application does.
-- (A future retention job must be a deliberate, separately reviewed migration that replaces this.)
CREATE FUNCTION audit_logs_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_change();

CREATE TABLE entity_activity_logs (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    occurred_at     timestamptz  NOT NULL DEFAULT now(),
    organization_id uuid,
    entity_type     varchar(100) NOT NULL,
    entity_id       varchar(100) NOT NULL,
    action          varchar(20)  NOT NULL,
    actor_id        uuid,
    actor_label     varchar(254) NOT NULL,
    -- One sentence for the timeline: "Updated name, base price".
    message         varchar(500) NOT NULL,
    -- Field-level changes: [{"field": "basePrice", "from": 100, "to": 120}, ...]
    changes         jsonb,
    -- The full audit record of the same event.
    audit_log_id    uuid,
    CONSTRAINT pk_entity_activity_logs PRIMARY KEY (id),
    CONSTRAINT fk_entity_activity_logs_audit_log FOREIGN KEY (audit_log_id) REFERENCES audit_logs (id),
    CONSTRAINT ck_entity_activity_logs_action CHECK (action IN (
        'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'STATUS_CHANGE',
        'APPROVAL', 'PAYMENT', 'EXPORT', 'IMPORT'))
);

CREATE INDEX ix_entity_activity_logs_entity_time ON entity_activity_logs (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_entity_activity_logs_audit_log ON entity_activity_logs (audit_log_id);

-- Who may read the audit log.
INSERT INTO permissions (code, name) VALUES ('AUDIT_VIEW', 'View the audit log and record activity');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE p.code = 'AUDIT_VIEW' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR');
