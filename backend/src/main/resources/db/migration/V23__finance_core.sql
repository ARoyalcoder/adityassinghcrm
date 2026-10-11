-- Finance core: what customers are billed, what they have paid, and what they still owe.
--
--   invoices ──< invoice_items
--       │    ──< credit_notes          (reduce what is owed on the invoice)
--       │    ──< debit_notes           (add to what is owed on the invoice)
--       └────< payment_allocations >── payments ──< refunds
--   payment_methods                    (configurable: how money arrives and is returned)
--   finance_document_sequences         (gapless numbering per organization, document and fiscal year)
--   finance_idempotency_keys           (a request repeated with the same key takes effect once)
--
-- Nothing here is ever changed destructively. A draft invoice is the only thing that can be edited
-- or discarded, because it is not yet a financial record. Everything else is corrected by a new
-- record: a credit or debit note, a released allocation (a second, negative row), a reversed
-- payment, a refund. The triggers at the end of this file make the database itself refuse anything
-- else, whatever the application does.
--
-- organization_id and customer_id refer to other modules by id only. Money is numeric(14,2).

-- ------------------------------------------------------------------ configuration

CREATE TABLE payment_methods (
    id                 uuid         NOT NULL DEFAULT gen_random_uuid(),
    organization_id    uuid         NOT NULL,
    -- Stable identifier for integrations: CASH, UPI, BANK_TRANSFER.
    code               varchar(30)  NOT NULL,
    name               varchar(100) NOT NULL,
    -- A cheque number, a UTR, a transaction id: must be given with every payment by this method.
    requires_reference boolean      NOT NULL DEFAULT false,
    active             boolean      NOT NULL DEFAULT true,
    display_order      integer      NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         varchar(100) NOT NULL,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         varchar(100) NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,
    deleted_at         timestamptz,
    CONSTRAINT pk_payment_methods PRIMARY KEY (id),
    CONSTRAINT ck_payment_methods_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$')
);

