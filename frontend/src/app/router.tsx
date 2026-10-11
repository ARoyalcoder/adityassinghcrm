import type { ReactNode } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { AuditLogDetailPage } from '@/features/audit/pages/AuditLogDetailPage'
import { AuditLogPage } from '@/features/audit/pages/AuditLogPage'
import { RequireAuth } from '@/features/auth/components/RequireAuth'
import { RequirePermission } from '@/features/auth/components/RequirePermission'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { CategoriesPage } from '@/features/catalog/pages/CategoriesPage'
import { CategoryFormPage } from '@/features/catalog/pages/CategoryFormPage'
import { ServiceFieldsPage } from '@/features/catalog/pages/ServiceFieldsPage'
import { ServiceFormPage } from '@/features/catalog/pages/ServiceFormPage'
import { ServicesPage } from '@/features/catalog/pages/ServicesPage'
import { VerticalFormPage } from '@/features/catalog/pages/VerticalFormPage'
import { VerticalsPage } from '@/features/catalog/pages/VerticalsPage'
import { BranchesPage } from '@/features/organization/pages/BranchesPage'
import { BranchFormPage } from '@/features/organization/pages/BranchFormPage'
import { RequireAnyPermission } from '@/features/auth/components/RequireAnyPermission'
import { FieldJobFormPage } from '@/features/field-jobs/pages/FieldJobFormPage'
import { FieldJobPage } from '@/features/field-jobs/pages/FieldJobPage'
import { FieldJobsPage } from '@/features/field-jobs/pages/FieldJobsPage'
import { MyJobsPage } from '@/features/field-jobs/pages/MyJobsPage'
import { ProjectAnalyticsPage } from '@/features/project-analytics/pages/ProjectAnalyticsPage'
import { ProjectTemplateFormPage } from '@/features/project-templates/pages/ProjectTemplateFormPage'
import { ProjectTemplatePage } from '@/features/project-templates/pages/ProjectTemplatePage'
import { ProjectTemplatesPage } from '@/features/project-templates/pages/ProjectTemplatesPage'
import { LeadDetailPage } from '@/features/leads/pages/LeadDetailPage'
import { LeadFormPage } from '@/features/leads/pages/LeadFormPage'
import { LeadsPage } from '@/features/leads/pages/LeadsPage'
import { QualificationQuestionsPage } from '@/features/leads/pages/QualificationQuestionsPage'
import { ActivitiesPage } from '@/features/activities/pages/ActivitiesPage'
import { OpportunitiesPage } from '@/features/opportunities/pages/OpportunitiesPage'
import { QuotationsPage } from '@/features/quotations/pages/QuotationsPage'
import { QuotationDetailPage } from '@/features/quotations/pages/QuotationDetailPage'
import { OrdersPage } from '@/features/orders/pages/OrdersPage'
import { OrderDetailPage } from '@/features/orders/pages/OrderDetailPage'
import { ProjectsPage } from '@/features/projects/pages/ProjectsPage'
import { ProjectDetailPage } from '@/features/projects/pages/ProjectDetailPage'
import { CustomerFormPage } from '@/features/customers/pages/CustomerFormPage'
import { CustomerProfilePage } from '@/features/customers/pages/CustomerProfilePage'
import { CustomersPage } from '@/features/customers/pages/CustomersPage'
import { OverviewPage } from '@/features/system/pages/OverviewPage'
import { NotFoundPage } from '@/pages/NotFoundPage'

import { FinTrackDashboardPage } from '@/features/fintrack/pages/FinTrackDashboardPage'

const needs = (permission: string, page: ReactNode) => (
  <RequirePermission permission={permission}>{page}</RequirePermission>
)

const needsAny = (permissions: string[], page: ReactNode) => (
  <RequireAnyPermission permissions={permissions}>{page}</RequireAnyPermission>
)

