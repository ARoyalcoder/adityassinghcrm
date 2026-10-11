import { useQuery } from '@tanstack/react-query'
import { Calendar, Flag, CheckCircle2, Clock } from 'lucide-react'
import { projectsApi } from '../api'

interface ProjectTimelineTabProps {
  projectId: string
}

export function ProjectTimelineTab({ projectId }: ProjectTimelineTabProps) {
  const { data: timeline, isLoading } = useQuery(projectsApi.timelineQuery(projectId))

  if (isLoading) {
    return <div className="text-center py-12 text-muted-foreground text-sm">Loading project timeline...</div>
  }

  if (!timeline || timeline.items.length === 0) {
    return (
      <div className="text-center py-12 border border-dashed rounded-lg bg-muted/20 text-muted-foreground">
        No milestones or tasks scheduled yet. Add milestones and tasks with dates to see the timeline.
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between pb-3 border-b">
        <div>
          <h3 className="text-base font-semibold">Project Roadmap & Timeline</h3>
          <p className="text-xs text-muted-foreground">
            Timeline spans {new Date(timeline.startDate).toLocaleDateString()} to{' '}
            {new Date(timeline.expectedCompletionDate).toLocaleDateString()}
          </p>
        </div>
      </div>

      <div className="relative border-l-2 border-primary/30 ml-4 pl-6 space-y-6">
        {timeline.items.map((item) => {
          const isMilestone = item.type === 'MILESTONE'
          const isCompleted = item.status === 'COMPLETED'

          return (
            <div key={item.id} className="relative group">
              {/* Dot / Icon node */}
              <div
                className={`absolute -left-[33px] top-1.5 w-6 h-6 rounded-full flex items-center justify-center border-2 bg-background ${
                  isCompleted
                    ? 'border-emerald-600 text-emerald-600'
                    : isMilestone
                    ? 'border-primary text-primary'
                    : 'border-muted-foreground text-muted-foreground'
                }`}
              >
                {isCompleted ? (
                  <CheckCircle2 className="h-3.5 w-3.5" />
                ) : isMilestone ? (
                  <Flag className="h-3.5 w-3.5" />
                ) : (
                  <Clock className="h-3.5 w-3.5" />
                )}
              </div>

              {/* Timeline item card */}
              <div className="p-3.5 rounded-xl border bg-card shadow-sm space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span
                      className={`text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded ${
                        isMilestone
                          ? 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-300'
                          : 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                      }`}
                    >
                      {item.type}
                    </span>
                    <h4 className="font-semibold text-sm">{item.title}</h4>
                  </div>
                  <span
                    className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                      isCompleted
                        ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                        : 'bg-muted text-muted-foreground'
                    }`}
                  >
                    {item.status}
                  </span>
                </div>

                <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
                  {item.startDate && item.dueDate && (
                    <span className="flex items-center gap-1">
                      <Calendar className="h-3.5 w-3.5" />
                      {new Date(item.startDate).toLocaleDateString()} — {new Date(item.dueDate).toLocaleDateString()}
                    </span>
                  )}
                  {item.assignedToName && <span>Assignee: {item.assignedToName}</span>}
                  {item.progressPercentage != null && <span>Progress: {item.progressPercentage}%</span>}
                </div>

                {/* Progress bar */}
                <div className="w-full bg-muted rounded-full h-1.5 overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all ${isCompleted ? 'bg-emerald-600' : 'bg-primary'}`}
                    style={{ width: `${Math.min(100, Math.max(0, item.progressPercentage))}%` }}
                  />
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
