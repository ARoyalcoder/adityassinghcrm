import { CheckCircle2, Clock, PlayCircle, XCircle } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { labelOf, TASK_STATUSES } from '@/features/activities/api'

export function TaskStatusBadge({ status }: { status: string }) {
  switch (status) {
    case 'COMPLETED':
      return (
        <Badge variant="outline" className="gap-1 border-emerald-300 bg-emerald-50 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
          <CheckCircle2 className="h-3 w-3" />
          <span>{labelOf(TASK_STATUSES, status)}</span>
        </Badge>
      )
    case 'IN_PROGRESS':
      return (
        <Badge variant="outline" className="gap-1 border-blue-300 bg-blue-50 text-blue-800 dark:bg-blue-950/40 dark:text-blue-300">
          <PlayCircle className="h-3 w-3" />
          <span>{labelOf(TASK_STATUSES, status)}</span>
        </Badge>
      )
    case 'CANCELLED':
      return (
        <Badge variant="outline" className="gap-1 border-slate-300 bg-slate-50 text-slate-600 dark:bg-slate-900 dark:text-slate-400">
          <XCircle className="h-3 w-3" />
          <span>{labelOf(TASK_STATUSES, status)}</span>
        </Badge>
      )
    default:
      return (
        <Badge variant="outline" className="gap-1 border-muted-foreground/30 text-muted-foreground">
          <Clock className="h-3 w-3" />
          <span>{labelOf(TASK_STATUSES, status)}</span>
        </Badge>
      )
  }
}
