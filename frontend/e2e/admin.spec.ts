import { expect, test, type Page } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, mockVerticals, pageOf, problem } from './support'

const ADMIN = [
  'CATALOG_VIEW',
  'CATALOG_CREATE',
  'CATALOG_UPDATE',
  'CATALOG_DELETE',
  'BRANCH_VIEW',
  'BRANCH_CREATE',
  'BRANCH_UPDATE',
]

const ADMIN_VERTICALS = [
  { code: 'CCTV_SECURITY', name: 'CCTV & Security' },
  { code: 'DIGITAL_MARKETING', name: 'Digital Marketing' },
  { code: 'INTERIOR_DESIGN', name: 'Interior Design' },
  { code: 'ARCHITECTURE_TECH', name: 'Architecture & Tech' },
  { code: 'SOLAR', name: 'Solar' },
  { code: 'IT_SUPPORT', name: 'IT Support' },
].map((vertical, index) => ({
  ...vertical,
  id: `00000000-0000-0000-0000-00000000000${index + 1}`,
  description: null,
  displayOrder: index + 1,
  active: true,
  version: 0,
}))

const SOLAR = { code: 'SOLAR', name: 'Solar' }
const CATEGORY = {
  id: '11111111-1111-1111-1111-111111111111',
  code: 'ROOFTOP',
  name: 'Rooftop systems',
  description: null,
  displayOrder: 0,
  active: true,
  vertical: SOLAR,
  version: 0,
}

function service(overrides: Record<string, unknown> = {}) {
  return {
    id: '22222222-2222-2222-2222-222222222222',
    code: 'SOLAR_ROOFTOP_3KW',
    name: '3 kW rooftop installation',
    description: null,
    displayOrder: 0,
    active: true,
    billingType: 'ONE_TIME',
    unitLabel: 'per kW',
    basePrice: 55000,
    requiresSiteVisit: true,
    estimatedDurationDays: 7,
    category: { id: CATEGORY.id, code: CATEGORY.code, name: CATEGORY.name },
    vertical: SOLAR,
    version: 0,
    ...overrides,
  }
}

async function mockCatalogLookups(page: Page) {
  await page.route('**/api/v1/catalog/verticals', (route) => route.fulfill({ json: ADMIN_VERTICALS }))
  await page.route('**/api/v1/catalog/categories?*', (route) => route.fulfill({ json: pageOf([CATEGORY]) }))
}

test.beforeEach(async ({ page }) => {
  await mockSystemInfo(page)
  await mockVerticals(page)
})

test('services list sends search, filters and page to the server', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await mockCatalogLookups(page)
  const requests: URLSearchParams[] = []
  await page.route('**/api/v1/catalog/services**', (route) => {
    const params = new URL(route.request().url()).searchParams
    requests.push(params)
    const pageNumber = Number(params.get('page') ?? '0')
    return route.fulfill({ json: pageOf([service({ name: `Service on page ${pageNumber + 1}` })], pageNumber, 45) })
  })

  await page.goto('/admin/services')
  await expect(page.getByRole('table', { name: 'Services' })).toContainText('Service on page 1')
  await expect(page.getByText('45 services · page 1 of 3')).toBeVisible()
  await expect(page.getByText('₹55,000.00 per kW')).toBeVisible()

  await page.getByRole('searchbox', { name: 'Search services' }).fill('rooftop')
  await expect.poll(() => requests.at(-1)?.get('search')).toBe('rooftop')

  await page.getByLabel('Vertical').selectOption('SOLAR')
  await expect.poll(() => requests.at(-1)?.get('vertical')).toBe('SOLAR')
  await page.getByLabel('Status').selectOption('false')
  await expect.poll(() => requests.at(-1)?.get('active')).toBe('false')

  await page.getByRole('button', { name: 'Next' }).click()
  await expect(page.getByRole('table', { name: 'Services' })).toContainText('Service on page 2')
  expect(requests.at(-1)?.get('page')).toBe('1')
  // Filters survive in the URL, so the view can be bookmarked or shared.
  await expect(page).toHaveURL(/search=rooftop/)
  await expect(page).toHaveURL(/vertical=SOLAR/)

  // Changing a filter goes back to the first page.
  await page.getByLabel('Billing').selectOption('RECURRING')
  await expect.poll(() => requests.at(-1)?.get('billingType')).toBe('RECURRING')
  expect(requests.at(-1)?.get('page')).toBe('0')
})

test('a service can be deactivated from the list', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await mockCatalogLookups(page)
  let active = true
  await page.route('**/api/v1/catalog/services?*', (route) => route.fulfill({ json: pageOf([service({ active })]) }))
  await page.route('**/api/v1/catalog/services', (route) => route.fulfill({ json: pageOf([service({ active })]) }))
  let deactivateCalls = 0
  await page.route('**/api/v1/catalog/services/*/deactivate', (route) => {
    deactivateCalls += 1
    active = false
    return route.fulfill({ json: service({ active: false, version: 1 }) })
  })

  await page.goto('/admin/services')
  await page.getByRole('button', { name: 'Deactivate 3 kW rooftop installation' }).click()

  await expect(page.getByRole('button', { name: 'Activate 3 kW rooftop installation' })).toBeVisible()
  await expect(page.getByRole('table', { name: 'Services' })).toContainText('Inactive')
  expect(deactivateCalls).toBe(1)
})

