import { expect, test, type Page } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, mockVerticals } from './support'

const MANAGER = ['PROJECT_VIEW', 'PROJECT_UPDATE']

test.use({ timezoneId: 'Asia/Kolkata', locale: 'en-IN' })

// The pipeline from the brief. It is what the server returns for this organization's templates;
// the screen does not know these names.
const PIPELINE = [
  ['Received', 4], ['Presentation', 2], ['Drafting/Modeling', 6], ['Finishing/Rendering', 3], ['Test Render/Draft', 1],
  ['Delivered', 5], ['Under Revision', 2], ['Revised & Closed', 7], ['On Hold', 1], ['Dead', 1],
] as const

function analytics(overrides: Record<string, unknown> = {}) {
  return {
    asOf: '2026-10-10',
    cards: {
      activeProjects: 19, startingToday: 2, dueToday: 1, delayedProjects: 3, completedThisMonth: 4,
      revenueInProgress: 1845000, completedRevenue: 5230000, completedRevenueThisMonth: 760000,
      averageProjectDurationDays: 34.7, averageDeliveryDelayDays: 9, clientSatisfaction: 4.5, ratedProjects: 12,
    },
    pipeline: PIPELINE.map(([name, projects]) => ({ name, projects, revenue: projects * 100000 })),
    unstagedProjects: 2,
    delivery: {
      deliveredOnTime: 18, deliveredLate: 6, onTimePercentage: 75, averageDelayDays: 9,
      fastestDelivery: { id: 'p1', projectNumber: 'PRJ-0007', title: 'Lobby render', days: 6 },
      longestDelivery: { id: 'p2', projectNumber: 'PRJ-0003', title: 'Villa walkthrough', days: 92 },
      averageCompletionDays: 34.7,
    },
    productivity: {
      projectsCompleted: 24, projectsCompletedThisMonth: 4, averageHours: 46.5, averageDeliverables: 3.2, averageDrawings: null,
      employees: [
        { userId: 'u1', name: 'Asha Verma', tasksCompleted: 41, openTasks: 3, hoursLogged: 312.5, projects: 9 },
        { userId: 'u2', name: 'Ravi Kumar', tasksCompleted: 28, openTasks: 6, hoursLogged: 205, projects: 7 },
      ],
    },
    ...overrides,
  }
}

const STAGES = PIPELINE.map(([name]) => ({ key: name.toLowerCase().replace(/[^a-z0-9]+/g, '_'), name }))

function projects() {
  return [
    { id: 'a1', projectNumber: 'PRJ-0012', title: 'Showroom interiors', customerName: 'Acme', expectedCompletionDate: '2026-10-05', delayed: true, stageKey: 'drafting_modeling', stageName: 'Drafting/Modeling', stages: STAGES },
    { id: 'a2', projectNumber: 'PRJ-0015', title: 'Villa exterior', customerName: 'Globex', expectedCompletionDate: '2026-10-20', delayed: false, stageKey: null, stageName: null, stages: STAGES },
    { id: 'a3', projectNumber: 'PRJ-0016', title: 'Site survey only', customerName: 'Initech', expectedCompletionDate: '2026-11-01', delayed: false, stageKey: null, stageName: null, stages: [] },
  ]
}

async function mockAnalytics(page: Page, summary: () => unknown, list: () => unknown = projects) {
  await page.route('**/api/v1/project-analytics', (route) => route.fulfill({ json: summary() }))
  await page.route('**/api/v1/project-analytics/pipeline-projects', (route) => route.fulfill({ json: list() }))
}

function card(page: Page, name: string) {
  return page.getByRole('region', { name: 'Overview' }).getByRole('group', { name, exact: true })
}

test.beforeEach(async ({ page }) => {
  await mockSystemInfo(page)
  await mockVerticals(page)
})

