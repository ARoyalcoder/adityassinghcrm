-- Service-specific lead fields.
--
-- What to ask when someone enquires about a service differs per service (a solar enquiry needs the
-- roof area, a CCTV enquiry the camera count). Those questions are data, defined by administrators
-- per service; no code or screen names any of them.

CREATE TABLE service_custom_fields (
    id                 uuid          NOT NULL DEFAULT gen_random_uuid(),
    service_id         uuid          NOT NULL,
    -- Identifies the field in the API and in stored answers. Never changes once created.
    field_key          varchar(60)   NOT NULL,
    label              varchar(120)  NOT NULL,
    -- Never changes once created: stored answers depend on it.
    field_type         varchar(20)   NOT NULL,
    help_text          varchar(300),
    required           boolean       NOT NULL DEFAULT false,
    -- SELECT and MULTI_SELECT only: [{"value": "INDOOR", "label": "Indoor"}, ...]
    options            jsonb,
    -- NUMBER and CURRENCY only.
    min_value          numeric(18, 4),
    max_value          numeric(18, 4),
    -- TEXT and TEXTAREA only.
    max_length         integer,
    -- NUMBER only: what the number counts, shown next to the input ("sq ft", "kW").
    unit               varchar(30),
    -- FILE only: comma-separated lower-case extensions ("pdf,jpg"); NULL means any.
    allowed_file_types varchar(200),
    max_file_size_mb   integer,
    display_order      integer       NOT NULL DEFAULT 0,
    active             boolean       NOT NULL DEFAULT true,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    created_by         varchar(100)  NOT NULL,
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    updated_by         varchar(100)  NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    deleted_at         timestamptz,
    CONSTRAINT pk_service_custom_fields PRIMARY KEY (id),
    CONSTRAINT fk_service_custom_fields_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT ck_service_custom_fields_key_format CHECK (field_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_service_custom_fields_type CHECK (field_type IN
        ('TEXT', 'NUMBER', 'CURRENCY', 'SELECT', 'MULTI_SELECT', 'BOOLEAN', 'DATE', 'TEXTAREA', 'FILE')),
    CONSTRAINT ck_service_custom_fields_options CHECK (
        (field_type IN ('SELECT', 'MULTI_SELECT')) = (options IS NOT NULL)),
    CONSTRAINT ck_service_custom_fields_range CHECK (
        min_value IS NULL OR max_value IS NULL OR min_value <= max_value),
    CONSTRAINT ck_service_custom_fields_max_length CHECK (max_length IS NULL OR max_length > 0),
    CONSTRAINT ck_service_custom_fields_file_size CHECK (max_file_size_mb IS NULL OR max_file_size_mb > 0)
);

-- A key is unique within its service; a removed field's key can be used again.
CREATE UNIQUE INDEX uq_service_custom_fields_service_key
    ON service_custom_fields (service_id, field_key) WHERE deleted_at IS NULL;
CREATE INDEX ix_service_custom_fields_service
    ON service_custom_fields (service_id, display_order) WHERE deleted_at IS NULL;

-- The answers given on one lead. One row per answered field; an unanswered field has no row.
--
-- lead_id has no foreign key: leads belong to another module (and that module is not built yet),
-- and modules refer to each other's records by id only.
CREATE TABLE lead_custom_field_values (
    id            uuid          NOT NULL DEFAULT gen_random_uuid(),
    lead_id       uuid          NOT NULL,
    field_id      uuid          NOT NULL,
    -- Exactly one of these holds the answer, chosen by the field's type:
    value_text    text,             -- TEXT, TEXTAREA, SELECT (the option's value)
    value_number  numeric(18, 4),   -- NUMBER, CURRENCY (INR)
    value_boolean boolean,          -- BOOLEAN
    value_date    date,             -- DATE
    value_json    jsonb,            -- MULTI_SELECT (array of option values), FILE (reference to the stored object)
    created_at    timestamptz   NOT NULL DEFAULT now(),
    created_by    varchar(100)  NOT NULL,
    updated_at    timestamptz   NOT NULL DEFAULT now(),
    updated_by    varchar(100)  NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_lead_custom_field_values PRIMARY KEY (id),
    CONSTRAINT fk_lead_custom_field_values_field FOREIGN KEY (field_id) REFERENCES service_custom_fields (id),
    CONSTRAINT uq_lead_custom_field_values_lead_field UNIQUE (lead_id, field_id),
    CONSTRAINT ck_lead_custom_field_values_one_value CHECK (
        num_nonnulls(value_text, value_number, value_boolean, value_date, value_json) = 1)
);

-- Reporting on one field across leads ("leads with roof area over 1000 sq ft").
CREATE INDEX ix_lead_custom_field_values_field ON lead_custom_field_values (field_id);
