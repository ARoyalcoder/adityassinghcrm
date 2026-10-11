import { Badge } from '@/components/ui/badge'
import { CUSTOMER_STATUSES, labelOf } from '@/features/customers/api'

/** The customer's status in words; a blocked customer stands out. */
export function CustomerStatusBadge({ status }: { status: string }) {
  const variant = status === 'BLOCKED' ? 'destructive' : status === 'ACTIVE' ? 'outline' : 'secondary'
  return <Badge variant={variant}>{labelOf(CUSTOMER_STATUSES, status)}</Badge>
}
