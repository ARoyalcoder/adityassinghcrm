import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  User,
  ShoppingBag,
  Wrench,
  Layers,
  ListTodo,
  Clock,
  FileText,
  Users,
  Trash2,
  Activity,
  Camera,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ProjectStatusBadge, PriorityBadge } from '../components/ProjectStatusBadge'
import { ProjectMilestonesTab } from '../components/ProjectMilestonesTab'
import { ProjectTasksTab } from '../components/ProjectTasksTab'
import { ProjectTimelineTab } from '../components/ProjectTimelineTab'
import { ProjectDeliverablesTab } from '../components/ProjectDeliverablesTab'
import { ProjectMembersTab } from '../components/ProjectMembersTab'
import { ProjectHealthTab } from '../components/ProjectHealthTab'
import { ProjectProofTab } from '../components/ProjectProofTab'
import { projectsApi } from '../api'

type TabKey = 'health' | 'proof' | 'milestones' | 'tasks' | 'timeline' | 'deliverables' | 'team'

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<TabKey>('milestones')

  const { data: project, isLoading } = useQuery(projectsApi.detailQuery(id!))
  const { data: statuses } = useQuery(projectsApi.statusesQuery())

  const statusMutation = useMutation({
    mutationFn: (newStatus: string) => projectsApi.updateStatus(id!, newStatus),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', id] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => projectsApi.deleteProject(id!),
    onSuccess: () => {
      navigate('/projects')
    },
  })

  if (isLoading) {
    return <div className="p-8 text-center text-muted-foreground text-sm">Loading project details...</div>
  }

  if (!project) {
    return (
      <div className="p-8 text-center space-y-3">
        <p className="text-muted-foreground">Project not found.</p>
        <Button variant="outline" size="sm" asChild>
          <Link to="/projects">Back to Projects</Link>
        </Button>
      </div>
    )
  }

  const milestones = project.milestones ?? []
  const tasks = project.tasks ?? []
  const deliverables = project.deliverables ?? []
  const members = project.members ?? []

  return (
    <div className="space-y-6 p-6">
      {/* Navigation & Header */}
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm" className="h-8 gap-1 text-xs" asChild>
            <Link to="/projects">
              <ArrowLeft className="h-4 w-4" />
              Projects
            </Link>
          </Button>
          <span className="text-muted-foreground text-xs">/</span>
          <span className="text-xs font-mono font-medium text-muted-foreground">
            {project.projectNumber}
          </span>
        </div>

        <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4 bg-card p-5 rounded-xl border">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="text-xl font-bold tracking-tight">{project.title}</h1>
              <ProjectStatusBadge status={project.status} />
              <PriorityBadge priority={project.priority} />
            </div>

            <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
              <Link
                to={`/customers/${project.customerId}`}
                className="hover:text-primary transition-colors font-medium text-foreground"
              >
                Customer: {project.customerName}
              </Link>
              {project.orderNumber && (
                <span className="flex items-center gap-1">
                  <ShoppingBag className="h-3.5 w-3.5" />
                  Order: {project.orderNumber}
                </span>
              )}
              {project.serviceName && (
                <span className="flex items-center gap-1">
                  <Wrench className="h-3.5 w-3.5" />
                  Service: {project.serviceName}
                </span>
              )}
              <span className="flex items-center gap-1">
                <User className="h-3.5 w-3.5" />
                PM: {project.projectManagerName}
              </span>
            </div>

            {project.description && (
              <p className="text-xs text-muted-foreground max-w-2xl pt-1">
                {project.description}
              </p>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {/* Status change select */}
            <div className="flex items-center gap-1.5">
              <span className="text-xs text-muted-foreground">Status:</span>
              <select
                value={project.status}
                onChange={(e) => statusMutation.mutate(e.target.value)}
                className="h-8 text-xs bg-background border rounded px-2 font-medium"
              >
                {statuses?.map((s) => (
                  <option key={s.code} value={s.code}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>

            <Button
              variant="outline"
              size="sm"
              className="text-destructive hover:bg-destructive/10 h-8 text-xs"
              onClick={() => {
                if (confirm('Are you sure you want to delete / archive this project?')) {
                  deleteMutation.mutate()
                }
              }}
            >
              <Trash2 className="h-3.5 w-3.5 mr-1" />
              Delete
            </Button>
          </div>
        </div>
      </div>

      {/* Progress & Metrics Ribbon */}
      <div className="grid grid-cols-2 md:grid-cols-6 gap-3">
        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1.5">
            <div className="flex justify-between items-center text-xs text-muted-foreground font-medium">
              <span>Overall Progress</span>
              <span>{project.progressPercentage}%</span>
            </div>
            <div className="w-full bg-muted rounded-full h-2 overflow-hidden">
              <div
                className="bg-primary h-full rounded-full transition-all"
                style={{ width: `${Math.min(100, Math.max(0, project.progressPercentage))}%` }}
              />
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1.5">
            <div className="flex justify-between items-center text-xs text-muted-foreground font-medium">
              <span>Health Score</span>
              <span className={`font-bold ${
                (project.health?.overallScore ?? 100) >= 80
                  ? 'text-emerald-600'
                  : (project.health?.overallScore ?? 100) >= 60
                  ? 'text-amber-600'
                  : 'text-rose-600'
              }`}>
                {project.health?.overallScore ?? 100}%
              </span>
            </div>
            <div className="w-full bg-muted rounded-full h-2 overflow-hidden">
              <div
                className={`h-full rounded-full transition-all ${
                  (project.health?.overallScore ?? 100) >= 80
                    ? 'bg-emerald-500'
                    : (project.health?.overallScore ?? 100) >= 60
                    ? 'bg-amber-500'
                    : 'bg-rose-500'
                }`}
                style={{ width: `${Math.min(100, Math.max(0, project.health?.overallScore ?? 100))}%` }}
              />
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium">Budget</span>
            <p className="text-base font-bold">₹{project.budget.toLocaleString('en-IN')}</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium">Revenue</span>
            <p className="text-base font-bold">₹{project.revenue.toLocaleString('en-IN')}</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium">Start Date</span>
            <p className="text-base font-semibold">
              {new Date(project.startDate).toLocaleDateString()}
            </p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium">Target Completion</span>
            <p className="text-base font-semibold">
              {new Date(project.expectedCompletionDate).toLocaleDateString()}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Tabs */}
      <div className="space-y-4">
        <div className="border-b flex gap-4 text-sm overflow-x-auto">
          <button
            onClick={() => setActiveTab('health')}
            className={`pb-2.5 font-medium border-b-2 flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              activeTab === 'health'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <Activity className="h-4 w-4" />
            Health & Profitability
            {project.health && (
              <span className={`text-[11px] px-1.5 py-0.5 rounded-full font-bold ${
                project.health.overallScore >= 80
                  ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
                  : project.health.overallScore >= 60
                  ? 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300'
                  : 'bg-rose-100 text-rose-700 dark:bg-rose-950 dark:text-rose-300'
              }`}>
                {project.health.overallScore}%
              </span>
            )}
            {project.health?.riskAlerts && project.health.riskAlerts.length > 0 && (
              <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-rose-500 text-white font-bold">
                {project.health.riskAlerts.length}
              </span>
            )}
          </button>
          <button
            onClick={() => setActiveTab('proof')}
            className={`pb-2.5 font-medium border-b-2 flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              activeTab === 'proof'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <Camera className="h-4 w-4" />
            Proof of Work
          </button>
          <button
            onClick={() => setActiveTab('milestones')}
            className={`pb-2.5 font-medium border-b-2 flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              activeTab === 'milestones'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <Layers className="h-4 w-4" />
            Milestones ({milestones.length})
          </button>
          <button
            onClick={() => setActiveTab('tasks')}
            className={`pb-2.5 font-medium border-b-2 flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              activeTab === 'tasks'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <ListTodo className="h-4 w-4" />
            Tasks ({tasks.length})
          </button>
          <button
            onClick={() => setActiveTab('timeline')}
            className={`pb-2.5 font-medium border-b-2 flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              activeTab === 'timeline'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <Clock className="h-4 w-4" />
            Timeline & Roadmap
          </button>
          <button
            onClick={() => setActiveTab('deliverables')}
            className={`pb-2.5 font-medium border-b-2 flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              activeTab === 'deliverables'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <FileText className="h-4 w-4" />
            Deliverables ({deliverables.length})
          </button>
          <button
            onClick={() => setActiveTab('team')}
            className={`pb-2.5 font-medium border-b-2 flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              activeTab === 'team'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <Users className="h-4 w-4" />
            Team ({members.length})
          </button>
        </div>

        {/* Tab Content Display */}
        <div className="pt-2">
          {activeTab === 'health' && (
            <ProjectHealthTab projectId={project.id} health={project.health} />
          )}

          {activeTab === 'proof' && (
            <ProjectProofTab projectId={project.id} />
          )}

          {activeTab === 'milestones' && (
            <ProjectMilestonesTab projectId={project.id} milestones={milestones} />
          )}

          {activeTab === 'tasks' && (
            <ProjectTasksTab
              projectId={project.id}
              tasks={tasks}
              milestones={milestones}
              members={members}
            />
          )}

          {activeTab === 'timeline' && <ProjectTimelineTab projectId={project.id} />}

          {activeTab === 'deliverables' && (
            <ProjectDeliverablesTab
              projectId={project.id}
              deliverables={deliverables}
              milestones={milestones}
            />
          )}

          {activeTab === 'team' && (
            <ProjectMembersTab
              projectId={project.id}
              members={members}
              projectManagerId={project.projectManagerId}
            />
          )}
        </div>
      </div>
    </div>
  )
}
