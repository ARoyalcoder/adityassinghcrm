import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiDownload, apiRequest } from '@/lib/api/client'
import { timelinePageSchema, timelineQueryString } from '@/components/timeline/model'
import { pageSchema, queryString } from '@/lib/api/page'

const noContent = z.undefined()

// These lists must match the backend enums in customer.api.

export const CUSTOMER_TYPES = [
  { value: 'INDIVIDUAL', label: 'Individual' },
  { value: 'BUSINESS', label: 'Business' },
] as const

export const CUSTOMER_STATUSES = [
  { value: 'PROSPECT', label: 'Prospect' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'BLOCKED', label: 'Blocked' },
] as const

export const CUSTOMER_SOURCES = [
  { value: 'WALK_IN', label: 'Walk-in' },
  { value: 'REFERRAL', label: 'Referral' },
  { value: 'WEBSITE', label: 'Website' },
  { value: 'PHONE_CALL', label: 'Phone call' },
  { value: 'SOCIAL_MEDIA', label: 'Social media' },
  { value: 'ADVERTISEMENT', label: 'Advertisement' },
  { value: 'PARTNER', label: 'Partner' },
  { value: 'OTHER', label: 'Other' },
] as const

export const ADDRESS_TYPES = [
  { value: 'BILLING', label: 'Billing' },
  { value: 'SERVICE', label: 'Service' },
  { value: 'OTHER', label: 'Other' },
] as const

export function labelOf(options: readonly { value: string; label: string }[], value: string | null | undefined) {
  return options.find((option) => option.value === value)?.label ?? value ?? '—'
}

const assigneeSchema = z.object({ id: z.string(), name: z.string() })

const summarySchema = z.object({
  id: z.string(),
  customerNumber: z.string(),
  type: z.string(),
  displayName: z.string(),
  email: z.string().nullish(),
  phone: z.string().nullish(),
  status: z.string(),
  source: z.string().nullish(),
  assignedTo: assigneeSchema.nullish(),
  tags: z.array(z.string()),
  createdAt: z.string(),
})
export type CustomerSummary = z.infer<typeof summarySchema>

const detailSchema = summarySchema.extend({
  firstName: z.string().nullish(),
  lastName: z.string().nullish(),
  companyName: z.string().nullish(),
  taxId: z.string().nullish(),
  contactCount: z.number(),
  addressCount: z.number(),
  version: z.number(),
  updatedAt: z.string(),
})
export type CustomerDetail = z.infer<typeof detailSchema>

const contactSchema = z.object({
  id: z.string(),
  name: z.string(),
  designation: z.string().nullish(),
  email: z.string().nullish(),
  phone: z.string().nullish(),
  primaryContact: z.boolean(),
  version: z.number(),
})
export type Contact = z.infer<typeof contactSchema>

const addressSchema = z.object({
  id: z.string(),
  type: z.string(),
  label: z.string().nullish(),
  line1: z.string(),
  line2: z.string().nullish(),
  city: z.string(),
  state: z.string(),
  postalCode: z.string().nullish(),
  countryCode: z.string(),
  defaultAddress: z.boolean(),
  version: z.number(),
})
export type Address = z.infer<typeof addressSchema>

const noteSchema = z.object({ id: z.string(), body: z.string(), authorName: z.string(), createdAt: z.string() })
export type Note = z.infer<typeof noteSchema>

const duplicateSchema = z.object({
  id: z.string(),
  customerNumber: z.string(),
  displayName: z.string(),
  email: z.string().nullish(),
  phone: z.string().nullish(),
  status: z.string(),
  matchedOn: z.array(z.string()),
})
export type DuplicateMatch = z.infer<typeof duplicateSchema>

export const customerKeys = {
  all: ['customers'] as const,
  detail: (id: string) => ['customers', 'detail', id] as const,
}

export type CustomerFilters = {
  search?: string
  type?: string
  status?: string
  source?: string
  assignedTo?: string
  tagId?: string
  sort?: string
  page?: number
}

export function customersQuery(filters: CustomerFilters) {
  return queryOptions({
    queryKey: [...customerKeys.all, 'list', filters],
    queryFn: ({ signal }) => apiRequest(`/customers${queryString(filters)}`, pageSchema(summarySchema), { signal }),
    placeholderData: keepPreviousData,
  })
}

