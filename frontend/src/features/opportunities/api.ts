import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

const noContent = z.undefined()

export const OPPORTUNITY_STATUSES = [
  { value: 'OPEN', label: 'Open' },
  { value: 'WON', label: 'Won' },
  { value: 'LOST', label: 'Lost' },
] as const

export type OpportunityStatus = (typeof OPPORTUNITY_STATUSES)[number]['value']

export const pipelineStageSchema = z.object({
  id: z.string(),
  name: z.string(),
  code: z.string(),
  displayOrder: z.number(),
  defaultProbability: z.number(),
  isWon: z.boolean(),
  isLost: z.boolean(),
  color: z.string().nullish(),
})
export type PipelineStage = z.infer<typeof pipelineStageSchema>

export const pipelineDetailSchema = z.object({
  id: z.string(),
  name: z.string(),
  code: z.string(),
  description: z.string().nullish(),
  isDefault: z.boolean(),
  active: z.boolean(),
  stages: z.array(pipelineStageSchema),
})
export type PipelineDetail = z.infer<typeof pipelineDetailSchema>

export const opportunitySummarySchema = z.object({
  id: z.string(),
  title: z.string(),
  customerId: z.string().nullish(),
  customerName: z.string().nullish(),
  leadId: z.string().nullish(),
  leadName: z.string().nullish(),
  serviceId: z.string().nullish(),
  serviceName: z.string().nullish(),
  verticalCode: z.string().nullish(),
  value: z.number(),
  probability: z.number(),
  expectedCloseDate: z.string().nullish(),
  ownerId: z.string().nullish(),
  ownerName: z.string().nullish(),
  pipelineId: z.string(),
  pipelineName: z.string(),
  stageId: z.string(),
  stageName: z.string(),
  stageColor: z.string().nullish(),
  status: z.enum(['OPEN', 'WON', 'LOST']),
  lossReason: z.string().nullish(),
  stageEnteredAt: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type OpportunitySummary = z.infer<typeof opportunitySummarySchema>

export const stageHistorySchema = z.object({
  id: z.string(),
  fromStageId: z.string().nullish(),
  fromStageName: z.string().nullish(),
  toStageId: z.string(),
  toStageName: z.string(),
  enteredAt: z.string(),
  exitedAt: z.string().nullish(),
  durationSeconds: z.number().nullish(),
  durationDays: z.number().nullish(),
  changedBy: z.string().nullish(),
  notes: z.string().nullish(),
})
export type StageHistory = z.infer<typeof stageHistorySchema>

export const opportunityNoteSchema = z.object({
  id: z.string(),
  body: z.string(),
  authorId: z.string().nullish(),
  authorName: z.string().nullish(),
  createdAt: z.string(),
})
export type OpportunityNote = z.infer<typeof opportunityNoteSchema>

export const opportunityDetailSchema = z.object({
  id: z.string(),
  title: z.string(),
  customerId: z.string().nullish(),
  customerName: z.string().nullish(),
  leadId: z.string().nullish(),
  leadName: z.string().nullish(),
  serviceId: z.string().nullish(),
  serviceName: z.string().nullish(),
  verticalCode: z.string().nullish(),
  value: z.number(),
  probability: z.number(),
  expectedCloseDate: z.string().nullish(),
  ownerId: z.string().nullish(),
  ownerName: z.string().nullish(),
  pipeline: pipelineDetailSchema,
  currentStage: pipelineStageSchema,
  status: z.enum(['OPEN', 'WON', 'LOST']),
  lossReason: z.string().nullish(),
  wonAt: z.string().nullish(),
  lostAt: z.string().nullish(),
  stageEnteredAt: z.string(),
  stageHistory: z.array(stageHistorySchema),
  notes: z.array(opportunityNoteSchema),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number(),
})
export type OpportunityDetail = z.infer<typeof opportunityDetailSchema>

export const kanbanColumnSchema = z.object({
  stage: pipelineStageSchema,
  count: z.number(),
  totalValue: z.number(),
  opportunities: z.array(opportunitySummarySchema),
})
export type KanbanColumn = z.infer<typeof kanbanColumnSchema>

export const kanbanBoardSchema = z.object({
  pipeline: pipelineDetailSchema,
  columns: z.array(kanbanColumnSchema),
  totalOpportunities: z.number(),
  totalPipelineValue: z.number(),
})
export type KanbanBoard = z.infer<typeof kanbanBoardSchema>

export const stageTimeMetricSchema = z.object({
  stageId: z.string(),
  stageName: z.string(),
  averageDays: z.number(),
  averageHours: z.number(),
})
export type StageTimeMetric = z.infer<typeof stageTimeMetricSchema>

export const pipelineAnalyticsSchema = z.object({
  totalOpportunities: z.number(),
  openOpportunities: z.number(),
  wonOpportunities: z.number(),
  lostOpportunities: z.number(),
  pipelineValue: z.number(),
  weightedPipelineValue: z.number(),
  winRatePercentage: z.number(),
  averageTimePerStage: z.array(stageTimeMetricSchema),
})
export type PipelineAnalytics = z.infer<typeof pipelineAnalyticsSchema>

export type OpportunityFilters = {
  pipelineId?: string | null
  stageId?: string | null
  ownerId?: string | null
  status?: string | null
  customerId?: string | null
  leadId?: string | null
  serviceId?: string | null
  search?: string | null
}

export type CreateOpportunityPayload = {
  title: string
  customerId?: string | null
  customerName?: string | null
  leadId?: string | null
  leadName?: string | null
  serviceId?: string | null
  serviceName?: string | null
  verticalCode?: string | null
  value?: number | null
  probability?: number | null
  expectedCloseDate?: string | null
  ownerId?: string | null
  pipelineId?: string | null
  stageId?: string | null
}

export type UpdateOpportunityPayload = {
  title?: string | null
  customerId?: string | null
  customerName?: string | null
  leadId?: string | null
  leadName?: string | null
  serviceId?: string | null
  serviceName?: string | null
  verticalCode?: string | null
  value?: number | null
  probability?: number | null
  expectedCloseDate?: string | null
  ownerId?: string | null
}

export type ChangeStagePayload = {
  stageId: string
  notes?: string | null
  lossReason?: string | null
}

export const opportunityKeys = {
  all: ['opportunities'] as const,
  lists: () => [...opportunityKeys.all, 'list'] as const,
  list: (params: { filters?: OpportunityFilters; page?: number; size?: number; sort?: string }) =>
    [...opportunityKeys.lists(), params] as const,
  kanban: (pipelineId?: string | null) => [...opportunityKeys.all, 'kanban', pipelineId ?? 'default'] as const,
  analytics: (pipelineId?: string | null) => [...opportunityKeys.all, 'analytics', pipelineId ?? 'default'] as const,
  details: () => [...opportunityKeys.all, 'detail'] as const,
  detail: (id: string) => [...opportunityKeys.details(), id] as const,
  pipelines: () => ['pipelines'] as const,
}

export function pipelinesQuery() {
  return queryOptions({
    queryKey: opportunityKeys.pipelines(),
    queryFn: ({ signal }) =>
      apiRequest('/pipelines', z.array(pipelineDetailSchema), {
        signal,
        method: 'GET',
      }),
    staleTime: 60_000,
  })
}

export function opportunitiesQuery(params: {
  filters?: OpportunityFilters
  page?: number
  size?: number
  sort?: string
}) {
  return queryOptions({
    queryKey: opportunityKeys.list(params),
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) => {
      const q = queryString({
        pipelineId: params.filters?.pipelineId,
        stageId: params.filters?.stageId,
        ownerId: params.filters?.ownerId,
        status: params.filters?.status,
        customerId: params.filters?.customerId,
        leadId: params.filters?.leadId,
        serviceId: params.filters?.serviceId,
        search: params.filters?.search,
        page: params.page,
        size: params.size,
        sort: params.sort,
      })
      return apiRequest(`/opportunities${q}`, pageSchema(opportunitySummarySchema), {
        signal,
        method: 'GET',
      })
    },
  })
}

export function kanbanQuery(pipelineId?: string | null) {
  return queryOptions({
    queryKey: opportunityKeys.kanban(pipelineId),
    queryFn: ({ signal }) => {
      const q = queryString({ pipelineId })
      return apiRequest(`/opportunities/kanban${q}`, kanbanBoardSchema, {
        signal,
        method: 'GET',
      })
    },
  })
}

export function analyticsQuery(pipelineId?: string | null) {
  return queryOptions({
    queryKey: opportunityKeys.analytics(pipelineId),
    queryFn: ({ signal }) => {
      const q = queryString({ pipelineId })
      return apiRequest(`/opportunities/analytics${q}`, pipelineAnalyticsSchema, {
        signal,
        method: 'GET',
      })
    },
  })
}

export function opportunityDetailQuery(id: string) {
  return queryOptions({
    queryKey: opportunityKeys.detail(id),
    queryFn: ({ signal }) =>
      apiRequest(`/opportunities/${id}`, opportunityDetailSchema, {
        signal,
        method: 'GET',
      }),
  })
}

export async function createOpportunity(payload: CreateOpportunityPayload): Promise<OpportunityDetail> {
  return apiRequest('/opportunities', opportunityDetailSchema, {
    method: 'POST',
    body: payload,
  })
}

export async function updateOpportunity(id: string, payload: UpdateOpportunityPayload): Promise<OpportunityDetail> {
  return apiRequest(`/opportunities/${id}`, opportunityDetailSchema, {
    method: 'PUT',
    body: payload,
  })
}

export async function changeOpportunityStage(id: string, payload: ChangeStagePayload): Promise<OpportunityDetail> {
  return apiRequest(`/opportunities/${id}/stage`, opportunityDetailSchema, {
    method: 'POST',
    body: payload,
  })
}

export async function addOpportunityNote(id: string, body: string): Promise<OpportunityNote> {
  return apiRequest(`/opportunities/${id}/notes`, opportunityNoteSchema, {
    method: 'POST',
    body: { body },
  })
}

export async function deleteOpportunity(id: string): Promise<void> {
  return apiRequest(`/opportunities/${id}`, noContent, {
    method: 'DELETE',
  })
}
