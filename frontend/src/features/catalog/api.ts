import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { fieldDefinitionSchema, fileReferenceSchema } from '@/components/dynamic-fields/model'
import type { Answers } from '@/components/dynamic-fields/values'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

const noContent = z.undefined()

// ---------------------------------------------------------------------------- verticals

const verticalSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  displayOrder: z.number(),
  active: z.boolean(),
  version: z.number(),
})
export type Vertical = z.infer<typeof verticalSchema>

export const catalogKeys = {
  verticals: ['catalog', 'verticals'] as const,
  categories: ['catalog', 'categories'] as const,
  services: ['catalog', 'services'] as const,
}

/** All six verticals, including inactive ones (the public endpoint lists active ones only). */
export const verticalsQuery = queryOptions({
  queryKey: catalogKeys.verticals,
  queryFn: ({ signal }) => apiRequest('/catalog/verticals', z.array(verticalSchema), { signal }),
})

export type VerticalInput = { name: string; description: string | null; displayOrder: number; version: number }

export function updateVertical(code: string, input: VerticalInput) {
  return apiRequest(`/catalog/verticals/${code}`, verticalSchema, { method: 'PUT', body: input })
}

export function setVerticalActive(code: string, active: boolean) {
  return apiRequest(`/catalog/verticals/${code}/${active ? 'activate' : 'deactivate'}`, verticalSchema, {
    method: 'POST',
  })
}

// ---------------------------------------------------------------------------- categories

const verticalRefSchema = z.object({ code: z.string(), name: z.string() })

const categorySchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  displayOrder: z.number(),
  active: z.boolean(),
  vertical: verticalRefSchema,
  version: z.number(),
})
export type Category = z.infer<typeof categorySchema>

export type CategoryFilters = { search?: string; vertical?: string; active?: string; page?: number; size?: number }

export function categoriesQuery(filters: CategoryFilters) {
  return queryOptions({
    queryKey: [...catalogKeys.categories, 'list', filters],
    queryFn: ({ signal }) =>
      apiRequest(`/catalog/categories${queryString(filters)}`, pageSchema(categorySchema), { signal }),
    // Keep showing the current rows while the next page or filter loads.
    placeholderData: keepPreviousData,
  })
}

export function categoryQuery(id: string) {
  return queryOptions({
    queryKey: [...catalogKeys.categories, 'detail', id],
    queryFn: ({ signal }) => apiRequest(`/catalog/categories/${id}`, categorySchema, { signal }),
  })
}

export type CategoryInput = { name: string; description: string | null; displayOrder: number }

export function createCategory(input: CategoryInput & { vertical: string; code: string }) {
  return apiRequest('/catalog/categories', categorySchema, { method: 'POST', body: input })
}

export function updateCategory(id: string, input: CategoryInput & { version: number }) {
  return apiRequest(`/catalog/categories/${id}`, categorySchema, { method: 'PUT', body: input })
}

export function setCategoryActive(id: string, active: boolean) {
  return apiRequest(`/catalog/categories/${id}/${active ? 'activate' : 'deactivate'}`, categorySchema, {
    method: 'POST',
  })
}

export function deleteCategory(id: string) {
  return apiRequest(`/catalog/categories/${id}`, noContent, { method: 'DELETE' })
}

// ---------------------------------------------------------------------------- services

export const BILLING_TYPES = [
  { value: 'ONE_TIME', label: 'One-time' },
  { value: 'RECURRING', label: 'Recurring' },
  { value: 'QUOTE_BASED', label: 'Quote-based' },
] as const

export function billingTypeLabel(value: string) {
  return BILLING_TYPES.find((type) => type.value === value)?.label ?? value
}

const serviceSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  displayOrder: z.number(),
  active: z.boolean(),
  billingType: z.string(),
  unitLabel: z.string().nullish(),
  basePrice: z.number().nullish(),
  requiresSiteVisit: z.boolean(),
  estimatedDurationDays: z.number().nullish(),
  category: z.object({ id: z.string(), code: z.string(), name: z.string() }),
  vertical: verticalRefSchema,
  version: z.number(),
})
export type Service = z.infer<typeof serviceSchema>

export type ServiceFilters = {
  search?: string
  vertical?: string
  categoryId?: string
  active?: string
  billingType?: string
  page?: number
  size?: number
}

