import { expect, test, type Page } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, mockVerticals, pageOf } from './support'

const ACTOR = { id: '44444444-4444-4444-4444-444444444444', label: 'asha@example.com' }
const SERVICE_ID = '22222222-2222-2222-2222-222222222222'

function entry(overrides: Record<string, unknown> = {}) {
  return {
    id: '55555555-5555-5555-5555-555555555555',
    occurredAt: '2026-10-05T10:15:30Z',
    actorId: ACTOR.id,
    actorLabel: ACTOR.label,
    action: 'UPDATE',
    module: 'catalog',
    entityType: 'Service',
    entityId: SERVICE_ID,
    entityLabel: '3 kW rooftop installation',
    summary: "Updated Service '3 kW rooftop installation': basePrice",
    ipAddress: '203.0.113.7',
    ...overrides,
  }
}

async function mockFacets(page: Page) {
  await page.route('**/api/v1/audit/facets', (route) =>
    route.fulfill({
      json: { modules: ['catalog', 'identity'], entityTypes: ['Branch', 'Service', 'User'], actors: [ACTOR] },
    }),
  )
}

test.beforeEach(async ({ page }) => {
  await mockSystemInfo(page)
  await mockVerticals(page)
})

test('audit log filters by user, module, action, entity, date and text', async ({ page }) => {
  await mockSignedIn(page, ['AUDIT_VIEW'])
  await mockFacets(page)
  const requests: URLSearchParams[] = []
  await page.route('**/api/v1/audit/logs**', (route) => {
    requests.push(new URL(route.request().url()).searchParams)
    return route.fulfill({ json: pageOf([entry(), entry({ id: 'second', action: 'LOGIN', summary: 'Signed in' })]) })
  })

  await page.goto('/')
  await page.getByRole('link', { name: 'Audit log' }).click()
  await expect(page).toHaveURL('/admin/audit')

  const table = page.getByRole('table', { name: 'Events' })
  await expect(table).toContainText("Updated Service '3 kW rooftop installation': basePrice")
  await expect(table).toContainText('asha@example.com')
  await expect(table).toContainText('203.0.113.7')
  await expect(table).toContainText('Login')
  // Nothing on this screen can change the log.
  await expect(page.getByRole('button', { name: /delete|edit|new/i })).toHaveCount(0)

  await page.getByLabel('User').selectOption(ACTOR.id)
  await expect.poll(() => requests.at(-1)?.get('actorId')).toBe(ACTOR.id)
  await page.getByLabel('Module').selectOption('catalog')
  await expect.poll(() => requests.at(-1)?.get('module')).toBe('catalog')
  await page.getByLabel('Action').selectOption('STATUS_CHANGE')
  await expect.poll(() => requests.at(-1)?.get('action')).toBe('STATUS_CHANGE')
  await page.getByLabel('Entity').selectOption('Service')
  await expect.poll(() => requests.at(-1)?.get('entityType')).toBe('Service')
  await page.getByRole('searchbox', { name: 'Search audit log' }).fill('rooftop')
  await expect.poll(() => requests.at(-1)?.get('search')).toBe('rooftop')

  // A day picked in the browser becomes an exact range: start of "from" up to the end of "to".
  await page.getByLabel('From date').fill('2026-10-05')
  await page.getByLabel('To date').fill('2026-10-06')
  await expect.poll(() => requests.at(-1)?.get('to')).not.toBeNull()
  const from = new Date(requests.at(-1)!.get('from')!)
  const to = new Date(requests.at(-1)!.get('to')!)
  expect(from.getTime()).toBe(new Date('2026-10-05T00:00:00').getTime())
  expect(to.getTime()).toBe(new Date('2026-10-07T00:00:00').getTime())

  // All filters are still applied together, and are in the URL.
  expect(requests.at(-1)?.get('actorId')).toBe(ACTOR.id)
  expect(requests.at(-1)?.get('module')).toBe('catalog')
  await expect(page).toHaveURL(/action=STATUS_CHANGE/)
  await expect(page).toHaveURL(/from=2026-10-05/)

  await page.getByRole('button', { name: 'Clear filters' }).click()
  await expect(page).toHaveURL('/admin/audit')
  await expect.poll(() => requests.at(-1)?.get('module')).toBeNull()
  expect(requests.at(-1)?.get('from')).toBeNull()
})

