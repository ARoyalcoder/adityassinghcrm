-- Seed: the system roles, the permission catalogue, and which role gets which permission.
--
-- Rules for later migrations:
--   * A module adds its own permissions (MODULE_ACTION) in the migration that introduces it,
--     together with the matching constant in identity.api.Permissions.
--   * Every new permission must also be granted to SUPER_ADMIN.

INSERT INTO roles (code, name, description, system_role, created_by, updated_by) VALUES
    ('SUPER_ADMIN',     'Super Admin',     'Full access to everything, including roles and other administrators', true, 'system', 'system'),
    ('ADMIN',           'Admin',           'Runs the system day to day: users, branches, catalog',                true, 'system', 'system'),
    ('DIRECTOR',        'Director',        'Sees everything; approves finance',                                   true, 'system', 'system'),
    ('SALES_MANAGER',   'Sales Manager',   'Owns customers and the sales team''s work',                           true, 'system', 'system'),
    ('SALES_EXECUTIVE', 'Sales Executive', 'Works with customers',                                                true, 'system', 'system'),
    ('PROJECT_MANAGER', 'Project Manager', 'Plans and runs projects',                                             true, 'system', 'system'),
    ('DESIGNER',        'Designer',        'Works on assigned projects',                                          true, 'system', 'system'),
    ('TECHNICIAN',      'Technician',      'Carries out on-site work for projects',                               true, 'system', 'system'),
    ('FINANCE',         'Finance',         'Views and approves finance records',                                  true, 'system', 'system'),
    ('HR',              'HR',              'Manages people records',                                              true, 'system', 'system'),
    ('SUPPORT_AGENT',   'Support Agent',   'Handles customer support',                                            true, 'system', 'system'),
    ('VENDOR',          'Vendor',          'External supplier. No access until vendor features exist',            true, 'system', 'system'),
    ('PARTNER',         'Partner',         'External partner. No access until partner features exist',            true, 'system', 'system');

INSERT INTO permissions (code, name) VALUES
    ('USER_VIEW',           'View users'),
    ('USER_CREATE',         'Create users'),
    ('USER_UPDATE',         'Edit, activate and deactivate users'),
    ('USER_DELETE',         'Delete users'),
    ('ROLE_VIEW',           'View roles and permissions'),
    ('ROLE_UPDATE',         'Change roles and assign them'),
    ('ORGANIZATION_VIEW',   'View organization details'),
    ('ORGANIZATION_UPDATE', 'Edit organization details'),
    ('BRANCH_VIEW',         'View branches'),
    ('BRANCH_CREATE',       'Create branches'),
    ('BRANCH_UPDATE',       'Edit branches'),
    ('BRANCH_DELETE',       'Delete branches'),
    ('CATALOG_VIEW',        'View the service catalog'),
    ('CATALOG_CREATE',      'Add service categories and services'),
    ('CATALOG_UPDATE',      'Edit service categories and services'),
    ('CATALOG_DELETE',      'Delete service categories and services'),
    ('CUSTOMER_VIEW',       'View customers'),
    ('CUSTOMER_CREATE',     'Create customers'),
    ('CUSTOMER_UPDATE',     'Edit customers'),
    ('CUSTOMER_DELETE',     'Delete customers'),
    ('PROJECT_VIEW',        'View projects'),
    ('PROJECT_UPDATE',      'Edit projects'),
    ('FINANCE_VIEW',        'View finance records'),
    ('FINANCE_APPROVE',     'Approve finance records');

-- SUPER_ADMIN: everything.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

-- Everyone else: explicit grants. VENDOR and PARTNER deliberately get none.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('ADMIN', 'USER_VIEW'), ('ADMIN', 'USER_CREATE'), ('ADMIN', 'USER_UPDATE'), ('ADMIN', 'USER_DELETE'),
    ('ADMIN', 'ROLE_VIEW'),
    ('ADMIN', 'ORGANIZATION_VIEW'), ('ADMIN', 'ORGANIZATION_UPDATE'),
    ('ADMIN', 'BRANCH_VIEW'), ('ADMIN', 'BRANCH_CREATE'), ('ADMIN', 'BRANCH_UPDATE'), ('ADMIN', 'BRANCH_DELETE'),
    ('ADMIN', 'CATALOG_VIEW'), ('ADMIN', 'CATALOG_CREATE'), ('ADMIN', 'CATALOG_UPDATE'), ('ADMIN', 'CATALOG_DELETE'),
    ('ADMIN', 'CUSTOMER_VIEW'), ('ADMIN', 'CUSTOMER_CREATE'), ('ADMIN', 'CUSTOMER_UPDATE'), ('ADMIN', 'CUSTOMER_DELETE'),
    ('ADMIN', 'PROJECT_VIEW'), ('ADMIN', 'PROJECT_UPDATE'),
    ('ADMIN', 'FINANCE_VIEW'),

    ('DIRECTOR', 'USER_VIEW'), ('DIRECTOR', 'ROLE_VIEW'), ('DIRECTOR', 'ORGANIZATION_VIEW'), ('DIRECTOR', 'BRANCH_VIEW'),
    ('DIRECTOR', 'CATALOG_VIEW'), ('DIRECTOR', 'CUSTOMER_VIEW'),
    ('DIRECTOR', 'PROJECT_VIEW'), ('DIRECTOR', 'PROJECT_UPDATE'),
    ('DIRECTOR', 'FINANCE_VIEW'), ('DIRECTOR', 'FINANCE_APPROVE'),

    ('SALES_MANAGER', 'CUSTOMER_VIEW'), ('SALES_MANAGER', 'CUSTOMER_CREATE'),
    ('SALES_MANAGER', 'CUSTOMER_UPDATE'), ('SALES_MANAGER', 'CUSTOMER_DELETE'),
    ('SALES_MANAGER', 'PROJECT_VIEW'), ('SALES_MANAGER', 'CATALOG_VIEW'),

    ('SALES_EXECUTIVE', 'CUSTOMER_VIEW'), ('SALES_EXECUTIVE', 'CUSTOMER_CREATE'), ('SALES_EXECUTIVE', 'CUSTOMER_UPDATE'),
    ('SALES_EXECUTIVE', 'CATALOG_VIEW'),

    ('PROJECT_MANAGER', 'PROJECT_VIEW'), ('PROJECT_MANAGER', 'PROJECT_UPDATE'),
    ('PROJECT_MANAGER', 'CUSTOMER_VIEW'), ('PROJECT_MANAGER', 'CATALOG_VIEW'),

    ('DESIGNER', 'PROJECT_VIEW'), ('DESIGNER', 'CATALOG_VIEW'),

    ('TECHNICIAN', 'PROJECT_VIEW'),

    ('FINANCE', 'FINANCE_VIEW'), ('FINANCE', 'FINANCE_APPROVE'),
    ('FINANCE', 'CUSTOMER_VIEW'), ('FINANCE', 'PROJECT_VIEW'),

    ('HR', 'USER_VIEW'), ('HR', 'USER_CREATE'), ('HR', 'USER_UPDATE'),
    ('HR', 'ORGANIZATION_VIEW'), ('HR', 'BRANCH_VIEW'),

    ('SUPPORT_AGENT', 'CUSTOMER_VIEW'), ('SUPPORT_AGENT', 'PROJECT_VIEW')
) AS grants (role_code, permission_code)
JOIN roles r ON r.code = grants.role_code
JOIN permissions p ON p.code = grants.permission_code;
