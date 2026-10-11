import { expect, test, type Page } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, mockVerticals, pageOf } from './support'

const ADMIN = ['PROJECT_VIEW', 'PROJECT_CONFIGURE', 'CATALOG_VIEW']
const INTERIOR = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
const SOLAR = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
const INTERIOR_SERVICE = { id: '11111111-0000-0000-0000-000000000001', name: 'Home interiors', verticalName: 'Interior Design' }
const SOLAR_SERVICE = { id: '11111111-0000-0000-0000-000000000002', name: 'Rooftop solar', verticalName: 'Solar' }
const ROLES = ['ADMIN', 'DESIGNER', 'PROJECT_MANAGER', 'SALES_EXECUTIVE', 'TECHNICIAN']

type Stage = {
  key: string; name: string; description: string | null; durationDays: number; responsibleRole: string | null
  dependsOn: string[]; milestones: string[]; tasks: Record<string, unknown>[]; documents: Record<string, unknown>[]
}

function stage(key: string, name: string, durationDays: number, responsibleRole: string | null, dependsOn: string[] = [], extra: Partial<Stage> = {}): Stage {
  return { key, name, description: null, durationDays, responsibleRole, dependsOn, milestones: [], tasks: [], documents: [], ...extra }
}

/** Places stages the way the server does, so the mock answers what the real endpoint would. */
function plan(stages: Stage[]) {
  const ends = new Map<string, number>()
  const end = (key: string): number => {
    if (!ends.has(key)) {
      const current = stages.find((candidate) => candidate.key === key)!
      ends.set(key, Math.max(0, ...current.dependsOn.map(end)) + current.durationDays)
    }
    return ends.get(key)!
  }
  const scheduled = stages.map((current) => ({ stage: current, startDay: end(current.key) - current.durationDays, endDay: end(current.key) }))
  return { stages: scheduled, totalDays: Math.max(0, ...scheduled.map((item) => item.endDay)) }
}

function template(id: string, service: typeof SOLAR_SERVICE, name: string, stages: Stage[], overrides: Record<string, unknown> = {}) {
  return { id, service, name, description: null, active: true, revision: 1, plan: plan(stages), version: 0, ...overrides }
}

// The examples from the brief. They exist here, as test data, and nowhere in the application.
const INTERIOR_STAGES = [
  stage('lead', 'Lead', 2, 'SALES_EXECUTIVE'),
  stage('requirement', 'Requirement', 5, 'DESIGNER', ['lead'], {
    documents: [{ name: 'Floor plan', required: true, description: 'From the customer' }, { name: 'Reference photos', required: false, description: null }],
  }),
  stage('concept', 'Concept', 7, 'DESIGNER', ['requirement']),
  stage('design_3d', '3D Design', 10, 'DESIGNER', ['concept'], {
    milestones: ['3D design approved by the customer'],
    tasks: [
      { title: 'Model the rooms', description: null, responsibleRole: null, estimatedHours: 24, checklist: ['Living room', 'Kitchen'] },
      { title: 'Render and present', description: 'Walk the customer through it', responsibleRole: 'PROJECT_MANAGER', estimatedHours: null, checklist: [] },
    ],
  }),
  stage('revision', 'Revision', 5, 'DESIGNER', ['design_3d']),
  stage('working_drawings', 'Working Drawings', 10, 'DESIGNER', ['revision']),
  stage('final_delivery', 'Final Delivery', 3, 'PROJECT_MANAGER', ['working_drawings']),
]
const SOLAR_STAGES = [
  stage('site_survey', 'Site Survey', 2, 'TECHNICIAN'),
  stage('design', 'Design', 4, 'DESIGNER', ['site_survey']),
  stage('quotation', 'Quotation', 2, 'SALES_EXECUTIVE', ['design']),
  stage('procurement', 'Procurement', 14, null, ['quotation']),
  stage('installation', 'Installation', 5, 'TECHNICIAN', ['procurement']),
  stage('testing', 'Testing', 1, 'TECHNICIAN', ['installation']),
  stage('handover', 'Handover', 1, 'PROJECT_MANAGER', ['testing']),
]

function summaryOf(detail: ReturnType<typeof template>) {
  return { id: detail.id, service: detail.service, name: detail.name, active: detail.active, stageCount: detail.plan.stages.length, totalDays: detail.plan.totalDays, version: detail.version }
}

