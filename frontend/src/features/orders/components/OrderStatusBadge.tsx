import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { OrderStatus } from '../api'

interface OrderStatusBadgeProps {
  status: OrderStatus
  className?: string
}

export function OrderStatusBadge({ status, className }: OrderStatusBadgeProps) {
  let badgeClass = 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300'
  let label = status as string

  switch (status) {
    case 'CONFIRMED':
      badgeClass = 'bg-blue-100 text-blue-800 border-blue-300 dark:bg-blue-950/40 dark:text-blue-300'
      label = 'Confirmed'
      break
    case 'IN_PROGRESS':
      badgeClass = 'bg-amber-100 text-amber-800 border-amber-300 dark:bg-amber-950/40 dark:text-amber-300'
      label = 'In Progress'
      break
    case 'PARTIALLY_DELIVERED':
      badgeClass = 'bg-purple-100 text-purple-800 border-purple-300 dark:bg-purple-950/40 dark:text-purple-300'
      label = 'Partially Delivered'
      break
    case 'COMPLETED':
      badgeClass = 'bg-emerald-100 text-emerald-800 border-emerald-300 dark:bg-emerald-950/40 dark:text-emerald-300'
      label = 'Completed'
      break
    case 'CANCELLED':
      badgeClass = 'bg-rose-100 text-rose-800 border-rose-300 dark:bg-rose-950/40 dark:text-rose-300'
      label = 'Cancelled'
      break
  }

  return (
    <Badge variant="outline" className={cn('font-medium', badgeClass, className)}>
      {label}
    </Badge>
  )
}
