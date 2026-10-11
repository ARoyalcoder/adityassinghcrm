-- Customer management.
--
--   customers ──< customer_contacts
--       │    ──< customer_addresses
--       │    ──< customer_notes
--       └──< customer_tag_assignments >── customer_tags
--
-- Customers belong to an organization. organization_id, assigned_user_id and the actor columns refer
-- to the identity module by id only (no foreign key across modules).
--
-- The *_key columns hold a normalised form of the value next to them (digits of a phone number, a
-- company name without punctuation or "Pvt Ltd"). They exist for duplicate detection and are
-- maintained by the application.

CREATE SEQUENCE customer_number_seq START WITH 1001;

CREATE TABLE customers (
    id               uuid         NOT NULL DEFAULT gen_random_uuid(),
    organization_id  uuid         NOT NULL,
    -- The number people quote on the phone and on documents: CUS-001001.
    customer_number  varchar(20)  NOT NULL,
    type             varchar(20)  NOT NULL,
    -- Company name for a business, full name for an individual. Stored so lists can search and sort on it.
    display_name     varchar(200) NOT NULL,
    first_name       varchar(100),
    last_name        varchar(100),
    company_name     varchar(200),
    company_name_key varchar(200),
    tax_id           varchar(30),
    email            varchar(254),
    phone            varchar(30),
    phone_key        varchar(20),
    status           varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    source           varchar(30),
    -- The employee who looks after this customer.
    assigned_user_id uuid,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       varchar(100) NOT NULL,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       varchar(100) NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,
    deleted_at       timestamptz,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uq_customers_number UNIQUE (organization_id, customer_number),
    CONSTRAINT ck_customers_type CHECK (type IN ('INDIVIDUAL', 'BUSINESS')),
    CONSTRAINT ck_customers_status CHECK (status IN ('PROSPECT', 'ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT ck_customers_source CHECK (source IN (
        'WALK_IN', 'REFERRAL', 'WEBSITE', 'PHONE_CALL', 'SOCIAL_MEDIA', 'ADVERTISEMENT', 'PARTNER', 'OTHER')),
    -- An individual has a person's name; a business has a company name.
    CONSTRAINT ck_customers_name CHECK (
        (type = 'INDIVIDUAL' AND first_name IS NOT NULL) OR (type = 'BUSINESS' AND company_name IS NOT NULL)),
    CONSTRAINT ck_customers_email_lowercase CHECK (email = lower(email))
);

-- List screen: always within one organization, live rows only.
CREATE INDEX ix_customers_org_name ON customers (organization_id, display_name) WHERE deleted_at IS NULL;
CREATE INDEX ix_customers_org_status ON customers (organization_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_customers_org_assigned ON customers (organization_id, assigned_user_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_customers_org_created ON customers (organization_id, created_at DESC) WHERE deleted_at IS NULL;
-- Duplicate detection.
CREATE INDEX ix_customers_org_phone_key ON customers (organization_id, phone_key) WHERE deleted_at IS NULL;
CREATE INDEX ix_customers_org_email ON customers (organization_id, email) WHERE deleted_at IS NULL;
CREATE INDEX ix_customers_org_company_key ON customers (organization_id, company_name_key) WHERE deleted_at IS NULL;

-- People at the customer. A business usually has several; an individual may have family members.
CREATE TABLE customer_contacts (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    customer_id     uuid         NOT NULL,
    -- Copied from the customer so duplicate detection can search contacts with one index.
    organization_id uuid         NOT NULL,
    name            varchar(200) NOT NULL,
    designation     varchar(100),
    email           varchar(254),
    phone           varchar(30),
    phone_key       varchar(20),
    primary_contact boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(100) NOT NULL,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(100) NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_contacts PRIMARY KEY (id),
    CONSTRAINT fk_customer_contacts_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT ck_customer_contacts_email_lowercase CHECK (email = lower(email))
);

CREATE INDEX ix_customer_contacts_customer ON customer_contacts (customer_id);
CREATE UNIQUE INDEX uq_customer_contacts_primary ON customer_contacts (customer_id) WHERE primary_contact;
CREATE INDEX ix_customer_contacts_org_phone_key ON customer_contacts (organization_id, phone_key);
CREATE INDEX ix_customer_contacts_org_email ON customer_contacts (organization_id, email);

-- Where to bill and where to do the work. A customer can have several of each; one per type is the default.
CREATE TABLE customer_addresses (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    customer_id     uuid         NOT NULL,
    type            varchar(20)  NOT NULL,
    -- What the customer calls the place: "Head office", "Farmhouse", "Warehouse 2".
    label           varchar(100),
    line1           varchar(200) NOT NULL,
    line2           varchar(200),
    city            varchar(100) NOT NULL,
    state           varchar(100) NOT NULL,
    postal_code     varchar(20),
    country_code    varchar(2)   NOT NULL DEFAULT 'IN',
    default_address boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(100) NOT NULL,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(100) NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_addresses PRIMARY KEY (id),
    CONSTRAINT fk_customer_addresses_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT ck_customer_addresses_type CHECK (type IN ('BILLING', 'SERVICE', 'OTHER'))
);

CREATE INDEX ix_customer_addresses_customer ON customer_addresses (customer_id);
CREATE UNIQUE INDEX uq_customer_addresses_default ON customer_addresses (customer_id, type) WHERE default_address;

-- Free-form labels an organization invents for itself: "VIP", "Builder", "AMC due".
CREATE TABLE customer_tags (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid         NOT NULL,
    name            varchar(50)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(100) NOT NULL,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(100) NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_tags PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_customer_tags_org_name ON customer_tags (organization_id, lower(name));

CREATE TABLE customer_tag_assignments (
    customer_id uuid NOT NULL,
    tag_id      uuid NOT NULL,
    CONSTRAINT pk_customer_tag_assignments PRIMARY KEY (customer_id, tag_id),
    CONSTRAINT fk_customer_tag_assignments_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT fk_customer_tag_assignments_tag FOREIGN KEY (tag_id) REFERENCES customer_tags (id) ON DELETE CASCADE
);

CREATE INDEX ix_customer_tag_assignments_tag ON customer_tag_assignments (tag_id);

-- Notes staff write about a customer. A note is not edited after it is written; it can be removed.
CREATE TABLE customer_notes (
    id          uuid          NOT NULL DEFAULT gen_random_uuid(),
    customer_id uuid          NOT NULL,
    body        varchar(4000) NOT NULL,
    -- The author's name when the note was written, so it still reads correctly if they are renamed or leave.
    author_name varchar(200)  NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    created_by  varchar(100)  NOT NULL,
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    updated_by  varchar(100)  NOT NULL,
    version     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_notes PRIMARY KEY (id),
    CONSTRAINT fk_customer_notes_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
);

CREATE INDEX ix_customer_notes_customer_time ON customer_notes (customer_id, created_at DESC);

-- Taking customer data out of the system is a separate right from viewing it.
INSERT INTO permissions (code, name) VALUES ('CUSTOMER_EXPORT', 'Export the customer list');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE p.code = 'CUSTOMER_EXPORT' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER');