test('the dashboard shows every card, the pipeline, delivery performance and productivity', async ({ page }) => {
  await mockSignedIn(page, MANAGER)
  await mockAnalytics(page, () => analytics())

  await page.goto('/')
  await page.getByRole('link', { name: 'Project analytics' }).click()

  await expect(page.getByRole('heading', { level: 1 })).toContainText(/Good (morning|afternoon|evening), Asha!/)

  // The ten cards.
  await expect(page.getByRole('region', { name: 'Overview' }).getByRole('group')).toHaveCount(10)
  await expect(card(page, 'Active Projects')).toContainText('19')
  await expect(card(page, 'Starting Today')).toContainText('2')
  await expect(card(page, 'Due Today')).toContainText('1')
  await expect(card(page, 'Delayed Projects')).toContainText('3')
  await expect(card(page, 'Delayed Projects')).toContainText('Past their expected date')
  await expect(card(page, 'Completed This Month')).toContainText('4')
  await expect(card(page, 'Revenue in Progress')).toContainText('₹18,45,000')
  await expect(card(page, 'Completed Revenue')).toContainText('₹52,30,000')
  await expect(card(page, 'Completed Revenue')).toContainText('₹7,60,000 this month')
  await expect(card(page, 'Average Project Duration')).toContainText('34.7 days')
  await expect(card(page, 'Average Delivery Delay')).toContainText('9 days')
  await expect(card(page, 'Client Satisfaction')).toContainText('4.5 / 5')
  await expect(card(page, 'Client Satisfaction')).toContainText('From 12 rated projects')

  // The pipeline: the stages the server sent, in its order, each with its count.
  const stages = page.getByRole('list', { name: 'Projects by stage' }).getByRole('listitem')
  await expect(stages).toHaveText(PIPELINE.map(([name, count]) => `${name}${count}`))
  await expect(stages.nth(2)).toHaveAttribute('title', 'Drafting/Modeling: 6 projects, ₹6,00,000')
  await expect(page.getByText('2 active projects are not in any stage yet.')).toBeVisible()

  // Delivery performance.
  await expect(page.getByRole('meter', { name: 'Delivered on time' })).toHaveAttribute('aria-valuenow', '75')
  await expect(page.getByText('75%')).toBeVisible()
  const delivery = page.getByLabel('Delivery figures')
  await expect(delivery).toContainText('Delivered On Time✓ 18')
  await expect(delivery).toContainText('Delivered Late⚠ 6')
  await expect(delivery).toContainText('Average Delay9 days')
  await expect(delivery).toContainText('Average Completion Time34.7 days')
  await expect(delivery).toContainText('Fastest Delivery6 days · Lobby render')
  await expect(delivery).toContainText('Longest Delivery92 days · Villa walkthrough')

  // Productivity.
  const productivity = page.getByLabel('Productivity figures')
  await expect(productivity).toContainText('Projects Completed24 · 4 this month')
  await expect(productivity).toContainText('Average Hours46.5 hours per project')
  await expect(productivity).toContainText('Average Deliverables3.2 per project')
  // Drawings are not recorded anywhere yet: the screen says so rather than showing a number.
  await expect(productivity).toContainText('Average DrawingsNot tracked yet')
  await expect(page.getByRole('table', { name: 'Employee productivity' }).getByRole('row')).toHaveText([
    /Employee/, /Asha Verma\s*41\s*3\s*312\.5\s*9/, /Ravi Kumar\s*28\s*6\s*205\s*7/,
  ])

  await expect(page.getByRole('note', { name: 'Today' })).toContainText('3 projects are past the expected date.')
})

