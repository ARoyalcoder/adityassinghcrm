import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'

export const PROOF_CATEGORIES = [
  { value: 'BEFORE_PHOTO', label: 'Before Photo' },
  { value: 'DURING_PHOTO', label: 'During Photo' },
  { value: 'AFTER_PHOTO', label: 'After Photo' },
  { value: 'DOCUMENT', label: 'Document / Report' },
  { value: 'CHECKLIST', label: 'Checklist Item' },
  { value: 'TECHNICIAN_NOTE', label: 'Technician Note' },
  { value: 'CUSTOMER_CONFIRMATION', label: 'Customer Confirmation' },
  { value: 'GENERAL', label: 'General Proof' },
] as const

export const ATTACHMENT_TYPES = [
  { value: 'BEFORE_PHOTO', label: 'Before Photo' },
  { value: 'DURING_PHOTO', label: 'During Photo' },
  { value: 'AFTER_PHOTO', label: 'After Photo' },
  { value: 'DOCUMENT', label: 'Technical Document' },
] as const

export const attachmentViewSchema = z.object({
  id: z.string().uuid(),
  proofItemId: z.string().uuid(),
  attachmentType: z.enum(['BEFORE_PHOTO', 'DURING_PHOTO', 'AFTER_PHOTO', 'DOCUMENT']),
  fileName: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  caption: z.string().nullable().optional(),
  uploadedByName: z.string().nullable().optional(),
  createdAt: z.string(),
  downloadUrl: z.string(),
})

export type AttachmentView = z.infer<typeof attachmentViewSchema>

export const checklistViewSchema = z.object({
  id: z.string().uuid(),
  proofItemId: z.string().uuid(),
  itemTitle: z.string(),
  completed: z.boolean(),
  completedAt: z.string().nullable().optional(),
  completedByName: z.string().nullable().optional(),
  displayOrder: z.number(),
  createdAt: z.string(),
})

export type ChecklistView = z.infer<typeof checklistViewSchema>

export const proofItemDetailSchema = z.object({
  id: z.string().uuid(),
  organizationId: z.string().uuid(),
  entityType: z.enum(['PROJECT', 'JOB']),
  projectId: z.string().uuid().nullable().optional(),
  jobId: z.string().uuid().nullable().optional(),
  title: z.string(),
  category: z.enum([
    'BEFORE_PHOTO',
    'DURING_PHOTO',
    'AFTER_PHOTO',
    'DOCUMENT',
    'CHECKLIST',
    'TECHNICIAN_NOTE',
    'CUSTOMER_CONFIRMATION',
    'GENERAL',
  ]),
  technicianId: z.string().uuid().nullable().optional(),
  technicianName: z.string().nullable().optional(),
  technicianNotes: z.string().nullable().optional(),
  customerConfirmed: z.boolean(),
  customerName: z.string().nullable().optional(),
  customerRemarks: z.string().nullable().optional(),
  customerSignature: z.string().nullable().optional(),
  confirmedAt: z.string().nullable().optional(),
  verified: z.boolean(),
  verifiedById: z.string().uuid().nullable().optional(),
  verifiedByName: z.string().nullable().optional(),
  verifiedAt: z.string().nullable().optional(),
  recordedAt: z.string(),
  createdAt: z.string(),
  createdBy: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
  updatedBy: z.string().nullable().optional(),
  attachments: z.array(attachmentViewSchema),
  checklists: z.array(checklistViewSchema),
})

export type ProofItemDetail = z.infer<typeof proofItemDetailSchema>

export const timelineViewSchema = z.object({
  entityId: z.string().uuid(),
  entityType: z.enum(['PROJECT', 'JOB']),
  totalItems: z.number(),
  verifiedItems: z.number(),
  customerConfirmedItems: z.number(),
  photoCount: z.number(),
  documentCount: z.number(),
  items: z.array(proofItemDetailSchema),
})

export type TimelineView = z.infer<typeof timelineViewSchema>

export type CreateProofItemInput = {
  entityType: 'PROJECT' | 'JOB'
  projectId?: string | null
  jobId?: string | null
  title: string
  category: string
  technicianId?: string | null
  technicianNotes?: string | null
  checklistTitles?: string[]
}

export type CustomerConfirmationInput = {
  customerName: string
  customerRemarks?: string | null
  customerSignature: string
}

export type VerificationInput = {
  remarks?: string | null
}

export type DeleteProofInput = {
  reason: string
}

export const proofOfWorkApi = {
  projectTimelineQuery: (projectId: string) => ({
    queryKey: ['proof-of-work', 'timeline', 'PROJECT', projectId],
    queryFn: () =>
      apiRequest(`/api/v1/proof-of-work/projects/${projectId}/timeline`, timelineViewSchema),
  }),

  jobTimelineQuery: (jobId: string) => ({
    queryKey: ['proof-of-work', 'timeline', 'JOB', jobId],
    queryFn: () =>
      apiRequest(`/api/v1/proof-of-work/jobs/${jobId}/timeline`, timelineViewSchema),
  }),

  detailQuery: (id: string) => ({
    queryKey: ['proof-of-work', 'item', id],
    queryFn: () =>
      apiRequest(`/api/v1/proof-of-work/${id}`, proofItemDetailSchema),
  }),

  createItem: (input: CreateProofItemInput) =>
    apiRequest('/api/v1/proof-of-work', proofItemDetailSchema, {
      method: 'POST',
      body: input,
    }),

  uploadAttachment: (
    proofItemId: string,
    file: File,
    type: 'BEFORE_PHOTO' | 'DURING_PHOTO' | 'AFTER_PHOTO' | 'DOCUMENT',
    caption?: string
  ) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('type', type)
    if (caption) {
      formData.append('caption', caption)
    }
    return apiRequest(`/api/v1/proof-of-work/${proofItemId}/attachments`, proofItemDetailSchema, {
      method: 'POST',
      body: formData,
    })
  },

  toggleChecklist: (proofItemId: string, checklistId: string, completed: boolean) =>
    apiRequest(`/api/v1/proof-of-work/${proofItemId}/checklists/${checklistId}`, proofItemDetailSchema, {
      method: 'PUT',
      body: { completed },
    }),

  recordConfirmation: (proofItemId: string, input: CustomerConfirmationInput) =>
    apiRequest(`/api/v1/proof-of-work/${proofItemId}/customer-confirmation`, proofItemDetailSchema, {
      method: 'POST',
      body: input,
    }),

  verifyItem: (proofItemId: string, input: VerificationInput) =>
    apiRequest(`/api/v1/proof-of-work/${proofItemId}/verify`, proofItemDetailSchema, {
      method: 'POST',
      body: input,
    }),

  deleteItem: (proofItemId: string, input: DeleteProofInput) =>
    apiRequest(`/api/v1/proof-of-work/${proofItemId}`, z.undefined(), {
      method: 'DELETE',
      body: input,
    }),
}
