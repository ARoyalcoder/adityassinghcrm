import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, CheckSquare, Square, Calendar, User, Clock, Trash2, Link as LinkIcon, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { PriorityBadge } from './ProjectStatusBadge'
import { projectsApi, type ProjectTask, type ProjectMilestone, type ProjectMember } from '../api'

interface ProjectTasksTabProps {
  projectId: string
  tasks: ProjectTask[]
  milestones: ProjectMilestone[]
  members: ProjectMember[]
}

export function ProjectTasksTab({ projectId, tasks, milestones, members }: ProjectTasksTabProps) {
  const queryClient = useQueryClient()
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [newChecklistTaskId, setNewChecklistTaskId] = useState<string | null>(null)
  const [checklistText, setChecklistText] = useState('')

  // Task form state
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [milestoneId, setMilestoneId] = useState('')
  const [assignedToId, setAssignedToId] = useState('')
  const [priority, setPriority] = useState('MEDIUM')
  const [startDate, setStartDate] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [estimatedHours, setEstimatedHours] = useState('')
  const [selectedDependencies, setSelectedDependencies] = useState<string[]>([])

  const addMutation = useMutation({
    mutationFn: (data: any) => projectsApi.addTask(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
      setIsAddOpen(false)
      resetForm()
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ taskId, status }: { taskId: string; status: string }) =>
      projectsApi.updateTaskStatus(projectId, taskId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (taskId: string) => projectsApi.deleteTask(projectId, taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const addChecklistMutation = useMutation({
    mutationFn: ({ taskId, itemText }: { taskId: string; itemText: string }) =>
      projectsApi.addChecklistItem(projectId, taskId, itemText),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
      setNewChecklistTaskId(null)
      setChecklistText('')
    },
  })

  const toggleChecklistMutation = useMutation({
    mutationFn: ({ taskId, itemId, completed }: { taskId: string; itemId: string; completed: boolean }) =>
      projectsApi.toggleChecklistItem(projectId, taskId, itemId, completed),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const deleteChecklistMutation = useMutation({
    mutationFn: ({ taskId, itemId }: { taskId: string; itemId: string }) =>
      projectsApi.deleteChecklistItem(projectId, taskId, itemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const resetForm = () => {
    setTitle('')
    setDescription('')
    setMilestoneId('')
    setAssignedToId('')
    setPriority('MEDIUM')
    setStartDate('')
    setDueDate('')
    setEstimatedHours('')
    setSelectedDependencies([])
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return

    addMutation.mutate({
      title: title.trim(),
      description: description.trim() || undefined,
      milestoneId: milestoneId || undefined,
      assignedToId: assignedToId || undefined,
      priority,
      startDate: startDate || undefined,
      dueDate: dueDate || undefined,
      estimatedHours: estimatedHours ? parseFloat(estimatedHours) : undefined,
      dependsOnTaskIds: selectedDependencies.length > 0 ? selectedDependencies : undefined,
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold">Tasks ({tasks.length})</h3>
        <Button size="sm" onClick={() => setIsAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1.5" />
          Add Task
        </Button>
      </div>

      {tasks.length === 0 ? (
        <div className="text-center py-12 border border-dashed rounded-lg bg-muted/20 text-muted-foreground">
          No tasks defined yet. Click "Add Task" to create actionable work items.
        </div>
      ) : (
        <div className="space-y-3">
          {tasks.map((task) => {
            const isCompleted = task.status === 'COMPLETED'
            return (
              <div
                key={task.id}
                className={`p-4 rounded-xl border space-y-3 transition-colors ${
                  isCompleted ? 'bg-muted/30 border-muted' : 'bg-card border-border hover:border-primary/50'
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-start gap-3 flex-1">
                    <button
                      onClick={() =>
                        statusMutation.mutate({
                          taskId: task.id,
                          status: isCompleted ? 'TODO' : 'COMPLETED',
                        })
                      }
                      className="mt-0.5 text-muted-foreground hover:text-primary transition-colors"
                      title={isCompleted ? 'Mark To-do' : 'Mark Completed'}
                    >
                      {isCompleted ? (
                        <CheckSquare className="h-5 w-5 text-emerald-600" />
                      ) : (
                        <Square className="h-5 w-5 text-muted-foreground hover:text-primary" />
                      )}
                    </button>

                    <div className="space-y-1 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h4
                          className={`text-sm font-semibold ${
                            isCompleted ? 'line-through text-muted-foreground' : 'text-foreground'
                          }`}
                        >
                          {task.title}
                        </h4>
                        <PriorityBadge priority={task.priority} />
                        {task.milestoneTitle && (
                          <span className="text-[11px] bg-muted px-2 py-0.5 rounded text-muted-foreground">
                            {task.milestoneTitle}
                          </span>
                        )}
                        <span
                          className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                            isCompleted
                              ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                              : task.status === 'IN_PROGRESS'
                              ? 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                              : 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-300'
                          }`}
                        >
                          {task.status}
                        </span>
                      </div>

                      {task.description && (
                        <p className="text-xs text-muted-foreground">{task.description}</p>
                      )}

                      <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground pt-1">
                        {task.assignedToName && (
                          <span className="flex items-center gap-1">
                            <User className="h-3.5 w-3.5" />
                            {task.assignedToName}
                          </span>
                        )}
                        {task.dueDate && (
                          <span className="flex items-center gap-1">
                            <Calendar className="h-3.5 w-3.5" />
                            Due: {new Date(task.dueDate).toLocaleDateString()}
                          </span>
                        )}
                        {task.estimatedHours != null && (
                          <span className="flex items-center gap-1">
                            <Clock className="h-3.5 w-3.5" />
                            Est: {task.estimatedHours}h
                          </span>
                        )}
                      </div>

                      {/* Dependencies */}
                      {task.dependencies && task.dependencies.length > 0 && (
                        <div className="flex items-center gap-1.5 pt-1 text-[11px] text-amber-600 dark:text-amber-400">
                          <LinkIcon className="h-3 w-3" />
                          <span>Depends on: {task.dependencies.map((d) => d.dependsOnTaskTitle).join(', ')}</span>
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <select
                      value={task.status}
                      onChange={(e) =>
                        statusMutation.mutate({ taskId: task.id, status: e.target.value })
                      }
                      className="h-7 text-xs bg-background border rounded px-2"
                    >
                      <option value="TODO">TODO</option>
                      <option value="IN_PROGRESS">IN_PROGRESS</option>
                      <option value="IN_REVIEW">IN_REVIEW</option>
                      <option value="COMPLETED">COMPLETED</option>
                      <option value="BLOCKED">BLOCKED</option>
                    </select>

                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:bg-destructive/10 h-7 w-7 p-0"
                      onClick={() => {
                        if (confirm('Delete this task?')) {
                          deleteMutation.mutate(task.id)
                        }
                      }}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>

                {/* Checklist items section */}
                <div className="pl-8 pt-2 border-t space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-muted-foreground">
                      Checklist ({(task.checklists ?? []).filter((c) => c.completed).length} / {(task.checklists ?? []).length})
                    </span>
                    <button
                      onClick={() => setNewChecklistTaskId(task.id)}
                      className="text-[11px] text-primary hover:underline font-medium"
                    >
                      + Add Item
                    </button>
                  </div>

                  {(task.checklists ?? []).length > 0 && (
                    <div className="space-y-1">
                      {(task.checklists ?? []).map((item) => (
                        <div key={item.id} className="flex items-center justify-between text-xs py-0.5 group">
                          <label className="flex items-center gap-2 cursor-pointer flex-1">
                            <input
                              type="checkbox"
                              checked={item.completed}
                              onChange={(e) =>
                                toggleChecklistMutation.mutate({
                                  taskId: task.id,
                                  itemId: item.id,
                                  completed: e.target.checked,
                                })
                              }
                              className="rounded text-primary focus:ring-primary h-3.5 w-3.5"
                            />
                            <span className={item.completed ? 'line-through text-muted-foreground' : ''}>
                              {item.itemText}
                            </span>
                          </label>
                          <button
                            onClick={() =>
                              deleteChecklistMutation.mutate({ taskId: task.id, itemId: item.id })
                            }
                            className="opacity-0 group-hover:opacity-100 text-destructive text-[11px] px-1 hover:underline"
                          >
                            remove
                          </button>
                        </div>
                      ))}
                    </div>
                  )}

                  {newChecklistTaskId === task.id && (
                    <div className="flex items-center gap-2 pt-1">
                      <Input
                        placeholder="New checklist item..."
                        value={checklistText}
                        onChange={(e) => setChecklistText(e.target.value)}
                        className="h-7 text-xs flex-1"
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault()
                            if (checklistText.trim()) {
                              addChecklistMutation.mutate({
                                taskId: task.id,
                                itemText: checklistText.trim(),
                              })
                            }
                          }
                        }}
                      />
                      <Button
                        size="sm"
                        className="h-7 text-xs px-2"
                        onClick={() => {
                          if (checklistText.trim()) {
                            addChecklistMutation.mutate({
                              taskId: task.id,
                              itemText: checklistText.trim(),
                            })
                          }
                        }}
                      >
                        Add
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="h-7 text-xs px-2"
                        onClick={() => {
                          setNewChecklistTaskId(null)
                          setChecklistText('')
                        }}
                      >
                        Cancel
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Add Task Dialog */}
      {isAddOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="max-w-md w-full p-6 shadow-xl bg-card max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b mb-4">
              <h3 className="font-semibold text-base">Add Project Task</h3>
              <Button variant="ghost" size="sm" className="h-7 w-7 p-0" onClick={() => setIsAddOpen(false)}>
                <X className="h-4 w-4" />
              </Button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div>
                <label className="text-xs font-medium block mb-1">Task Title *</label>
                <Input
                  placeholder="e.g. Procure inverter units"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Description</label>
                <Input
                  placeholder="Details of the task"
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
                  <label className="text-xs font-medium block mb-1">Assignee</label>
                  <select
                    value={assignedToId}
                    onChange={(e) => setAssignedToId(e.target.value)}
                    className="w-full h-9 text-xs bg-background border rounded px-2"
                  >
                    <option value="">Unassigned</option>
                    {members.map((mem) => (
                      <option key={mem.userId} value={mem.userId}>
                        {mem.name} ({mem.role})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-medium block mb-1">Priority</label>
                  <select
                    value={priority}
                    onChange={(e) => setPriority(e.target.value)}
                    className="w-full h-9 text-xs bg-background border rounded px-2"
                  >
                    <option value="LOW">LOW</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="HIGH">HIGH</option>
                    <option value="CRITICAL">CRITICAL</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium block mb-1">Start Date</label>
                  <Input
                    type="date"
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    className="h-9 text-xs"
                  />
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
                <label className="text-xs font-medium block mb-1">Estimated Hours</label>
                <Input
                  type="number"
                  placeholder="e.g. 16"
                  value={estimatedHours}
                  onChange={(e) => setEstimatedHours(e.target.value)}
                  className="h-9 text-xs"
                />
              </div>

              {/* Task dependencies selector */}
              {tasks.length > 0 && (
                <div>
                  <label className="text-xs font-medium block mb-1">Dependencies (Finish to Start)</label>
                  <div className="max-h-24 overflow-y-auto border rounded p-2 space-y-1 bg-muted/20">
                    {tasks.map((existing) => (
                      <label key={existing.id} className="flex items-center gap-2 text-xs cursor-pointer">
                        <input
                          type="checkbox"
                          checked={selectedDependencies.includes(existing.id)}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedDependencies([...selectedDependencies, existing.id])
                            } else {
                              setSelectedDependencies(selectedDependencies.filter((id) => id !== existing.id))
                            }
                          }}
                          className="rounded text-primary h-3.5 w-3.5"
                        />
                        <span className="truncate">{existing.title}</span>
                      </label>
                    ))}
                  </div>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2 border-t">
                <Button type="button" variant="outline" onClick={() => setIsAddOpen(false)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={addMutation.isPending}>
                  {addMutation.isPending ? 'Saving...' : 'Create Task'}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
    </div>
  )
}
