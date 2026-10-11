-- V16: Orders, Order Items, Order Status, and Order History

CREATE TABLE orders (
    id                          uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id             uuid          NOT NULL,
    order_number                varchar(50)   NOT NULL,
    title                       varchar(250)  NOT NULL,
    customer_id                 uuid          NOT NULL,
    customer_name               varchar(255)  NOT NULL,
    quotation_id                uuid,
    quotation_number            varchar(50),
    quotation_version_number    integer,
    status                      varchar(30)   NOT NULL DEFAULT 'CONFIRMED',

    -- Financial summary preserved from quotation or calculated
    subtotal                    numeric(14,2) NOT NULL DEFAULT 0.00,
    discount_amount             numeric(14,2) NOT NULL DEFAULT 0.00,
    tax_amount                  numeric(14,2) NOT NULL DEFAULT 0.00,
    final_amount                numeric(14,2) NOT NULL DEFAULT 0.00,

    owner_id                    uuid          NOT NULL,
    owner_name                  varchar(255)  NOT NULL,
    delivery_date               date,
    notes                       text,
    terms                       text,
    cancellation_reason         varchar(1000),
    cancelled_at                timestamptz,
    cancelled_by                varchar(100),
    completed_at                timestamptz,
    completed_by                varchar(100),

    created_at                  timestamptz   NOT NULL DEFAULT now(),
    created_by                  varchar(100)  NOT NULL,
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_by                  varchar(100)  NOT NULL,
    version                     bigint        NOT NULL DEFAULT 0,
    deleted_at                  timestamptz,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_org_number UNIQUE (organization_id, order_number),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'CONFIRMED', 'IN_PROGRESS', 'PARTIALLY_DELIVERED', 'COMPLETED', 'CANCELLED'
    ))
);

CREATE INDEX ix_orders_org_status ON orders (organization_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_orders_customer ON orders (organization_id, customer_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_orders_quotation ON orders (organization_id, quotation_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_orders_delivery_date ON orders (organization_id, delivery_date) WHERE deleted_at IS NULL;

CREATE TABLE order_items (
    id                          uuid          NOT NULL DEFAULT gen_random_uuid(),
    order_id                    uuid          NOT NULL,
    item_order                  integer       NOT NULL DEFAULT 1,
    service_id                  uuid,
    service_name                varchar(255)  NOT NULL,
    description                 varchar(1000),
    quantity                    numeric(12,2) NOT NULL DEFAULT 1.00,
    unit_price                  numeric(14,2) NOT NULL DEFAULT 0.00,
    discount_percentage         numeric(5,2)  DEFAULT 0.00,
    discount_amount             numeric(14,2) NOT NULL DEFAULT 0.00,
    tax_percentage              numeric(5,2)  DEFAULT 18.00,
    tax_amount                  numeric(14,2) NOT NULL DEFAULT 0.00,
    subtotal                    numeric(14,2) NOT NULL DEFAULT 0.00,
    total_amount                numeric(14,2) NOT NULL DEFAULT 0.00,
    delivered_quantity          numeric(12,2) NOT NULL DEFAULT 0.00,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    created_by                  varchar(100)  NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX ix_order_items_order ON order_items (order_id, item_order);

CREATE TABLE order_history (
    id                          uuid          NOT NULL DEFAULT gen_random_uuid(),
    order_id                    uuid          NOT NULL,
    action                      varchar(50)   NOT NULL,
    from_status                 varchar(30),
    to_status                   varchar(30),
    notes                       varchar(1000),
    performed_by                varchar(100)  NOT NULL,
    performed_at                timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_order_history PRIMARY KEY (id),
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX ix_order_history_order ON order_history (order_id, performed_at DESC);

-- Permissions
INSERT INTO permissions (code, name) VALUES
    ('ORDER_VIEW', 'View orders and order dashboard'),
    ('ORDER_CREATE', 'Create orders directly or convert from accepted quotations'),
    ('ORDER_UPDATE', 'Update order details, fulfillment status, and progress'),
    ('ORDER_CANCEL', 'Cancel active orders');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE (p.code = 'ORDER_VIEW' 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'PROJECT_MANAGER', 'FINANCE'))
   OR (p.code = 'ORDER_CREATE' 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER', 'SALES_EXECUTIVE'))
   OR (p.code = 'ORDER_UPDATE' 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'PROJECT_MANAGER'))
   OR (p.code = 'ORDER_CANCEL' 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER'));
