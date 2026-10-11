-- Customer 360: a log of contact with the customer (calls, meetings, emails, site visits).
--
-- This is the only table the Customer 360 adds. The customer timeline has no table of its own: it
-- is assembled on request from the records each module already keeps (these interactions, notes,
-- the audit trail, and later quotations, invoices, tickets...), so no event is ever stored twice.

CREATE TABLE customer_interactions (
    id             uuid          NOT NULL DEFAULT gen_random_uuid(),
    customer_id    uuid          NOT NULL,
    type           varchar(20)   NOT NULL,
    -- Who contacted whom. NULL when it does not apply (a meeting, a site visit).
    direction      varchar(10),
    -- When the call or meeting actually happened, which may be earlier than when it was typed in.
    occurred_at    timestamptz   NOT NULL,
    subject        varchar(200)  NOT NULL,
    details        varchar(4000),
    -- What came of it: "Will confirm by Friday", "Not interested this year".
    outcome        varchar(500),
    -- The name of whoever logged it, as it was at the time.
    logged_by_name varchar(200)  NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    created_by     varchar(100)  NOT NULL,
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     varchar(100)  NOT NULL,
    version        bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_interactions PRIMARY KEY (id),
    CONSTRAINT fk_customer_interactions_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT ck_customer_interactions_type CHECK (type IN ('CALL', 'MEETING', 'EMAIL', 'SITE_VISIT', 'MESSAGE', 'OTHER')),
    CONSTRAINT ck_customer_interactions_direction CHECK (direction IN ('INBOUND', 'OUTBOUND'))
);

-- Newest first per customer: the Activities list, the timeline, and "when were they last contacted".
CREATE INDEX ix_customer_interactions_customer_time ON customer_interactions (customer_id, occurred_at DESC);

-- The timeline reads a record's history from the audit trail by time.
CREATE INDEX ix_entity_activity_logs_org_entity_time
    ON entity_activity_logs (organization_id, entity_type, entity_id, occurred_at DESC);
