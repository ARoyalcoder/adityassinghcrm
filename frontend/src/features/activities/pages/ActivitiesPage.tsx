import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  Bell,
  CalendarCheck,
  CheckCircle2,
  Clock,
  ListTodo,
  Plus,
  Repeat,
  Search,
  Trash2,
  User,
} from 'lucide-react'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  activitiesQuery,
  activityCountsQuery,
  activityKeys,
  completeActivity,
  createActivity,
  deleteActivity,
  dismissReminder,
  labelOf,
  rescheduleActivity,
  updateActivity,
  ACTIVITY_TYPES,
  ENTITY_TYPES,
  RECURRENCE_PATTERNS,
  TASK_PRIORITIES,
  TASK_STATUSES,
  type ActivityDetail,
  type ActivityFilters,
  type CreateActivityPayload,
} from '@/features/activities/api'
import { ActivityModal } from '@/features/activities/components/ActivityModal'
import { ActivityTypeBadge } from '@/features/activities/components/ActivityTypeBadge'
import { CompleteTaskModal } from '@/features/activities/components/CompleteTaskModal'
import { RescheduleModal } from '@/features/activities/components/RescheduleModal'
import { TaskPriorityBadge } from '@/features/activities/components/TaskPriorityBadge'
import { TaskStatusBadge } from '@/features/activities/components/TaskStatusBadge'
import { useHasPermission } from '@/features/auth/auth-context'

type ViewMode = 'todays-work' | 'my-tasks' | 'overdue' | 'upcoming' | 'completed' | 'all'

