import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'

export const currentUserSchema = z.object({
  id: z.string(),
  email: z.string(),
  fullName: z.string(),
  status: z.string(),
  organizationId: z.string(),
  branchId: z.string().nullish(),
  roles: z.array(z.string()),
  permissions: z.array(z.string()),
})
export type CurrentUser = z.infer<typeof currentUserSchema>

const tokenResponseSchema = z.object({
  accessToken: z.string(),
  tokenType: z.string(),
  expiresIn: z.number(),
  user: currentUserSchema,
})
export type TokenResponse = z.infer<typeof tokenResponseSchema>

const noContent = z.undefined()

export function loginRequest(email: string, password: string) {
  return apiRequest('/auth/login', tokenResponseSchema, {
    method: 'POST',
    body: { email, password },
    authenticated: false,
  })
}

/** Exchanges the HttpOnly refresh cookie for a new access token. The cookie is sent by the browser. */
export function refreshRequest() {
  return apiRequest('/auth/refresh', tokenResponseSchema, { method: 'POST', authenticated: false })
}

export function logoutRequest() {
  return apiRequest('/auth/logout', noContent, { method: 'POST', authenticated: false })
}
