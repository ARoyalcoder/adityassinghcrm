-- Proof of Work: verifiable completion records for field jobs and project tasks.
--
-- Supports:
--   - Before, during, and after photos (stored via ObjectStorage abstraction)
--   - Technical and delivery documents
--   - Checklist completions with status and timestamps
--   - Technician field notes and observations
--   - Customer sign-off confirmations with signatures and remarks
--   - Immutable verification log and protection against unauthorized deletion
--
-- Tables:
--   proof_of_work_items ──< proof_of_work_attachments
--                       ──< proof_of_work_checklists
--
-- Every proof item links to a project (project_id) or field job (job_id).

CREATE TABLE proof_of_work_items (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id      uuid          NOT NULL,
    entity_type          varchar(20)   NOT NULL, -- 'PROJECT' or 'JOB'
    project_id           uuid,
    job_id               uuid,
    title                varchar(200)  NOT NULL,
    category             varchar(30)   NOT NULL DEFAULT 'GENERAL', -- BEFORE_PHOTO, DURING_PHOTO, AFTER_PHOTO, DOCUMENT, CHECKLIST, TECHNICIAN_NOTE, CUSTOMER_CONFIRMATION, GENERAL
    technician_id        uuid,
    technician_name      varchar(200)  NOT NULL,
    technician_notes     text,
    customer_confirmed   boolean       NOT NULL DEFAULT false,
    customer_name        varchar(200),
    customer_remarks     varchar(1000),
    customer_signature   text,         -- PNG data URL
    confirmed_at         timestamptz,
    verified             boolean       NOT NULL DEFAULT false,
    verified_by_id       uuid,
    verified_by_name     varchar(200),
    verified_at          timestamptz,
    recorded_at          timestamptz   NOT NULL DEFAULT now(),
    created_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           varchar(100)  NOT NULL,
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    updated_by           varchar(100)  NOT NULL,
    deleted_at           timestamptz,
    deleted_by           varchar(100),
    deletion_reason      varchar(500),
    CONSTRAINT pk_proof_of_work_items PRIMARY KEY (id),
    CONSTRAINT ck_pow_entity_type CHECK (entity_type IN ('PROJECT', 'JOB')),
    CONSTRAINT ck_pow_entity_reference CHECK (
        (entity_type = 'PROJECT' AND project_id IS NOT NULL) OR
        (entity_type = 'JOB' AND job_id IS NOT NULL)
    ),
    CONSTRAINT ck_pow_category CHECK (category IN (
        'BEFORE_PHOTO', 'DURING_PHOTO', 'AFTER_PHOTO', 'DOCUMENT',
        'CHECKLIST', 'TECHNICIAN_NOTE', 'CUSTOMER_CONFIRMATION', 'GENERAL'
    ))
);

CREATE INDEX ix_pow_org_project ON proof_of_work_items (organization_id, project_id, recorded_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_pow_org_job ON proof_of_work_items (organization_id, job_id, recorded_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_pow_org_category ON proof_of_work_items (organization_id, category, recorded_at DESC) WHERE deleted_at IS NULL;

-- File attachments (photos and documents stored in S3/MinIO through ObjectStorage)
CREATE TABLE proof_of_work_attachments (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    proof_item_id        uuid          NOT NULL,
    attachment_type      varchar(20)   NOT NULL, -- 'BEFORE_PHOTO', 'DURING_PHOTO', 'AFTER_PHOTO', 'DOCUMENT'
    file_name            varchar(255)  NOT NULL,
    content_type         varchar(100)  NOT NULL,
    size_bytes           bigint        NOT NULL,
    storage_key          varchar(300)  NOT NULL,
    caption              varchar(300),
    uploaded_by_name     varchar(200)  NOT NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_proof_of_work_attachments PRIMARY KEY (id),
    CONSTRAINT fk_pow_attachments_item FOREIGN KEY (proof_item_id) REFERENCES proof_of_work_items (id) ON DELETE CASCADE,
    CONSTRAINT ck_pow_attachment_type CHECK (attachment_type IN ('BEFORE_PHOTO', 'DURING_PHOTO', 'AFTER_PHOTO', 'DOCUMENT'))
);

CREATE INDEX ix_pow_attachments_item ON proof_of_work_attachments (proof_item_id);

-- Checklists within Proof of Work
CREATE TABLE proof_of_work_checklists (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    proof_item_id        uuid          NOT NULL,
    item_title           varchar(300)  NOT NULL,
    completed            boolean       NOT NULL DEFAULT false,
    completed_at         timestamptz,
    completed_by_name    varchar(200),
    display_order        integer       NOT NULL DEFAULT 0,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_proof_of_work_checklists PRIMARY KEY (id),
    CONSTRAINT fk_pow_checklists_item FOREIGN KEY (proof_item_id) REFERENCES proof_of_work_items (id) ON DELETE CASCADE
);

CREATE INDEX ix_pow_checklists_item ON proof_of_work_checklists (proof_item_id, display_order);

-- Permissions
INSERT INTO permissions (code, name) VALUES
    ('PROOF_OF_WORK_VIEW', 'View proof of work records, media and timelines'),
    ('PROOF_OF_WORK_CREATE', 'Upload and record proof of work photos, documents and notes'),
    ('PROOF_OF_WORK_VERIFY', 'Verify and sign off proof of work submissions'),
    ('PROOF_OF_WORK_DELETE', 'Authorize deletion or archiving of proof of work items');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE (p.code = 'PROOF_OF_WORK_VIEW' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'PROJECT_MANAGER', 'TECHNICIAN', 'SUPPORT_AGENT'))
   OR (p.code = 'PROOF_OF_WORK_CREATE' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'PROJECT_MANAGER', 'TECHNICIAN'))
   OR (p.code = 'PROOF_OF_WORK_VERIFY' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'PROJECT_MANAGER', 'DIRECTOR'))
   OR (p.code = 'PROOF_OF_WORK_DELETE' AND r.code IN ('SUPER_ADMIN', 'ADMIN'));
