import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  FolderKanban,
  List,
  Plus,
  Settings,
  Search,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { ProjectStatusBadge, PriorityBadge } from '@/features/projects/components/ProjectStatusBadge'
import { ProjectKanbanBoard } from '@/features/projects/components/ProjectKanbanBoard'
import { CreateProjectModal } from '@/features/projects/components/CreateProjectModal'
import { ProjectStatusSettingsModal } from '@/features/projects/components/ProjectStatusSettingsModal'
import { projectsApi } from '@/features/projects/api'

export function ProjectsPage() {
  const [viewMode, setViewMode] = useState<'list' | 'kanban'>('list')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [page] = useState(0)

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isSettingsOpen, setIsSettingsOpen] = useState(false)

  const { data: dashboard } = useQuery(projectsApi.dashboardQuery())
  const { data: statuses } = useQuery(projectsApi.statusesQuery())
  const { data: kanbanBoard } = useQuery(projectsApi.kanbanQuery())
  const { data: listResponse, isLoading: isListLoading } = useQuery(
    projectsApi.listQuery({
      search: search || undefined,
      status: statusFilter !== 'ALL' ? statusFilter : undefined,
      priority: priorityFilter || undefined,
      page,
      size: 20,
    }),
  )

  return (
    <div className="space-y-6 p-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Project Management</h1>
          <p className="text-sm text-muted-foreground">
            Track customer deliveries, milestone roadmaps, tasks, and budgets
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* View toggle */}
          <div className="bg-muted p-1 rounded-lg flex items-center gap-1 border">
            <Button
              variant={viewMode === 'list' ? 'secondary' : 'ghost'}
              size="sm"
              className="h-8 text-xs gap-1.5"
              onClick={() => setViewMode('list')}
            >
              <List className="h-4 w-4" />
              List
            </Button>
            <Button
              variant={viewMode === 'kanban' ? 'secondary' : 'ghost'}
              size="sm"
              className="h-8 text-xs gap-1.5"
              onClick={() => setViewMode('kanban')}
            >
              <FolderKanban className="h-4 w-4" />
              Kanban
            </Button>
          </div>

          <Button
            variant="outline"
            size="sm"
            className="h-8 text-xs gap-1.5"
            onClick={() => setIsSettingsOpen(true)}
          >
            <Settings className="h-4 w-4" />
            Statuses
          </Button>

          <Button size="sm" className="h-8 text-xs gap-1.5" onClick={() => setIsCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            New Project
          </Button>
        </div>
      </div>

      {/* KPI Cards */}
      {dashboard && (
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
          <Card className="shadow-sm">
            <CardContent className="p-3.5 space-y-1">
              <span className="text-xs text-muted-foreground font-medium">Total Projects</span>
              <p className="text-xl font-bold">{dashboard.totalProjects}</p>
            </CardContent>
          </Card>
          <Card className="shadow-sm">
            <CardContent className="p-3.5 space-y-1">
              <span className="text-xs text-muted-foreground font-medium">Active</span>
              <p className="text-xl font-bold text-blue-600">{dashboard.activeProjects}</p>
            </CardContent>
          </Card>
          <Card className="shadow-sm">
            <CardContent className="p-3.5 space-y-1">
              <span className="text-xs text-muted-foreground font-medium">Completed</span>
              <p className="text-xl font-bold text-emerald-600">{dashboard.completedProjects}</p>
            </CardContent>
          </Card>
          <Card className="shadow-sm">
            <CardContent className="p-3.5 space-y-1">
              <span className="text-xs text-muted-foreground font-medium">Delayed</span>
              <p className="text-xl font-bold text-rose-600">{dashboard.delayedProjects}</p>
            </CardContent>
          </Card>
          <Card className="shadow-sm">
            <CardContent className="p-3.5 space-y-1">
              <span className="text-xs text-muted-foreground font-medium">Total Budget</span>
              <p className="text-xl font-bold">₹{dashboard.totalBudget.toLocaleString('en-IN')}</p>
            </CardContent>
          </Card>
          <Card className="shadow-sm">
            <CardContent className="p-3.5 space-y-1">
              <span className="text-xs text-muted-foreground font-medium">Total Revenue</span>
              <p className="text-xl font-bold">₹{dashboard.totalRevenue.toLocaleString('en-IN')}</p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Filter Toolbar (for List View) */}
      {viewMode === 'list' && (
        <div className="flex flex-wrap items-center justify-between gap-3 bg-card p-3 rounded-xl border">
          <div className="flex flex-wrap items-center gap-2 flex-1">
            <div className="relative min-w-[200px] flex-1 max-w-sm">
              <Search className="h-4 w-4 absolute left-2.5 top-2.5 text-muted-foreground" />
              <Input
                placeholder="Search projects, client, order..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-8 h-9 text-xs"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-9 text-xs bg-background border rounded px-2"
            >
              <option value="ALL">All Statuses</option>
              {statuses?.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.name}
                </option>
              ))}
            </select>

            <select
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
              className="h-9 text-xs bg-background border rounded px-2"
            >
              <option value="">All Priorities</option>
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
              <option value="CRITICAL">CRITICAL</option>
            </select>
          </div>
        </div>
      )}

      {/* Main View Display */}
      {viewMode === 'kanban' ? (
        kanbanBoard ? (
          <ProjectKanbanBoard board={kanbanBoard} />
        ) : (
          <div className="text-center py-12 text-muted-foreground text-sm">Loading Kanban board...</div>
        )
      ) : (
        /* List View */
        <div className="border rounded-xl bg-card overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead className="bg-muted/50 border-b font-medium text-muted-foreground">
                <tr>
                  <th className="p-3">Project #</th>
                  <th className="p-3">Title & Customer</th>
                  <th className="p-3">Status</th>
                  <th className="p-3">Priority</th>
                  <th className="p-3">Project Manager</th>
                  <th className="p-3">Progress</th>
                  <th className="p-3">Budget</th>
                  <th className="p-3">Expected Date</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {isListLoading ? (
                  <tr>
                    <td colSpan={8} className="text-center py-8 text-muted-foreground">
                      Loading projects...
                    </td>
                  </tr>
                ) : listResponse?.items.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="text-center py-12 text-muted-foreground">
                      No projects found matching the filter criteria.
                    </td>
                  </tr>
                ) : (
                  listResponse?.items.map((p) => (
                    <tr key={p.id} className="hover:bg-muted/30 transition-colors">
                      <td className="p-3 font-mono font-medium">
                        <Link to={`/projects/${p.id}`} className="text-primary hover:underline">
                          {p.projectNumber}
                        </Link>
                      </td>
                      <td className="p-3">
                        <Link to={`/projects/${p.id}`} className="font-semibold block hover:text-primary">
                          {p.title}
                        </Link>
                        <span className="text-[11px] text-muted-foreground">{p.customerName}</span>
                      </td>
                      <td className="p-3">
                        <ProjectStatusBadge status={p.status} />
                      </td>
                      <td className="p-3">
                        <PriorityBadge priority={p.priority} />
                      </td>
                      <td className="p-3 text-muted-foreground">{p.projectManagerName}</td>
                      <td className="p-3">
                        <div className="flex items-center gap-2">
                          <div className="w-16 bg-muted rounded-full h-1.5 overflow-hidden">
                            <div
                              className="bg-primary h-full rounded-full"
                              style={{ width: `${Math.min(100, Math.max(0, p.progressPercentage))}%` }}
                            />
                          </div>
                          <span className="text-[11px] font-medium">{p.progressPercentage}%</span>
                        </div>
                      </td>
                      <td className="p-3 font-medium">₹{p.budget.toLocaleString('en-IN')}</td>
                      <td className="p-3 text-muted-foreground">
                        {new Date(p.expectedCompletionDate).toLocaleDateString()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Modals */}
      <CreateProjectModal open={isCreateOpen} onOpenChange={setIsCreateOpen} />
      <ProjectStatusSettingsModal open={isSettingsOpen} onOpenChange={setIsSettingsOpen} />
    </div>
  )
}
