import {
  Building2,
  HardHat,
  Truck,
  ChartNoAxesColumn,
  CalendarCheck,
  FileSpreadsheet,
  FolderKanban,
  FolderTree,
  Layers,
  LayoutDashboard,
  ListChecks,
  ScrollText,
  ShoppingBag,
  Target,
  TrendingUp,
  Users,
  Workflow,
  Wrench,
  type LucideIcon,
} from 'lucide-react'

export type NavItem = {
  label: string
  to: string
  icon: LucideIcon
  /** If set, the entry is only shown to users holding this permission (e.g. 'CUSTOMER_VIEW'). */
  permission?: string
}

export type NavSection = {
  /** Heading above the group; omitted for the first, untitled group. */
  title?: string
  items: NavItem[]
}

// Each feature module registers its entries here. A section with no visible items is not shown.
export const navigation: NavSection[] = [
  {
    items: [
      { label: 'Overview', to: '/', icon: LayoutDashboard },
      { label: 'Activities & Tasks', to: '/activities', icon: CalendarCheck, permission: 'ACTIVITY_VIEW' },
      { label: 'Leads', to: '/leads', icon: Target, permission: 'LEAD_VIEW' },
      { label: 'Opportunities', to: '/opportunities', icon: TrendingUp, permission: 'OPPORTUNITY_VIEW' },
      { label: 'Quotations', to: '/quotations', icon: FileSpreadsheet, permission: 'QUOTATION_VIEW' },
      { label: 'Orders', to: '/orders', icon: ShoppingBag, permission: 'ORDER_VIEW' },
      { label: 'Projects', to: '/projects', icon: FolderKanban, permission: 'PROJECT_VIEW' },
      { label: 'Project analytics', to: '/project-analytics', icon: ChartNoAxesColumn, permission: 'PROJECT_VIEW' },
      { label: 'My jobs', to: '/my-jobs', icon: HardHat, permission: 'FIELD_JOB_WORK' },
      { label: 'Field jobs', to: '/field-jobs', icon: Truck, permission: 'FIELD_JOB_VIEW' },
      { label: 'Customers', to: '/customers', icon: Users, permission: 'CUSTOMER_VIEW' },
    ],
  },
  {
    title: 'Configuration',
    items: [
      { label: 'Services', to: '/admin/services', icon: Wrench, permission: 'CATALOG_VIEW' },
      { label: 'Categories', to: '/admin/categories', icon: FolderTree, permission: 'CATALOG_VIEW' },
      { label: 'Verticals', to: '/admin/verticals', icon: Layers, permission: 'CATALOG_VIEW' },
      { label: 'Branches', to: '/admin/branches', icon: Building2, permission: 'BRANCH_VIEW' },
      { label: 'Project templates', to: '/admin/project-templates', icon: Workflow, permission: 'PROJECT_VIEW' },
      {
        label: 'Qualification',
        to: '/admin/qualification-questions',
        icon: ListChecks,
        permission: 'LEAD_CONFIGURE',
      },
    ],
  },
  {
    title: 'Administration',
    items: [{ label: 'Audit log', to: '/admin/audit', icon: ScrollText, permission: 'AUDIT_VIEW' }],
  },
]
