import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

const noContent = z.undefined()

export const QUOTATION_STATUSES = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PENDING_APPROVAL', label: 'Pending Approval' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'SENT', label: 'Sent' },
  { value: 'VIEWED', label: 'Viewed' },
  { value: 'ACCEPTED', label: 'Accepted' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'CANCELLED', label: 'Cancelled' },
] as const

export type QuotationStatus = (typeof QUOTATION_STATUSES)[number]['value']

export const itemSchema = z.object({
  id: z.string(),
  itemOrder: z.number(),
  serviceId: z.string().nullish(),
  serviceName: z.string(),
  description: z.string().nullish(),
  quantity: z.number(),
  unitPrice: z.number(),
  discountPercentage: z.number().nullish(),
  discountAmount: z.number(),
  taxPercentage: z.number().nullish(),
  taxAmount: z.number(),
  subtotal: z.number(),
  totalAmount: z.number(),
})
export type QuotationItem = z.infer<typeof itemSchema>

export const versionSummarySchema = z.object({
  id: z.string(),
  versionNumber: z.number(),
  subtotal: z.number(),
  discountPercentage: z.number().nullish(),
  discountAmount: z.number(),
  taxPercentage: z.number().nullish(),
  taxAmount: z.number(),
  finalAmount: z.number(),
  terms: z.string().nullish(),
  validUntil: z.string().nullish(),
  notes: z.string().nullish(),
  pdfStorageKey: z.string().nullish(),
  pdfGeneratedAt: z.string().nullish(),
  changeSummary: z.string().nullish(),
  createdAt: z.string(),
  createdBy: z.string(),
  itemCount: z.number(),
})
export type VersionSummary = z.infer<typeof versionSummarySchema>

export const versionDetailSchema = z.object({
  id: z.string(),
  versionNumber: z.number(),
  subtotal: z.number(),
  discountPercentage: z.number().nullish(),
  discountAmount: z.number(),
  taxPercentage: z.number().nullish(),
  taxAmount: z.number(),
  finalAmount: z.number(),
  terms: z.string().nullish(),
  validUntil: z.string().nullish(),
  notes: z.string().nullish(),
  pdfStorageKey: z.string().nullish(),
  pdfGeneratedAt: z.string().nullish(),
  changeSummary: z.string().nullish(),
  createdAt: z.string(),
  createdBy: z.string(),
  items: z.array(itemSchema),
})
export type VersionDetail = z.infer<typeof versionDetailSchema>

export const auditLogSchema = z.object({
  id: z.string(),
  action: z.string(),
  fromStatus: z.string().nullish(),
  toStatus: z.string().nullish(),
  versionNumber: z.number().nullish(),
  notes: z.string().nullish(),
  performedBy: z.string(),
  performedAt: z.string(),
})
export type QuotationAuditLog = z.infer<typeof auditLogSchema>

