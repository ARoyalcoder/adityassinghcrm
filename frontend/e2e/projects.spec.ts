import { expect, test } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, pageOf } from './support'

const PROJECT_PERMISSIONS = [
  'PROJECT_VIEW',
  'PROJECT_CREATE',
  'PROJECT_UPDATE',
  'PROJECT_DELETE',
  'CUSTOMER_VIEW',
]

const MOCK_STATUSES = [
  {
    id: 'stat-1',
    code: 'PLANNING',
    name: 'Planning',
    color: '#64748b',
    category: 'PLANNING',
    displayOrder: 1,
    isDefault: true,
    isCompleted: false,
    isCancelled: false,
  },
  {
    id: 'stat-2',
    code: 'IN_PROGRESS',
    name: 'In Progress',
    color: '#3b82f6',
    category: 'IN_PROGRESS',
    displayOrder: 2,
    isDefault: false,
    isCompleted: false,
    isCancelled: false,
  },
  {
    id: 'stat-3',
    code: 'COMPLETED',
    name: 'Completed',
    color: '#10b981',
    category: 'DONE',
    displayOrder: 3,
    isDefault: false,
    isCompleted: true,
    isCancelled: false,
  },
]

const MOCK_PROJECT = {
  id: 'prj-101',
  projectNumber: 'PRJ-2026-0001',
  title: 'Rooftop Solar Plant 25kW',
  description: 'Solar EPC setup with Net Metering',
  customerId: 'cust-101',
  customerName: 'Astra Tech Solutions',
  orderId: 'ord-101',
  orderNumber: 'ORD-2026-0001',
  serviceId: 'srv-101',
  serviceName: 'Solar Commercial Grid Tie',
  projectManagerId: 'user-pm-1',
  projectManagerName: 'Vikram PM',
  startDate: '2026-10-15',
  expectedCompletionDate: '2026-11-30',
  actualCompletionDate: null,
  status: 'IN_PROGRESS',
  priority: 'HIGH',
  progressPercentage: 45,
  budget: 1250000,
  revenue: 1550000,
  cost: 950000,
  notes: 'Net metering application in review with DISCOM',
  createdAt: '2026-10-10T09:00:00Z',
  createdBy: 'admin',
  updatedAt: '2026-10-10T09:00:00Z',
  updatedBy: 'admin',
  members: [
    {
      id: 'mem-1',
      userId: 'user-pm-1',
      name: 'Vikram PM',
      email: 'vikram@example.com',
      role: 'PROJECT_MANAGER',
      addedAt: '2026-10-10T09:00:00Z',
    },
  ],
  milestones: [
    {
      id: 'ms-1',
      title: 'Site Engineering Sign-off',
      description: 'Shadow analysis & structure design',
      dueDate: '2026-10-25',
      completedDate: '2026-10-24',
      status: 'COMPLETED',
      displayOrder: 1,
      progressPercentage: 100,
      taskCount: 3,
      completedTaskCount: 3,
    },
    {
      id: 'ms-2',
      title: 'Module Procurement & Delivery',
      description: 'Bifacial Tier-1 panels delivery',
      dueDate: '2026-11-10',
      completedDate: null,
      status: 'PENDING',
      displayOrder: 2,
      progressPercentage: 30,
      taskCount: 4,
      completedTaskCount: 1,
    },
  ],
  tasks: [
    {
      id: 'tsk-1',
      milestoneId: 'ms-1',
      milestoneTitle: 'Site Engineering Sign-off',
      title: 'Civil structural roof load audit',
      description: 'Verify dead and live wind load',
      assignedToId: 'user-pm-1',
      assignedToName: 'Vikram PM',
      status: 'COMPLETED',
      priority: 'HIGH',
      startDate: '2026-10-16',
      dueDate: '2026-10-20',
      completedAt: '2026-10-19T10:00:00Z',
      estimatedHours: 12,
      actualHours: 10,
      displayOrder: 1,
      checklists: [
        {
          id: 'chk-1',
          itemText: 'Check RCC slab depth',
          completed: true,
          displayOrder: 1,
        },
      ],
      dependencies: [],
    },
  ],
  deliverables: [
    {
      id: 'del-1',
      milestoneId: 'ms-1',
      milestoneTitle: 'Site Engineering Sign-off',
      title: 'SLD & 3D Shadow Simulation',
      description: 'Helioscope yield simulation report',
      dueDate: '2026-10-24',
      deliveredDate: '2026-10-24',
      status: 'ACCEPTED',
      acceptanceCriteria: 'Signed off by Chief Electrical Inspector',
      fileUrl: 'https://cdn.example.com/sld.pdf',
      reviewedBy: 'Vikram PM',
      reviewedAt: '2026-10-24T15:00:00Z',
    },
  ],
  health: {
    overallScore: 82,
    scheduleHealth: 74,
    budgetHealth: 91,
    taskHealth: 88,
    clientApprovalHealth: 62,
    teamCapacityHealth: 93,
    profitability: {
      revenue: 1550000,
      materialCost: 500000,
      labourCost: 200000,
      vendorCost: 150000,
      travelCost: 50000,
      otherExpenses: 50000,
      actualCost: 950000,
      expectedCost: 1250000,
      grossProfit: 600000,
      grossMargin: 38.71,
      projectedFinalCost: 1250000,
    },
    riskAlerts: [
      {
        code: 'APPROVAL_PENDING',
        severity: 'INFO',
        title: 'Client Approval Pending',
        description: '1 deliverables submitted and awaiting client sign-off.',
      },
    ],
  },
}

const MOCK_DASHBOARD = {
  totalProjects: 12,
  totalBudget: 8500000,
  totalRevenue: 11200000,
  activeProjects: 8,
  completedProjects: 3,
  delayedProjects: 1,
  recentProjects: [MOCK_PROJECT],
}

