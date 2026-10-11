-- V15: Quotations, Items, Versioning, Approval Workflow, and Follow-ups

CREATE TABLE quotations (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id         uuid          NOT NULL,
    quotation_number        varchar(50)   NOT NULL,
    title                   varchar(250)  NOT NULL,
    customer_id             uuid,
    customer_name           varchar(255),
    lead_id                 uuid,
    lead_name               varchar(255),
    opportunity_id          uuid,
    opportunity_title       varchar(255),
    status                  varchar(30)   NOT NULL DEFAULT 'DRAFT',
    current_version_number  integer       NOT NULL DEFAULT 1,
    current_version_id      uuid,
    valid_until             date,
    owner_id                uuid          NOT NULL,
    owner_name              varchar(255)  NOT NULL,

    -- Financial summary from current version
    subtotal                numeric(14,2) NOT NULL DEFAULT 0.00,
    discount_amount         numeric(14,2) NOT NULL DEFAULT 0.00,
    tax_amount              numeric(14,2) NOT NULL DEFAULT 0.00,
    final_amount            numeric(14,2) NOT NULL DEFAULT 0.00,

    -- Approval tracking
    approval_required       boolean       NOT NULL DEFAULT false,
    approval_requested_at   timestamptz,
    approval_requested_by   varchar(100),
    approved_at             timestamptz,
    approved_by             varchar(100),
    rejected_at             timestamptz,
    rejected_by             varchar(100),
    rejection_reason        varchar(1000),

    -- Dispatch & Lifecycle tracking
    sent_at                 timestamptz,
    sent_by                 varchar(100),
    viewed_at               timestamptz,
    accepted_at             timestamptz,
    accepted_by             varchar(100),
    client_rejection_reason varchar(1000),
    cancelled_at            timestamptz,
    cancellation_reason     varchar(1000),

    -- Follow-up tracking
    next_follow_up_date     date,

    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL,
    version                 bigint        NOT NULL DEFAULT 0,
    deleted_at              timestamptz,
    CONSTRAINT pk_quotations PRIMARY KEY (id),
    CONSTRAINT uq_quotations_org_number UNIQUE (organization_id, quotation_number),
    CONSTRAINT ck_quotations_status CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SENT', 'VIEWED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED'
    ))
);

CREATE INDEX ix_quotations_org_status ON quotations (organization_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_quotations_customer ON quotations (organization_id, customer_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_quotations_lead ON quotations (organization_id, lead_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_quotations_opportunity ON quotations (organization_id, opportunity_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_quotations_follow_up ON quotations (organization_id, next_follow_up_date) WHERE deleted_at IS NULL;

CREATE TABLE quotation_versions (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    quotation_id            uuid          NOT NULL,
    version_number          integer       NOT NULL,
    subtotal                numeric(14,2) NOT NULL DEFAULT 0.00,
    discount_percentage     numeric(5,2)  DEFAULT 0.00,
    discount_amount         numeric(14,2) NOT NULL DEFAULT 0.00,
    tax_percentage          numeric(5,2)  DEFAULT 18.00,
    tax_amount              numeric(14,2) NOT NULL DEFAULT 0.00,
    final_amount            numeric(14,2) NOT NULL DEFAULT 0.00,
    terms                   text,
    valid_until             date,
    notes                   text,
    pdf_storage_key         varchar(500),
    pdf_generated_at        timestamptz,
    change_summary          varchar(500),
    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    CONSTRAINT pk_quotation_versions PRIMARY KEY (id),
    CONSTRAINT fk_quotation_versions_quotation FOREIGN KEY (quotation_id) REFERENCES quotations (id) ON DELETE CASCADE,
    CONSTRAINT uq_quotation_versions_quotation_num UNIQUE (quotation_id, version_number)
);

CREATE INDEX ix_quotation_versions_quotation ON quotation_versions (quotation_id, version_number DESC);

CREATE TABLE quotation_items (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    version_id              uuid          NOT NULL,
    item_order              integer       NOT NULL DEFAULT 1,
    service_id              uuid,
    service_name            varchar(255)  NOT NULL,
    description             varchar(1000),
    quantity                numeric(12,2) NOT NULL DEFAULT 1.00,
    unit_price              numeric(14,2) NOT NULL DEFAULT 0.00,
    discount_percentage     numeric(5,2)  DEFAULT 0.00,
    discount_amount         numeric(14,2) NOT NULL DEFAULT 0.00,
    tax_percentage          numeric(5,2)  DEFAULT 18.00,
    tax_amount              numeric(14,2) NOT NULL DEFAULT 0.00,
    subtotal                numeric(14,2) NOT NULL DEFAULT 0.00,
    total_amount            numeric(14,2) NOT NULL DEFAULT 0.00,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    CONSTRAINT pk_quotation_items PRIMARY KEY (id),
    CONSTRAINT fk_quotation_items_version FOREIGN KEY (version_id) REFERENCES quotation_versions (id) ON DELETE CASCADE
);

CREATE INDEX ix_quotation_items_version_order ON quotation_items (version_id, item_order);

CREATE TABLE quotation_audit_logs (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    quotation_id            uuid          NOT NULL,
    action                  varchar(50)   NOT NULL,
    from_status             varchar(30),
    to_status               varchar(30),
    version_number          integer,
    notes                   varchar(1000),
    performed_by            varchar(100)  NOT NULL,
    performed_at            timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_quotation_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_quotation_audit_quotation FOREIGN KEY (quotation_id) REFERENCES quotations (id) ON DELETE CASCADE
);

CREATE INDEX ix_quotation_audit_quotation ON quotation_audit_logs (quotation_id, performed_at DESC);

-- Permissions
INSERT INTO permissions (code, name) VALUES
    ('QUOTATION_VIEW', 'View quotations and quotations dashboard'),
    ('QUOTATION_CREATE', 'Create quotations and new quotation versions'),
    ('QUOTATION_UPDATE', 'Edit draft quotations, send, and manage status'),
    ('QUOTATION_APPROVE', 'Approve or reject quotation approval requests'),
    ('QUOTATION_DELETE', 'Cancel or delete quotations');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE (p.code IN ('QUOTATION_VIEW', 'QUOTATION_CREATE', 'QUOTATION_UPDATE') 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'FINANCE'))
   OR (p.code = 'QUOTATION_APPROVE' 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER', 'FINANCE'))
   OR (p.code = 'QUOTATION_DELETE' 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER'));
