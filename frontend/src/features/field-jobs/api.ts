import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

// These lists must match the backend enums in fieldops.api.

/** The workflow, in order. A job moves one step at a time. */
export const JOB_STATUSES = [
  { value: 'UNASSIGNED', label: 'Unassigned' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'ACCEPTED', label: 'Accepted' },
  { value: 'EN_ROUTE', label: 'En route' },
  { value: 'ARRIVED', label: 'Arrived' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CUSTOMER_CONFIRMED', label: 'Customer confirmed' },
  { value: 'CANCELLED', label: 'Cancelled' },
] as const

/** What the technician presses to take each step. */
export const STEP_ACTIONS: Record<string, string> = {
  ACCEPTED: 'Accept job',
  EN_ROUTE: 'Start travelling',
  ARRIVED: 'I have arrived',
  IN_PROGRESS: 'Start work',
  COMPLETED: 'Complete job',
  CUSTOMER_CONFIRMED: 'Get customer confirmation',
}

export const JOB_TYPES = [
  { value: 'JOB', label: 'Job' },
  { value: 'SITE_VISIT', label: 'Site visit' },
] as const

export function labelOf(options: readonly { value: string; label: string }[], value: string | null | undefined) {
  return options.find((option) => option.value === value)?.label ?? value ?? '—'
}

const dayFormat = new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })
const timeFormat = new Intl.DateTimeFormat('en-IN', { timeStyle: 'short' })

/** "Sat, 10 Oct, 2:00 pm", or with the end time when there is one. */
export function formatSchedule(start: string, end?: string | null) {
  const from = new Date(start)
  const text = `${dayFormat.format(from)}, ${timeFormat.format(from)}`
  return end ? `${text} to ${timeFormat.format(new Date(end))}` : text
}

const summarySchema = z.object({
  id: z.string(),
  jobNumber: z.string(),
  jobType: z.string(),
  title: z.string(),
  customerName: z.string(),
  siteAddress: z.string(),
  scheduledStart: z.string(),
  scheduledEnd: z.string().nullish(),
  technicianId: z.string().nullish(),
  technicianName: z.string().nullish(),
  status: z.string(),
  /** Still to be done, and scheduled for a day that has passed. */
  delayed: z.boolean(),
})
export type JobSummary = z.infer<typeof summarySchema>

const detailSchema = z.object({
  job: summarySchema,
  instructions: z.string().nullish(),
  customerId: z.string().nullish(),
  siteContactName: z.string().nullish(),
  siteContactPhone: z.string().nullish(),
  completionNotes: z.string().nullish(),
  signOff: z
    .object({
      signedByName: z.string(),
      remarks: z.string().nullish(),
      /** The signature as drawn: a PNG data URL. */
      signature: z.string().nullish(),
      confirmedAt: z.string().nullish(),
    })
    .nullish(),
  checklist: z.array(
    z.object({ id: z.string(), label: z.string(), done: z.boolean(), doneAt: z.string().nullish(), doneByName: z.string().nullish() }),
  ),
  materials: z.array(
    z.object({ id: z.string(), name: z.string(), quantity: z.number(), unit: z.string().nullish(), notes: z.string().nullish(), recordedByName: z.string() }),
  ),
  photos: z.array(
    z.object({ id: z.string(), fileName: z.string(), caption: z.string().nullish(), uploadedByName: z.string(), createdAt: z.string() }),
  ),
  notes: z.array(z.object({ id: z.string(), body: z.string(), authorName: z.string(), createdAt: z.string() })),
  /** The step that can be taken now, if any. */
  nextStatus: z.string().nullish(),
  /** Whether the signed-in person may take it and record work on this job. */
  canAct: z.boolean(),
  version: z.number(),
})
export type JobDetail = z.infer<typeof detailSchema>

const BASE = '/field-jobs'

export const jobKeys = {
  all: ['field-jobs'] as const,
  detail: (id: string) => ['field-jobs', 'detail', id] as const,
}

export const BUCKETS = [
  { value: 'TODAY', label: "Today's Jobs", count: 'today' },
  { value: 'UPCOMING', label: 'Upcoming Jobs', count: 'upcoming' },
  { value: 'DELAYED', label: 'Delayed Jobs', count: 'delayed' },
  { value: 'COMPLETED', label: 'Completed Jobs', count: 'completed' },
] as const
export type Bucket = (typeof BUCKETS)[number]['value']

