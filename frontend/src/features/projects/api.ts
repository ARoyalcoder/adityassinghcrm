import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

// Project status config schema
export const projectStatusConfigSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  color: z.string(),
  category: z.string(),
  displayOrder: z.number(),
  isDefault: z.boolean(),
  isCompleted: z.boolean(),
  isCancelled: z.boolean(),
})
export type ProjectStatusConfig = z.infer<typeof projectStatusConfigSchema>

// Checklist item schema
export const checklistItemSchema = z.object({
  id: z.string(),
  itemText: z.string(),
  completed: z.boolean(),
  displayOrder: z.number(),
  completedAt: z.string().nullish(),
  completedBy: z.string().nullish(),
})
export type ChecklistItem = z.infer<typeof checklistItemSchema>

// Task dependency schema
export const taskDependencySchema = z.object({
  id: z.string(),
  dependsOnTaskId: z.string(),
  dependsOnTaskTitle: z.string(),
  dependencyType: z.string(),
})
export type TaskDependency = z.infer<typeof taskDependencySchema>

// Task schema
export const taskSchema = z.object({
  id: z.string(),
  milestoneId: z.string().nullish(),
  milestoneTitle: z.string().nullish(),
  title: z.string(),
  description: z.string().nullish(),
  assignedToId: z.string().nullish(),
  assignedToName: z.string().nullish(),
  status: z.string(),
  priority: z.string(),
  startDate: z.string().nullish(),
  dueDate: z.string().nullish(),
  completedAt: z.string().nullish(),
  estimatedHours: z.number().nullish(),
  actualHours: z.number().nullish(),
  displayOrder: z.number(),
  checklists: z.array(checklistItemSchema).optional(),
  dependencies: z.array(taskDependencySchema).optional(),
})
export type ProjectTask = z.infer<typeof taskSchema>

// Milestone schema
export const milestoneSchema = z.object({
  id: z.string(),
  title: z.string(),
  description: z.string().nullish(),
  dueDate: z.string(),
  completedDate: z.string().nullish(),
  status: z.string(),
  displayOrder: z.number(),
  progressPercentage: z.number(),
  taskCount: z.number(),
  completedTaskCount: z.number(),
})
export type ProjectMilestone = z.infer<typeof milestoneSchema>

// Member schema
export const memberSchema = z.object({
  id: z.string(),
  userId: z.string(),
  name: z.string(),
  email: z.string().nullish(),
  role: z.string(),
  addedAt: z.string(),
})
export type ProjectMember = z.infer<typeof memberSchema>

// Deliverable schema
export const deliverableSchema = z.object({
  id: z.string(),
  milestoneId: z.string().nullish(),
  milestoneTitle: z.string().nullish(),
  title: z.string(),
  description: z.string().nullish(),
  dueDate: z.string().nullish(),
  deliveredDate: z.string().nullish(),
  status: z.string(),
  acceptanceCriteria: z.string().nullish(),
  fileUrl: z.string().nullish(),
  reviewedBy: z.string().nullish(),
  reviewedAt: z.string().nullish(),
})
export type Deliverable = z.infer<typeof deliverableSchema>

// Project summary schema
export const projectSummarySchema = z.object({
  id: z.string(),
  projectNumber: z.string(),
  title: z.string(),
  customerId: z.string(),
  customerName: z.string(),
  orderId: z.string().nullish(),
  orderNumber: z.string().nullish(),
  serviceId: z.string().nullish(),
  serviceName: z.string().nullish(),
  projectManagerId: z.string(),
  projectManagerName: z.string(),
  startDate: z.string(),
  expectedCompletionDate: z.string(),
  actualCompletionDate: z.string().nullish(),
  status: z.string(),
  priority: z.string(),
  progressPercentage: z.number(),
  budget: z.number(),
  revenue: z.number(),
  cost: z.number().nullish(),
  createdAt: z.string(),
})
export type ProjectSummary = z.infer<typeof projectSummarySchema>

// Risk Alert schema
export const riskAlertSchema = z.object({
  code: z.string(),
  severity: z.string(),
  title: z.string(),
  description: z.string(),
})
export type RiskAlert = z.infer<typeof riskAlertSchema>

