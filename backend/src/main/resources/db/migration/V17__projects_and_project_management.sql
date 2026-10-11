-- V17: Projects, Project Statuses, Members, Milestones, Tasks, Dependencies, Checklists, and Deliverables

-- 1. Configurable Project Statuses
CREATE TABLE project_statuses (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id         uuid          NOT NULL,
    code                    varchar(50)   NOT NULL,
    name                    varchar(100)  NOT NULL,
    display_order           integer       NOT NULL DEFAULT 1,
    color                   varchar(30)   NOT NULL DEFAULT 'blue',
    category                varchar(30)   NOT NULL DEFAULT 'IN_PROGRESS',
    is_default              boolean       NOT NULL DEFAULT false,
    is_completed            boolean       NOT NULL DEFAULT false,
    is_cancelled            boolean       NOT NULL DEFAULT false,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL,
    CONSTRAINT pk_project_statuses PRIMARY KEY (id),
    CONSTRAINT uq_project_statuses_org_code UNIQUE (organization_id, code)
);

CREATE INDEX ix_project_statuses_org_order ON project_statuses (organization_id, display_order);

-- 2. Projects
CREATE TABLE projects (
    id                          uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id             uuid          NOT NULL,
    project_number              varchar(50)   NOT NULL,
    title                       varchar(250)  NOT NULL,
    description                 text,
    customer_id                 uuid          NOT NULL,
    customer_name               varchar(255)  NOT NULL,
    order_id                    uuid,
    order_number                varchar(50),
    service_id                  uuid,
    service_name                varchar(255),
    project_manager_id          uuid          NOT NULL,
    project_manager_name        varchar(255)  NOT NULL,
    start_date                  date          NOT NULL,
    expected_completion_date    date          NOT NULL,
    actual_completion_date      date,
    status                      varchar(50)   NOT NULL DEFAULT 'PLANNING',
    priority                    varchar(30)   NOT NULL DEFAULT 'MEDIUM',
    budget                      numeric(14,2) NOT NULL DEFAULT 0.00,
    revenue                     numeric(14,2) NOT NULL DEFAULT 0.00,
    cost                        numeric(14,2) NOT NULL DEFAULT 0.00,
    progress_percentage         integer       NOT NULL DEFAULT 0,
    notes                       text,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    created_by                  varchar(100)  NOT NULL,
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_by                  varchar(100)  NOT NULL,
    version                     bigint        NOT NULL DEFAULT 0,
    deleted_at                  timestamptz,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT uq_projects_org_number UNIQUE (organization_id, project_number),
    CONSTRAINT ck_projects_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_projects_progress CHECK (progress_percentage >= 0 AND progress_percentage <= 100)
);