const MOCK_KANBAN = {
  columns: [
    {
      status: MOCK_STATUSES[0],
      projects: [],
      totalBudget: 0,
      totalRevenue: 0,
    },
    {
      status: MOCK_STATUSES[1],
      projects: [MOCK_PROJECT],
      totalBudget: 1250000,
      totalRevenue: 1550000,
    },
    {
      status: MOCK_STATUSES[2],
      projects: [],
      totalBudget: 0,
      totalRevenue: 0,
    },
  ],
}

test.describe('Project Management Blackbox E2E', () => {
  test.beforeEach(async ({ page }) => {
    await mockSystemInfo(page)
    await mockSignedIn(page, PROJECT_PERMISSIONS)

    await page.route('**/api/v1/projects/statuses', (route) =>
      route.fulfill({ json: MOCK_STATUSES }),
    )

    await page.route('**/api/v1/projects/dashboard', (route) =>
      route.fulfill({ json: MOCK_DASHBOARD }),
    )

    await page.route('**/api/v1/projects/kanban', (route) =>
      route.fulfill({ json: MOCK_KANBAN }),
    )

    await page.route('**/api/v1/projects?*', (route) =>
      route.fulfill({ json: pageOf([MOCK_PROJECT]) }),
    )

    await page.route('**/api/v1/projects/prj-101', (route) =>
      route.fulfill({ json: MOCK_PROJECT }),
    )
  })

  test('displays project dashboard KPIs and projects list view', async ({ page }) => {
    await page.goto('/projects')

    // Header & KPIs
    await expect(page.getByRole('heading', { name: 'Project Management' })).toBeVisible()
    await expect(page.getByText('Total Projects')).toBeVisible()
    await expect(page.getByText('12', { exact: true })).toBeVisible()
    await expect(page.getByText('Rooftop Solar Plant 25kW')).toBeVisible()
    await expect(page.getByText('PRJ-2026-0001')).toBeVisible()
    await expect(page.getByText('Astra Tech Solutions')).toBeVisible()
  })

  test('switches to Kanban board and displays status columns', async ({ page }) => {
    await page.goto('/projects')

    const kanbanBtn = page.getByRole('button', { name: 'Kanban' })
    await kanbanBtn.click()

    await expect(page.getByRole('heading', { name: 'In Progress' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Planning' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Completed' })).toBeVisible()
    await expect(page.getByText('Rooftop Solar Plant 25kW')).toBeVisible()
  })

  test('opens project detail page with milestones, tasks, and deliverables tabs', async ({ page }) => {
    await page.goto('/projects/prj-101')

    await expect(page.getByRole('heading', { name: 'Rooftop Solar Plant 25kW' })).toBeVisible()
    await expect(page.getByText('Customer: Astra Tech Solutions')).toBeVisible()
    await expect(page.getByText('Order: ORD-2026-0001')).toBeVisible()

    // Milestones Tab
    await expect(page.getByText('Site Engineering Sign-off')).toBeVisible()
    await expect(page.getByText('Module Procurement & Delivery')).toBeVisible()

    // Switch to Tasks Tab
    await page.getByRole('button', { name: /Tasks/i }).click()
    await expect(page.getByText('Civil structural roof load audit')).toBeVisible()

    // Switch to Deliverables Tab
    await page.getByRole('button', { name: /Deliverables/i }).click()
    await expect(page.getByText('SLD & 3D Shadow Simulation')).toBeVisible()

    // Switch to Team Tab
    await page.getByRole('button', { name: /Team/i }).click()
    await expect(page.getByRole('heading', { name: 'Vikram PM' })).toBeVisible()
  })

  test('displays project health intelligence, component scores, risk alerts, and profitability breakdown', async ({ page }) => {
    await page.goto('/projects/prj-101')

    // Health Score in Ribbon
    await expect(page.getByText('Health Score')).toBeVisible()
    await expect(page.getByText('82%').first()).toBeVisible()

    // Switch to Health & Profitability Tab
    await page.getByRole('button', { name: /Health & Profitability/i }).click()

    // Overall Score and Components
    await expect(page.getByText('Overall Project Health')).toBeVisible()
    await expect(page.getByText('Schedule Health')).toBeVisible()
    await expect(page.getByText('74%')).toBeVisible()
    await expect(page.getByText('Budget Health')).toBeVisible()
    await expect(page.getByText('91%')).toBeVisible()
    await expect(page.getByText('Task Health')).toBeVisible()
    await expect(page.getByText('88%')).toBeVisible()
    await expect(page.getByText('Client Approval Health')).toBeVisible()
    await expect(page.getByText('62%')).toBeVisible()
    await expect(page.getByText('Team Capacity Health')).toBeVisible()
    await expect(page.getByText('93%')).toBeVisible()

    // Risk Alerts
    await expect(page.getByText('Active Risk Alerts')).toBeVisible()
    await expect(page.getByText('Client Approval Pending')).toBeVisible()

    // Profitability Overview & Breakdown
    await expect(page.getByText('Project Profitability Overview')).toBeVisible()
    await expect(page.getByText('Itemized Project Cost Breakdown')).toBeVisible()
    await expect(page.getByText('Material Cost')).toBeVisible()
    await expect(page.getByText('Labour Cost')).toBeVisible()
    await expect(page.getByText('Vendor / Subcontract')).toBeVisible()
    await expect(page.getByText('Travel & Logistics')).toBeVisible()
    await expect(page.getByText('Other Direct Expenses')).toBeVisible()
    await expect(page.getByText('Total Actual Cost Incurred:')).toBeVisible()
  })
})

