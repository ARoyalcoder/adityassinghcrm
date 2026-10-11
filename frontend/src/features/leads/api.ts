import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { fieldDefinitionSchema, fileReferenceSchema } from '@/components/dynamic-fields/model'
import type { Answers } from '@/components/dynamic-fields/values'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

const noContent = z.undefined()

// These lists must match the backend enums in lead.api.

export const LEAD_STATUSES = [
  { value: 'OPEN', label: 'Open' },
  { value: 'WON', label: 'Won' },
  { value: 'LOST', label: 'Lost' },
] as const

export const LEAD_PRIORITIES = [
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
] as const

export const LEAD_HEALTHS = [
  { value: 'GOOD', label: 'Good' },
  { value: 'NEEDS_ATTENTION', label: 'Needs attention' },
  { value: 'AT_RISK', label: 'At risk' },
] as const

export const QUALIFICATION_STATUSES = [
  { value: 'NOT_STARTED', label: 'Not started' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'QUALIFIED', label: 'Qualified' },
  { value: 'DISQUALIFIED', label: 'Disqualified' },
] as const

export const ACTIVITY_TYPES = [
  { value: 'CALL', label: 'Call' },
  { value: 'MEETING', label: 'Meeting' },
  { value: 'EMAIL', label: 'Email' },
  { value: 'SITE_VISIT', label: 'Site visit' },
  { value: 'MESSAGE', label: 'Message' },
  { value: 'OTHER', label: 'Other' },
] as const

export function labelOf(options: readonly { value: string; label: string }[], value: string | null | undefined) {
  return options.find((option) => option.value === value)?.label ?? value ?? '—'
}

const summarySchema = z.object({
  id: z.string(),
  leadNumber: z.string(),
  contactName: z.string(),
  companyName: z.string().nullish(),
  email: z.string().nullish(),
  phone: z.string().nullish(),
  service: z.object({ id: z.string(), name: z.string(), verticalName: z.string().nullish() }),
  estimatedValue: z.number().nullish(),
  status: z.string(),
  score: z.number(),
  priority: z.string(),
  health: z.string(),
  qualificationStatus: z.string(),
  createdAt: z.string(),
})
export type LeadSummary = z.infer<typeof summarySchema>

const detailSchema = summarySchema.extend({
  closedAt: z.string().nullish(),
  /** The answers to the service's own fields, each with the definition it answers. */
  customFields: z.array(z.object({ field: fieldDefinitionSchema, value: z.unknown() })),
  version: z.number(),
  updatedAt: z.string(),
})
export type LeadDetail = z.infer<typeof detailSchema>

const factorSchema = z.object({
  code: z.string(),
  /** VALUE, ENGAGEMENT, QUALIFICATION or PROFILE. */
  category: z.string(),
  label: z.string(),
  points: z.number(),
})
export type ScoreFactor = z.infer<typeof factorSchema>

const questionSchema = z.object({
  id: z.string(),
  key: z.string(),
  label: z.string(),
  helpText: z.string().nullish(),
  required: z.boolean(),
  options: z.array(z.object({ value: z.string(), label: z.string() })),
  answer: z.string().nullish(),
})
export type QualificationQuestion = z.infer<typeof questionSchema>

/** Why a lead scores what it does, and what follows from it. Worked out by the server as of now. */
const qualificationSchema = z.object({
  score: z.number(),
  /** The sum of the factors; differs from the score only when it fell outside 0..100. */
  total: z.number(),
  factors: z.array(factorSchema),
  /** Which scorer produced the score, e.g. "rules-v1". */
  engine: z.string(),
  priority: z.string(),
  priorityReason: z.string(),
  health: z.string(),
  healthReason: z.string(),
  qualificationStatus: z.string(),
  qualificationReason: z.string(),
  computedAt: z.string(),
  questions: z.array(questionSchema),
})
export type Qualification = z.infer<typeof qualificationSchema>

const activitySchema = z.object({
  id: z.string(),
  type: z.string(),
  occurredAt: z.string(),
  subject: z.string(),
  loggedByName: z.string(),
})

export const leadKeys = {
  all: ['leads'] as const,
  detail: (id: string) => ['leads', 'detail', id] as const,
  questions: ['leads', 'qualification-questions'] as const,
}

export type LeadFilters = {
  search?: string
  status?: string
  priority?: string
  health?: string
  qualificationStatus?: string
  sort?: string
  page?: number
}