CREATE INDEX ix_projects_org_status ON projects (organization_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_projects_customer ON projects (organization_id, customer_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_projects_order ON projects (organization_id, order_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_projects_pm ON projects (organization_id, project_manager_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_projects_dates ON projects (organization_id, start_date, expected_completion_date) WHERE deleted_at IS NULL;

-- 3. Project Members
CREATE TABLE project_members (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    project_id              uuid          NOT NULL,
    user_id                 uuid          NOT NULL,
    user_name               varchar(255)  NOT NULL,
    user_email              varchar(255),
    role                    varchar(50)   NOT NULL DEFAULT 'CONTRIBUTOR',
    joined_at               timestamptz   NOT NULL DEFAULT now(),
    assigned_by             varchar(100)  NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_project_members_project_user UNIQUE (project_id, user_id)
);

CREATE INDEX ix_project_members_project ON project_members (project_id);
CREATE INDEX ix_project_members_user ON project_members (user_id);

-- 4. Milestones
CREATE TABLE project_milestones (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    project_id              uuid          NOT NULL,
    title                   varchar(250)  NOT NULL,
    description             text,
    due_date                date          NOT NULL,
    completed_date          date,
    status                  varchar(30)   NOT NULL DEFAULT 'PENDING',
    display_order           integer       NOT NULL DEFAULT 1,
    progress_percentage     integer       NOT NULL DEFAULT 0,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL,
    CONSTRAINT pk_project_milestones PRIMARY KEY (id),
    CONSTRAINT fk_project_milestones_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_milestones_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'DELAYED'))
);

CREATE INDEX ix_project_milestones_project ON project_milestones (project_id, display_order);

-- 5. Tasks
CREATE TABLE project_tasks (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    project_id              uuid          NOT NULL,
    milestone_id            uuid,
    title                   varchar(250)  NOT NULL,
    description             text,
    assigned_to_id          uuid,
    assigned_to_name        varchar(255),
    status                  varchar(30)   NOT NULL DEFAULT 'TODO',
    priority                varchar(30)   NOT NULL DEFAULT 'MEDIUM',
    start_date              date,
    due_date                date,
    completed_at            timestamptz,
    estimated_hours         numeric(8,2)  DEFAULT 0.00,
    actual_hours            numeric(8,2)  DEFAULT 0.00,
    display_order           integer       NOT NULL DEFAULT 1,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL,
    CONSTRAINT pk_project_tasks PRIMARY KEY (id),
    CONSTRAINT fk_project_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_tasks_milestone FOREIGN KEY (milestone_id) REFERENCES project_milestones (id) ON DELETE SET NULL,
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE INDEX ix_project_tasks_project ON project_tasks (project_id, display_order);
CREATE INDEX ix_project_tasks_milestone ON project_tasks (milestone_id);
CREATE INDEX ix_project_tasks_assigned ON project_tasks (assigned_to_id);

-- 6. Task Dependencies
CREATE TABLE project_task_dependencies (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    task_id                 uuid          NOT NULL,
    depends_on_task_id      uuid          NOT NULL,
    dependency_type         varchar(30)   NOT NULL DEFAULT 'FINISH_TO_START',
    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    CONSTRAINT pk_task_dependencies PRIMARY KEY (id),
    CONSTRAINT fk_task_dependencies_task FOREIGN KEY (task_id) REFERENCES project_tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_dependencies_dep FOREIGN KEY (depends_on_task_id) REFERENCES project_tasks (id) ON DELETE CASCADE,
    CONSTRAINT uq_task_dependencies UNIQUE (task_id, depends_on_task_id)
);

CREATE INDEX ix_task_dependencies_task ON project_task_dependencies (task_id);
CREATE INDEX ix_task_dependencies_dep ON project_task_dependencies (depends_on_task_id);

-- 7. Task Checklists
CREATE TABLE project_checklist_items (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    task_id                 uuid          NOT NULL,
    item_text               varchar(500)  NOT NULL,
    completed               boolean       NOT NULL DEFAULT false,
    completed_at            timestamptz,
    completed_by            varchar(100),
    display_order           integer       NOT NULL DEFAULT 1,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    CONSTRAINT pk_checklist_items PRIMARY KEY (id),
    CONSTRAINT fk_checklist_items_task FOREIGN KEY (task_id) REFERENCES project_tasks (id) ON DELETE CASCADE
);

CREATE INDEX ix_checklist_items_task ON project_checklist_items (task_id, display_order);

-- 8. Deliverables
CREATE TABLE project_deliverables (
    id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
    project_id              uuid          NOT NULL,
    milestone_id            uuid,
    title                   varchar(250)  NOT NULL,
    description             text,
    status                  varchar(30)   NOT NULL DEFAULT 'DRAFT',
    due_date                date,
    delivered_date          date,
    acceptance_criteria     text,
    file_url                varchar(500),
    reviewed_by             varchar(100),
    reviewed_at             timestamptz,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL,
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL,
    CONSTRAINT pk_project_deliverables PRIMARY KEY (id),
    CONSTRAINT fk_project_deliverables_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_deliverables_milestone FOREIGN KEY (milestone_id) REFERENCES project_milestones (id) ON DELETE SET NULL,
    CONSTRAINT ck_deliverables_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX ix_project_deliverables_project ON project_deliverables (project_id);

-- 9. Seed default statuses for all existing organizations
INSERT INTO project_statuses (organization_id, code, name, display_order, color, category, is_default, is_completed, is_cancelled, created_by, updated_by)
SELECT o.id, s.code, s.name, s.display_order, s.color, s.category, s.is_default, s.is_completed, s.is_cancelled, 'system', 'system'
FROM organizations o
CROSS JOIN (
    VALUES
        ('PLANNING', 'Planning', 1, 'blue', 'NOT_STARTED', true, false, false),
        ('IN_PROGRESS', 'In Progress', 2, 'amber', 'IN_PROGRESS', false, false, false),
        ('ON_HOLD', 'On Hold', 3, 'orange', 'ON_HOLD', false, false, false),
        ('IN_REVIEW', 'In Review', 4, 'purple', 'IN_PROGRESS', false, false, false),
        ('COMPLETED', 'Completed', 5, 'emerald', 'COMPLETED', false, true, false),
        ('CANCELLED', 'Cancelled', 6, 'rose', 'CANCELLED', false, false, true)
) AS s(code, name, display_order, color, category, is_default, is_completed, is_cancelled)
ON CONFLICT (organization_id, code) DO NOTHING;

-- 10. Permissions
INSERT INTO permissions (code, name) VALUES
    ('PROJECT_CREATE', 'Create projects, milestones, tasks, and deliverables'),
    ('PROJECT_DELETE', 'Cancel or delete projects')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE p.code IN ('PROJECT_CREATE', 'PROJECT_DELETE')
  AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'PROJECT_MANAGER')
ON CONFLICT DO NOTHING;