CREATE UNIQUE INDEX uq_payment_methods_code ON payment_methods (organization_id, code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_payment_methods_name ON payment_methods (organization_id, lower(name)) WHERE deleted_at IS NULL;

-- One counter per organization, kind of document and fiscal year. It is advanced inside the
-- transaction that creates the document, so numbers are consecutive: a failed attempt rolls the
-- counter back with everything else and leaves no gap.
CREATE TABLE finance_document_sequences (
    organization_id uuid        NOT NULL,
    document_type   varchar(20) NOT NULL,
    -- The calendar year the fiscal year starts in: 2026 for April 2026 to March 2027.
    fiscal_year     integer     NOT NULL,
    last_number     integer     NOT NULL,
    CONSTRAINT pk_finance_document_sequences PRIMARY KEY (organization_id, document_type, fiscal_year),
    CONSTRAINT ck_finance_document_sequences_type CHECK (document_type IN (
        'INVOICE', 'PAYMENT', 'CREDIT_NOTE', 'DEBIT_NOTE', 'REFUND')),
    CONSTRAINT ck_finance_document_sequences_number CHECK (last_number > 0)
);

-- ------------------------------------------------------------------ invoices

CREATE TABLE invoices (
    id                  uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id     uuid          NOT NULL,
    -- Given when the invoice is issued: INV-2627-000001. A draft has no number.
    invoice_number      varchar(20),
    -- What has been settled. OVERDUE is not stored: it is this status plus a due date that has
    -- passed, worked out when the invoice is read, so it is never out of date.
    status              varchar(20)   NOT NULL DEFAULT 'DRAFT',
    customer_id         uuid          NOT NULL,
    -- The customer as billed, copied onto the invoice: an issued invoice must keep saying what it
    -- said even if the customer record changes later.
    customer_name       varchar(200)  NOT NULL,
    billing_address     varchar(1000),
    customer_tax_id     varchar(30),
    -- The customer's purchase order or other reference.
    reference           varchar(100),
    issue_date          date,
    payment_terms_days  integer       NOT NULL DEFAULT 0,
    due_date            date,
    currency            varchar(3)    NOT NULL DEFAULT 'INR',
    subtotal            numeric(14,2) NOT NULL DEFAULT 0,
    discount_total      numeric(14,2) NOT NULL DEFAULT 0,
    tax_total           numeric(14,2) NOT NULL DEFAULT 0,
    total               numeric(14,2) NOT NULL DEFAULT 0,
    -- Running totals of the records below, kept in step with them inside one transaction while the
    -- invoice row is locked. Outstanding = total + debit_total - credit_total - amount_paid.
    amount_paid         numeric(14,2) NOT NULL DEFAULT 0,
    credit_total        numeric(14,2) NOT NULL DEFAULT 0,
    debit_total         numeric(14,2) NOT NULL DEFAULT 0,
    notes               text,
    terms               text,
    issued_at           timestamptz,
    issued_by_name      varchar(200),
    cancelled_at        timestamptz,
    cancellation_reason varchar(500),
    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          varchar(100)  NOT NULL,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(100)  NOT NULL,
    version             bigint        NOT NULL DEFAULT 0,
    deleted_at          timestamptz,
    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_invoices_terms CHECK (payment_terms_days BETWEEN 0 AND 365),
    CONSTRAINT ck_invoices_amounts CHECK (
        subtotal >= 0 AND discount_total >= 0 AND tax_total >= 0 AND total >= 0
        AND amount_paid >= 0 AND credit_total >= 0 AND debit_total >= 0),
    -- Never paid or credited beyond what is owed.
    CONSTRAINT ck_invoices_not_oversettled CHECK (amount_paid + credit_total <= total + debit_total),
    CONSTRAINT ck_invoices_issued CHECK (
        (status = 'DRAFT' AND invoice_number IS NULL)
        OR (status <> 'DRAFT' AND invoice_number IS NOT NULL AND issue_date IS NOT NULL
            AND due_date IS NOT NULL AND due_date >= issue_date AND issued_at IS NOT NULL)),
    CONSTRAINT ck_invoices_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    -- Only a draft can be discarded.
    CONSTRAINT ck_invoices_discarded CHECK (deleted_at IS NULL OR status = 'DRAFT')
);

CREATE UNIQUE INDEX uq_invoices_number ON invoices (organization_id, invoice_number) WHERE invoice_number IS NOT NULL;
CREATE INDEX ix_invoices_org_status ON invoices (organization_id, status, due_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_invoices_customer ON invoices (organization_id, customer_id, created_at DESC) WHERE deleted_at IS NULL;
-- The customer timeline: a customer's issued invoices, newest first.
CREATE INDEX ix_invoices_customer_issued ON invoices (customer_id, issued_at DESC) WHERE issued_at IS NOT NULL;

CREATE TABLE invoice_items (
    id               uuid          NOT NULL DEFAULT gen_random_uuid(),
    invoice_id       uuid          NOT NULL,
    position         integer       NOT NULL,
    -- The catalog service billed, if the line was picked from the catalog.
    service_id       uuid,
    description      varchar(500)  NOT NULL,
    hsn_sac          varchar(10),
    quantity         numeric(12,3) NOT NULL,
    unit             varchar(20),
    unit_price       numeric(14,2) NOT NULL,
    discount_percent numeric(5,2)  NOT NULL DEFAULT 0,
    tax_percent      numeric(5,2)  NOT NULL DEFAULT 0,
    -- Worked out from the four values above and stored, so the invoice adds up the same way forever.
    line_subtotal    numeric(14,2) NOT NULL,
    discount_amount  numeric(14,2) NOT NULL,
    tax_amount       numeric(14,2) NOT NULL,
    line_total       numeric(14,2) NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_invoice_items PRIMARY KEY (id),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT ck_invoice_items_values CHECK (
        quantity > 0 AND unit_price >= 0
        AND discount_percent BETWEEN 0 AND 100 AND tax_percent BETWEEN 0 AND 100)
);

CREATE INDEX ix_invoice_items_invoice ON invoice_items (invoice_id, position);

-- ------------------------------------------------------------------ payments

CREATE TABLE payments (
    id                 uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id    uuid          NOT NULL,
    -- The receipt number: RCT-2627-000001.
    payment_number     varchar(20)   NOT NULL,
    status             varchar(20)   NOT NULL DEFAULT 'RECEIVED',
    customer_id        uuid          NOT NULL,
    customer_name      varchar(200)  NOT NULL,
    received_on        date          NOT NULL,
    amount             numeric(14,2) NOT NULL,
    currency           varchar(3)    NOT NULL DEFAULT 'INR',
    method_id          uuid          NOT NULL,
    method_name        varchar(100)  NOT NULL,
    -- Cheque number, UTR, transaction id.
    reference          varchar(100),
    notes              varchar(1000),
    -- Running totals of this payment's allocations (net of releases) and refunds.
    -- Unallocated = amount - allocated_amount - refunded_amount.
    allocated_amount   numeric(14,2) NOT NULL DEFAULT 0,
    refunded_amount    numeric(14,2) NOT NULL DEFAULT 0,
    -- MANUAL: typed in by someone. GATEWAY: reported by a payment gateway.
    source             varchar(20)   NOT NULL DEFAULT 'MANUAL',
    gateway            varchar(50),
    -- The gateway's own id for the payment. Unique per gateway, so the same payment reported twice
    -- (a retried webhook) cannot be recorded twice.
    gateway_payment_id varchar(200),
    recorded_by_name   varchar(200)  NOT NULL,
    reversed_at        timestamptz,
    reversed_by_name   varchar(200),
    reversal_reason    varchar(500),
    created_at         timestamptz   NOT NULL DEFAULT now(),
    created_by         varchar(100)  NOT NULL,
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    updated_by         varchar(100)  NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_number UNIQUE (organization_id, payment_number),
    CONSTRAINT fk_payments_method FOREIGN KEY (method_id) REFERENCES payment_methods (id),
    CONSTRAINT ck_payments_status CHECK (status IN ('RECEIVED', 'REVERSED')),
    CONSTRAINT ck_payments_source CHECK (source IN ('MANUAL', 'GATEWAY')),
    CONSTRAINT ck_payments_gateway CHECK (
        (source = 'GATEWAY') = (gateway IS NOT NULL AND gateway_payment_id IS NOT NULL)),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    -- Never allocated or refunded beyond what was received.
    CONSTRAINT ck_payments_not_overspent CHECK (
        allocated_amount >= 0 AND refunded_amount >= 0 AND allocated_amount + refunded_amount <= amount),
    CONSTRAINT ck_payments_reversed CHECK (
        (status = 'REVERSED') = (reversed_at IS NOT NULL)
        AND (status <> 'REVERSED' OR (allocated_amount = 0 AND refunded_amount = 0)))
);

CREATE UNIQUE INDEX uq_payments_gateway ON payments (organization_id, gateway, gateway_payment_id)
    WHERE gateway IS NOT NULL;
CREATE INDEX ix_payments_org_received ON payments (organization_id, received_on DESC);
CREATE INDEX ix_payments_customer ON payments (organization_id, customer_id, created_at DESC);
CREATE INDEX ix_payments_customer_created ON payments (customer_id, created_at DESC);
CREATE INDEX ix_payments_method ON payments (method_id);

-- How much of a payment settles which invoice. Rows are only ever added. Releasing an allocation
-- adds a second row with the amount negated that points at the first; the net of the two is zero.
CREATE TABLE payment_allocations (
    id                uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id   uuid          NOT NULL,
    payment_id        uuid          NOT NULL,
    invoice_id        uuid          NOT NULL,
    amount            numeric(14,2) NOT NULL,
    reverses_id       uuid,
    reason            varchar(500),
    allocated_by_name varchar(200)  NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_payment_allocations PRIMARY KEY (id),
    CONSTRAINT fk_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_payment_allocations_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_payment_allocations_reverses FOREIGN KEY (reverses_id) REFERENCES payment_allocations (id),
    CONSTRAINT ck_payment_allocations_amount CHECK (amount <> 0 AND (amount < 0) = (reverses_id IS NOT NULL))
);

-- An allocation can be released once.
CREATE UNIQUE INDEX uq_payment_allocations_reverses ON payment_allocations (reverses_id) WHERE reverses_id IS NOT NULL;
CREATE INDEX ix_payment_allocations_payment ON payment_allocations (payment_id, created_at);
CREATE INDEX ix_payment_allocations_invoice ON payment_allocations (invoice_id, created_at);

CREATE TABLE refunds (
    id               uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id  uuid          NOT NULL,
    refund_number    varchar(20)   NOT NULL,
    -- Money goes back out of the payment it came in with, from the part not allocated to an invoice.
    payment_id       uuid          NOT NULL,
    customer_id      uuid          NOT NULL,
    refund_date      date          NOT NULL,
    amount           numeric(14,2) NOT NULL,
    method_id        uuid          NOT NULL,
    method_name      varchar(100)  NOT NULL,
    reference        varchar(100),
    reason           varchar(500)  NOT NULL,
    refunded_by_name varchar(200)  NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT uq_refunds_number UNIQUE (organization_id, refund_number),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_method FOREIGN KEY (method_id) REFERENCES payment_methods (id),
    CONSTRAINT ck_refunds_amount CHECK (amount > 0)
);

CREATE INDEX ix_refunds_payment ON refunds (payment_id, created_at);
CREATE INDEX ix_refunds_method ON refunds (method_id);

-- ------------------------------------------------------------------ credit and debit notes

CREATE TABLE credit_notes (
    id              uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid          NOT NULL,
    note_number     varchar(20)   NOT NULL,
    invoice_id      uuid          NOT NULL,
    customer_id     uuid          NOT NULL,
    note_date       date          NOT NULL,
    amount          numeric(14,2) NOT NULL,
    reason          varchar(500)  NOT NULL,
    issued_by_name  varchar(200)  NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_credit_notes PRIMARY KEY (id),
    CONSTRAINT uq_credit_notes_number UNIQUE (organization_id, note_number),
    CONSTRAINT fk_credit_notes_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT ck_credit_notes_amount CHECK (amount > 0)
);

CREATE INDEX ix_credit_notes_invoice ON credit_notes (invoice_id, created_at);

CREATE TABLE debit_notes (
    id              uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid          NOT NULL,
    note_number     varchar(20)   NOT NULL,
    invoice_id      uuid          NOT NULL,
    customer_id     uuid          NOT NULL,
    note_date       date          NOT NULL,
    amount          numeric(14,2) NOT NULL,
    reason          varchar(500)  NOT NULL,
    issued_by_name  varchar(200)  NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_debit_notes PRIMARY KEY (id),
    CONSTRAINT uq_debit_notes_number UNIQUE (organization_id, note_number),
    CONSTRAINT fk_debit_notes_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT ck_debit_notes_amount CHECK (amount > 0)
);

CREATE INDEX ix_debit_notes_invoice ON debit_notes (invoice_id, created_at);

-- ------------------------------------------------------------------ idempotency

-- One row per request that must take effect only once. The row is written in the same transaction
-- as the work it stands for, so there is never a key without its result or a result without its key.
-- A second request with the same key waits for the first to finish, then gets the first one's result.
--
-- scope says what kind of request the key belongs to: 'payments.record' for a person recording a
-- payment (the key comes from the Idempotency-Key header), 'gateway:<name>' for a payment reported
-- by a gateway (the key is the gateway's payment or event id).
CREATE TABLE finance_idempotency_keys (
    organization_id uuid         NOT NULL,
    scope           varchar(60)  NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    -- SHA-256 of what was asked for, to tell a retry from a different request reusing the key.
    request_hash    char(64)     NOT NULL,
    -- What the request produced; filled in before its transaction commits.
    resource_type   varchar(30),
    resource_id     uuid,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_finance_idempotency_keys PRIMARY KEY (organization_id, scope, idempotency_key)
);

CREATE INDEX ix_finance_idempotency_keys_created ON finance_idempotency_keys (created_at);

-- ------------------------------------------------------------------ nothing is changed destructively

-- Allocations, refunds and notes: rows are added and then left alone.
CREATE FUNCTION finance_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% of % refused: financial records are never changed or deleted. Record a correcting entry instead.',
        TG_OP, TG_TABLE_NAME USING ERRCODE = '23001';
END
$$;

CREATE TRIGGER payment_allocations_append_only BEFORE UPDATE OR DELETE ON payment_allocations
    FOR EACH ROW EXECUTE FUNCTION finance_append_only();
CREATE TRIGGER refunds_append_only BEFORE UPDATE OR DELETE ON refunds
    FOR EACH ROW EXECUTE FUNCTION finance_append_only();
CREATE TRIGGER credit_notes_append_only BEFORE UPDATE OR DELETE ON credit_notes
    FOR EACH ROW EXECUTE FUNCTION finance_append_only();
CREATE TRIGGER debit_notes_append_only BEFORE UPDATE OR DELETE ON debit_notes
    FOR EACH ROW EXECUTE FUNCTION finance_append_only();

-- Invoices: never deleted; once issued, only the running totals and the status may move.
CREATE FUNCTION invoices_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Invoices are never deleted. Discard a draft or cancel an issued invoice.' USING ERRCODE = '23001';
    END IF;
    IF OLD.status <> 'DRAFT' THEN
        IF NEW.status = 'DRAFT' THEN
            RAISE EXCEPTION 'An issued invoice cannot go back to being a draft.' USING ERRCODE = '23001';
        END IF;
        IF OLD.status = 'CANCELLED' THEN
            RAISE EXCEPTION 'A cancelled invoice cannot be changed.' USING ERRCODE = '23001';
        END IF;
        IF ROW(NEW.organization_id, NEW.invoice_number, NEW.customer_id, NEW.customer_name, NEW.billing_address,
               NEW.customer_tax_id, NEW.reference, NEW.issue_date, NEW.payment_terms_days, NEW.due_date, NEW.currency,
               NEW.subtotal, NEW.discount_total, NEW.tax_total, NEW.total, NEW.notes, NEW.terms, NEW.issued_at,
               NEW.issued_by_name)
           IS DISTINCT FROM
           ROW(OLD.organization_id, OLD.invoice_number, OLD.customer_id, OLD.customer_name, OLD.billing_address,
               OLD.customer_tax_id, OLD.reference, OLD.issue_date, OLD.payment_terms_days, OLD.due_date, OLD.currency,
               OLD.subtotal, OLD.discount_total, OLD.tax_total, OLD.total, OLD.notes, OLD.terms, OLD.issued_at,
               OLD.issued_by_name) THEN
            RAISE EXCEPTION 'An issued invoice cannot be changed. Use a credit note or a debit note.' USING ERRCODE = '23001';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER invoices_guard BEFORE UPDATE OR DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION invoices_guard();

-- Invoice lines: free while the invoice is a draft, frozen with it once it is issued.
CREATE FUNCTION invoice_items_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_id     uuid;
    parent_status varchar;
BEGIN
    IF TG_OP = 'DELETE' THEN
        parent_id := OLD.invoice_id;
    ELSE
        parent_id := NEW.invoice_id;
        IF TG_OP = 'UPDATE' AND NEW.invoice_id <> OLD.invoice_id THEN
            RAISE EXCEPTION 'An invoice line cannot be moved to another invoice.' USING ERRCODE = '23001';
        END IF;
    END IF;
    SELECT status INTO parent_status FROM invoices WHERE id = parent_id;
    IF parent_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'The lines of an issued invoice cannot be changed. Use a credit note or a debit note.' USING ERRCODE = '23001';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER invoice_items_guard BEFORE INSERT OR UPDATE OR DELETE ON invoice_items
    FOR EACH ROW EXECUTE FUNCTION invoice_items_guard();

-- Payments: never deleted; only the running totals may move, and the status once, to REVERSED.
CREATE FUNCTION payments_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payments are never deleted. Reverse the payment instead.' USING ERRCODE = '23001';
    END IF;
    IF OLD.status = 'REVERSED' THEN
        RAISE EXCEPTION 'A reversed payment cannot be changed.' USING ERRCODE = '23001';
    END IF;
    IF ROW(NEW.organization_id, NEW.payment_number, NEW.customer_id, NEW.customer_name, NEW.received_on, NEW.amount,
           NEW.currency, NEW.method_id, NEW.method_name, NEW.reference, NEW.notes, NEW.source, NEW.gateway,
           NEW.gateway_payment_id, NEW.recorded_by_name)
       IS DISTINCT FROM
       ROW(OLD.organization_id, OLD.payment_number, OLD.customer_id, OLD.customer_name, OLD.received_on, OLD.amount,
           OLD.currency, OLD.method_id, OLD.method_name, OLD.reference, OLD.notes, OLD.source, OLD.gateway,
           OLD.gateway_payment_id, OLD.recorded_by_name) THEN
        RAISE EXCEPTION 'A recorded payment cannot be changed. Reverse it and record it again.' USING ERRCODE = '23001';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER payments_guard BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION payments_guard();

-- ------------------------------------------------------------------ permissions
--
-- FINANCE_VIEW and FINANCE_APPROVE exist since V6. FINANCE_APPROVE now has something to approve:
-- everything that takes money back off the books (credit and debit notes, refunds, cancelling an
-- invoice, releasing an allocation, reversing a payment).

INSERT INTO permissions (code, name) VALUES
    ('FINANCE_MANAGE', 'Create and issue invoices, record and allocate payments'),
    ('FINANCE_CONFIGURE', 'Define the payment methods');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE p.code IN ('FINANCE_MANAGE', 'FINANCE_CONFIGURE') AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'FINANCE');
