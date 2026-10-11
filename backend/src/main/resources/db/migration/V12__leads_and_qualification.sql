-- Leads and lead qualification.
--
--   leads ──< lead_activities
--     │   ──< lead_qualification_answers >── qualification_questions
--     └───< lead_score_history
--
-- A lead here is the minimum that qualification needs: who enquired, about which service, how much
-- it may be worth, and what has happened since. organization_id and service_id refer to other
-- modules by id only (no foreign key across modules). A lead's service-specific answers are in
-- lead_custom_field_values (V11).

CREATE SEQUENCE lead_number_seq START WITH 1001;

CREATE TABLE leads (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id      uuid          NOT NULL,
    -- The number people quote: LD-001001.
    lead_number          varchar(20)   NOT NULL,
    contact_name         varchar(200)  NOT NULL,
    company_name         varchar(200),
    email                varchar(254),
    phone                varchar(30),
    service_id           uuid          NOT NULL,
    -- What the business may be worth, in INR. NULL when nobody has estimated it yet.
    estimated_value      numeric(14, 2),
    status               varchar(20)   NOT NULL DEFAULT 'OPEN',
    -- When the lead was won or lost. Time-based rules stop at this moment for a closed lead.
    closed_at            timestamptz,

    -- The latest assessment, kept here so lists can filter and sort by it. These columns are
    -- written only by the qualification service (on every change to the lead, and by a scheduled
    -- job, because rules such as "no activity for 3 days" change with time alone). Why the score
    -- is what it is, is worked out on request and never stored here.
    score                integer       NOT NULL DEFAULT 0,
    priority             varchar(20)   NOT NULL DEFAULT 'LOW',
    health               varchar(20)   NOT NULL DEFAULT 'GOOD',
    qualification_status varchar(20)   NOT NULL DEFAULT 'NOT_STARTED',
    scored_at            timestamptz,
    -- Which scorer produced the score: 'rules-v1' today; a model's name and version later.
    score_engine         varchar(50),

    created_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           varchar(100)  NOT NULL,
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    updated_by           varchar(100)  NOT NULL,
    version              bigint        NOT NULL DEFAULT 0,
    deleted_at           timestamptz,
    CONSTRAINT pk_leads PRIMARY KEY (id),
    CONSTRAINT uq_leads_number UNIQUE (organization_id, lead_number),
    CONSTRAINT ck_leads_status CHECK (status IN ('OPEN', 'WON', 'LOST')),
    CONSTRAINT ck_leads_closed CHECK ((status = 'OPEN') = (closed_at IS NULL)),
    CONSTRAINT ck_leads_reachable CHECK (email IS NOT NULL OR phone IS NOT NULL),
    CONSTRAINT ck_leads_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_leads_estimated_value CHECK (estimated_value IS NULL OR estimated_value >= 0),
    CONSTRAINT ck_leads_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_leads_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_leads_health CHECK (health IN ('GOOD', 'NEEDS_ATTENTION', 'AT_RISK')),
    CONSTRAINT ck_leads_qualification_status CHECK (
        qualification_status IN ('NOT_STARTED', 'IN_PROGRESS', 'QUALIFIED', 'DISQUALIFIED'))
);