export function myJobsQuery(bucket: Bucket) {
  return queryOptions({
    queryKey: [...jobKeys.all, 'my', bucket],
    queryFn: ({ signal }) => apiRequest(`${BASE}/my?bucket=${bucket}`, z.array(summarySchema), { signal }),
  })
}

export const myCountsQuery = queryOptions({
  queryKey: [...jobKeys.all, 'my', 'summary'],
  queryFn: ({ signal }) =>
    apiRequest(`${BASE}/my/summary`, z.object({ today: z.number(), upcoming: z.number(), delayed: z.number(), completed: z.number() }), { signal }),
})

export type JobFilters = { search?: string; status?: string; technicianId?: string; page?: number }

export function jobsQuery(filters: JobFilters) {
  return queryOptions({
    queryKey: [...jobKeys.all, 'list', filters],
    queryFn: ({ signal }) => apiRequest(`${BASE}${queryString(filters)}`, pageSchema(summarySchema), { signal }),
    placeholderData: keepPreviousData,
  })
}

export function jobQuery(id: string) {
  return queryOptions({
    queryKey: jobKeys.detail(id),
    queryFn: ({ signal }) => apiRequest(`${BASE}/${id}`, detailSchema, { signal }),
  })
}

export const techniciansQuery = queryOptions({
  queryKey: [...jobKeys.all, 'technicians'],
  queryFn: ({ signal }) => apiRequest(`${BASE}/technicians`, z.array(z.object({ id: z.string(), name: z.string() })), { signal }),
})

export type JobInput = {
  jobType: string
  title: string
  instructions: string | null
  customerName: string
  siteAddress: string
  siteContactName: string | null
  siteContactPhone: string | null
  scheduledStart: string
  scheduledEnd: string | null
  /** On create: who to give it to straight away. */
  technicianId?: string | null
  /** Null on an edit keeps the checklist as it is. */
  checklist: string[] | null
}

export function createJob(input: JobInput) {
  return apiRequest(BASE, detailSchema, { method: 'POST', body: input })
}

export function updateJob(id: string, input: JobInput & { version: number }) {
  return apiRequest(`${BASE}/${id}`, detailSchema, { method: 'PUT', body: input })
}

export function assignJob(id: string, technicianId: string) {
  return apiRequest(`${BASE}/${id}/assign`, detailSchema, { method: 'POST', body: { technicianId } })
}

export function cancelJob(id: string) {
  return apiRequest(`${BASE}/${id}/cancel`, detailSchema, { method: 'POST' })
}

export type AdvanceInput = {
  to: string
  completionNotes?: string | null
  signedByName?: string | null
  remarks?: string | null
  signature?: string | null
}

export function advanceJob(id: string, input: AdvanceInput) {
  return apiRequest(`${BASE}/${id}/advance`, detailSchema, { method: 'POST', body: input })
}

export function tickItem(id: string, itemId: string, done: boolean) {
  return apiRequest(`${BASE}/${id}/checklist/${itemId}`, detailSchema, { method: 'PUT', body: { done } })
}

export function addMaterial(id: string, input: { name: string; quantity: number; unit: string | null }) {
  return apiRequest(`${BASE}/${id}/materials`, detailSchema, { method: 'POST', body: input })
}

export function removeMaterial(id: string, materialId: string) {
  return apiRequest(`${BASE}/${id}/materials/${materialId}`, detailSchema, { method: 'DELETE' })
}

export function addNote(id: string, body: string) {
  return apiRequest(`${BASE}/${id}/notes`, detailSchema, { method: 'POST', body: { body } })
}

export function uploadPhoto(id: string, file: File, caption: string | null) {
  const body = new FormData()
  body.append('file', file)
  if (caption) {
    body.append('caption', caption)
  }
  return apiRequest(`${BASE}/${id}/photos`, detailSchema, { method: 'POST', body })
}

export function removePhoto(id: string, photoId: string) {
  return apiRequest(`${BASE}/${id}/photos/${photoId}`, detailSchema, { method: 'DELETE' })
}

export function photoPath(id: string, photoId: string) {
  return `${BASE}/${id}/photos/${photoId}/content`
}