/** Downloads the customers matching the filters (not just the visible page) as a CSV file. */
export function exportCustomers(filters: CustomerFilters) {
  return apiDownload(`/customers/export${queryString({ ...filters, page: undefined })}`, 'customers.csv')
}

export function customerQuery(id: string) {
  return queryOptions({
    queryKey: customerKeys.detail(id),
    queryFn: ({ signal }) => apiRequest(`/customers/${id}`, detailSchema, { signal }),
  })
}

export const customerTagsQuery = queryOptions({
  queryKey: [...customerKeys.all, 'tags'],
  queryFn: ({ signal }) =>
    apiRequest('/customers/tags', z.array(z.object({ id: z.string(), name: z.string() })), { signal }),
})

export const assigneesQuery = queryOptions({
  queryKey: [...customerKeys.all, 'assignees'],
  queryFn: ({ signal }) => apiRequest('/customers/assignees', z.array(assigneeSchema), { signal }),
})

export type CustomerInput = {
  type: string
  firstName: string | null
  lastName: string | null
  companyName: string | null
  taxId: string | null
  email: string | null
  phone: string | null
  status: string
  source: string | null
  assignedUserId: string | null
  tags: string[]
  /** The user has seen the possible duplicates and wants to save anyway. */
  confirmDuplicates: boolean
}

export function createCustomer(input: CustomerInput) {
  return apiRequest('/customers', detailSchema, { method: 'POST', body: input })
}

export function updateCustomer(id: string, input: CustomerInput & { version: number }) {
  return apiRequest(`/customers/${id}`, detailSchema, { method: 'PUT', body: input })
}

export function checkDuplicates(input: {
  phone: string | null
  email: string | null
  companyName: string | null
  excludeCustomerId?: string
}) {
  return apiRequest('/customers/duplicate-check', z.array(duplicateSchema), { method: 'POST', body: input })
}

// ---------------------------------------------------------------------------- contacts

export function contactsQuery(customerId: string) {
  return queryOptions({
    queryKey: [...customerKeys.detail(customerId), 'contacts'],
    queryFn: ({ signal }) => apiRequest(`/customers/${customerId}/contacts`, z.array(contactSchema), { signal }),
  })
}

export type ContactInput = {
  name: string
  designation: string | null
  email: string | null
  phone: string | null
  primaryContact: boolean
}

export function saveContact(customerId: string, input: ContactInput, existing?: Contact) {
  return existing
    ? apiRequest(`/customers/${customerId}/contacts/${existing.id}`, contactSchema, {
        method: 'PUT',
        body: { ...input, version: existing.version },
      })
    : apiRequest(`/customers/${customerId}/contacts`, contactSchema, { method: 'POST', body: input })
}

export function removeContact(customerId: string, contactId: string) {
  return apiRequest(`/customers/${customerId}/contacts/${contactId}`, noContent, { method: 'DELETE' })
}

// ---------------------------------------------------------------------------- addresses

export function addressesQuery(customerId: string) {
  return queryOptions({
    queryKey: [...customerKeys.detail(customerId), 'addresses'],
    queryFn: ({ signal }) => apiRequest(`/customers/${customerId}/addresses`, z.array(addressSchema), { signal }),
  })
}

export type AddressInput = {
  type: string
  label: string | null
  line1: string
  line2: string | null
  city: string
  state: string
  postalCode: string | null
  defaultAddress: boolean
}

export function saveAddress(customerId: string, input: AddressInput, existing?: Address) {
  return existing
    ? apiRequest(`/customers/${customerId}/addresses/${existing.id}`, addressSchema, {
        method: 'PUT',
        body: { ...input, version: existing.version },
      })
    : apiRequest(`/customers/${customerId}/addresses`, addressSchema, { method: 'POST', body: input })
}

export function removeAddress(customerId: string, addressId: string) {
  return apiRequest(`/customers/${customerId}/addresses/${addressId}`, noContent, { method: 'DELETE' })
}

// ---------------------------------------------------------------------------- notes

export function notesQuery(customerId: string) {
  return queryOptions({
    queryKey: [...customerKeys.detail(customerId), 'notes'],
    queryFn: ({ signal }) =>
      apiRequest(`/customers/${customerId}/notes?size=50`, pageSchema(noteSchema), { signal }),
  })
}

