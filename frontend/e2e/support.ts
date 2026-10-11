import type { Page } from '@playwright/test'

export const VERTICALS = [
  { code: 'CCTV_SECURITY', name: 'CCTV & Security', displayOrder: 1 },
  { code: 'DIGITAL_MARKETING', name: 'Digital Marketing', displayOrder: 2 },
  { code: 'INTERIOR_DESIGN', name: 'Interior Design', displayOrder: 3 },
  { code: 'ARCHITECTURE_TECH', name: 'Architecture & Tech', displayOrder: 4 },
  { code: 'SOLAR', name: 'Solar', displayOrder: 5 },
  { code: 'IT_SUPPORT', name: 'IT Support', displayOrder: 6 },
]

export const USER = {
  id: '0b6c2a7e-6c1d-4a1e-9a55-0c3f3f1f6f10',
  email: 'asha@example.com',
  fullName: 'Asha Verma',
  status: 'ACTIVE',
  organizationId: '1e9748c2-1869-4002-bfbf-1db6669f6cbb',
  branchId: null,
  roles: ['SALES_EXECUTIVE'],
  permissions: ['CUSTOMER_VIEW', 'CUSTOMER_CREATE'],
}

export const ACCESS_TOKEN = 'e2e-access-token'

export const TOKEN_RESPONSE = { accessToken: ACCESS_TOKEN, tokenType: 'Bearer', expiresIn: 900, user: USER }

export function problem(status: number, code: string, detail: string, requestId = 'req-e2e-12345') {
  return {
    status,
    contentType: 'application/problem+json',
    json: { type: `urn:bos:error:${code.toLowerCase()}`, title: code, status, code, detail, requestId },
  }
}

/** The browser has no refresh cookie: the app must send the visitor to the login page. */
export async function mockSignedOut(page: Page) {
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill(problem(401, 'UNAUTHENTICATED', 'Your session has ended. Please sign in again.')),
  )
}

/**
 * The browser holds a valid refresh cookie: the app starts already signed in.
 * Pass `permissions` to sign in as a user who holds exactly those.
 */
export async function mockSignedIn(page: Page, permissions: string[] = USER.permissions) {
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill({ json: { ...TOKEN_RESPONSE, user: { ...USER, permissions } } }),
  )
}

export function pageOf<T>(items: T[], page = 0, totalItems = items.length, size = 20) {
  return { items, page, size, totalItems, totalPages: Math.max(1, Math.ceil(totalItems / size)) }
}

export async function mockSystemInfo(page: Page) {
  await page.route('**/api/v1/system/info', (route) =>
    route.fulfill({
      json: { name: 'Pawan Putra Business OS', version: '0.1.0-test', apiVersion: 'v1', serverTime: '2026-01-01T00:00:00Z' },
    }),
  )
}

export async function mockVerticals(page: Page) {
  await page.route('**/api/v1/service-verticals', (route) => route.fulfill({ json: VERTICALS }))
}