test('new service form validates, then creates the service', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await mockCatalogLookups(page)
  let created: Record<string, unknown> | undefined
  await page.route('**/api/v1/catalog/services', (route) => {
    if (route.request().method() === 'POST') {
      created = route.request().postDataJSON() as Record<string, unknown>
      return route.fulfill({ status: 201, json: service() })
    }
    return route.fulfill({ json: pageOf([service()]) })
  })

  await page.goto('/admin/services/new')
  await page.getByRole('button', { name: 'Create service' }).click()

  await expect(page.getByText('Enter a name')).toBeVisible()
  await expect(page.getByText('Enter a code')).toBeVisible()
  await expect(page.getByText('Choose a category', { exact: true })).toBeVisible()
  expect(created).toBeUndefined()

  await page.getByLabel('Name').fill('3 kW rooftop installation')
  await page.getByLabel('Code').fill('solar rooftop 3kw')
  await expect(page.getByLabel('Code')).toHaveValue('SOLAR_ROOFTOP_3KW')
  // The picker groups categories under the six verticals.
  await expect(page.getByLabel('Category').locator('optgroup')).toHaveAttribute('label', 'Solar')
  await page.getByLabel('Category').selectOption(CATEGORY.id)
  await page.getByLabel('Billing').selectOption('ONE_TIME')
  await page.getByRole('button', { name: 'Create service' }).click()
  await expect(page.getByText('Enter a base price, or choose quote-based billing')).toBeVisible()

  await page.getByLabel('Base price (₹)').fill('55000.50')
  await page.getByLabel('Unit').fill('per kW')
  await page.getByLabel('Typical duration (days)').fill('7')
  await page.getByLabel('Needs a site visit before work starts').check()
  await page.getByRole('button', { name: 'Create service' }).click()

  await expect(page).toHaveURL('/admin/services')
  expect(created).toEqual({
    code: 'SOLAR_ROOFTOP_3KW',
    categoryId: CATEGORY.id,
    name: '3 kW rooftop installation',
    description: null,
    displayOrder: 0,
    billingType: 'ONE_TIME',
    unitLabel: 'per kW',
    basePrice: 55000.5,
    requiresSiteVisit: true,
    estimatedDurationDays: 7,
  })
})

test('editing a service sends its version, and a conflict is explained on the form', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await mockCatalogLookups(page)
  let updated: Record<string, unknown> | undefined
  await page.route('**/api/v1/catalog/services/22222222-2222-2222-2222-222222222222', (route) => {
    if (route.request().method() === 'PUT') {
      updated = route.request().postDataJSON() as Record<string, unknown>
      return route.fulfill(
        problem(409, 'CONFLICT', 'This service was changed by someone else. Reload it and apply your changes again.'),
      )
    }
    return route.fulfill({ json: service({ version: 4 }) })
  })

  await page.goto('/admin/services/22222222-2222-2222-2222-222222222222/edit')
  await expect(page.getByLabel('Name')).toHaveValue('3 kW rooftop installation')
  await expect(page.getByLabel('Code')).toBeDisabled()
  await expect(page.getByLabel('Base price (₹)')).toHaveValue('55000')

  await page.getByLabel('Name').fill('3 kW rooftop plant')
  await page.getByRole('button', { name: 'Save changes' }).click()

  await expect(page.getByRole('alert')).toContainText('changed by someone else')
  await expect(page).toHaveURL(/\/edit$/)
  expect(updated).toMatchObject({ name: '3 kW rooftop plant', version: 4 })
  expect(updated).not.toHaveProperty('code')
})

test('a field error from the server is shown next to that field', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await page.route('**/api/v1/catalog/verticals', (route) => route.fulfill({ json: ADMIN_VERTICALS }))
  await page.route('**/api/v1/catalog/categories', (route) =>
    route.fulfill({
      status: 400,
      contentType: 'application/problem+json',
      json: {
        status: 400,
        code: 'VALIDATION_FAILED',
        detail: 'One or more fields are invalid',
        errors: [{ field: 'name', message: 'size must be between 0 and 150' }],
      },
    }),
  )

  await page.goto('/admin/categories/new')
  await page.getByLabel('Vertical').selectOption('SOLAR')
  await page.getByLabel('Name').fill('Rooftop systems')
  await page.getByLabel('Code').fill('ROOFTOP')
  await page.getByRole('button', { name: 'Create category' }).click()

  await expect(page.getByText('size must be between 0 and 150')).toBeVisible()
})