export function servicesQuery(filters: ServiceFilters) {
  return queryOptions({
    queryKey: [...catalogKeys.services, 'list', filters],
    queryFn: ({ signal }) =>
      apiRequest(`/catalog/services${queryString(filters)}`, pageSchema(serviceSchema), { signal }),
    placeholderData: keepPreviousData,
  })
}

export function serviceQuery(id: string) {
  return queryOptions({
    queryKey: [...catalogKeys.services, 'detail', id],
    queryFn: ({ signal }) => apiRequest(`/catalog/services/${id}`, serviceSchema, { signal }),
  })
}

export type ServiceInput = {
  categoryId: string
  name: string
  description: string | null
  displayOrder: number
  billingType: string
  unitLabel: string | null
  basePrice: number | null
  requiresSiteVisit: boolean
  estimatedDurationDays: number | null
}

export function createService(input: ServiceInput & { code: string }) {
  return apiRequest('/catalog/services', serviceSchema, { method: 'POST', body: input })
}

export function updateService(id: string, input: ServiceInput & { version: number }) {
  return apiRequest(`/catalog/services/${id}`, serviceSchema, { method: 'PUT', body: input })
}

export function setServiceActive(id: string, active: boolean) {
  return apiRequest(`/catalog/services/${id}/${active ? 'activate' : 'deactivate'}`, serviceSchema, {
    method: 'POST',
  })
}

export function deleteService(id: string) {
  return apiRequest(`/catalog/services/${id}`, noContent, { method: 'DELETE' })
}

// ---------------------------------------------------------------------------- custom lead fields

const customFieldSchema = z.object({ field: fieldDefinitionSchema, version: z.number() })
export type CustomField = z.infer<typeof customFieldSchema>

function fieldsPath(serviceId: string) {
  return `/catalog/services/${serviceId}/custom-fields`
}

export function customFieldsKey(serviceId: string) {
  return [...catalogKeys.services, 'detail', serviceId, 'custom-fields'] as const
}

/** Every field of the service, switched on or off, in display order. */
export function customFieldsQuery(serviceId: string) {
  return queryOptions({
    queryKey: customFieldsKey(serviceId),
    queryFn: ({ signal }) => apiRequest(fieldsPath(serviceId), z.array(customFieldSchema), { signal }),
  })
}

/** Settings that do not apply to the field's type are null. */
export type CustomFieldInput = {
  label: string
  helpText: string | null
  required: boolean
  options: { value: string; label: string }[] | null
  minValue: number | null
  maxValue: number | null
  maxLength: number | null
  unit: string | null
  allowedFileTypes: string[] | null
  maxFileSizeMb: number | null
  displayOrder: number | null
}

export function createCustomField(serviceId: string, input: CustomFieldInput & { key: string; type: string }) {
  return apiRequest(fieldsPath(serviceId), customFieldSchema, { method: 'POST', body: input })
}

export function updateCustomField(serviceId: string, fieldId: string, input: CustomFieldInput & { version: number }) {
  return apiRequest(`${fieldsPath(serviceId)}/${fieldId}`, customFieldSchema, { method: 'PUT', body: input })
}

export function setCustomFieldActive(serviceId: string, fieldId: string, active: boolean) {
  return apiRequest(`${fieldsPath(serviceId)}/${fieldId}/${active ? 'activate' : 'deactivate'}`, customFieldSchema, {
    method: 'POST',
  })
}

export function deleteCustomField(serviceId: string, fieldId: string) {
  return apiRequest(`${fieldsPath(serviceId)}/${fieldId}`, noContent, { method: 'DELETE' })
}

/** Asks the server whether a set of answers is acceptable, without saving anything. */
export function validateAnswers(serviceId: string, values: Answers) {
  return apiRequest(`${fieldsPath(serviceId)}/validate`, z.object({ values: z.record(z.string(), z.unknown()) }), {
    method: 'POST',
    body: { values },
  })
}

/** Uploads a file for a FILE field and returns the reference to use as its answer. */
export function uploadFieldFile(serviceId: string, fieldKey: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest(`${fieldsPath(serviceId)}/${fieldKey}/files`, fileReferenceSchema, { method: 'POST', body })
}
