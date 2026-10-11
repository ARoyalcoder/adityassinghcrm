import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

/** Must match the backend's AuditAction enum. */
export const AUDIT_ACTIONS = [
  { value: 'CREATE', label: 'Create' },
  { value: 'UPDATE', label: 'Update' },
  { value: 'DELETE', label: 'Delete' },
  { value: 'STATUS_CHANGE', label: 'Status change' },
  { value: 'LOGIN', label: 'Login' },
  { value: 'LOGOUT', label: 'Logout' },
  { value: 'APPROVAL', label: 'Approval' },
  { value: 'PAYMENT', label: 'Payment' },
  { value: 'EXPORT', label: 'Export' },
  { value: 'IMPORT', label: 'Import' },
] as const

export function actionLabel(action: string) {
  return AUDIT_ACTIONS.find((item) => item.value === action)?.label ?? action
}

const entrySchema = z.object({
  id: z.string(),
  occurredAt: z.string(),
  actorId: z.string().nullish(),
  actorLabel: z.string(),
  action: z.string(),
  module: z.string(),
  entityType: z.string().nullish(),
  entityId: z.string().nullish(),
  entityLabel: z.string().nullish(),
  summary: z.string(),
  ipAddress: z.string().nullish(),
})
export type AuditEntry = z.infer<typeof entrySchema>

const valuesSchema = z.record(z.unknown()).nullish()

const detailSchema = z.object({
  entry: entrySchema,
  before: valuesSchema,
  after: valuesSchema,
  metadata: valuesSchema,
  userAgent: z.string().nullish(),
  requestId: z.string().nullish(),
})
export type AuditDetail = z.infer<typeof detailSchema>

const facetsSchema = z.object({
  modules: z.array(z.string()),
  entityTypes: z.array(z.string()),
  actors: z.array(z.object({ id: z.string(), label: z.string() })),
})

const activitySchema = z.object({
  id: z.string(),
  occurredAt: z.string(),
  action: z.string(),
  actorLabel: z.string(),
  message: z.string(),
  changes: z.array(z.object({ field: z.string(), from: z.unknown(), to: z.unknown() })).nullish(),
  auditLogId: z.string().nullish(),
})
export type Activity = z.infer<typeof activitySchema>

export type AuditFilters = {
  search?: string
  actorId?: string
  module?: string
  action?: string
  entityType?: string
  entityId?: string
  /** ISO-8601 instants: `from` inclusive, `to` exclusive. */
  from?: string
  to?: string
  page?: number
}

const auditKeys = { all: ['audit'] as const }

export function auditLogsQuery(filters: AuditFilters) {
  return queryOptions({
    queryKey: [...auditKeys.all, 'logs', filters],
    queryFn: ({ signal }) => apiRequest(`/audit/logs${queryString(filters)}`, pageSchema(entrySchema), { signal }),
    placeholderData: keepPreviousData,
    // The log grows while you look at it; do not keep showing an old first page.
    staleTime: 0,
  })
}

export function auditLogQuery(id: string) {
  return queryOptions({
    queryKey: [...auditKeys.all, 'log', id],
    queryFn: ({ signal }) => apiRequest(`/audit/logs/${id}`, detailSchema, { signal }),
    // An audit event never changes once written.
    staleTime: Infinity,
  })
}

export const auditFacetsQuery = queryOptions({
  queryKey: [...auditKeys.all, 'facets'],
  queryFn: ({ signal }) => apiRequest('/audit/facets', facetsSchema, { signal }),
})

export function activityQuery(entityType: string, entityId: string) {
  return queryOptions({
    queryKey: [...auditKeys.all, 'activity', entityType, entityId],
    queryFn: ({ signal }) =>
      apiRequest(`/audit/activity${queryString({ entityType, entityId, size: 20 })}`, pageSchema(activitySchema), {
        signal,
      }),
    staleTime: 0,
  })
}

/**
 * The date inputs give calendar days in the viewer's own time zone; the API wants exact instants.
 * "From 5 Oct" means from the start of that day here, "to 5 Oct" means up to the end of it.
 */
export function dayStart(date: string): string | undefined {
  return date ? new Date(`${date}T00:00:00`).toISOString() : undefined
}

export function dayEnd(date: string): string | undefined {
  if (!date) {
    return undefined
  }
  const next = new Date(`${date}T00:00:00`)
  next.setDate(next.getDate() + 1)
  return next.toISOString()
}

const dateTime = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'medium' })

export function formatDateTime(instant: string) {
  return dateTime.format(new Date(instant))
}

/** Shows one stored value: text as it is, everything else as compact JSON, nothing as a dash. */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  return typeof value === 'string' ? value : JSON.stringify(value)
}