export const quotationSummarySchema = z.object({
  id: z.string(),
  quotationNumber: z.string(),
  title: z.string(),
  customerId: z.string().nullish(),
  customerName: z.string().nullish(),
  leadId: z.string().nullish(),
  leadName: z.string().nullish(),
  opportunityId: z.string().nullish(),
  opportunityTitle: z.string().nullish(),
  status: z.enum([
    'DRAFT',
    'PENDING_APPROVAL',
    'APPROVED',
    'SENT',
    'VIEWED',
    'ACCEPTED',
    'REJECTED',
    'EXPIRED',
    'CANCELLED',
  ]),
  currentVersionNumber: z.number(),
  validUntil: z.string().nullish(),
  ownerId: z.string(),
  ownerName: z.string(),
  subtotal: z.number(),
  discountAmount: z.number(),
  taxAmount: z.number(),
  finalAmount: z.number(),
  nextFollowUpDate: z.string().nullish(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type QuotationSummary = z.infer<typeof quotationSummarySchema>

export const quotationDetailSchema = z.object({
  id: z.string(),
  quotationNumber: z.string(),
  title: z.string(),
  customerId: z.string().nullish(),
  customerName: z.string().nullish(),
  leadId: z.string().nullish(),
  leadName: z.string().nullish(),
  opportunityId: z.string().nullish(),
  opportunityTitle: z.string().nullish(),
  status: z.enum([
    'DRAFT',
    'PENDING_APPROVAL',
    'APPROVED',
    'SENT',
    'VIEWED',
    'ACCEPTED',
    'REJECTED',
    'EXPIRED',
    'CANCELLED',
  ]),
  currentVersionNumber: z.number(),
  currentVersionId: z.string().nullish(),
  validUntil: z.string().nullish(),
  ownerId: z.string(),
  ownerName: z.string(),
  subtotal: z.number(),
  discountAmount: z.number(),
  taxAmount: z.number(),
  finalAmount: z.number(),
  approvalRequired: z.boolean(),
  approvalRequestedAt: z.string().nullish(),
  approvalRequestedBy: z.string().nullish(),
  approvedAt: z.string().nullish(),
  approvedBy: z.string().nullish(),
  rejectedAt: z.string().nullish(),
  rejectedBy: z.string().nullish(),
  rejectionReason: z.string().nullish(),
  sentAt: z.string().nullish(),
  sentBy: z.string().nullish(),
  viewedAt: z.string().nullish(),
  acceptedAt: z.string().nullish(),
  acceptedBy: z.string().nullish(),
  clientRejectionReason: z.string().nullish(),
  cancelledAt: z.string().nullish(),
  cancellationReason: z.string().nullish(),
  nextFollowUpDate: z.string().nullish(),
  currentVersion: versionDetailSchema.nullish(),
  allVersions: z.array(versionSummarySchema),
  auditLogs: z.array(auditLogSchema),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number(),
})
export type QuotationDetail = z.infer<typeof quotationDetailSchema>

export const quotationDashboardSchema = z.object({
  totalQuotations: z.number(),
  totalValue: z.number(),
  draftCount: z.number(),
  pendingApprovalCount: z.number(),
  sentCount: z.number(),
  acceptedCount: z.number(),
  rejectedCount: z.number(),
  expiredCount: z.number(),
  todaysFollowUpsCount: z.number(),
  todaysFollowUps: z.array(quotationSummarySchema),
})
export type QuotationDashboard = z.infer<typeof quotationDashboardSchema>

export type ItemInput = {
  serviceId?: string | null
  serviceName: string
  description?: string | null
  quantity: number
  unitPrice: number
  discountPercentage?: number | null
  taxPercentage?: number | null
}

export type CreateQuotationPayload = {
  title: string
  customerId?: string | null
  customerName?: string | null
  leadId?: string | null
  leadName?: string | null
  opportunityId?: string | null
  opportunityTitle?: string | null
  ownerId?: string | null
  discountPercentage?: number | null
  taxPercentage?: number | null
  validUntil?: string | null
  terms?: string | null
  notes?: string | null
  nextFollowUpDate?: string | null
  items: ItemInput[]
}

export type UpdateDraftPayload = {
  title?: string | null
  customerId?: string | null
  customerName?: string | null
  leadId?: string | null
  leadName?: string | null
  opportunityId?: string | null
  opportunityTitle?: string | null
  ownerId?: string | null
  discountPercentage?: number | null
  taxPercentage?: number | null
  validUntil?: string | null
  terms?: string | null
  notes?: string | null
  nextFollowUpDate?: string | null
  items?: ItemInput[] | null
}

export type CreateVersionPayload = {
  discountPercentage?: number | null
  taxPercentage?: number | null
  validUntil?: string | null
  terms?: string | null
  notes?: string | null
  changeSummary?: string | null
  nextFollowUpDate?: string | null
  items: ItemInput[]
}

export type QuotationFilters = {
  status?: string | null
  customerId?: string | null
  leadId?: string | null
  opportunityId?: string | null
  ownerId?: string | null
  followUpDate?: string | null
  search?: string | null
}

export const quotationKeys = {
  all: ['quotations'] as const,
  lists: () => [...quotationKeys.all, 'list'] as const,
  list: (params: { filters?: QuotationFilters; page?: number; size?: number; sort?: string }) =>
    [...quotationKeys.lists(), params] as const,
  dashboard: () => [...quotationKeys.all, 'dashboard'] as const,
  details: () => [...quotationKeys.all, 'detail'] as const,
  detail: (id: string) => [...quotationKeys.details(), id] as const,
  version: (id: string, ver: number) => [...quotationKeys.detail(id), 'version', ver] as const,
}

export function quotationsQuery(params: {
  filters?: QuotationFilters
  page?: number
  size?: number
  sort?: string
}) {
  return queryOptions({
    queryKey: quotationKeys.list(params),
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) => {
      const q = queryString({
        status: params.filters?.status,
        customerId: params.filters?.customerId,
        leadId: params.filters?.leadId,
        opportunityId: params.filters?.opportunityId,
        ownerId: params.filters?.ownerId,
        followUpDate: params.filters?.followUpDate,
        search: params.filters?.search,
        page: params.page,
        size: params.size,
        sort: params.sort,
      })
      return apiRequest(`/quotations${q}`, pageSchema(quotationSummarySchema), {
        signal,
        method: 'GET',
      })
    },
  })
}

export function quotationDashboardQuery() {
  return queryOptions({
    queryKey: quotationKeys.dashboard(),
    queryFn: ({ signal }) =>
      apiRequest('/quotations/dashboard', quotationDashboardSchema, {
        signal,
        method: 'GET',
      }),
  })
}

export function quotationDetailQuery(id: string) {
  return queryOptions({
    queryKey: quotationKeys.detail(id),
    queryFn: ({ signal }) =>
      apiRequest(`/quotations/${id}`, quotationDetailSchema, {
        signal,
        method: 'GET',
      }),
  })
}

export function quotationVersionQuery(id: string, versionNumber: number) {
  return queryOptions({
    queryKey: quotationKeys.version(id, versionNumber),
    queryFn: ({ signal }) =>
      apiRequest(`/quotations/${id}/versions/${versionNumber}`, versionDetailSchema, {
        signal,
        method: 'GET',
      }),
  })
}

export async function createQuotation(payload: CreateQuotationPayload): Promise<QuotationDetail> {
  return apiRequest('/quotations', quotationDetailSchema, {
    method: 'POST',
    body: payload,
  })
}

export async function updateDraftQuotation(id: string, payload: UpdateDraftPayload): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}`, quotationDetailSchema, {
    method: 'PUT',
    body: payload,
  })
}

export async function createQuotationVersion(id: string, payload: CreateVersionPayload): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}/versions`, quotationDetailSchema, {
    method: 'POST',
    body: payload,
  })
}

