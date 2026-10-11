import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, CheckCircle2, Circle, Calendar, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { projectsApi, type ProjectMilestone } from '../api'

interface ProjectMilestonesTabProps {
  projectId: string
  milestones: ProjectMilestone[]
}

export function ProjectMilestonesTab({ projectId, milestones }: ProjectMilestonesTabProps) {
  const queryClient = useQueryClient()
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')

  const addMutation = useMutation({
    mutationFn: (data: { title: string; description?: string; dueDate: string }) =>
      projectsApi.addMilestone(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
      setIsAddOpen(false)
      setTitle('')
      setDescription('')
      setDueDate('')
    },
  })

  const completeMutation = useMutation({
    mutationFn: (milestoneId: string) =>
      projectsApi.completeMilestone(projectId, milestoneId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (milestoneId: string) =>
      projectsApi.deleteMilestone(projectId, milestoneId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !dueDate) return
    addMutation.mutate({
      title: title.trim(),
      description: description.trim() || undefined,
      dueDate,
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold">Milestones ({milestones.length})</h3>
        <Button size="sm" onClick={() => setIsAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1.5" />
          Add Milestone
        </Button>
      </div>

      {milestones.length === 0 ? (
        <div className="text-center py-12 border border-dashed rounded-lg bg-muted/20 text-muted-foreground">
          No milestones defined yet. Click "Add Milestone" to establish project phases.
        </div>
      ) : (
        <div className="space-y-3">
          {milestones.map((milestone, idx) => {
            const isCompleted = milestone.status === 'COMPLETED'
            return (
              <div
                key={milestone.id}
                className={`flex items-start justify-between p-4 rounded-xl border transition-colors ${
                  isCompleted ? 'bg-muted/30 border-muted' : 'bg-card border-border hover:border-primary/50'
                }`}
              >
                <div className="flex items-start gap-3">
                  <button
                    onClick={() => !isCompleted && completeMutation.mutate(milestone.id)}
                    disabled={isCompleted || completeMutation.isPending}
                    className="mt-0.5 text-muted-foreground hover:text-primary transition-colors disabled:opacity-100"
                    title={isCompleted ? 'Completed' : 'Click to complete milestone'}
                  >
                    {isCompleted ? (
                      <CheckCircle2 className="h-5 w-5 text-emerald-600" />
                    ) : (
                      <Circle className="h-5 w-5 text-muted-foreground hover:text-primary" />
                    )}
                  </button>

                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono font-medium text-muted-foreground">
                        Phase {idx + 1}
                      </span>
                      <h4
                        className={`text-sm font-semibold ${
                          isCompleted ? 'line-through text-muted-foreground' : 'text-foreground'
                        }`}
                      >
                        {milestone.title}
                      </h4>
                      <span
                        className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                          isCompleted
                            ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                            : 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                        }`}
                      >
                        {milestone.status}
                      </span>
                    </div>

                    {milestone.description && (
                      <p className="text-xs text-muted-foreground">{milestone.description}</p>
                    )}

                    <div className="flex items-center gap-4 text-xs text-muted-foreground pt-1">
                      <span className="flex items-center gap-1">
                        <Calendar className="h-3.5 w-3.5" />
                        Due: {new Date(milestone.dueDate).toLocaleDateString()}
                      </span>
                      {milestone.completedDate && (
                        <span className="text-emerald-600">
                          Completed on: {new Date(milestone.completedDate).toLocaleDateString()}
                        </span>
                      )}
                      <span>
                        Tasks: {milestone.completedTaskCount} / {milestone.taskCount}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive hover:bg-destructive/10"
                    onClick={() => {
                      if (confirm('Are you sure you want to delete this milestone?')) {
                        deleteMutation.mutate(milestone.id)
                      }
                    }}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Add Milestone Dialog */}
      {isAddOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="w-full max-w-md p-6 shadow-xl bg-card">
            <div className="flex items-center justify-between pb-3 border-b mb-4">
              <h3 className="font-semibold text-base">Add Milestone</h3>
              <Button variant="ghost" size="sm" className="h-7 w-7 p-0" onClick={() => setIsAddOpen(false)}>
                <X className="h-4 w-4" />
              </Button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="text-xs font-medium block mb-1">Milestone Title *</label>
                <Input
                  placeholder="e.g. Phase 1 - Site Survey & Requirements"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Description</label>
                <Input
                  placeholder="Key deliverables and scope of this milestone"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Due Date *</label>
                <Input
                  type="date"
                  value={dueDate}
                  onChange={(e) => setDueDate(e.target.value)}
                  required
                />
              </div>
              <div className="flex justify-end gap-2 pt-2 border-t">
                <Button type="button" variant="outline" onClick={() => setIsAddOpen(false)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={addMutation.isPending}>
                  {addMutation.isPending ? 'Saving...' : 'Create Milestone'}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
    </div>
  )
}
