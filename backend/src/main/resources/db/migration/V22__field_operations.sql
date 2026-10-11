-- Field operations: jobs and site visits carried out at a customer's site by a technician.
--
--   field_jobs ──< field_job_checklist_items
--       │      ──< field_job_materials
--       │      ──< field_job_photos
--       └──────< field_job_notes
--
-- organization_id, customer_id, project_id and technician_id refer to other modules by id only.
-- The customer's name and the site details are copied onto the job: a technician on site must be
-- able to read them even if the customer record changes or cannot be reached.

CREATE SEQUENCE field_job_number_seq START WITH 1001;

CREATE TABLE field_jobs (
    id                 uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id    uuid          NOT NULL,
    -- The number people quote: JOB-001001.
    job_number         varchar(20)   NOT NULL,
    -- JOB: work to be done. SITE_VISIT: go and look (survey, inspection, measurement).
    job_type           varchar(20)   NOT NULL DEFAULT 'JOB',
    title              varchar(200)  NOT NULL,
    instructions       text,
    customer_id        uuid,
    customer_name      varchar(200)  NOT NULL,
    site_address       varchar(500)  NOT NULL,
    site_contact_name  varchar(200),
    site_contact_phone varchar(30),
    project_id         uuid,
    scheduled_start    timestamptz   NOT NULL,
    scheduled_end      timestamptz,
    technician_id      uuid,
    technician_name    varchar(200),
    status             varchar(30)   NOT NULL DEFAULT 'UNASSIGNED',
    -- When each step of the workflow was reached.
    assigned_at        timestamptz,
    accepted_at        timestamptz,
    en_route_at        timestamptz,
    arrived_at         timestamptz,
    started_at         timestamptz,
    completed_at       timestamptz,
    confirmed_at       timestamptz,
    cancelled_at       timestamptz,
    completion_notes   text,
    -- Customer sign-off: who confirmed the work, what they said, and their signature as drawn on
    -- the technician's screen (a PNG data URL).
    signed_by_name     varchar(200),
    signoff_remarks    varchar(1000),
    signature          text,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    created_by         varchar(100)  NOT NULL,
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    updated_by         varchar(100)  NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    deleted_at         timestamptz,
    CONSTRAINT pk_field_jobs PRIMARY KEY (id),
    CONSTRAINT uq_field_jobs_number UNIQUE (organization_id, job_number),
    CONSTRAINT ck_field_jobs_type CHECK (job_type IN ('JOB', 'SITE_VISIT')),
    CONSTRAINT ck_field_jobs_status CHECK (status IN (
        'UNASSIGNED', 'ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED',
        'CUSTOMER_CONFIRMED', 'CANCELLED')),
    -- A job that has left UNASSIGNED has a technician.
    CONSTRAINT ck_field_jobs_technician CHECK (
        status IN ('UNASSIGNED', 'CANCELLED') OR technician_id IS NOT NULL),
    CONSTRAINT ck_field_jobs_schedule CHECK (scheduled_end IS NULL OR scheduled_end >= scheduled_start),
    CONSTRAINT ck_field_jobs_signoff CHECK (status <> 'CUSTOMER_CONFIRMED' OR signed_by_name IS NOT NULL)
);

-- A technician's own jobs by day; the dispatcher's list by status and day.
CREATE INDEX ix_field_jobs_technician_schedule ON field_jobs (technician_id, scheduled_start) WHERE deleted_at IS NULL;
CREATE INDEX ix_field_jobs_org_status ON field_jobs (organization_id, status, scheduled_start) WHERE deleted_at IS NULL;

CREATE TABLE field_job_checklist_items (
    id           uuid          NOT NULL DEFAULT gen_random_uuid(),
    job_id       uuid          NOT NULL,
    label        varchar(300)  NOT NULL,
    position     integer       NOT NULL,
    done         boolean       NOT NULL DEFAULT false,
    done_at      timestamptz,
    done_by_name varchar(200),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_field_job_checklist_items PRIMARY KEY (id),
    CONSTRAINT fk_field_job_checklist_items_job FOREIGN KEY (job_id) REFERENCES field_jobs (id) ON DELETE CASCADE
);
CREATE INDEX ix_field_job_checklist_items_job ON field_job_checklist_items (job_id, position);

CREATE TABLE field_job_materials (
    id               uuid           NOT NULL DEFAULT gen_random_uuid(),
    job_id           uuid           NOT NULL,
    name             varchar(200)   NOT NULL,
    quantity         numeric(12, 3) NOT NULL,
    -- "pcs", "m", "kg".
    unit             varchar(20),
    notes            varchar(500),
    recorded_by_name varchar(200)   NOT NULL,
    created_at       timestamptz    NOT NULL DEFAULT now(),
    updated_at       timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT pk_field_job_materials PRIMARY KEY (id),
    CONSTRAINT fk_field_job_materials_job FOREIGN KEY (job_id) REFERENCES field_jobs (id) ON DELETE CASCADE,
    CONSTRAINT ck_field_job_materials_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_field_job_materials_job ON field_job_materials (job_id);

-- The database holds where a photo is stored and what it is; the bytes are in object storage.
CREATE TABLE field_job_photos (
    id               uuid          NOT NULL DEFAULT gen_random_uuid(),
    job_id           uuid          NOT NULL,
    storage_key      varchar(300)  NOT NULL,
    file_name        varchar(255)  NOT NULL,
    content_type     varchar(100)  NOT NULL,
    size_bytes       bigint        NOT NULL,
    caption          varchar(300),
    uploaded_by_name varchar(200)  NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_field_job_photos PRIMARY KEY (id),
    CONSTRAINT fk_field_job_photos_job FOREIGN KEY (job_id) REFERENCES field_jobs (id) ON DELETE CASCADE
);
CREATE INDEX ix_field_job_photos_job ON field_job_photos (job_id);

CREATE TABLE field_job_notes (
    id          uuid          NOT NULL DEFAULT gen_random_uuid(),
    job_id      uuid          NOT NULL,
    body        varchar(2000) NOT NULL,
    author_name varchar(200)  NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_field_job_notes PRIMARY KEY (id),
    CONSTRAINT fk_field_job_notes_job FOREIGN KEY (job_id) REFERENCES field_jobs (id) ON DELETE CASCADE
);
CREATE INDEX ix_field_job_notes_job ON field_job_notes (job_id, created_at);

-- Permissions.
--   FIELD_JOB_WORK   carry out the jobs assigned to you (and see only those)
--   FIELD_JOB_VIEW   see every job of the organization
--   FIELD_JOB_MANAGE create, schedule, assign and cancel jobs
INSERT INTO permissions (code, name) VALUES
    ('FIELD_JOB_WORK', 'Carry out the field jobs assigned to you'),
    ('FIELD_JOB_VIEW', 'View all field jobs'),
    ('FIELD_JOB_MANAGE', 'Create, schedule, assign and cancel field jobs');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE (p.code = 'FIELD_JOB_WORK' AND r.code IN ('SUPER_ADMIN', 'TECHNICIAN'))
   OR (p.code = 'FIELD_JOB_VIEW' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'PROJECT_MANAGER', 'SUPPORT_AGENT'))
   OR (p.code = 'FIELD_JOB_MANAGE' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'PROJECT_MANAGER'));
