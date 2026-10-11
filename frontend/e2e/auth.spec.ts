import { expect, test } from '@playwright/test'
import {
  ACCESS_TOKEN,
  mockSignedIn,
  mockSignedOut,
  mockSystemInfo,
  mockVerticals,
  problem,
  TOKEN_RESPONSE,
  USER,
  VERTICALS,
} from './support'

test('a signed-out visitor is sent to the login page and back to their page after signing in', async ({ page }) => {
  await mockSignedOut(page)
  await mockSystemInfo(page)
  await mockVerticals(page)
  let submitted: unknown
  await page.route('**/api/v1/auth/login', (route) => {
    submitted = route.request().postDataJSON()
    return route.fulfill({ json: TOKEN_RESPONSE })
  })

  await page.goto('/some/deep/page')
  await expect(page).toHaveURL('/login')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

  await page.getByLabel('Email').fill(USER.email)
  await page.getByLabel('Password').fill('a-password-typed-by-the-user')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page).toHaveURL('/some/deep/page')
  expect(submitted).toEqual({ email: USER.email, password: 'a-password-typed-by-the-user' })
  await expect(page.getByText(USER.fullName)).toBeVisible()
})

test('wrong credentials show the server message and keep the visitor on the login page', async ({ page }) => {
  await mockSignedOut(page)
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill(problem(401, 'INVALID_CREDENTIALS', 'Invalid email or password')),
  )

  await page.goto('/login')
  await page.getByLabel('Email').fill(USER.email)
  await page.getByLabel('Password').fill('wrong-password')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page.getByRole('alert')).toHaveText('Invalid email or password')
  await expect(page).toHaveURL('/login')
})

test('the login form validates before calling the server', async ({ page }) => {
  await mockSignedOut(page)
  let loginCalls = 0
  await page.route('**/api/v1/auth/login', (route) => {
    loginCalls += 1
    return route.fulfill({ json: TOKEN_RESPONSE })
  })

  await page.goto('/login')
  await page.getByLabel('Email').fill('not-an-email')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page.getByText('Enter a valid email address')).toBeVisible()
  await expect(page.getByText('Enter your password')).toBeVisible()
  expect(loginCalls).toBe(0)
})

test('a returning visitor with a valid session skips the login page and sees their access', async ({ page }) => {
  await mockSignedIn(page)
  await mockSystemInfo(page)
  await mockVerticals(page)

  await page.goto('/login')

  await expect(page).toHaveURL('/')
  await expect(page.getByLabel('Your roles')).toContainText('SALES_EXECUTIVE')
  await expect(page.getByLabel('Your permissions')).toContainText('CUSTOMER_VIEW')
})

test('API calls carry the access token, and an expired one is renewed once and the call repeated', async ({ page }) => {
  await mockSystemInfo(page)
  let refreshCalls = 0
  await page.route('**/api/v1/auth/refresh', (route) => {
    refreshCalls += 1
    const token = refreshCalls === 1 ? ACCESS_TOKEN : 'renewed-access-token'
    return route.fulfill({ json: { ...TOKEN_RESPONSE, accessToken: token } })
  })
  const authorizationHeaders: (string | undefined)[] = []
  await page.route('**/api/v1/service-verticals', (route) => {
    const authorization = route.request().headers()['authorization']
    authorizationHeaders.push(authorization)
    // The first token has "expired": only the renewed one is accepted.
    if (authorization !== 'Bearer renewed-access-token') {
      return route.fulfill(problem(401, 'UNAUTHENTICATED', 'Authentication is required to access this resource'))
    }
    return route.fulfill({ json: VERTICALS })
  })

  await page.goto('/')

  await expect(page.getByRole('table', { name: 'Business verticals' }).locator('tbody tr')).toHaveCount(6)
  expect(authorizationHeaders).toContain(`Bearer ${ACCESS_TOKEN}`)
  expect(authorizationHeaders.at(-1)).toBe('Bearer renewed-access-token')
})

test('when the session cannot be renewed the visitor is returned to the login page', async ({ page }) => {
  await mockSystemInfo(page)
  let refreshCalls = 0
  await page.route('**/api/v1/auth/refresh', (route) => {
    refreshCalls += 1
    return refreshCalls === 1
      ? route.fulfill({ json: TOKEN_RESPONSE })
      : route.fulfill(problem(401, 'UNAUTHENTICATED', 'Your session has ended. Please sign in again.'))
  })
  await page.route('**/api/v1/service-verticals', (route) =>
    route.fulfill(problem(401, 'UNAUTHENTICATED', 'Authentication is required to access this resource')),
  )

  await page.goto('/')

  await expect(page).toHaveURL('/login')
})

test('signing out calls the server and returns to the login page', async ({ page }) => {
  await mockSignedIn(page)
  await mockSystemInfo(page)
  await mockVerticals(page)
  let logoutCalls = 0
  await page.route('**/api/v1/auth/logout', (route) => {
    logoutCalls += 1
    return route.fulfill({ status: 204 })
  })

  await page.goto('/')
  await page.getByRole('button', { name: 'Sign out' }).click()

  await expect(page).toHaveURL('/login')
  expect(logoutCalls).toBe(1)
})