// Everything except /login is behind RequireAuth, and each admin page names the permission it needs.
// These guards only decide what to show; the API enforces the same rules on every request.
export const router = createBrowserRouter([
  { path: '/fintrack', element: <FinTrackDashboardPage /> },
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <AppShell />,
        children: [
          { index: true, element: <OverviewPage /> },

          { path: 'activities', element: needs('ACTIVITY_VIEW', <ActivitiesPage />) },

          { path: 'customers', element: needs('CUSTOMER_VIEW', <CustomersPage />) },
          { path: 'customers/new', element: needs('CUSTOMER_CREATE', <CustomerFormPage />) },
          { path: 'customers/:id', element: needs('CUSTOMER_VIEW', <CustomerProfilePage />) },
          { path: 'customers/:id/edit', element: needs('CUSTOMER_UPDATE', <CustomerFormPage />) },

          { path: 'leads', element: needs('LEAD_VIEW', <LeadsPage />) },
          { path: 'leads/new', element: needs('LEAD_CREATE', <LeadFormPage />) },
          { path: 'leads/:id', element: needs('LEAD_VIEW', <LeadDetailPage />) },
          { path: 'leads/:id/edit', element: needs('LEAD_UPDATE', <LeadFormPage />) },

          { path: 'opportunities', element: needs('OPPORTUNITY_VIEW', <OpportunitiesPage />) },

          { path: 'quotations', element: needs('QUOTATION_VIEW', <QuotationsPage />) },
          { path: 'quotations/:id', element: needs('QUOTATION_VIEW', <QuotationDetailPage />) },

          { path: 'orders', element: needs('ORDER_VIEW', <OrdersPage />) },
          { path: 'orders/:id', element: needs('ORDER_VIEW', <OrderDetailPage />) },

          { path: 'projects', element: needs('PROJECT_VIEW', <ProjectsPage />) },
          { path: 'projects/:id', element: needs('PROJECT_VIEW', <ProjectDetailPage />) },

          { path: 'admin/qualification-questions', element: needs('LEAD_CONFIGURE', <QualificationQuestionsPage />) },

          { path: 'my-jobs', element: needs('FIELD_JOB_WORK', <MyJobsPage />) },
          { path: 'field-jobs', element: needsAny(['FIELD_JOB_VIEW', 'FIELD_JOB_MANAGE'], <FieldJobsPage />) },
          { path: 'field-jobs/new', element: needs('FIELD_JOB_MANAGE', <FieldJobFormPage />) },
          { path: 'field-jobs/:id', element: needsAny(['FIELD_JOB_WORK', 'FIELD_JOB_VIEW', 'FIELD_JOB_MANAGE'], <FieldJobPage />) },
          { path: 'field-jobs/:id/edit', element: needs('FIELD_JOB_MANAGE', <FieldJobFormPage />) },
          { path: 'project-analytics', element: needs('PROJECT_VIEW', <ProjectAnalyticsPage />) },
          { path: 'admin/project-templates', element: needs('PROJECT_VIEW', <ProjectTemplatesPage />) },
          { path: 'admin/project-templates/new', element: needs('PROJECT_CONFIGURE', <ProjectTemplateFormPage />) },
          { path: 'admin/project-templates/:id', element: needs('PROJECT_VIEW', <ProjectTemplatePage />) },
          { path: 'admin/project-templates/:id/edit', element: needs('PROJECT_CONFIGURE', <ProjectTemplateFormPage />) },

          { path: 'admin/services', element: needs('CATALOG_VIEW', <ServicesPage />) },
          { path: 'admin/services/new', element: needs('CATALOG_CREATE', <ServiceFormPage />) },
          { path: 'admin/services/:id/edit', element: needs('CATALOG_UPDATE', <ServiceFormPage />) },
          { path: 'admin/services/:id/fields', element: needs('CATALOG_VIEW', <ServiceFieldsPage />) },

          { path: 'admin/categories', element: needs('CATALOG_VIEW', <CategoriesPage />) },
          { path: 'admin/categories/new', element: needs('CATALOG_CREATE', <CategoryFormPage />) },
          { path: 'admin/categories/:id/edit', element: needs('CATALOG_UPDATE', <CategoryFormPage />) },

          { path: 'admin/verticals', element: needs('CATALOG_VIEW', <VerticalsPage />) },
          { path: 'admin/verticals/:code/edit', element: needs('CATALOG_UPDATE', <VerticalFormPage />) },

          { path: 'admin/branches', element: needs('BRANCH_VIEW', <BranchesPage />) },
          { path: 'admin/branches/new', element: needs('BRANCH_CREATE', <BranchFormPage />) },
          { path: 'admin/branches/:id/edit', element: needs('BRANCH_UPDATE', <BranchFormPage />) },

          { path: 'admin/audit', element: needs('AUDIT_VIEW', <AuditLogPage />) },
          { path: 'admin/audit/:id', element: needs('AUDIT_VIEW', <AuditLogDetailPage />) },

          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
