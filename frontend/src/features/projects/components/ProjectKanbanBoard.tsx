import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { User, IndianRupee } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { PriorityBadge } from './ProjectStatusBadge'
import { projectsApi, type KanbanBoard, type ProjectSummary } from '../api'

interface ProjectKanbanBoardProps {
  board: KanbanBoard
}

export function ProjectKanbanBoard({ board }: ProjectKanbanBoardProps) {
  const queryClient = useQueryClient()
  const [movingProjectId, setMovingProjectId] = useState<string | null>(null)

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      projectsApi.updateStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
    onSettled: () => {
      setMovingProjectId(null)
    },
  })

  const handleStatusChange = (projectId: string, newStatus: string) => {
    setMovingProjectId(projectId)
    statusMutation.mutate({ id: projectId, status: newStatus })
  }

  return (
    <div className="flex gap-4 overflow-x-auto pb-4 pt-1 items-start min-h-[600px]">
      {board.columns.map((column) => {
        const { status, projects, totalBudget, totalRevenue } = column
        return (
          <div
            key={status.id}
            className="flex-shrink-0 w-80 bg-muted/40 rounded-xl p-3 border flex flex-col max-h-[85vh]"
          >
            {/* Column Header */}
            <div className="flex items-center justify-between pb-2 mb-2 border-b">
              <div className="flex items-center gap-2">
                <span
                  className="w-3 h-3 rounded-full"
                  style={{ backgroundColor: status.color || '#3b82f6' }}
                />
                <h3 className="font-semibold text-sm">{status.name}</h3>
                <span className="text-xs bg-muted px-1.5 py-0.5 rounded-full font-medium text-muted-foreground">
                  {projects.length}
                </span>
              </div>
            </div>

            {/* Column Metrics */}
            <div className="text-[11px] text-muted-foreground flex justify-between mb-3 px-1">
              <span>Budget: ₹{totalBudget.toLocaleString('en-IN')}</span>
              <span>Rev: ₹{totalRevenue.toLocaleString('en-IN')}</span>
            </div>

            {/* Column Project Cards */}
            <div className="flex-1 overflow-y-auto space-y-3 pr-1">
              {projects.length === 0 ? (
                <div className="text-center py-8 text-xs text-muted-foreground border border-dashed rounded-lg bg-background/50">
                  No projects
                </div>
              ) : (
                projects.map((project) => (
                  <KanbanProjectCard
                    key={project.id}
                    project={project}
                    allColumns={board.columns}
                    currentStatus={status.code}
                    isMoving={movingProjectId === project.id}
                    onStatusChange={(newStatus) => handleStatusChange(project.id, newStatus)}
                  />
                ))
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}

function KanbanProjectCard({
  project,
  allColumns,
  currentStatus,
  isMoving,
  onStatusChange,
}: {
  project: ProjectSummary
  allColumns: KanbanBoard['columns']
  currentStatus: string
  isMoving: boolean
  onStatusChange: (status: string) => void
}) {
  return (
    <Card className={`shadow-sm hover:shadow transition-shadow bg-card ${isMoving ? 'opacity-50 pointer-events-none' : ''}`}>
      <CardContent className="p-3.5 space-y-2.5">
        <div className="flex items-start justify-between gap-2">
          <Link
            to={`/projects/${project.id}`}
            className="text-xs font-mono font-medium text-primary hover:underline"
          >
            {project.projectNumber}
          </Link>
          <PriorityBadge priority={project.priority} />
        </div>

        <Link to={`/projects/${project.id}`} className="block">
          <h4 className="font-semibold text-sm hover:text-primary transition-colors line-clamp-1">
            {project.title}
          </h4>
          <p className="text-xs text-muted-foreground line-clamp-1 mt-0.5">
            {project.customerName}
          </p>
        </Link>

        {/* Progress bar */}
        <div className="space-y-1">
          <div className="flex justify-between text-[11px] text-muted-foreground">
            <span>Progress</span>
            <span>{project.progressPercentage}%</span>
          </div>
          <div className="w-full bg-muted rounded-full h-1.5 overflow-hidden">
            <div
              className="bg-primary h-full transition-all rounded-full"
              style={{ width: `${Math.min(100, Math.max(0, project.progressPercentage))}%` }}
            />
          </div>
        </div>

        {/* Details footer */}
        <div className="pt-2 border-t flex flex-wrap items-center justify-between text-[11px] text-muted-foreground gap-1">
          <div className="flex items-center gap-1">
            <User className="h-3 w-3" />
            <span className="truncate max-w-[90px]">{project.projectManagerName}</span>
          </div>
          <div className="flex items-center gap-1">
            <IndianRupee className="h-3 w-3" />
            <span>{project.budget.toLocaleString('en-IN')}</span>
          </div>
        </div>

        {/* Quick status transition dropdown */}
        <div className="pt-1 flex items-center justify-between gap-1">
          <span className="text-[10px] text-muted-foreground">Move:</span>
          <select
            value={currentStatus}
            onChange={(e) => onStatusChange(e.target.value)}
            className="h-6 text-[11px] bg-background border rounded px-1.5 py-0 focus:outline-none focus:ring-1 focus:ring-primary"
          >
            {allColumns.map((c) => (
              <option key={c.status.code} value={c.status.code}>
                {c.status.name}
              </option>
            ))}
          </select>
        </div>
      </CardContent>
    </Card>
  )
}
