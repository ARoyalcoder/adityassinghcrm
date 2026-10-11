import { Badge } from '@/components/ui/badge'
import { actionLabel } from '@/features/audit/api'

/** The action in words; destructive and access events stand out a little, without relying on colour alone. */
export function ActionBadge({ action }: { action: string }) {
  const variant = action === 'DELETE' ? 'destructive' : action === 'LOGIN' || action === 'LOGOUT' ? 'outline' : 'secondary'
  return <Badge variant={variant}>{actionLabel(action)}</Badge>
}