// Project Profitability schema
export const projectProfitabilitySchema = z.object({
  revenue: z.number(),
  materialCost: z.number(),
  labourCost: z.number(),
  vendorCost: z.number(),
  travelCost: z.number(),
  otherExpenses: z.number(),
  actualCost: z.number(),
  expectedCost: z.number(),
  grossProfit: z.number(),
  grossMargin: z.number(),
  projectedFinalCost: z.number(),
})
export type ProjectProfitability = z.infer<typeof projectProfitabilitySchema>

// Project Health Intelligence schema
export const projectHealthSchema = z.object({
  overallScore: z.number(),
  scheduleHealth: z.number(),
  budgetHealth: z.number(),
  taskHealth: z.number(),
  clientApprovalHealth: z.number(),
  teamCapacityHealth: z.number(),
  profitability: projectProfitabilitySchema,
  riskAlerts: z.array(riskAlertSchema),
})
export type ProjectHealth = z.infer<typeof projectHealthSchema>

// Project detail schema
export const projectDetailSchema = z.object({
  id: z.string(),
  projectNumber: z.string(),
  title: z.string(),
  description: z.string().nullish(),
  customerId: z.string(),
  customerName: z.string(),
  orderId: z.string().nullish(),
  orderNumber: z.string().nullish(),
  serviceId: z.string().nullish(),
  serviceName: z.string().nullish(),
  projectManagerId: z.string(),
  projectManagerName: z.string(),
  startDate: z.string(),
  expectedCompletionDate: z.string(),
  actualCompletionDate: z.string().nullish(),
  status: z.string(),
  priority: z.string(),
  progressPercentage: z.number(),
  budget: z.number(),
  revenue: z.number(),
  cost: z.number().nullish(),
  notes: z.string().nullish(),
  createdAt: z.string(),
  createdBy: z.string(),
  updatedAt: z.string(),
  updatedBy: z.string(),
  members: z.array(memberSchema).default([]),
  milestones: z.array(milestoneSchema).default([]),
  tasks: z.array(taskSchema).default([]),
  deliverables: z.array(deliverableSchema).default([]),
  health: projectHealthSchema.optional(),
})
export type ProjectDetail = z.infer<typeof projectDetailSchema>

// Kanban column & board schemas
export const kanbanColumnSchema = z.object({
  status: projectStatusConfigSchema,
  projects: z.array(projectSummarySchema),
  totalBudget: z.number(),
  totalRevenue: z.number(),
})
export const kanbanBoardSchema = z.object({
  columns: z.array(kanbanColumnSchema),
})
export type KanbanBoard = z.infer<typeof kanbanBoardSchema>

// Timeline item & timeline schema
export const timelineItemSchema = z.object({
  id: z.string(),
  type: z.enum(['MILESTONE', 'TASK']),
  title: z.string(),
  status: z.string(),
  startDate: z.string().nullish(),
  dueDate: z.string().nullish(),
  progressPercentage: z.number(),
  assignedToName: z.string().nullish(),
  dependsOnTaskIds: z.array(z.string()).default([]),
})
export const projectTimelineSchema = z.object({
  projectId: z.string(),
  projectNumber: z.string(),
  title: z.string(),
  startDate: z.string(),
  expectedCompletionDate: z.string(),
  items: z.array(timelineItemSchema),
})
export type ProjectTimeline = z.infer<typeof projectTimelineSchema>

// Dashboard KPI schema
export const projectDashboardSchema = z.object({
  totalProjects: z.number(),
  totalBudget: z.number(),
  totalRevenue: z.number(),
  activeProjects: z.number(),
  completedProjects: z.number(),
  delayedProjects: z.number(),
  recentProjects: z.array(projectSummarySchema),
})
export type ProjectDashboard = z.infer<typeof projectDashboardSchema>

// Query Options & Filters
export type ProjectListParams = {
  status?: string
  customerId?: string
  projectManagerId?: string
  priority?: string
  search?: string
  page?: number
  size?: number
  sort?: string
}