function catalogService(service: typeof SOLAR_SERVICE) {
  return {
    id: service.id, code: service.name.toUpperCase().replace(/[^A-Z]+/g, '_'), name: service.name, description: null, displayOrder: 0,
    active: true, billingType: 'QUOTE_BASED', unitLabel: null, basePrice: null, requiresSiteVisit: false, estimatedDurationDays: null,
    category: { id: 'c1', code: 'GENERAL', name: 'General' }, vertical: { code: 'X', name: service.verticalName }, version: 0,
  }
}

async function mockEditorLookups(page: Page) {
  await page.route('**/api/v1/project-templates/options', (route) => route.fulfill({ json: { roles: ROLES } }))
  await page.route('**/api/v1/catalog/services?*', (route) =>
    route.fulfill({ json: pageOf([catalogService(INTERIOR_SERVICE), catalogService(SOLAR_SERVICE)]) }),
  )
}

test.beforeEach(async ({ page }) => {
  await mockSystemInfo(page)
  await mockVerticals(page)
})

test('each service follows the stages its own template defines', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  const interior = template(INTERIOR, INTERIOR_SERVICE, 'Interior design project', INTERIOR_STAGES)
  const solar = template(SOLAR, SOLAR_SERVICE, 'Solar project', SOLAR_STAGES, { active: false })
  await page.route('**/api/v1/project-templates', (route) => route.fulfill({ json: [summaryOf(interior), summaryOf(solar)] }))
  await page.route(`**/api/v1/project-templates/${INTERIOR}`, (route) => route.fulfill({ json: interior }))
  await page.route(`**/api/v1/project-templates/${SOLAR}`, (route) => route.fulfill({ json: solar }))

  await page.goto('/')
  await page.getByRole('link', { name: 'Project templates' }).click()

  const rows = page.getByRole('table', { name: 'Project templates' }).getByRole('row')
  await expect(rows).toHaveCount(3)
  await expect(rows.nth(1)).toContainText('Interior design project')
  await expect(rows.nth(1)).toContainText('Home interiors · Interior Design')
  await expect(rows.nth(1)).toContainText('42 days')
  await expect(rows.nth(2)).toContainText('Solar project')
  await expect(rows.nth(2)).toContainText('29 days')
  await expect(rows.nth(2)).toContainText('Inactive')

  // Interior design: Lead → Requirement → Concept → 3D Design → Revision → Working Drawings → Final Delivery.
  await page.getByRole('link', { name: 'Interior design project', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Interior design project', level: 1 })).toBeVisible()
  const timeline = page.getByRole('table', { name: 'Timeline' }).getByRole('row')
  await expect(timeline).toHaveText([
    /Stage/,
    /1\. Lead.*Day 0 to 2.*2 days.*Sales executive.*Nothing: starts with the project/,
    /2\. Requirement.*Day 2 to 7.*5 days.*Designer.*Lead/,
    /3\. Concept.*Day 7 to 14/,
    /4\. 3D Design.*Day 14 to 24.*10 days.*Designer.*Concept/,
    /5\. Revision.*Day 24 to 29/,
    /6\. Working Drawings.*Day 29 to 39/,
    /7\. Final Delivery.*Day 39 to 42.*3 days.*Project manager.*Working Drawings/,
  ])
  await expect(page.getByText('7 stages, estimated 42 days from the start of a project.')).toBeVisible()
  // What happens inside a stage.
  const tasks = page.getByRole('region', { name: 'Tasks of 3D Design' })
  await expect(tasks).toContainText('Model the rooms · 24 h')
  await expect(tasks).toContainText('Render and present · Project manager')
  await expect(page.getByRole('list', { name: 'Checklist of Model the rooms' }).getByRole('listitem')).toHaveText(['Living room', 'Kitchen'])
  await expect(page.getByRole('region', { name: 'Milestones of 3D Design' })).toContainText('3D design approved by the customer')
  const documents = page.getByRole('region', { name: 'Documents of Requirement' })
  await expect(documents.getByRole('listitem')).toHaveText([/Floor plan.*Required.*From the customer/, /Reference photos.*Optional/])
  await expect(page.getByRole('list', { name: 'Stages' }).getByRole('listitem').filter({ hasText: '1. Lead' })).toContainText('Nothing is defined inside this stage.')

  // Solar: other data, drawn by the same screen.
  await page.goto(`/admin/project-templates/${SOLAR}`)
  await expect(page.getByRole('table', { name: 'Timeline' }).getByRole('row')).toHaveText([
    /Stage/, /1\. Site Survey.*Day 0 to 2/, /2\. Design.*Day 2 to 6/, /3\. Quotation.*Day 6 to 8/,
    /4\. Procurement.*Day 8 to 22.*Nobody in particular/, /5\. Installation.*Day 22 to 27/, /6\. Testing.*Day 27 to 28/,
    /7\. Handover.*Day 28 to 29/,
  ])
  await expect(page.getByText('3D Design')).toHaveCount(0)
})

test('an administrator builds a template stage by stage', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await mockEditorLookups(page)
  let posted: Record<string, unknown> | undefined
  let reject = true
  let created: ReturnType<typeof template> | undefined
  await page.route('**/api/v1/project-templates', (route) => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({ json: created ? [summaryOf(created)] : [] })
    }
    posted = route.request().postDataJSON() as Record<string, unknown>
    if (reject) {
      return route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        json: {
          status: 400, code: 'VALIDATION_FAILED', detail: 'One or more values are invalid',
          errors: [{ field: 'stages[1].dependsOn', message: 'This stage depends, through other stages, on itself' }],
        },
      })
    }
    created = template(SOLAR, SOLAR_SERVICE, posted.name as string, posted.stages as Stage[])
    return route.fulfill({ status: 201, json: created })
  })
  await page.route(`**/api/v1/project-templates/${SOLAR}`, (route) => route.fulfill({ json: created }))

  await page.goto('/admin/project-templates')
  await expect(page.getByText('No service has a project template yet. Create one to define how its projects run.')).toBeVisible()
  await page.getByRole('link', { name: 'New template' }).click()

  const form = page.getByRole('form', { name: 'New template' })
  // Nothing is filled in: the form says what is missing before anything is sent.
  await form.getByRole('button', { name: 'Create template' }).click()
  await expect(form.getByText('Choose the service this template is for')).toBeVisible()
  await expect(form.getByText('Enter a name')).toBeVisible()
  await expect(form.getByText('Enter the name of the stage')).toBeVisible()
  expect(posted).toBeUndefined()

  await form.getByLabel('Service').selectOption({ label: 'Rooftop solar (Solar)' })
  await form.getByLabel('Template name').fill('Solar project')

  // Stage 1 starts with the project.
  const first = form.getByRole('group', { name: 'Stage 1', exact: true })
  await first.getByLabel('Stage name').fill('Site Survey')
  await first.getByLabel('Estimated duration (days)').fill('2')
  await expect(first.getByLabel('Responsible role').getByRole('option')).toHaveText([
    'Nobody in particular', 'Admin', 'Designer', 'Project manager', 'Sales executive', 'Technician',
  ])
  await first.getByLabel('Responsible role').selectOption('Technician')
  await expect(first).toContainText('This is the only stage: it starts with the project.')
  await first.getByRole('button', { name: 'Add task' }).click()
  const task = first.getByRole('group', { name: 'Task 1 of Site Survey' })
  await task.getByRole('textbox', { name: 'Task', exact: true }).fill('Measure the roof')
  await task.getByLabel('Hours').fill('3')
  await task.getByLabel('Checklist').fill('Usable area\n\n  Shading ')
  await first.getByRole('button', { name: 'Add document' }).click()
  await first.getByRole('group', { name: 'Document 1 of Site Survey' }).getByPlaceholder('Document').fill('Latest electricity bill')

  // A new stage starts out waiting for the one before it.
  await form.getByRole('button', { name: 'Add stage' }).click()
  const second = form.getByRole('group', { name: 'Stage 2', exact: true })
  await second.getByLabel('Stage name').fill('Design')
  await second.getByLabel('Estimated duration (days)').fill('4')
  await expect(second.getByRole('group', { name: 'Design waits for' }).getByLabel('Site Survey')).toBeChecked()
  await second.getByLabel('Milestones').fill('Design approved by the customer')

  await form.getByRole('button', { name: 'Add stage' }).click()
  const third = form.getByRole('group', { name: 'Stage 3', exact: true })
  await third.getByLabel('Stage name').fill('Installation')
  await third.getByLabel('Estimated duration (days)').fill('lots')
  await form.getByRole('button', { name: 'Create template' }).click()
  await expect(third.getByText('Whole days, 0 to 3650')).toBeVisible()
  await third.getByLabel('Estimated duration (days)').fill('5')
  const waitsFor = third.getByRole('group', { name: 'Installation waits for' })
  await expect(waitsFor.getByLabel('Design')).toBeChecked()
  await expect(waitsFor.getByLabel('Site Survey')).not.toBeChecked()

  // The server has the last word on how stages fit together, and its objection lands on the stage.
  await form.getByRole('button', { name: 'Create template' }).click()
  await expect(second.getByText('This stage depends, through other stages, on itself')).toBeVisible()
  await expect(form.getByRole('alert').first()).toContainText('Some stages need attention.')

  reject = false
  await form.getByRole('button', { name: 'Create template' }).click()

  // Keys are made from the names; "waits for" is sent as those keys.
  expect(posted).toEqual({
    serviceId: SOLAR_SERVICE.id,
    name: 'Solar project',
    description: null,
    stages: [
      {
        key: 'site_survey', name: 'Site Survey', description: null, durationDays: 2, responsibleRole: 'TECHNICIAN', dependsOn: [],
        milestones: [],
        tasks: [{ title: 'Measure the roof', description: null, responsibleRole: null, estimatedHours: 3, checklist: ['Usable area', 'Shading'] }],
        documents: [{ name: 'Latest electricity bill', required: true, description: null }],
      },
      {
        key: 'design', name: 'Design', description: null, durationDays: 4, responsibleRole: null, dependsOn: ['site_survey'],
        milestones: ['Design approved by the customer'], tasks: [], documents: [],
      },
      {
        key: 'installation', name: 'Installation', description: null, durationDays: 5, responsibleRole: null, dependsOn: ['design'],
        milestones: [], tasks: [], documents: [],
      },
    ],
  })
  // Saved: the template is shown as it will be followed.
  await expect(page).toHaveURL(new RegExp(`/admin/project-templates/${SOLAR}$`))
  await expect(page.getByRole('table', { name: 'Timeline' }).getByRole('row')).toHaveText([
    /Stage/, /1\. Site Survey.*Day 0 to 2/, /2\. Design.*Day 2 to 6/, /3\. Installation.*Day 6 to 11/,
  ])
})

