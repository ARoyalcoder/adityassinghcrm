import { queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'

const noContent = z.undefined()

const taskSchema = z.object({
  title: z.string(),
  description: z.string().nullish(),
  /** A role code; when absent the stage's role is responsible. */
  responsibleRole: z.string().nullish(),
  estimatedHours: z.number().nullish(),
  checklist: z.array(z.string()),
})

const documentSchema = z.object({ name: z.string(), required: z.boolean(), description: z.string().nullish() })

/** One stage as an administrator defined it. Nothing in the app knows any particular stage. */
const stageSchema = z.object({
  key: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  /** Estimated duration in calendar days. */
  durationDays: z.number(),
  responsibleRole: z.string().nullish(),
  /** Keys of the stages that must finish before this one starts. */
  dependsOn: z.array(z.string()),
  milestones: z.array(z.string()),
  tasks: z.array(taskSchema),
  documents: z.array(documentSchema),
})
export type TemplateStage = z.infer<typeof stageSchema>

/** A stage placed in time by the server, in days from the start of the project. */
const scheduledStageSchema = z.object({ stage: stageSchema, startDay: z.number(), endDay: z.number() })
export type ScheduledStage = z.infer<typeof scheduledStageSchema>

const serviceRefSchema = z.object({ id: z.string(), name: z.string(), verticalName: z.string().nullish() })

const summarySchema = z.object({
  id: z.string(),
  service: serviceRefSchema,
  name: z.string(),
  active: z.boolean(),
  stageCount: z.number(),
  totalDays: z.number(),
  version: z.number(),
})
export type TemplateSummary = z.infer<typeof summarySchema>

const detailSchema = z.object({
  id: z.string(),
  service: serviceRefSchema,
  name: z.string(),
  description: z.string().nullish(),
  active: z.boolean(),
  revision: z.number(),
  plan: z.object({ stages: z.array(scheduledStageSchema), totalDays: z.number() }),
  version: z.number(),
})
export type TemplateDetail = z.infer<typeof detailSchema>

const TEMPLATES = '/project-templates'

export const templateKeys = {
  all: ['project-templates'] as const,
  detail: (id: string) => ['project-templates', 'detail', id] as const,
}

export const templatesQuery = queryOptions({
  queryKey: [...templateKeys.all, 'list'],
  queryFn: ({ signal }) => apiRequest(TEMPLATES, z.array(summarySchema), { signal }),
})

export function templateQuery(id: string) {
  return queryOptions({
    queryKey: templateKeys.detail(id),
    queryFn: ({ signal }) => apiRequest(`${TEMPLATES}/${id}`, detailSchema, { signal }),
  })
}

/** What the editor offers to choose from: the role codes a stage or task can be made the responsibility of. */
export const templateOptionsQuery = queryOptions({
  queryKey: [...templateKeys.all, 'options'],
  queryFn: ({ signal }) => apiRequest(`${TEMPLATES}/options`, z.object({ roles: z.array(z.string()) }), { signal }),
  staleTime: Infinity,
})

export type StageInput = {
  key: string
  name: string
  description: string | null
  durationDays: number
  responsibleRole: string | null
  dependsOn: string[]
  milestones: string[]
  tasks: {
    title: string
    description: string | null
    responsibleRole: string | null
    estimatedHours: number | null
    checklist: string[]
  }[]
  documents: { name: string; required: boolean; description: string | null }[]
}

export type TemplateInput = { name: string; description: string | null; stages: StageInput[] }

export function createTemplate(input: TemplateInput & { serviceId: string }) {
  return apiRequest(TEMPLATES, detailSchema, { method: 'POST', body: input })
}

export function updateTemplate(id: string, input: TemplateInput & { version: number }) {
  return apiRequest(`${TEMPLATES}/${id}`, detailSchema, { method: 'PUT', body: input })
}

export function setTemplateActive(id: string, active: boolean) {
  return apiRequest(`${TEMPLATES}/${id}/${active ? 'activate' : 'deactivate'}`, detailSchema, { method: 'POST' })
}

export function deleteTemplate(id: string) {
  return apiRequest(`${TEMPLATES}/${id}`, noContent, { method: 'DELETE' })
}

/** "PROJECT_MANAGER" as "Project manager". Role codes come from the server; this only makes them readable. */
export function roleLabel(code: string | null | undefined) {
  if (!code) {
    return 'Nobody in particular'
  }
  const words = code.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function days(count: number) {
  return count === 1 ? '1 day' : `${count} days`
}
