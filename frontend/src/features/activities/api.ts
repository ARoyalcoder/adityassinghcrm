import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

const noContent = z.undefined()

export const ACTIVITY_TYPES = [
  { value: 'CALL', label: 'Call' },
  { value: 'MEETING', label: 'Meeting' },
  { value: 'WHATSAPP', label: 'WhatsApp' },
  { value: 'EMAIL', label: 'Email' },
  { value: 'SITE_VISIT', label: 'Site Visit' },
  { value: 'FOLLOW_UP', label: 'Follow-up' },
  { value: 'NOTE', label: 'Note' },
  { value: 'TASK', label: 'Task' },
] as const

export const ENTITY_TYPES = [
  { value: 'CUSTOMER', label: 'Customer' },
  { value: 'LEAD', label: 'Lead' },
  { value: 'OPPORTUNITY', label: 'Opportunity' },
  { value: 'QUOTATION', label: 'Quotation' },
  { value: 'PROJECT', label: 'Project' },
  { value: 'TICKET', label: 'Ticket' },
] as const

export const TASK_PRIORITIES = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'URGENT', label: 'Urgent' },
] as const

export const TASK_STATUSES = [
  { value: 'PENDING', label: 'To Do' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
] as const

export const RECURRENCE_PATTERNS = [
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'BIWEEKLY', label: 'Every 2 Weeks' },
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'YEARLY', label: 'Yearly' },
] as const

export const INTERACTION_DIRECTIONS = [
  { value: 'INBOUND', label: 'Inbound' },
  { value: 'OUTBOUND', label: 'Outbound' },
] as const

export function labelOf(options: readonly { value: string; label: string }[], value: string | null | undefined) {
  return options.find((option) => option.value === value)?.label ?? value ?? '—'
}

export const activityDetailSchema = z.object({
  id: z.string(),
  type: z.enum(['CALL', 'MEETING', 'WHATSAPP', 'EMAIL', 'SITE_VISIT', 'FOLLOW_UP', 'NOTE', 'TASK']),
  title: z.string(),
  description: z.string().nullish(),
  entityType: z.enum(['CUSTOMER', 'LEAD', 'OPPORTUNITY', 'QUOTATION', 'PROJECT', 'TICKET']),
  entityId: z.string(),
  entityName: z.string().nullish(),
  ownerId: z.string(),
  ownerName: z.string(),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']),
  status: z.enum(['PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']),
  dueDate: z.string().nullish(),
  occurredAt: z.string(),
  completedAt: z.string().nullish(),
  completedBy: z.string().nullish(),
  outcome: z.string().nullish(),
  direction: z.enum(['INBOUND', 'OUTBOUND']).nullish(),
  reminderAt: z.string().nullish(),
  reminderStatus: z.enum(['NONE', 'PENDING', 'TRIGGERED', 'DISMISSED']),
  isRecurring: z.boolean(),
  recurrencePattern: z.enum(['DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'YEARLY']).nullish(),
  recurrenceInterval: z.number().nullish(),
  recurrenceEndAt: z.string().nullish(),
  parentActivityId: z.string().nullish(),
  createdAt: z.string(),
  createdBy: z.string(),
  updatedAt: z.string(),
  version: z.number(),
})

export type ActivityDetail = z.infer<typeof activityDetailSchema>

export const activityCountsSchema = z.object({
  todaysWorkCount: z.number(),
  myTasksCount: z.number(),
  overdueCount: z.number(),
  upcomingCount: z.number(),
  completedCount: z.number(),
  totalOpenCount: z.number(),
})

export type ActivityCounts = z.infer<typeof activityCountsSchema>

export type ActivityFilters = {
  type?: string | null
  entityType?: string | null
  entityId?: string | null
  ownerId?: string | null
  priority?: string | null
  status?: string | null
  view?: string | null
  search?: string | null
}

export const activityKeys = {
  all: ['activities'] as const,
  lists: () => [...activityKeys.all, 'list'] as const,
  list: (params: { filters: ActivityFilters; page: number; size: number; sort?: string }) =>
    [...activityKeys.lists(), params] as const,
  counts: () => [...activityKeys.all, 'counts'] as const,
  details: () => [...activityKeys.all, 'detail'] as const,
  detail: (id: string) => [...activityKeys.details(), id] as const,
}

export function activitiesQuery(params: {
  filters: ActivityFilters
  page: number
  size: number
  sort?: string
}) {
  const query = queryString({
    type: params.filters.type,
    entityType: params.filters.entityType,
    entityId: params.filters.entityId,
    ownerId: params.filters.ownerId,
    priority: params.filters.priority,
    status: params.filters.status,
    view: params.filters.view,
    search: params.filters.search,
    page: params.page,
    size: params.size,
    sort: params.sort,
  })

  return queryOptions({
    queryKey: activityKeys.list(params),
    queryFn: () => apiRequest(`/activities${query}`, pageSchema(activityDetailSchema)),
    placeholderData: keepPreviousData,
  })
}

export function activityCountsQuery() {
  return queryOptions({
    queryKey: activityKeys.counts(),
    queryFn: () => apiRequest('/activities/counts', activityCountsSchema),
    refetchInterval: 30_000,
  })
}

export function activityDetailQuery(id: string) {
  return queryOptions({
    queryKey: activityKeys.detail(id),
    queryFn: () => apiRequest(`/activities/${id}`, activityDetailSchema),
  })
}

export type CreateActivityPayload = {
  type: string
  title: string
  description?: string | null
  entityType: string
  entityId: string
  entityName?: string | null
  ownerId?: string | null
  priority?: string | null
  status?: string | null
  dueDate?: string | null
  occurredAt?: string | null
  direction?: string | null
  reminderAt?: string | null
  isRecurring?: boolean
  recurrencePattern?: string | null
  recurrenceInterval?: number | null
  recurrenceEndAt?: string | null
}

export function createActivity(payload: CreateActivityPayload) {
  return apiRequest('/activities', activityDetailSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export type UpdateActivityPayload = Partial<CreateActivityPayload>

export function updateActivity(id: string, payload: UpdateActivityPayload) {
  return apiRequest(`/activities/${id}`, activityDetailSchema, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function completeActivity(id: string, payload?: { outcome?: string | null }) {
  return apiRequest(`/activities/${id}/complete`, activityDetailSchema, {
    method: 'POST',
    body: JSON.stringify(payload ?? {}),
  })
}

export function rescheduleActivity(id: string, payload: { dueDate: string; reminderAt?: string | null }) {
  return apiRequest(`/activities/${id}/reschedule`, activityDetailSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function dismissReminder(id: string) {
  return apiRequest(`/activities/${id}/reminders/dismiss`, activityDetailSchema, {
    method: 'POST',
  })
}

export function deleteActivity(id: string) {
  return apiRequest(`/activities/${id}`, noContent, {
    method: 'DELETE',
  })
}
