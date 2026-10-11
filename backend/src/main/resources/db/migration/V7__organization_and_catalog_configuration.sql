-- Organization and configuration module.

-- Branches: city and state are how the business expands (one city -> many cities and states), so
-- every branch must say where it is, and listings by location must be fast.
UPDATE branches SET city = 'Unknown' WHERE city IS NULL;
UPDATE branches SET state = 'Unknown' WHERE state IS NULL;
ALTER TABLE branches
    ALTER COLUMN city SET NOT NULL,
    ALTER COLUMN state SET NOT NULL;

CREATE INDEX ix_branches_location ON branches (organization_id, state, city) WHERE deleted_at IS NULL;

-- Verticals become editable by administrators (name, description, order, active). The set of codes
-- stays fixed by ck_service_verticals_code: nobody can add or remove a vertical.
ALTER TABLE service_verticals
    ADD COLUMN updated_by varchar(100),
    ADD COLUMN version    bigint NOT NULL DEFAULT 0;

-- Services: how a service is sold and delivered is configuration stored with the service.
-- Later modules (quotes, projects, billing) read these values instead of branching on a
-- vertical or service code.
ALTER TABLE services
    -- ONE_TIME: fixed job. RECURRING: billed every period (AMC, retainers). QUOTE_BASED: priced per enquiry.
    ADD COLUMN billing_type            varchar(20)   NOT NULL DEFAULT 'QUOTE_BASED',
    -- What one unit of the service is, as shown to staff and customers: "per camera", "per kW", "per month".
    ADD COLUMN unit_label              varchar(50),
    -- Starting price per unit in INR; NULL when the service is always quoted.
    ADD COLUMN base_price              numeric(12, 2),
    ADD COLUMN requires_site_visit     boolean       NOT NULL DEFAULT false,
    ADD COLUMN estimated_duration_days integer,
    ADD CONSTRAINT ck_services_billing_type CHECK (billing_type IN ('ONE_TIME', 'RECURRING', 'QUOTE_BASED')),
    ADD CONSTRAINT ck_services_base_price CHECK (base_price IS NULL OR base_price >= 0),
    ADD CONSTRAINT ck_services_estimated_duration CHECK (estimated_duration_days IS NULL OR estimated_duration_days > 0);

-- Filters used by the admin lists.
CREATE INDEX ix_services_active ON services (active) WHERE deleted_at IS NULL;
CREATE INDEX ix_service_categories_active ON service_categories (active) WHERE deleted_at IS NULL;