export function leadsQuery(filters: LeadFilters) {
  return queryOptions({
    queryKey: [...leadKeys.all, 'list', filters],
    queryFn: ({ signal }) => apiRequest(`/leads${queryString(filters)}`, pageSchema(summarySchema), { signal }),
    placeholderData: keepPreviousData,
  })
}

export function leadQuery(id: string) {
  return queryOptions({
    queryKey: leadKeys.detail(id),
    queryFn: ({ signal }) => apiRequest(`/leads/${id}`, detailSchema, { signal }),
  })
}

export type LeadInput = {
  contactName: string
  companyName: string | null
  email: string | null
  phone: string | null
  serviceId: string
  estimatedValue: number | null
  status: string
  /** Answers to the service's own fields. */
  customFields: Answers
}

export function createLead(input: LeadInput) {
  return apiRequest('/leads', detailSchema, { method: 'POST', body: input })
}

export function updateLead(id: string, input: LeadInput & { version: number }) {
  return apiRequest(`/leads/${id}`, detailSchema, { method: 'PUT', body: input })
}

export function deleteLead(id: string) {
  return apiRequest(`/leads/${id}`, noContent, { method: 'DELETE' })
}

/** Uploads a file for one of the FILE fields of a service and returns the reference to send as its answer. */
export function uploadLeadFieldFile(serviceId: string, fieldKey: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest(`/leads/custom-field-files${queryString({ serviceId, fieldKey })}`, fileReferenceSchema, {
    method: 'POST',
    body,
  })
}

// ---------------------------------------------------------------------------- qualification

export function qualificationQuery(leadId: string) {
  return queryOptions({
    queryKey: [...leadKeys.detail(leadId), 'qualification'],
    queryFn: ({ signal }) => apiRequest(`/leads/${leadId}/qualification`, qualificationSchema, { signal }),
    // The explanation depends on the time of day as well as on the lead, so it is not reused for long.
    staleTime: 60_000,
  })
}

/** Records answers (question key to option value; null clears one) and returns the new assessment. */
export function answerQuestions(leadId: string, answers: Record<string, string | null>) {
  return apiRequest(`/leads/${leadId}/qualification/answers`, qualificationSchema, {
    method: 'PUT',
    body: { answers },
  })
}

// ---------------------------------------------------------------------------- activity

export function leadActivitiesQuery(leadId: string) {
  return queryOptions({
    queryKey: [...leadKeys.detail(leadId), 'activities'],
    queryFn: ({ signal }) => apiRequest(`/leads/${leadId}/activities`, z.array(activitySchema), { signal }),
  })
}

export function logLeadActivity(leadId: string, input: { type: string; subject: string }) {
  return apiRequest(`/leads/${leadId}/activities`, activitySchema, { method: 'POST', body: input })
}

// ---------------------------------------------------------------------------- question administration

const adminOptionSchema = z.object({
  value: z.string(),
  label: z.string(),
  points: z.number(),
  disqualifies: z.boolean(),
})

const adminQuestionSchema = z.object({
  id: z.string(),
  key: z.string(),
  label: z.string(),
  helpText: z.string().nullish(),
  options: z.array(adminOptionSchema),
  required: z.boolean(),
  displayOrder: z.number(),
  active: z.boolean(),
  version: z.number(),
})
export type AdminQuestion = z.infer<typeof adminQuestionSchema>

const QUESTIONS = '/lead-qualification/questions'

export const adminQuestionsQuery = queryOptions({
  queryKey: leadKeys.questions,
  queryFn: ({ signal }) => apiRequest(QUESTIONS, z.array(adminQuestionSchema), { signal }),
})

export type QuestionInput = {
  label: string
  helpText: string | null
  options: { value: string; label: string; points: number; disqualifies: boolean }[]
  required: boolean
  displayOrder: number | null
}

export function createQuestion(input: QuestionInput & { key: string }) {
  return apiRequest(QUESTIONS, adminQuestionSchema, { method: 'POST', body: input })
}

export function updateQuestion(id: string, input: QuestionInput & { version: number }) {
  return apiRequest(`${QUESTIONS}/${id}`, adminQuestionSchema, { method: 'PUT', body: input })
}

export function setQuestionActive(id: string, active: boolean) {
  return apiRequest(`${QUESTIONS}/${id}/${active ? 'activate' : 'deactivate'}`, adminQuestionSchema, { method: 'POST' })
}

export function deleteQuestion(id: string) {
  return apiRequest(`${QUESTIONS}/${id}`, noContent, { method: 'DELETE' })
}
