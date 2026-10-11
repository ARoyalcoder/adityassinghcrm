import type { ReactNode } from 'react'
import { useAuth } from '@/features/auth/auth-context'
import { RequirePermission } from '@/features/auth/components/RequirePermission'

/**
 * Like {@link RequirePermission}, for a page that several kinds of user may open (a technician
 * opens their own job; a dispatcher opens any). Shows the page if the user holds any of the
 * permissions. As always, the backend decides what they may actually see and do there.
 */
export function RequireAnyPermission({ permissions, children }: { permissions: string[]; children: ReactNode }) {
  const { user } = useAuth()
  const held = permissions.find((permission) => user?.permissions.includes(permission))
  // With none held, fall through to the single-permission guard, which renders the "no access" notice.
  return <RequirePermission permission={held ?? permissions[0]}>{children}</RequirePermission>
}
