import { Badge } from '@/components/ui/badge'
import { labelOf, TASK_PRIORITIES } from '@/features/activities/api'

export function TaskPriorityBadge({ priority }: { priority: string }) {
  const variant = (() => {
    switch (priority) {
      case 'URGENT':
        return 'destructive'
      case 'HIGH':
        return 'default'
      default:
        return 'secondary'
    }
  })()

  const customClass = (() => {
    switch (priority) {
      case 'HIGH':
        return 'bg-amber-600 hover:bg-amber-700 text-white border-transparent'
      case 'MEDIUM':
        return 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300 border-amber-300'
      case 'LOW':
        return 'bg-muted text-muted-foreground'
      default:
        return ''
    }
  })()

  return (
    <Badge variant={variant} className={`text-xs ${customClass}`}>
      {labelOf(TASK_PRIORITIES, priority)}
    </Badge>
  )
}
