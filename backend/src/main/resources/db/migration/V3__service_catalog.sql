-- Service catalog: vertical --< category --< service.

-- The verticals are a closed set of exactly six, fixed by the CHECK constraint below and mirrored by
-- the ServiceVerticalCode enum. Changing the set is a product decision that needs a new migration.
-- Rows are reference data: deactivated with `active`, never deleted.
CREATE TABLE service_verticals (
    id            uuid         NOT NULL DEFAULT gen_random_uuid(),
    code          varchar(40)  NOT NULL,
    name          varchar(100) NOT NULL,
    description   varchar(500),
    display_order integer      NOT NULL,
    active        boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_service_verticals PRIMARY KEY (id),
    CONSTRAINT uq_service_verticals_code UNIQUE (code),
    CONSTRAINT uq_service_verticals_name UNIQUE (name),
    CONSTRAINT uq_service_verticals_display_order UNIQUE (display_order),
    CONSTRAINT ck_service_verticals_code CHECK (code IN (
        'CCTV_SECURITY',
        'DIGITAL_MARKETING',
        'INTERIOR_DESIGN',
        'ARCHITECTURE_TECH',
        'SOLAR',
        'IT_SUPPORT'
    ))
);

CREATE TABLE service_categories (
    id            uuid         NOT NULL DEFAULT gen_random_uuid(),
    vertical_id   uuid         NOT NULL,
    code          varchar(60)  NOT NULL,
    name          varchar(150) NOT NULL,
    description   varchar(500),
    display_order integer      NOT NULL DEFAULT 0,
    active        boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    varchar(100) NOT NULL,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    varchar(100) NOT NULL,
    version       bigint       NOT NULL DEFAULT 0,
    deleted_at    timestamptz,
    CONSTRAINT pk_service_categories PRIMARY KEY (id),
    CONSTRAINT fk_service_categories_vertical FOREIGN KEY (vertical_id) REFERENCES service_verticals (id),
    CONSTRAINT ck_service_categories_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

-- Codes and names are unique within a vertical. Both indexes lead with vertical_id, so they also
-- serve "categories of a vertical" lookups.
CREATE UNIQUE INDEX uq_service_categories_vertical_code ON service_categories (vertical_id, code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_service_categories_vertical_name ON service_categories (vertical_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE services (
    id            uuid         NOT NULL DEFAULT gen_random_uuid(),
    category_id   uuid         NOT NULL,
    code          varchar(60)  NOT NULL,
    name          varchar(200) NOT NULL,
    description   varchar(1000),
    display_order integer      NOT NULL DEFAULT 0,
    active        boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    varchar(100) NOT NULL,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    varchar(100) NOT NULL,
    version       bigint       NOT NULL DEFAULT 0,
    deleted_at    timestamptz,
    CONSTRAINT pk_services PRIMARY KEY (id),
    CONSTRAINT fk_services_category FOREIGN KEY (category_id) REFERENCES service_categories (id),
    CONSTRAINT ck_services_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

-- A service code identifies the service everywhere (quotes, orders, reports), so it is unique
-- across the whole catalog, not just within its category.
CREATE UNIQUE INDEX uq_services_code ON services (code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_services_category_name ON services (category_id, lower(name)) WHERE deleted_at IS NULL;