test('editing keeps what stages are called in storage, and reorders and removes them safely', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await mockEditorLookups(page)
  const original = template(SOLAR, SOLAR_SERVICE, 'Solar project', SOLAR_STAGES.slice(0, 4), { version: 6 })
  let put: Record<string, unknown> | undefined
  await page.route(`**/api/v1/project-templates/${SOLAR}`, (route) => {
    if (route.request().method() === 'PUT') {
      put = route.request().postDataJSON() as Record<string, unknown>
      return route.fulfill({ json: template(SOLAR, SOLAR_SERVICE, put.name as string, put.stages as Stage[], { version: 7 }) })
    }
    return route.fulfill({ json: original })
  })
  await page.route('**/api/v1/project-templates', (route) => route.fulfill({ json: [summaryOf(original)] }))

  await page.goto(`/admin/project-templates/${SOLAR}`)
  await page.getByRole('link', { name: 'Edit' }).click()
  const form = page.getByRole('form', { name: 'Edit template' })

  // The service is fixed; the stages come back as saved.
  await expect(form.getByLabel('Service')).toBeDisabled()
  await expect(form.getByLabel('Service')).toHaveValue('Rooftop solar')
  await expect(form.getByLabel('Stage name')).toHaveCount(4)
  const stageNames = () => form.getByLabel('Stage name')
  await expect(stageNames().nth(1)).toHaveValue('Design')
  await expect(form.getByRole('group', { name: 'Quotation waits for' }).getByLabel('Design')).toBeChecked()

  // Renamed: the name changes, the stored key and what waits for it do not.
  await stageNames().nth(1).fill('System design')
  await expect(form.getByRole('group', { name: 'Quotation waits for' }).getByLabel('System design')).toBeChecked()

  // Moved up: order changes, dependencies stay with the stages.
  await form.getByRole('button', { name: 'Move Procurement up' }).click()
  await expect(stageNames()).toHaveCount(4)
  await expect(stageNames().nth(2)).toHaveValue('Procurement')
  await expect(form.getByRole('group', { name: 'Procurement waits for' }).getByLabel('Quotation')).toBeChecked()
  await expect(form.getByRole('button', { name: 'Move Site Survey up' })).toBeDisabled()

  // Removed: nothing is left waiting for a stage that is gone.
  await form.getByRole('button', { name: 'Remove Quotation' }).click()
  await expect(stageNames()).toHaveCount(3)
  const procurementWaits = form.getByRole('group', { name: 'Procurement waits for' })
  await expect(procurementWaits.getByRole('checkbox', { checked: true })).toHaveCount(0)
  await procurementWaits.getByLabel('System design').check()

  await form.getByRole('button', { name: 'Save template' }).click()

  expect(put).toMatchObject({
    version: 6,
    name: 'Solar project',
    stages: [
      { key: 'site_survey', name: 'Site Survey', dependsOn: [] },
      { key: 'design', name: 'System design', dependsOn: ['site_survey'] },
      { key: 'procurement', name: 'Procurement', dependsOn: ['design'] },
    ],
  })
  expect(put).not.toHaveProperty('serviceId')
  await expect(page).toHaveURL(new RegExp(`/admin/project-templates/${SOLAR}$`))
})