test('a figure that cannot be worked out is said in words, not shown as zero', async ({ page }) => {
  await mockSignedIn(page, MANAGER)
  await mockAnalytics(
    page,
    () =>
      analytics({
        cards: {
          activeProjects: 0, startingToday: 0, dueToday: 0, delayedProjects: 0, completedThisMonth: 0, revenueInProgress: 0,
          completedRevenue: 0, completedRevenueThisMonth: 0, averageProjectDurationDays: null, averageDeliveryDelayDays: null,
          clientSatisfaction: null, ratedProjects: 0,
        },
        pipeline: [],
        unstagedProjects: 0,
        delivery: {
          deliveredOnTime: 0, deliveredLate: 0, onTimePercentage: null, averageDelayDays: null, fastestDelivery: null,
          longestDelivery: null, averageCompletionDays: null,
        },
        productivity: {
          projectsCompleted: 0, projectsCompletedThisMonth: 0, averageHours: null, averageDeliverables: null, averageDrawings: null,
          employees: [],
        },
      }),
    () => [],
  )

  await page.goto('/project-analytics')

  await expect(card(page, 'Active Projects')).toContainText('0')
  await expect(card(page, 'Delayed Projects')).toContainText('Nothing is past its date')
  await expect(card(page, 'Average Project Duration')).toContainText('Not known yet')
  await expect(card(page, 'Average Delivery Delay')).toContainText('No late deliveries')
  await expect(card(page, 'Client Satisfaction')).toContainText('No ratings yet')
  await expect(page.getByText('No project has been placed in a stage yet.')).toBeVisible()
  await expect(page.getByText('There are no active projects.')).toBeVisible()
  await expect(page.getByText('No project has been delivered yet.')).toBeVisible()
  await expect(page.getByRole('meter')).toHaveCount(0)
  await expect(page.getByLabel('Delivery figures')).toContainText('Fastest DeliveryNothing delivered yet')
  await expect(page.getByLabel('Productivity figures')).toContainText('Average HoursNo hours logged yet')
  await expect(page.getByText('No tasks have been assigned to anyone yet.')).toBeVisible()
  await expect(page.getByRole('note', { name: 'Today' })).toContainText('No projects are running at the moment.')
})

test('a project is moved along the stages of its own template', async ({ page }) => {
  await mockSignedIn(page, MANAGER)
  const list = projects()
  let moved: { url: string; body: unknown } | undefined
  await mockAnalytics(page, () => analytics(), () => list)
  await page.route('**/api/v1/projects/*/stage', (route) => {
    const body = route.request().postDataJSON() as { stageKey: string }
    moved = { url: new URL(route.request().url()).pathname, body }
    const target = STAGES.find((stage) => stage.key === body.stageKey)!
    list[1] = { ...list[1], stageKey: target.key as never, stageName: target.name as never }
    return route.fulfill({ json: { stageKey: target.key, stageName: target.name } })
  })

  await page.goto('/project-analytics')

  const rows = page.getByRole('list', { name: 'Active projects' }).getByRole('listitem')
  await expect(rows).toHaveCount(3)
  await expect(rows.nth(0)).toContainText('Showroom interiors')
  await expect(rows.nth(0)).toContainText('PRJ-0012 · Acme · due 5 Oct 2026 · Delayed')
  await expect(page.getByLabel('Stage of Showroom interiors')).toHaveValue('drafting_modeling')
  // The choices are the template's stages, as sent.
  await expect(page.getByLabel('Stage of Showroom interiors').getByRole('option')).toHaveText(PIPELINE.map(([name]) => name))
  // A project that is in no stage yet says so; one whose service has no template cannot be moved.
  await expect(page.getByLabel('Stage of Villa exterior').getByRole('option').first()).toHaveText('Not in a stage')
  await expect(rows.nth(2)).toContainText('No template for its service')
  await expect(page.getByLabel('Stage of Site survey only')).toHaveCount(0)

  await page.getByLabel('Stage of Villa exterior').selectOption('Presentation')

  await expect(page.getByLabel('Stage of Villa exterior')).toHaveValue('presentation')
  expect(moved).toEqual({ url: '/api/v1/projects/a2/stage', body: { stageKey: 'presentation' } })
  await expect(rows.nth(1).getByRole('link', { name: 'Villa exterior' })).toHaveAttribute('href', '/projects/a2')
})

test('someone who may only view projects sees the analytics but moves nothing', async ({ page }) => {
  await mockSignedIn(page, ['PROJECT_VIEW'])
  await mockAnalytics(page, () => analytics())

  await page.goto('/project-analytics')

  await expect(card(page, 'Active Projects')).toContainText('19')
  const rows = page.getByRole('list', { name: 'Active projects' }).getByRole('listitem')
  await expect(rows.nth(0)).toContainText('Drafting/Modeling')
  await expect(rows.nth(1)).toContainText('Not in a stage')
  await expect(page.getByRole('combobox')).toHaveCount(0)
})

test('project analytics are hidden from users without the permission', async ({ page }) => {
  await mockSignedIn(page, ['CUSTOMER_VIEW'])

  await page.goto('/')
  await expect(page.getByRole('link', { name: 'Project analytics' })).toHaveCount(0)
  await page.goto('/project-analytics')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})
