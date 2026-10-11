import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { proofOfWorkApi, type CreateProofItemInput, type CustomerConfirmationInput } from '@/features/proof-of-work/api'
import { ProofTimeline } from '@/features/proof-of-work/components/ProofTimeline'

interface ProjectProofTabProps {
  projectId: string
}

export function ProjectProofTab({ projectId }: ProjectProofTabProps) {
  const queryClient = useQueryClient()
  const { data: timeline, isLoading, error } = useQuery(proofOfWorkApi.projectTimelineQuery(projectId))

  const createMutation = useMutation({
    mutationFn: (input: CreateProofItemInput) => proofOfWorkApi.createItem(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['proof-of-work', 'timeline', 'PROJECT', projectId] })
    },
  })

  const uploadMutation = useMutation({
    mutationFn: ({
      itemId,
      file,
      type,
      caption,
    }: {
      itemId: string
      file: File
      type: 'BEFORE_PHOTO' | 'DURING_PHOTO' | 'AFTER_PHOTO' | 'DOCUMENT'
      caption?: string
    }) => proofOfWorkApi.uploadAttachment(itemId, file, type, caption),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['proof-of-work', 'timeline', 'PROJECT', projectId] })
    },
  })

  const toggleChecklistMutation = useMutation({
    mutationFn: ({
      itemId,
      checklistId,
      completed,
    }: {
      itemId: string
      checklistId: string
      completed: boolean
    }) => proofOfWorkApi.toggleChecklist(itemId, checklistId, completed),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['proof-of-work', 'timeline', 'PROJECT', projectId] })
    },
  })

  const customerConfirmationMutation = useMutation({
    mutationFn: ({ itemId, input }: { itemId: string; input: CustomerConfirmationInput }) =>
      proofOfWorkApi.recordConfirmation(itemId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['proof-of-work', 'timeline', 'PROJECT', projectId] })
    },
  })

  const verifyMutation = useMutation({
    mutationFn: ({ itemId, remarks }: { itemId: string; remarks?: string }) =>
      proofOfWorkApi.verifyItem(itemId, { remarks }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['proof-of-work', 'timeline', 'PROJECT', projectId] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: ({ itemId, reason }: { itemId: string; reason: string }) =>
      proofOfWorkApi.deleteItem(itemId, { reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['proof-of-work', 'timeline', 'PROJECT', projectId] })
    },
  })

  if (isLoading) {
    return <div className="p-8 text-center text-muted-foreground text-sm">Loading Proof of Work timeline...</div>
  }

  if (error || !timeline) {
    return (
      <div className="p-8 text-center text-destructive text-sm">
        Failed to load Proof of Work records.
      </div>
    )
  }

  return (
    <ProofTimeline
      timeline={timeline}
      entityType="PROJECT"
      entityId={projectId}
      onCreateItem={async (input) => {
        await createMutation.mutateAsync(input)
      }}
      onUploadAttachment={async (itemId, file, type, caption) => {
        await uploadMutation.mutateAsync({ itemId, file, type, caption })
      }}
      onToggleChecklist={async (itemId, checklistId, completed) => {
        await toggleChecklistMutation.mutateAsync({ itemId, checklistId, completed })
      }}
      onCustomerConfirmation={async (itemId, input) => {
        await customerConfirmationMutation.mutateAsync({ itemId, input })
      }}
      onVerifyItem={async (itemId, remarks) => {
        await verifyMutation.mutateAsync({ itemId, remarks })
      }}
      onDeleteItem={async (itemId, reason) => {
        await deleteMutation.mutateAsync({ itemId, reason })
      }}
      isLoading={createMutation.isPending || uploadMutation.isPending}
    />
  )
}