test('templates can be switched off and removed from the list', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  const templates = [summaryOf(template(SOLAR, SOLAR_SERVICE, 'Solar project', SOLAR_STAGES))]
  const calls: string[] = []
  await page.route('**/api/v1/project-templates', (route) => route.fulfill({ json: templates }))
  await page.route(`**/api/v1/project-templates/${SOLAR}/**`, (route) => {
    calls.push(`${route.request().method()} ${new URL(route.request().url()).pathname.split('/').pop()}`)
    templates[0] = { ...templates[0], active: !route.request().url().endsWith('deactivate') }
    return route.fulfill({ json: template(SOLAR, SOLAR_SERVICE, 'Solar project', SOLAR_STAGES, { active: templates[0].active }) })
  })
  await page.route(`**/api/v1/project-templates/${SOLAR}`, (route) => {
    calls.push(route.request().method())
    templates.length = 0
    return route.fulfill({ status: 204 })
  })

  await page.goto('/admin/project-templates')
  const row = page.getByRole('table', { name: 'Project templates' }).getByRole('row').nth(1)
  await expect(row).toContainText('Active')
  await page.getByRole('button', { name: 'Switch off Solar project' }).click()
  await expect(row).toContainText('Inactive')
  await page.getByRole('button', { name: 'Switch on Solar project' }).click()
  await expect(row).toContainText('Active')
  await page.getByRole('button', { name: 'Remove Solar project' }).click()
  await expect(page.getByText('No service has a project template yet.')).toBeVisible()
  expect(calls).toEqual(['POST deactivate', 'POST activate', 'DELETE'])
})