test('verticals can be edited but there is no way to add one', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await page.route('**/api/v1/catalog/verticals', (route) => route.fulfill({ json: ADMIN_VERTICALS }))
  let updated: Record<string, unknown> | undefined
  await page.route('**/api/v1/catalog/verticals/SOLAR', (route) => {
    updated = route.request().postDataJSON() as Record<string, unknown>
    return route.fulfill({ json: { ...ADMIN_VERTICALS[4], name: 'Solar Energy', version: 1 } })
  })

  await page.goto('/admin/verticals')
  const table = page.getByRole('table', { name: 'Verticals' })
  await expect(table.locator('tbody tr')).toHaveCount(6)
  await expect(page.getByText(/real estate/i)).toHaveCount(0)
  await expect(page.getByRole('link', { name: /new/i })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /new|add/i })).toHaveCount(0)

  await page.getByRole('link', { name: 'Edit Solar' }).click()
  await expect(page).toHaveURL('/admin/verticals/SOLAR/edit')
  await page.getByLabel('Name').fill('Solar Energy')
  await page.getByRole('button', { name: 'Save changes' }).click()

  await expect(page).toHaveURL('/admin/verticals')
  expect(updated).toEqual({ name: 'Solar Energy', description: null, displayOrder: 5, version: 0 })

  // A made-up vertical code has no edit page.
  await page.goto('/admin/verticals/REAL_ESTATE/edit')
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible()
})

test('branches can be filtered by state and city, and a new branch needs a location', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await page.route('**/api/v1/branches/locations', (route) =>
    route.fulfill({
      json: [
        { state: 'Delhi', cities: ['New Delhi'] },
        { state: 'Uttar Pradesh', cities: ['Kanpur', 'Lucknow'] },
      ],
    }),
  )
  const branch = {
    id: '33333333-3333-3333-3333-333333333333',
    code: 'LKO-HZG',
    name: 'Hazratganj Office',
    city: 'Lucknow',
    state: 'Uttar Pradesh',
    countryCode: 'IN',
    headOffice: true,
    active: true,
    version: 0,
  }
  const requests: URLSearchParams[] = []
  let created: Record<string, unknown> | undefined
  await page.route('**/api/v1/branches**', (route) => {
    if (route.request().url().includes('/locations')) {
      return route.fallback()
    }
    if (route.request().method() === 'POST') {
      created = route.request().postDataJSON() as Record<string, unknown>
      return route.fulfill({ status: 201, json: branch })
    }
    requests.push(new URL(route.request().url()).searchParams)
    return route.fulfill({ json: pageOf([branch]) })
  })

  await page.goto('/admin/branches')
  await expect(page.getByRole('table', { name: 'Branches' })).toContainText('Head office')

  await page.getByLabel('State').selectOption('Uttar Pradesh')
  await expect.poll(() => requests.at(-1)?.get('state')).toBe('Uttar Pradesh')
  await expect(page.getByLabel('City').locator('option')).toHaveText(['All cities', 'Kanpur', 'Lucknow'])
  await page.getByLabel('City').selectOption('Kanpur')
  await expect.poll(() => requests.at(-1)?.get('city')).toBe('Kanpur')

  await page.getByRole('link', { name: 'New branch' }).click()
  await page.getByLabel('Name').fill('Civil Lines Office')
  await page.getByLabel('Code', { exact: true }).fill('knp 1')
  await page.getByLabel('Email').fill('not-an-email')
  await page.getByRole('button', { name: 'Create branch' }).click()
  await expect(page.getByText('Enter a city')).toBeVisible()
  await expect(page.getByText('Enter a state')).toBeVisible()
  await expect(page.getByText('Enter a valid email address')).toBeVisible()
  expect(created).toBeUndefined()

  await page.getByLabel('City').fill('Kanpur')
  await page.getByLabel('State').fill('Uttar Pradesh')
  await page.getByLabel('Email').fill('')
  await page.getByRole('button', { name: 'Create branch' }).click()

  await expect(page).toHaveURL('/admin/branches')
  expect(created).toMatchObject({
    code: 'KNP-1',
    name: 'Civil Lines Office',
    city: 'Kanpur',
    state: 'Uttar Pradesh',
    email: null,
    headOffice: false,
  })
})

test('a user without the permissions sees neither the menu entries nor the pages nor the buttons', async ({ page }) => {
  await mockSignedIn(page, ['CUSTOMER_VIEW'])

  await page.goto('/')
  await expect(page.getByRole('link', { name: 'Overview' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Services' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Branches' })).toHaveCount(0)

  await page.goto('/admin/services')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
  await page.goto('/admin/branches/new')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})

test('a view-only user gets the lists without create, edit or deactivate controls', async ({ page }) => {
  await mockSignedIn(page, ['CATALOG_VIEW'])
  await mockCatalogLookups(page)
  await page.route('**/api/v1/catalog/services**', (route) => route.fulfill({ json: pageOf([service()]) }))

  await page.goto('/admin/services')

  await expect(page.getByRole('table', { name: 'Services' })).toContainText('3 kW rooftop installation')
  await expect(page.getByRole('link', { name: 'New service' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: /^Edit/ })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /Deactivate/ })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Branches' })).toHaveCount(0)
})