export const projectsApi = {
  listQuery: (params: ProjectListParams = {}) =>
    queryOptions({
      queryKey: ['projects', params],
      queryFn: async () => {
        const query = queryString({
          status: params.status,
          customerId: params.customerId,
          projectManagerId: params.projectManagerId,
          priority: params.priority,
          search: params.search,
          page: params.page ?? 0,
          size: params.size ?? 20,
          sort: params.sort,
        })
        return apiRequest(`/api/v1/projects${query}`, pageSchema(projectSummarySchema))
      },
      placeholderData: keepPreviousData,
    }),

  detailQuery: (id: string) =>
    queryOptions({
      queryKey: ['projects', id],
      queryFn: () => apiRequest(`/api/v1/projects/${id}`, projectDetailSchema),
      enabled: Boolean(id),
    }),

  healthQuery: (id: string) =>
    queryOptions({
      queryKey: ['projects', id, 'health'],
      queryFn: () => apiRequest(`/api/v1/projects/${id}/health`, projectHealthSchema),
      enabled: Boolean(id),
    }),

  kanbanQuery: () =>
    queryOptions({
      queryKey: ['projects', 'kanban'],
      queryFn: () => apiRequest('/api/v1/projects/kanban', kanbanBoardSchema),
    }),

  timelineQuery: (id: string) =>
    queryOptions({
      queryKey: ['projects', id, 'timeline'],
      queryFn: () => apiRequest(`/api/v1/projects/${id}/timeline`, projectTimelineSchema),
      enabled: Boolean(id),
    }),

  dashboardQuery: () =>
    queryOptions({
      queryKey: ['projects', 'dashboard'],
      queryFn: () => apiRequest('/api/v1/projects/dashboard', projectDashboardSchema),
    }),

  statusesQuery: () =>
    queryOptions({
      queryKey: ['projects', 'statuses'],
      queryFn: () => apiRequest('/api/v1/projects/statuses', z.array(projectStatusConfigSchema)),
    }),

  // Mutations
  createProject: async (data: {
    title: string
    description?: string
    customerId: string
    customerName: string
    orderId?: string
    orderNumber?: string
    serviceId?: string
    serviceName?: string
    projectManagerId?: string
    startDate?: string
    expectedCompletionDate?: string
    status?: string
    priority?: string
    budget?: number
    revenue?: number
    notes?: string
  }) => {
    return apiRequest('/api/v1/projects', projectDetailSchema, {
      method: 'POST',
      body: data,
    })
  },

  updateProject: async (
    id: string,
    data: {
      title?: string
      description?: string
      projectManagerId?: string
      startDate?: string
      expectedCompletionDate?: string
      actualCompletionDate?: string
      priority?: string
      budget?: number
      revenue?: number
      cost?: number
      notes?: string
    },
  ) => {
    return apiRequest(`/api/v1/projects/${id}`, projectDetailSchema, {
      method: 'PUT',
      body: data,
    })
  },

  updateStatus: async (id: string, status: string, notes?: string) => {
    return apiRequest(`/api/v1/projects/${id}/status`, projectDetailSchema, {
      method: 'PATCH',
      body: { status, notes },
    })
  },

  updateProfitability: async (
    id: string,
    data: {
      revenue?: number
      materialCost?: number
      labourCost?: number
      vendorCost?: number
      travelCost?: number
      otherExpenses?: number
      expectedCost?: number
    },
  ) => {
    return apiRequest(`/api/v1/projects/${id}/profitability`, projectDetailSchema, {
      method: 'PUT',
      body: data,
    })
  },

  deleteProject: async (id: string) => {
    return apiRequest(`/api/v1/projects/${id}`, z.undefined(), { method: 'DELETE' })
  },

  // Member mutations
  addMember: async (id: string, userId: string, role: string) => {
    return apiRequest(`/api/v1/projects/${id}/members`, memberSchema, {
      method: 'POST',
      body: { userId, role },
    })
  },

  removeMember: async (id: string, userId: string) => {
    return apiRequest(`/api/v1/projects/${id}/members/${userId}`, z.undefined(), { method: 'DELETE' })
  },

  // Milestone mutations
  addMilestone: async (
    id: string,
    data: { title: string; description?: string; dueDate: string; displayOrder?: number },
  ) => {
    return apiRequest(`/api/v1/projects/${id}/milestones`, milestoneSchema, {
      method: 'POST',
      body: data,
    })
  },

  updateMilestone: async (
    id: string,
    milestoneId: string,
    data: { title: string; description?: string; dueDate: string; displayOrder: number; status?: string },
  ) => {
    return apiRequest(`/api/v1/projects/${id}/milestones/${milestoneId}`, milestoneSchema, {
      method: 'PUT',
      body: data,
    })
  },

  completeMilestone: async (id: string, milestoneId: string) => {
    return apiRequest(`/api/v1/projects/${id}/milestones/${milestoneId}/complete`, milestoneSchema, {
      method: 'POST',
    })
  },

  deleteMilestone: async (id: string, milestoneId: string) => {
    return apiRequest(`/api/v1/projects/${id}/milestones/${milestoneId}`, z.undefined(), { method: 'DELETE' })
  },

  // Task mutations
  addTask: async (
    id: string,
    data: {
      milestoneId?: string
      title: string
      description?: string
      assignedToId?: string
      priority?: string
      startDate?: string
      dueDate?: string
      estimatedHours?: number
      displayOrder?: number
      dependsOnTaskIds?: string[]
    },
  ) => {
    return apiRequest(`/api/v1/projects/${id}/tasks`, taskSchema, {
      method: 'POST',
      body: data,
    })
  },

  updateTask: async (
    id: string,
    taskId: string,
    data: {
      milestoneId?: string
      title: string
      description?: string
      assignedToId?: string
      priority?: string
      startDate?: string
      dueDate?: string
      estimatedHours?: number
      actualHours?: number
      status?: string
    },
  ) => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}`, taskSchema, {
      method: 'PUT',
      body: data,
    })
  },

  assignTask: async (id: string, taskId: string, assignedToId?: string) => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}/assign`, taskSchema, {
      method: 'PATCH',
      body: { assignedToId },
    })
  },

  updateTaskStatus: async (id: string, taskId: string, status: string) => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}/status`, taskSchema, {
      method: 'PATCH',
      body: { status },
    })
  },

  deleteTask: async (id: string, taskId: string) => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}`, z.undefined(), { method: 'DELETE' })
  },

  addDependency: async (id: string, taskId: string, dependsOnTaskId: string, dependencyType = 'FINISH_TO_START') => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}/dependencies`, z.undefined(), {
      method: 'POST',
      body: { dependsOnTaskId, dependencyType },
    })
  },

  removeDependency: async (id: string, taskId: string, dependsOnTaskId: string) => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}/dependencies/${dependsOnTaskId}`, z.undefined(), {
      method: 'DELETE',
    })
  },

  addChecklistItem: async (id: string, taskId: string, itemText: string) => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}/checklists`, checklistItemSchema, {
      method: 'POST',
      body: { itemText },
    })
  },

  toggleChecklistItem: async (id: string, taskId: string, itemId: string, completed: boolean) => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}/checklists/${itemId}/toggle`, checklistItemSchema, {
      method: 'PATCH',
      body: { completed },
    })
  },

  deleteChecklistItem: async (id: string, taskId: string, itemId: string) => {
    return apiRequest(`/api/v1/projects/${id}/tasks/${taskId}/checklists/${itemId}`, z.undefined(), {
      method: 'DELETE',
    })
  },

  // Deliverables
  addDeliverable: async (
    id: string,
    data: {
      milestoneId?: string
      title: string
      description?: string
      dueDate?: string
      acceptanceCriteria?: string
      fileUrl?: string
    },
  ) => {
    return apiRequest(`/api/v1/projects/${id}/deliverables`, deliverableSchema, {
      method: 'POST',
      body: data,
    })
  },

  updateDeliverableStatus: async (id: string, delivId: string, status: string) => {
    return apiRequest(`/api/v1/projects/${id}/deliverables/${delivId}/status`, deliverableSchema, {
      method: 'PATCH',
      body: { status },
    })
  },

  deleteDeliverable: async (id: string, delivId: string) => {
    return apiRequest(`/api/v1/projects/${id}/deliverables/${delivId}`, z.undefined(), { method: 'DELETE' })
  },

  // Status config mutations
  createStatusConfig: async (data: {
    code: string
    name: string
    displayOrder: number
    color?: string
    category?: string
    isDefault?: boolean
    isCompleted?: boolean
    isCancelled?: boolean
  }) => {
    return apiRequest('/api/v1/projects/statuses', projectStatusConfigSchema, {
      method: 'POST',
      body: data,
    })
  },

  updateStatusConfig: async (
    id: string,
    data: {
      name: string
      displayOrder: number
      color?: string
      category?: string
      isDefault?: boolean
      isCompleted?: boolean
      isCancelled?: boolean
    },
  ) => {
    return apiRequest(`/api/v1/projects/statuses/${id}`, projectStatusConfigSchema, {
      method: 'PUT',
      body: data,
    })
  },

  deleteStatusConfig: async (id: string) => {
    return apiRequest(`/api/v1/projects/statuses/${id}`, z.undefined(), { method: 'DELETE' })
  },
}
