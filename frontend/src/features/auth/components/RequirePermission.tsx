import type { ReactNode } from 'react'
import { ShieldAlert } from 'lucide-react'
import { useHasPermission } from '@/features/auth/auth-context'

type RequirePermissionProps = {
  permission: string
  children: ReactNode
  /** What to render instead. Defaults to a "no access" notice; pass `null` to render nothing. */
  fallback?: ReactNode
}

/**
 * Hides a page or a control from users who lack a permission, so they are not shown things that
 * would only fail. The backend enforces the same permission; this component protects nothing by itself.
 *
 *   <Route element={<RequirePermission permission="CUSTOMER_VIEW"><CustomersPage /></RequirePermission>} />
 *   <RequirePermission permission="CUSTOMER_DELETE" fallback={null}><DeleteButton /></RequirePermission>
 */
export function RequirePermission({ permission, children, fallback }: RequirePermissionProps) {
  const allowed = useHasPermission(permission)
  if (allowed) {
    return <>{children}</>
  }
  if (fallback !== undefined) {
    return <>{fallback}</>
  }
  return (
    <div className="flex flex-col items-start gap-3 py-12">
      <ShieldAlert className="size-5 text-muted-foreground" aria-hidden="true" />
      <h1 className="text-2xl font-semibold tracking-tight">You don't have access to this page</h1>
      <p className="text-sm text-muted-foreground">Ask an administrator if you think you should.</p>
    </div>
  )
}
