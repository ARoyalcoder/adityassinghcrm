import { expect, test } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, problem, VERTICALS } from './support'

test.beforeEach(async ({ page }) => {
  await mockSignedIn(page)
  await mockSystemInfo(page)
})

test('overview lists exactly the six business verticals', async ({ page }) => {
  await page.route('**/api/v1/service-verticals', (route) => route.fulfill({ json: VERTICALS }))

  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Overview', level: 1 })).toBeVisible()
  const table = page.getByRole('table', { name: 'Business verticals' })
  await expect(table.locator('tbody tr')).toHaveCount(6)
  for (const vertical of VERTICALS) {
    await expect(table.getByRole('cell', { name: vertical.name, exact: true })).toBeVisible()
  }
  await expect(page.getByText(/real estate/i)).toHaveCount(0)
  await expect(page.getByText('0.1.0-test')).toBeVisible()
})

test('an API failure shows the server message, the request id and a working retry', async ({ page }) => {
  // A flag rather than a call counter: in dev, React StrictMode mounts twice and may fetch twice.
  let failing = true
  await page.route('**/api/v1/service-verticals', (route) => {
    if (failing) {
      return route.fulfill(problem(403, 'FORBIDDEN', 'You do not have permission to perform this action'))
    }
    return route.fulfill({ json: VERTICALS })
  })

  await page.goto('/')

  const alert = page.getByRole('alert')
  await expect(alert).toContainText('Could not load business verticals')
  await expect(alert).toContainText('You do not have permission to perform this action')
  await expect(alert).toContainText('req-e2e-12345')

  failing = false
  await alert.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByRole('table', { name: 'Business verticals' }).locator('tbody tr')).toHaveCount(6)
})

test('unknown routes render the not-found page inside the shell', async ({ page }) => {
  await page.goto('/no-such-page')

  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible()
  await page.getByRole('link', { name: 'Back to overview' }).click()
  await expect(page).toHaveURL('/')
})
