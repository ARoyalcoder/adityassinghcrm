import { queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'

const projectRefSchema = z.object({ id: z.string(), projectNumber: z.string(), title: z.string(), days: z.number() })

/**
 * Project analytics as the server works them out. A figure that cannot be worked out from what is
 * recorded (an average with nothing to average) is null, and the screen says so instead of showing 0.
 */
const analyticsSchema = z.object({
  asOf: z.string(),
  cards: z.object({
    activeProjects: z.number(),
    startingToday: z.number(),
    dueToday: z.number(),
    delayedProjects: z.number(),
    completedThisMonth: z.number(),
    revenueInProgress: z.number(),
    completedRevenue: z.number(),
    completedRevenueThisMonth: z.number(),
    averageProjectDurationDays: z.number().nullish(),
    averageDeliveryDelayDays: z.number().nullish(),
    /** Average client rating, 1 to 5. */
    clientSatisfaction: z.number().nullish(),
    ratedProjects: z.number(),
  }),
  /** Projects by the stage they are in. The stages come from the project templates, in their order. */
  pipeline: z.array(z.object({ name: z.string(), projects: z.number(), revenue: z.number() })),
  unstagedProjects: z.number(),
  delivery: z.object({
    deliveredOnTime: z.number(),
    deliveredLate: z.number(),
    onTimePercentage: z.number().nullish(),
    averageDelayDays: z.number().nullish(),
    fastestDelivery: projectRefSchema.nullish(),
    longestDelivery: projectRefSchema.nullish(),
    averageCompletionDays: z.number().nullish(),
  }),
  productivity: z.object({
    projectsCompleted: z.number(),
    projectsCompletedThisMonth: z.number(),
    averageHours: z.number().nullish(),
    averageDeliverables: z.number().nullish(),
    /** Not recorded yet: always null today. */
    averageDrawings: z.number().nullish(),
    employees: z.array(
      z.object({
        userId: z.string(),
        name: z.string().nullish(),
        tasksCompleted: z.number(),
        openTasks: z.number(),
        hoursLogged: z.number(),
        projects: z.number(),
      }),
    ),
  }),
})
export type ProjectAnalytics = z.infer<typeof analyticsSchema>

const stageOptionSchema = z.object({ key: z.string(), name: z.string() })

const pipelineProjectSchema = z.object({
  id: z.string(),
  projectNumber: z.string(),
  title: z.string(),
  customerName: z.string(),
  expectedCompletionDate: z.string().nullish(),
  delayed: z.boolean(),
  stageKey: z.string().nullish(),
  stageName: z.string().nullish(),
  /** The stages of the project's template: what it can be moved to. */
  stages: z.array(stageOptionSchema),
})
export type PipelineProject = z.infer<typeof pipelineProjectSchema>

export const analyticsKeys = { all: ['project-analytics'] as const }

export const projectAnalyticsQuery = queryOptions({
  queryKey: [...analyticsKeys.all, 'summary'],
  queryFn: ({ signal }) => apiRequest('/project-analytics', analyticsSchema, { signal }),
})

export const pipelineProjectsQuery = queryOptions({
  queryKey: [...analyticsKeys.all, 'pipeline-projects'],
  queryFn: ({ signal }) => apiRequest('/project-analytics/pipeline-projects', z.array(pipelineProjectSchema), { signal }),
})

export function moveProjectStage(projectId: string, stageKey: string) {
  return apiRequest(
    `/projects/${projectId}/stage`,
    z.object({ stageKey: z.string().nullish(), stageName: z.string().nullish() }),
    { method: 'PUT', body: { stageKey } },
  )
}