test('someone who runs projects reads templates but does not define them', async ({ page }) => {
  await mockSignedIn(page, ['PROJECT_VIEW'])
  const solar = template(SOLAR, SOLAR_SERVICE, 'Solar project', SOLAR_STAGES)
  await page.route('**/api/v1/project-templates', (route) => route.fulfill({ json: [summaryOf(solar)] }))
  await page.route(`**/api/v1/project-templates/${SOLAR}`, (route) => route.fulfill({ json: solar }))

  await page.goto('/admin/project-templates')
  await expect(page.getByRole('table', { name: 'Project templates' })).toContainText('Solar project')
  await expect(page.getByRole('link', { name: 'New template' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /^(Switch off|Remove)/ })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Edit Solar project' })).toHaveCount(0)

  await page.getByRole('link', { name: 'Solar project' }).click()
  await expect(page.getByRole('table', { name: 'Timeline' }).getByRole('row')).toHaveCount(8)
  await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0)

  await page.goto('/admin/project-templates/new')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
  await page.goto(`/admin/project-templates/${SOLAR}/edit`)
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})

test('project templates are hidden from users without the permission', async ({ page }) => {
  await mockSignedIn(page, ['CUSTOMER_VIEW'])

  await page.goto('/')
  await expect(page.getByRole('link', { name: 'Project templates' })).toHaveCount(0)
  await page.goto('/admin/project-templates')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})
