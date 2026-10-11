-- Activities and Follow-ups module: Tasks, Calls, Meetings, WhatsApp touchpoints,
-- Site visits, Notes, and Follow-ups with reminders and recurrence.

CREATE TABLE activities (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id      uuid          NOT NULL,
    type                 varchar(30)   NOT NULL,
    title                varchar(250)  NOT NULL,
    description          varchar(4000),
    entity_type          varchar(30)   NOT NULL,
    entity_id            uuid          NOT NULL,
    entity_name          varchar(255),
    owner_id             uuid          NOT NULL,
    owner_name           varchar(255)  NOT NULL,
    priority             varchar(20)   NOT NULL DEFAULT 'MEDIUM',
    status               varchar(20)   NOT NULL DEFAULT 'PENDING',
    due_date             timestamptz,
    occurred_at          timestamptz   NOT NULL DEFAULT now(),
    completed_at         timestamptz,
    completed_by         varchar(100),
    outcome              varchar(1000),
    direction            varchar(10),

    -- Reminders
    reminder_at          timestamptz,
    reminder_status      varchar(20)   NOT NULL DEFAULT 'NONE',

    -- Recurrence
    is_recurring         boolean       NOT NULL DEFAULT false,
    recurrence_pattern   varchar(20),
    recurrence_interval  integer       DEFAULT 1,
    recurrence_end_at    timestamptz,
    parent_activity_id   uuid,

    created_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           varchar(100)  NOT NULL,
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    updated_by           varchar(100)  NOT NULL,
    version              bigint        NOT NULL DEFAULT 0,
    deleted_at           timestamptz,

    CONSTRAINT pk_activities PRIMARY KEY (id),
    CONSTRAINT fk_activities_parent FOREIGN KEY (parent_activity_id) REFERENCES activities (id) ON DELETE SET NULL,
    CONSTRAINT ck_activities_type CHECK (type IN ('CALL', 'MEETING', 'WHATSAPP', 'EMAIL', 'SITE_VISIT', 'FOLLOW_UP', 'NOTE', 'TASK')),
    CONSTRAINT ck_activities_entity_type CHECK (entity_type IN ('CUSTOMER', 'LEAD', 'OPPORTUNITY', 'QUOTATION', 'PROJECT', 'TICKET')),
    CONSTRAINT ck_activities_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_activities_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_activities_direction CHECK (direction IS NULL OR direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_activities_reminder_status CHECK (reminder_status IN ('NONE', 'PENDING', 'TRIGGERED', 'DISMISSED')),
    CONSTRAINT ck_activities_recurrence_pattern CHECK (recurrence_pattern IS NULL OR recurrence_pattern IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'YEARLY'))
);

CREATE INDEX ix_activities_org_owner_status ON activities (organization_id, owner_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_activities_org_due ON activities (organization_id, due_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_activities_org_status_due ON activities (organization_id, status, due_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_activities_entity ON activities (entity_type, entity_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_activities_reminders ON activities (reminder_status, reminder_at) WHERE deleted_at IS NULL AND reminder_status = 'PENDING';

CREATE TABLE activity_reminders (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    activity_id     uuid         NOT NULL,
    organization_id uuid         NOT NULL,
    user_id         uuid         NOT NULL,
    remind_at       timestamptz  NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'PENDING',
    sent_at         timestamptz,
    channel         varchar(30)  NOT NULL DEFAULT 'IN_APP',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(100) NOT NULL,
    CONSTRAINT pk_activity_reminders PRIMARY KEY (id),
    CONSTRAINT fk_activity_reminders_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE,
    CONSTRAINT ck_activity_reminders_status CHECK (status IN ('PENDING', 'SENT', 'DISMISSED'))
);

CREATE INDEX ix_activity_reminders_status_time ON activity_reminders (status, remind_at);
CREATE INDEX ix_activity_reminders_user ON activity_reminders (user_id, status, remind_at DESC);

-- Permissions
INSERT INTO permissions (code, name) VALUES
    ('ACTIVITY_VIEW', 'View activities and tasks'),
    ('ACTIVITY_CREATE', 'Create activities and tasks'),
    ('ACTIVITY_UPDATE', 'Update, complete, and reschedule activities and tasks'),
    ('ACTIVITY_DELETE', 'Delete activities and tasks');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE (p.code IN ('ACTIVITY_VIEW', 'ACTIVITY_CREATE', 'ACTIVITY_UPDATE') 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER', 'SALES_EXECUTIVE'))
   OR (p.code = 'ACTIVITY_DELETE' 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER'));
