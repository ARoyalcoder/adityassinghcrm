import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

const branchSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  addressLine1: z.string().nullish(),
  addressLine2: z.string().nullish(),
  city: z.string(),
  state: z.string(),
  postalCode: z.string().nullish(),
  countryCode: z.string(),
  phone: z.string().nullish(),
  email: z.string().nullish(),
  headOffice: z.boolean(),
  active: z.boolean(),
  version: z.number(),
})
export type Branch = z.infer<typeof branchSchema>

export const branchKeys = { all: ['branches'] as const }

export type BranchFilters = { search?: string; state?: string; city?: string; active?: string; page?: number }

export function branchesQuery(filters: BranchFilters) {
  return queryOptions({
    queryKey: [...branchKeys.all, 'list', filters],
    queryFn: ({ signal }) => apiRequest(`/branches${queryString(filters)}`, pageSchema(branchSchema), { signal }),
    placeholderData: keepPreviousData,
  })
}

export function branchQuery(id: string) {
  return queryOptions({
    queryKey: [...branchKeys.all, 'detail', id],
    queryFn: ({ signal }) => apiRequest(`/branches/${id}`, branchSchema, { signal }),
  })
}

/** The states, and the cities in each, where the organization has a branch. Feeds the location filters. */
export const branchLocationsQuery = queryOptions({
  queryKey: [...branchKeys.all, 'locations'],
  queryFn: ({ signal }) =>
    apiRequest('/branches/locations', z.array(z.object({ state: z.string(), cities: z.array(z.string()) })), {
      signal,
    }),
})

export type BranchInput = {
  name: string
  addressLine1: string | null
  addressLine2: string | null
  city: string
  state: string
  postalCode: string | null
  phone: string | null
  email: string | null
  headOffice: boolean
}

export function createBranch(input: BranchInput & { code: string }) {
  return apiRequest('/branches', branchSchema, { method: 'POST', body: input })
}

export function updateBranch(id: string, input: BranchInput & { version: number }) {
  return apiRequest(`/branches/${id}`, branchSchema, { method: 'PUT', body: input })
}

export function setBranchActive(id: string, active: boolean) {
  return apiRequest(`/branches/${id}/${active ? 'activate' : 'deactivate'}`, branchSchema, { method: 'POST' })
}
