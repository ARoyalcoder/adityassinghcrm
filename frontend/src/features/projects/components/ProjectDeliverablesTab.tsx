import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, ExternalLink, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { projectsApi, type Deliverable, type ProjectMilestone } from '../api'

interface ProjectDeliverablesTabProps {
  projectId: string
  deliverables: Deliverable[]
  milestones: ProjectMilestone[]
}

export function ProjectDeliverablesTab({ projectId, deliverables, milestones }: ProjectDeliverablesTabProps) {
  const queryClient = useQueryClient()
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [milestoneId, setMilestoneId] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [acceptanceCriteria, setAcceptanceCriteria] = useState('')
  const [fileUrl, setFileUrl] = useState('')

  const addMutation = useMutation({
    mutationFn: (data: Parameters<typeof projectsApi.addDeliverable>[1]) =>
      projectsApi.addDeliverable(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
      setIsAddOpen(false)
      resetForm()
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ delivId, status }: { delivId: string; status: string }) =>
      projectsApi.updateDeliverableStatus(projectId, delivId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (delivId: string) => projectsApi.deleteDeliverable(projectId, delivId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const resetForm = () => {
    setTitle('')
    setDescription('')
    setMilestoneId('')
    setDueDate('')
    setAcceptanceCriteria('')
    setFileUrl('')
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return

    addMutation.mutate({
      title: title.trim(),
      description: description.trim() || undefined,
      milestoneId: milestoneId || undefined,
      dueDate: dueDate || undefined,
      acceptanceCriteria: acceptanceCriteria.trim() || undefined,
      fileUrl: fileUrl.trim() || undefined,
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold">Deliverables ({deliverables.length})</h3>
        <Button size="sm" onClick={() => setIsAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1.5" />
          Add Deliverable
        </Button>
      </div>

      {deliverables.length === 0 ? (
        <div className="text-center py-12 border border-dashed rounded-lg bg-muted/20 text-muted-foreground">
          No deliverables recorded yet. Click "Add Deliverable" to track client artifacts and sign-offs.
        </div>
      ) : (
        <div className="space-y-3">
          {deliverables.map((deliv) => {
            const isAccepted = deliv.status === 'ACCEPTED'
            return (
              <div
                key={deliv.id}
                className="p-4 rounded-xl border bg-card hover:border-primary/50 transition-colors space-y-2"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <h4 className="text-sm font-semibold">{deliv.title}</h4>
                      {deliv.milestoneTitle && (
                        <span className="text-[11px] bg-muted px-2 py-0.5 rounded text-muted-foreground">
                          {deliv.milestoneTitle}
                        </span>
                      )}
                      <span
                        className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                          isAccepted
                            ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                            : deliv.status === 'REJECTED'
                            ? 'bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300'
                            : deliv.status === 'SUBMITTED'
                            ? 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                            : 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-300'
                        }`}
                      >
                        {deliv.status}
                      </span>
                    </div>

                    {deliv.description && <p className="text-xs text-muted-foreground">{deliv.description}</p>}
                    {deliv.acceptanceCriteria && (
                      <p className="text-xs text-muted-foreground">
                        <strong className="text-foreground">Acceptance:</strong> {deliv.acceptanceCriteria}
                      </p>
                    )}

                    <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground pt-1">
                      {deliv.dueDate && <span>Due: {new Date(deliv.dueDate).toLocaleDateString()}</span>}
                      {deliv.deliveredDate && (
                        <span>Submitted: {new Date(deliv.deliveredDate).toLocaleDateString()}</span>
                      )}
                      {deliv.reviewedBy && <span>Reviewed by: {deliv.reviewedBy}</span>}
                      {deliv.fileUrl && (
                        <a
                          href={deliv.fileUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="flex items-center gap-1 text-primary hover:underline"
                        >
                          <ExternalLink className="h-3 w-3" />
                          View File
                        </a>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <select
                      value={deliv.status}
                      onChange={(e) =>
                        statusMutation.mutate({ delivId: deliv.id, status: e.target.value })
                      }
                      className="h-7 text-xs bg-background border rounded px-2"
                    >
                      <option value="PENDING">PENDING</option>
                      <option value="SUBMITTED">SUBMITTED</option>
                      <option value="ACCEPTED">ACCEPTED</option>
                      <option value="REJECTED">REJECTED</option>
                    </select>

                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:bg-destructive/10 h-7 w-7 p-0"
                      onClick={() => {
                        if (confirm('Delete this deliverable?')) {
                          deleteMutation.mutate(deliv.id)
                        }
                      }}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Add Deliverable Dialog */}
      {isAddOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="max-w-md w-full p-6 shadow-xl bg-card max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b mb-4">
              <h3 className="font-semibold text-base">Add Deliverable</h3>
              <Button variant="ghost" size="sm" className="h-7 w-7 p-0" onClick={() => setIsAddOpen(false)}>
                <X className="h-4 w-4" />
              </Button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div>
                <label className="text-xs font-medium block mb-1">Deliverable Title *</label>
                <Input
                  placeholder="e.g. As-built CAD drawing"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Description</label>
                <Input
                  placeholder="Description of item"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium block mb-1">Milestone</label>
                  <select
                    value={milestoneId}
                    onChange={(e) => setMilestoneId(e.target.value)}
                    className="w-full h-9 text-xs bg-background border rounded px-2"
                  >
                    <option value="">(No Milestone)</option>
                    {milestones.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.title}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium block mb-1">Due Date</label>
                  <Input
                    type="date"
                    value={dueDate}
                    onChange={(e) => setDueDate(e.target.value)}
                    className="h-9 text-xs"
                  />
                </div>
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Acceptance Criteria</label>
                <Input
                  placeholder="e.g. Signed by structural engineer"
                  value={acceptanceCriteria}
                  onChange={(e) => setAcceptanceCriteria(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Artifact / Document URL</label>
                <Input
                  placeholder="https://..."
                  value={fileUrl}
                  onChange={(e) => setFileUrl(e.target.value)}
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t">
                <Button type="button" variant="outline" onClick={() => setIsAddOpen(false)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={addMutation.isPending}>
                  {addMutation.isPending ? 'Saving...' : 'Add Deliverable'}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
    </div>
  )
}