test('audit event detail shows who, where, and the values before and after', async ({ page }) => {
  await mockSignedIn(page, ['AUDIT_VIEW'])
  await mockFacets(page)
  await page.route('**/api/v1/audit/logs?*', (route) => route.fulfill({ json: pageOf([entry()]) }))
  await page.route('**/api/v1/audit/logs/55555555-5555-5555-5555-555555555555', (route) =>
    route.fulfill({
      json: {
        entry: entry(),
        before: { basePrice: 100, name: 'Old name' },
        after: { basePrice: 120, name: 'New name' },
        metadata: { reason: 'Annual revision' },
        userAgent: 'E2E-Browser/1.0',
        requestId: 'req-audit-123',
      },
    }),
  )

  await page.goto('/admin/audit')
  await page.getByRole('link', { name: /^View details/ }).click()
  await expect(page).toHaveURL('/admin/audit/55555555-5555-5555-5555-555555555555')

  await expect(page.getByText("Updated Service '3 kW rooftop installation': basePrice")).toBeVisible()
  await expect(page.getByText('203.0.113.7')).toBeVisible()
  await expect(page.getByText('E2E-Browser/1.0')).toBeVisible()
  await expect(page.getByText('req-audit-123')).toBeVisible()

  const values = page.getByRole('table', { name: 'Values before and after' })
  const priceRow = values.getByRole('row').filter({ hasText: 'basePrice' })
  await expect(priceRow.getByRole('cell')).toHaveText(['basePrice', '100', '120'])
  await expect(values.getByRole('row').filter({ hasText: 'name' }).getByRole('cell')).toHaveText([
    'name',
    'Old name',
    'New name',
  ])
  await expect(page.getByText('Annual revision')).toBeVisible()

  // From one event to everything that happened to the same record.
  await page.getByRole('link', { name: `all events for ${SERVICE_ID}` }).click()
  await expect(page).toHaveURL(new RegExp(`entityType=Service&entityId=${SERVICE_ID}`))
  await expect(page.getByText(`record ${SERVICE_ID}`)).toBeVisible()
})

test('audit log is hidden from users without the permission', async ({ page }) => {
  await mockSignedIn(page, ['CATALOG_VIEW'])

  await page.goto('/')
  await expect(page.getByRole('link', { name: 'Audit log' })).toHaveCount(0)

  await page.goto('/admin/audit')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})

test('a record page shows its activity timeline to users who may see the audit log', async ({ page }) => {
  await mockSignedIn(page, ['CATALOG_VIEW', 'CATALOG_UPDATE', 'AUDIT_VIEW'])
  await page.route('**/api/v1/catalog/categories?*', (route) => route.fulfill({ json: pageOf([]) }))
  await page.route(`**/api/v1/catalog/services/${SERVICE_ID}`, (route) =>
    route.fulfill({
      json: {
        id: SERVICE_ID,
        code: 'SOLAR_ROOFTOP_3KW',
        name: '3 kW rooftop installation',
        displayOrder: 0,
        active: true,
        billingType: 'ONE_TIME',
        basePrice: 120,
        requiresSiteVisit: false,
        category: { id: 'cat', code: 'ROOFTOP', name: 'Rooftop systems' },
        vertical: { code: 'SOLAR', name: 'Solar' },
        version: 1,
      },
    }),
  )
  let activityRequest: URLSearchParams | undefined
  await page.route('**/api/v1/audit/activity**', (route) => {
    activityRequest = new URL(route.request().url()).searchParams
    return route.fulfill({
      json: pageOf([
        {
          id: 'a2',
          occurredAt: '2026-10-05T10:15:30Z',
          action: 'UPDATE',
          actorLabel: ACTOR.label,
          message: "Updated Service '3 kW rooftop installation': basePrice",
          changes: [{ field: 'basePrice', from: 100, to: 120 }],
          auditLogId: 'x',
        },
        {
          id: 'a1',
          occurredAt: '2026-10-01T09:00:00Z',
          action: 'CREATE',
          actorLabel: ACTOR.label,
          message: "Created Service '3 kW rooftop installation'",
          changes: null,
          auditLogId: 'y',
        },
      ]),
    })
  })

  await page.goto(`/admin/services/${SERVICE_ID}/edit`)

  const timeline = page.getByRole('list', { name: 'Activity' })
  await expect(timeline.getByRole('listitem').filter({ hasText: 'Updated Service' })).toContainText('basePrice: 100 → 120')
  await expect(timeline).toContainText("Created Service '3 kW rooftop installation'")
  await expect(timeline).toContainText('asha@example.com')
  expect(activityRequest?.get('entityType')).toBe('Service')
  expect(activityRequest?.get('entityId')).toBe(SERVICE_ID)
})