export function addNote(customerId: string, body: string) {
  return apiRequest(`/customers/${customerId}/notes`, noteSchema, { method: 'POST', body: { body } })
}

export function removeNote(customerId: string, noteId: string) {
  return apiRequest(`/customers/${customerId}/notes/${noteId}`, noContent, { method: 'DELETE' })
}

// ---------------------------------------------------------------------------- interactions

// Must match the backend enums customer.api.InteractionType and InteractionDirection.

export const INTERACTION_TYPES = [
  { value: 'CALL', label: 'Call' },
  { value: 'MEETING', label: 'Meeting' },
  { value: 'EMAIL', label: 'Email' },
  { value: 'SITE_VISIT', label: 'Site visit' },
  { value: 'MESSAGE', label: 'Message' },
  { value: 'OTHER', label: 'Other' },
] as const

export const INTERACTION_DIRECTIONS = [
  { value: 'OUTBOUND', label: 'Outgoing' },
  { value: 'INBOUND', label: 'Incoming' },
] as const

const interactionSchema = z.object({
  id: z.string(),
  type: z.string(),
  direction: z.string().nullish(),
  occurredAt: z.string(),
  subject: z.string(),
  details: z.string().nullish(),
  outcome: z.string().nullish(),
  loggedByName: z.string(),
  createdAt: z.string(),
})
export type Interaction = z.infer<typeof interactionSchema>

export function interactionsQuery(customerId: string) {
  return queryOptions({
    queryKey: [...customerKeys.detail(customerId), 'interactions'],
    queryFn: ({ signal }) =>
      apiRequest(`/customers/${customerId}/interactions?size=50`, pageSchema(interactionSchema), { signal }),
  })
}

export type InteractionInput = {
  type: string
  direction: string | null
  /** ISO-8601; null means "just now". */
  occurredAt: string | null
  subject: string
  details: string | null
  outcome: string | null
}

export function logInteraction(customerId: string, input: InteractionInput) {
  return apiRequest(`/customers/${customerId}/interactions`, interactionSchema, { method: 'POST', body: input })
}

export function removeInteraction(customerId: string, interactionId: string) {
  return apiRequest(`/customers/${customerId}/interactions/${interactionId}`, noContent, { method: 'DELETE' })
}

// ---------------------------------------------------------------------------- customer 360

const healthSignalSchema = z.object({
  code: z.string(),
  /** POSITIVE, WARNING or CRITICAL. */
  impact: z.string(),
  message: z.string(),
})
export type HealthSignal = z.infer<typeof healthSignalSchema>

const overviewSchema = z.object({
  health: z.object({
    /** GOOD, NEEDS_ATTENTION, AT_RISK or UNKNOWN. */
    level: z.string(),
    signals: z.array(healthSignalSchema),
  }),
  summary: z.object({
    customerSince: z.string(),
    lastContactAt: z.string().nullish(),
    lastContactSubject: z.string().nullish(),
    contactCount: z.number(),
    addressCount: z.number(),
    interactionCount: z.number(),
    noteCount: z.number(),
    primaryContact: z
      .object({
        name: z.string(),
        designation: z.string().nullish(),
        phone: z.string().nullish(),
        email: z.string().nullish(),
      })
      .nullish(),
    billingAddress: z.string().nullish(),
    serviceAddress: z.string().nullish(),
  }),
})
export type CustomerOverview = z.infer<typeof overviewSchema>

/** Health and summary facts, worked out by the server from the customer's current records. */
export function customerOverviewQuery(customerId: string) {
  return queryOptions({
    queryKey: [...customerKeys.detail(customerId), 'overview'],
    queryFn: ({ signal }) => apiRequest(`/customers/${customerId}/overview`, overviewSchema, { signal }),
  })
}

/** The key every query of this customer's timeline starts with. */
export function customerTimelineKey(customerId: string) {
  return [...customerKeys.detail(customerId), 'timeline'] as const
}

/** One page of the customer's timeline: everything that has happened, from every module, newest first. */
export function fetchCustomerTimeline(
  customerId: string,
  request: { cursor?: string | null; categories?: string[]; limit?: number; signal?: AbortSignal },
) {
  return apiRequest(`/customers/${customerId}/timeline${timelineQueryString(request)}`, timelinePageSchema, {
    signal: request.signal,
  })
}