CREATE INDEX ix_leads_org_score ON leads (organization_id, score DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_leads_org_status ON leads (organization_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_leads_org_created ON leads (organization_id, created_at DESC) WHERE deleted_at IS NULL;
-- The scheduled job walks the open leads.
CREATE INDEX ix_leads_open ON leads (id) WHERE status = 'OPEN' AND deleted_at IS NULL;

-- What has been done about a lead: calls, meetings, emails, visits. Logged once, as a record.
CREATE TABLE lead_activities (
    id             uuid          NOT NULL DEFAULT gen_random_uuid(),
    lead_id        uuid          NOT NULL,
    type           varchar(20)   NOT NULL,
    occurred_at    timestamptz   NOT NULL,
    subject        varchar(200)  NOT NULL,
    -- The author's name when it was logged, so it still reads correctly if the user is renamed or removed.
    logged_by_name varchar(200)  NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    created_by     varchar(100)  NOT NULL,
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     varchar(100)  NOT NULL,
    version        bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_lead_activities PRIMARY KEY (id),
    CONSTRAINT fk_lead_activities_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE,
    CONSTRAINT ck_lead_activities_type CHECK (type IN ('CALL', 'MEETING', 'EMAIL', 'SITE_VISIT', 'MESSAGE', 'OTHER'))
);

CREATE INDEX ix_lead_activities_lead_time ON lead_activities (lead_id, occurred_at DESC);

-- The questions an organization asks to qualify a lead, defined by its administrators. Each
-- answer is one of the question's options, and each option says how many points it is worth and
-- whether it rules the lead out.
CREATE TABLE qualification_questions (
    id              uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid          NOT NULL,
    -- Identifies the question in the API and in score explanations. Never changes once created.
    question_key    varchar(60)   NOT NULL,
    label           varchar(200)  NOT NULL,
    help_text       varchar(300),
    -- [{"value": "YES", "label": "Yes", "points": 15, "disqualifies": false}, ...]
    options         jsonb         NOT NULL,
    -- A lead is not qualified until every required question is answered.
    required        boolean       NOT NULL DEFAULT false,
    display_order   integer       NOT NULL DEFAULT 0,
    active          boolean       NOT NULL DEFAULT true,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    created_by      varchar(100)  NOT NULL,
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(100)  NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    deleted_at      timestamptz,
    CONSTRAINT pk_qualification_questions PRIMARY KEY (id),
    CONSTRAINT ck_qualification_questions_key_format CHECK (question_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_qualification_questions_options CHECK (jsonb_typeof(options) = 'array')
);

CREATE UNIQUE INDEX uq_qualification_questions_org_key
    ON qualification_questions (organization_id, question_key) WHERE deleted_at IS NULL;

CREATE TABLE lead_qualification_answers (
    id           uuid          NOT NULL DEFAULT gen_random_uuid(),
    lead_id      uuid          NOT NULL,
    question_id  uuid          NOT NULL,
    -- The chosen option's value.
    option_value varchar(60)   NOT NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    created_by   varchar(100)  NOT NULL,
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    updated_by   varchar(100)  NOT NULL,
    version      bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_lead_qualification_answers PRIMARY KEY (id),
    CONSTRAINT fk_lead_qualification_answers_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE,
    CONSTRAINT fk_lead_qualification_answers_question FOREIGN KEY (question_id) REFERENCES qualification_questions (id),
    CONSTRAINT uq_lead_qualification_answers UNIQUE (lead_id, question_id)
);

CREATE INDEX ix_lead_qualification_answers_question ON lead_qualification_answers (question_id);

-- Every time a lead's assessment changes, what it became and why. This is the record of how a
-- lead's score moved, and (with the lead's eventual WON or LOST) the data a learned scorer would
-- be evaluated against.
CREATE TABLE lead_score_history (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    lead_id              uuid          NOT NULL,
    score                integer       NOT NULL,
    priority             varchar(20)   NOT NULL,
    health               varchar(20)   NOT NULL,
    qualification_status varchar(20)   NOT NULL,
    engine               varchar(50)   NOT NULL,
    -- The explanation at that moment: [{"code", "category", "label", "points"}, ...]
    factors              jsonb         NOT NULL,
    computed_at          timestamptz   NOT NULL,
    CONSTRAINT pk_lead_score_history PRIMARY KEY (id),
    CONSTRAINT fk_lead_score_history_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE
);

CREATE INDEX ix_lead_score_history_lead_time ON lead_score_history (lead_id, computed_at DESC);

-- Permissions.
INSERT INTO permissions (code, name) VALUES
    ('LEAD_VIEW', 'View leads'),
    ('LEAD_CREATE', 'Create leads'),
    ('LEAD_UPDATE', 'Edit leads, log activity and answer qualification questions'),
    ('LEAD_DELETE', 'Delete leads'),
    ('LEAD_CONFIGURE', 'Define the qualification questions');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE (p.code = 'LEAD_VIEW' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER', 'SALES_EXECUTIVE'))
   OR (p.code IN ('LEAD_CREATE', 'LEAD_UPDATE') AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE'))
   OR (p.code IN ('LEAD_DELETE', 'LEAD_CONFIGURE') AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER'));