export async function requestQuotationApproval(id: string, notes?: string): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}/request-approval`, quotationDetailSchema, {
    method: 'POST',
    body: { notes },
  })
}

export async function approveQuotation(id: string, notes?: string): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}/approve`, quotationDetailSchema, {
    method: 'POST',
    body: { notes },
  })
}

export async function rejectQuotationApproval(id: string, reason?: string): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}/reject-approval`, quotationDetailSchema, {
    method: 'POST',
    body: { reason },
  })
}

export async function sendQuotationToClient(id: string, nextFollowUpDate?: string | null, notes?: string): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}/send`, quotationDetailSchema, {
    method: 'POST',
    body: { nextFollowUpDate, notes },
  })
}

export async function markQuotationAccepted(id: string, notes?: string): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}/accept`, quotationDetailSchema, {
    method: 'POST',
    body: { notes },
  })
}

export async function markQuotationRejected(id: string, reason: string): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}/reject`, quotationDetailSchema, {
    method: 'POST',
    body: { reason },
  })
}

export async function cancelQuotation(id: string, reason?: string): Promise<QuotationDetail> {
  return apiRequest(`/quotations/${id}/cancel`, quotationDetailSchema, {
    method: 'POST',
    body: { reason },
  })
}

export async function generateQuotationPdf(id: string, versionNumber?: number): Promise<{ url: string }> {
  const q = versionNumber != null ? `?versionNumber=${versionNumber}` : ''
  return apiRequest(`/quotations/${id}/generate-pdf${q}`, z.object({ url: z.string() }), {
    method: 'POST',
  })
}

export async function deleteQuotation(id: string): Promise<void> {
  return apiRequest(`/quotations/${id}`, noContent, {
    method: 'DELETE',
  })
}
