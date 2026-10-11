import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { QuotationStatus } from '../api'

interface QuotationStatusBadgeProps {
  status: QuotationStatus
  className?: string
}

export function QuotationStatusBadge({ status, className }: QuotationStatusBadgeProps) {
  let badgeClass = 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300'
  let label = status as string

  switch (status) {
    case 'DRAFT':
      badgeClass = 'bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-800 dark:text-slate-300'
      label = 'Draft'
      break
    case 'PENDING_APPROVAL':
      badgeClass = 'bg-amber-100 text-amber-800 border-amber-300 dark:bg-amber-950/40 dark:text-amber-300'
      label = 'Pending Approval'
      break
    case 'APPROVED':
      badgeClass = 'bg-blue-100 text-blue-800 border-blue-300 dark:bg-blue-950/40 dark:text-blue-300'
      label = 'Approved'
      break
    case 'SENT':
      badgeClass = 'bg-indigo-100 text-indigo-800 border-indigo-300 dark:bg-indigo-950/40 dark:text-indigo-300'
      label = 'Sent'
      break
    case 'VIEWED':
      badgeClass = 'bg-purple-100 text-purple-800 border-purple-300 dark:bg-purple-950/40 dark:text-purple-300'
      label = 'Viewed'
      break
    case 'ACCEPTED':
      badgeClass = 'bg-emerald-100 text-emerald-800 border-emerald-300 dark:bg-emerald-950/40 dark:text-emerald-300'
      label = 'Accepted'
      break
    case 'REJECTED':
      badgeClass = 'bg-rose-100 text-rose-800 border-rose-300 dark:bg-rose-950/40 dark:text-rose-300'
      label = 'Rejected'
      break
    case 'EXPIRED':
      badgeClass = 'bg-orange-100 text-orange-800 border-orange-300 dark:bg-orange-950/40 dark:text-orange-300'
      label = 'Expired'
      break
    case 'CANCELLED':
      badgeClass = 'bg-zinc-100 text-zinc-600 border-zinc-300 dark:bg-zinc-800 dark:text-zinc-400'
      label = 'Cancelled'
      break
  }

  return (
    <Badge variant="outline" className={cn('font-medium', badgeClass, className)}>
      {label}
    </Badge>
  )
}