export function ActivitiesPage() {
  const queryClient = useQueryClient()
  const canCreate = useHasPermission('ACTIVITY_CREATE')
  const canUpdate = useHasPermission('ACTIVITY_UPDATE')
  const canDelete = useHasPermission('ACTIVITY_DELETE')

  const [view, setView] = useState<ViewMode>('todays-work')
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [entityTypeFilter, setEntityTypeFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [sort, setSort] = useState('dueDate,asc')
  const [page, setPage] = useState(0)
  const pageSize = 20

  // Modal states
  const [activityModalOpen, setActivityModalOpen] = useState(false)
  const [editingActivity, setEditingActivity] = useState<ActivityDetail | null>(null)
  const [completingActivity, setCompletingActivity] = useState<ActivityDetail | null>(null)
  const [reschedulingActivity, setReschedulingActivity] = useState<ActivityDetail | null>(null)

  const countsQuery = useQuery(activityCountsQuery())

  const filters: ActivityFilters = {
    view: view === 'all' ? null : view,
    search: search.trim() !== '' ? search.trim() : null,
    type: typeFilter !== '' ? typeFilter : null,
    priority: priorityFilter !== '' ? priorityFilter : null,
    entityType: entityTypeFilter !== '' ? entityTypeFilter : null,
    status: statusFilter !== '' ? statusFilter : null,
  }

  const listQuery = useQuery(activitiesQuery({ filters, page, size: pageSize, sort }))

  function invalidateAll() {
    void queryClient.invalidateQueries({ queryKey: activityKeys.all })
  }

  const createMut = useMutation({
    mutationFn: (data: CreateActivityPayload) => createActivity(data),
    onSuccess: invalidateAll,
  })

  const updateMut = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<CreateActivityPayload> }) => updateActivity(id, data),
    onSuccess: invalidateAll,
  })

  const completeMut = useMutation({
    mutationFn: ({ id, outcome }: { id: string; outcome: string }) => completeActivity(id, { outcome }),
    onSuccess: invalidateAll,
  })

  const rescheduleMut = useMutation({
    mutationFn: ({ id, dueDate, reminderAt }: { id: string; dueDate: string; reminderAt?: string | null }) =>
      rescheduleActivity(id, { dueDate, reminderAt }),
    onSuccess: invalidateAll,
  })

  const dismissReminderMut = useMutation({
    mutationFn: (id: string) => dismissReminder(id),
    onSuccess: invalidateAll,
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => deleteActivity(id),
    onSuccess: invalidateAll,
  })

  const counts = countsQuery.data ?? {
    todaysWorkCount: 0,
    myTasksCount: 0,
    overdueCount: 0,
    upcomingCount: 0,
    completedCount: 0,
    totalOpenCount: 0,
  }

  const viewTabs: { id: ViewMode; label: string; count: number; icon: typeof CalendarCheck; badgeClass?: string }[] = [
    { id: 'todays-work', label: "Today's Work", count: counts.todaysWorkCount, icon: CalendarCheck },
    { id: 'my-tasks', label: 'My Tasks', count: counts.myTasksCount, icon: User },
    {
      id: 'overdue',
      label: 'Overdue Tasks',
      count: counts.overdueCount,
      icon: AlertCircle,
      badgeClass: counts.overdueCount > 0 ? 'bg-destructive text-white' : undefined,
    },
    { id: 'upcoming', label: 'Upcoming Tasks', count: counts.upcomingCount, icon: Clock },
    { id: 'completed', label: 'Completed Tasks', count: counts.completedCount, icon: CheckCircle2 },
    { id: 'all', label: 'All Activities', count: counts.totalOpenCount, icon: ListTodo },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Activities & Follow-ups"
        description="Manage calls, meetings, follow-ups, and tasks across customers, leads, opportunities, and projects."
        action={
          canCreate ? (
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setEditingActivity(null)
                  setActivityModalOpen(true)
                }}
              >
                <Plus className="h-4 w-4 mr-1" />
                Log Activity
              </Button>
              <Button
                size="sm"
                onClick={() => {
                  setEditingActivity(null)
                  setActivityModalOpen(true)
                }}
              >
                <Plus className="h-4 w-4 mr-1" />
                New Task
              </Button>
            </div>
          ) : undefined
        }
      />

      {/* Top Views Tabs */}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        {viewTabs.map((tab) => {
          const Icon = tab.icon
          const active = view === tab.id
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => {
                setView(tab.id)
                setPage(0)
              }}
              className={`flex flex-col items-start gap-1 rounded-xl border p-3 text-left transition-all hover:bg-muted/50 ${
                active ? 'border-primary bg-primary/5 shadow-xs ring-1 ring-primary' : 'bg-card'
              }`}
            >
              <div className="flex w-full items-center justify-between">
                <Icon className={`h-4 w-4 ${active ? 'text-primary' : 'text-muted-foreground'}`} />
                <Badge
                  variant={active ? 'default' : 'secondary'}
                  className={`text-xs ${tab.badgeClass ?? ''}`}
                >
                  {tab.count}
                </Badge>
              </div>
              <span className={`text-xs font-semibold ${active ? 'text-foreground' : 'text-muted-foreground'}`}>
                {tab.label}
              </span>
            </button>
          )
        })}
      </div>

      {/* Filter and Search Bar */}
      <Card>
        <CardContent className="p-4 space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search activities, tasks, entities, outcomes..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setPage(0)
                }}
                className="pl-9"
              />
            </div>

            <Select
              value={typeFilter}
              onChange={(e) => {
                setTypeFilter(e.target.value)
                setPage(0)
              }}
              className="w-[140px]"
            >
              <option value="">All Types</option>
              {ACTIVITY_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </Select>

            <Select
              value={entityTypeFilter}
              onChange={(e) => {
                setEntityTypeFilter(e.target.value)
                setPage(0)
              }}
              className="w-[150px]"
            >
              <option value="">All Entities</option>
              {ENTITY_TYPES.map((e) => (
                <option key={e.value} value={e.value}>
                  {e.label}
                </option>
              ))}
            </Select>

            <Select
              value={priorityFilter}
              onChange={(e) => {
                setPriorityFilter(e.target.value)
                setPage(0)
              }}
              className="w-[130px]"
            >
              <option value="">All Priorities</option>
              {TASK_PRIORITIES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </Select>

            <Select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value)
                setPage(0)
              }}
              className="w-[130px]"
            >
              <option value="">All Statuses</option>
              {TASK_STATUSES.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>

            <Select
              value={sort}
              onChange={(e) => setSort(e.target.value)}
              className="w-[160px]"
            >
              <option value="dueDate,asc">Due Date (Earliest)</option>
              <option value="dueDate,desc">Due Date (Latest)</option>
              <option value="occurredAt,desc">Recently Created</option>
              <option value="priority,desc">Priority</option>
              <option value="title,asc">Title (A-Z)</option>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* Main Table / List */}
      <Card>
        <CardContent className="p-0">
          {listQuery.isPending ? (
            <div className="p-6 space-y-3">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : listQuery.isError ? (
            <div className="p-6">
              <ErrorState title="Failed to load activities" error={listQuery.error} onRetry={() => void listQuery.refetch()} />
            </div>
          ) : listQuery.data.items.length === 0 ? (
            <div className="py-12 text-center">
              <Clock className="mx-auto h-10 w-10 text-muted-foreground/60" />
              <h3 className="mt-2 text-sm font-semibold">No activities found</h3>
              <p className="text-xs text-muted-foreground max-w-sm mx-auto mt-1">
                There are no activities or tasks matching the current view and filters. Log an interaction or schedule a follow-up.
              </p>
              {canCreate ? (
                <Button
                  size="sm"
                  className="mt-4"
                  onClick={() => {
                    setEditingActivity(null)
                    setActivityModalOpen(true)
                  }}
                >
                  <Plus className="h-4 w-4 mr-1" />
                  Create First Activity
                </Button>
              ) : null}
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[40px]"></TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Title & Details</TableHead>
                  <TableHead>Related Entity</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Due Date</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Owner</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {listQuery.data.items.map((item) => {
                  const isDone = item.status === 'COMPLETED'
                  const isOverdue =
                    !isDone && item.status !== 'CANCELLED' && item.dueDate && new Date(item.dueDate) < new Date()

                  return (
                    <TableRow key={item.id} className={isDone ? 'opacity-70 bg-muted/20' : undefined}>
                      <TableCell>
                        {canUpdate ? (
                          <input
                            type="checkbox"
                            checked={isDone}
                            disabled={isDone || completeMut.isPending}
                            onChange={() => {
                              if (!isDone) {
                                setCompletingActivity(item)
                              }
                            }}
                            className="h-4 w-4 rounded border-gray-300 text-emerald-600 focus:ring-emerald-500 cursor-pointer"
                            aria-label={`Mark ${item.title} as completed`}
                          />
                        ) : null}
                      </TableCell>

                      <TableCell>
                        <ActivityTypeBadge type={item.type} />
                      </TableCell>

                      <TableCell className="max-w-xs">
                        <div className="grid">
                          <span className={`font-medium ${isDone ? 'line-through text-muted-foreground' : ''}`}>
                            {item.title}
                          </span>
                          {item.description ? (
                            <span className="text-xs text-muted-foreground line-clamp-1">{item.description}</span>
                          ) : null}
                          {item.outcome ? (
                            <span className="text-xs text-emerald-700 dark:text-emerald-400 mt-0.5">
                              ✓ {item.outcome}
                            </span>
                          ) : null}
                          <div className="flex items-center gap-2 mt-1">
                            {item.isRecurring ? (
                              <Badge variant="outline" className="gap-1 text-[10px] text-primary border-primary/30">
                                <Repeat className="h-2.5 w-2.5" />
                                <span>{labelOf(RECURRENCE_PATTERNS, item.recurrencePattern)}</span>
                              </Badge>
                            ) : null}
                            {item.reminderAt && item.reminderStatus === 'PENDING' ? (
                              <Badge
                                variant="outline"
                                onClick={() => {
                                  if (window.confirm('Dismiss this reminder?')) {
                                    dismissReminderMut.mutate(item.id)
                                  }
                                }}
                                className="gap-1 text-[10px] text-amber-600 border-amber-300 cursor-pointer hover:bg-amber-50"
                              >
                                <Bell className="h-2.5 w-2.5" />
                                <span>Reminder (Dismiss)</span>
                              </Badge>
                            ) : null}
                          </div>
                        </div>
                      </TableCell>

                      <TableCell>
                        <div className="grid text-xs">
                          <span className="font-medium text-foreground">
                            {item.entityName ?? '—'}
                          </span>
                          <span className="text-muted-foreground">
                            {labelOf(ENTITY_TYPES, item.entityType)}
                          </span>
                        </div>
                      </TableCell>

                      <TableCell>
                        <TaskPriorityBadge priority={item.priority} />
                      </TableCell>

                      <TableCell>
                        {item.dueDate ? (
                          <div className={`grid text-xs ${isOverdue ? 'text-destructive font-semibold' : 'text-muted-foreground'}`}>
                            <span>{new Date(item.dueDate).toLocaleDateString()}</span>
                            <span>{new Date(item.dueDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                          </div>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </TableCell>

                      <TableCell>
                        <TaskStatusBadge status={item.status} />
                      </TableCell>

                      <TableCell>
                        <span className="text-xs font-medium">{item.ownerName}</span>
                      </TableCell>

                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          {!isDone && canUpdate ? (
                            <>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setCompletingActivity(item)}
                                className="h-7 text-xs px-2 text-emerald-600 hover:text-emerald-700"
                              >
                                Done
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setReschedulingActivity(item)}
                                className="h-7 text-xs px-2"
                              >
                                Reschedule
                              </Button>
                            </>
                          ) : null}
                          {canUpdate ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => {
                                setEditingActivity(item)
                                setActivityModalOpen(true)
                              }}
                              className="h-7 text-xs px-2"
                            >
                              Edit
                            </Button>
                          ) : null}
                          {canDelete ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              disabled={deleteMut.isPending}
                              onClick={() => {
                                if (window.confirm(`Delete activity "${item.title}"?`)) {
                                  deleteMut.mutate(item.id)
                                }
                              }}
                              className="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
                              aria-label="Delete"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          ) : null}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}

          {/* Pagination */}
          {listQuery.data && listQuery.data.totalPages > 1 ? (
            <div className="flex items-center justify-between border-t px-4 py-3 text-xs text-muted-foreground">
              <span>
                Page {listQuery.data.page + 1} of {listQuery.data.totalPages} ({listQuery.data.totalItems} items)
              </span>
              <div className="flex gap-1">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= listQuery.data.totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>

      {/* Modals */}
      {activityModalOpen ? (
        <ActivityModal
          initial={editingActivity}
          onClose={() => {
            setActivityModalOpen(false)
            setEditingActivity(null)
          }}
          onSubmit={async (data) => {
            if (editingActivity) {
              await updateMut.mutateAsync({ id: editingActivity.id, data })
            } else {
              await createMut.mutateAsync(data)
            }
          }}
        />
      ) : null}

      {completingActivity ? (
        <CompleteTaskModal
          activity={completingActivity}
          onClose={() => setCompletingActivity(null)}
          onComplete={async (outcome) => {
            await completeMut.mutateAsync({ id: completingActivity.id, outcome })
          }}
        />
      ) : null}

      {reschedulingActivity ? (
        <RescheduleModal
          activity={reschedulingActivity}
          onClose={() => setReschedulingActivity(null)}
          onReschedule={async (dueDate, reminderAt) => {
            await rescheduleMut.mutateAsync({ id: reschedulingActivity.id, dueDate, reminderAt })
          }}
        />
      ) : null}
    </div>
  )
}
