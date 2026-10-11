import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useHasPermission } from '@/features/auth/auth-context'
import { activityQuery, formatDateTime, formatValue } from '@/features/audit/api'

type ActivityTimelineProps = {
  /** The entity name given in the backend's `@Audited(entity = ...)`, e.g. "Service". */
  entityType: string
  entityId: string
}

/**
 * The history of one record: who changed what and when. Drop it onto any record's page:
 *
 *   <ActivityTimeline entityType="Service" entityId={service.id} />
 *
 * It needs nothing from the page beyond the two props, and shows nothing to users without AUDIT_VIEW.
 */
export function ActivityTimeline({ entityType, entityId }: ActivityTimelineProps) {
  const allowed = useHasPermission('AUDIT_VIEW')
  const activity = useQuery({ ...activityQuery(entityType, entityId), enabled: allowed })

  if (!allowed) {
    return null
  }

  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>Activity</CardTitle>
        <CardDescription>
          The most recent changes to this record.{' '}
          <Link
            className="underline underline-offset-2"
            to={`/admin/audit?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`}
          >
            Open in the audit log
          </Link>
        </CardDescription>
      </CardHeader>
      <CardContent>
        {activity.isPending ? (
          <Skeleton className="h-16 w-full" aria-busy="true" aria-label="Loading activity" />
        ) : activity.isError ? (
          <ErrorState title="Could not load activity" error={activity.error} onRetry={() => void activity.refetch()} />
        ) : activity.data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No activity recorded yet.</p>
        ) : (
          <ActivityItems items={activity.data.items} />
        )}
      </CardContent>
    </Card>
  )
}

type ActivityItem = {
  id: string
  occurredAt: string
  actorLabel: string
  message: string
  changes?: { field: string; from?: unknown; to?: unknown }[] | null
}

/** The timeline itself, for any source of activity (the audit endpoint, or a module's own). */
export function ActivityItems({ items }: { items: ActivityItem[] }) {
  return (
    <ol className="grid gap-4" aria-label="Activity">
      {items.map((item) => (
        <li key={item.id} className="grid gap-1 border-l-2 pl-3 text-sm">
          <p>{item.message}</p>
          {item.changes && item.changes.length > 0 ? (
            <ul className="grid gap-0.5 text-xs text-muted-foreground">
              {item.changes.map((change) => (
                <li key={change.field}>
                  <span className="font-mono">{change.field}</span>: {formatValue(change.from)} →{' '}
                  <span className="text-foreground">{formatValue(change.to)}</span>
                </li>
              ))}
            </ul>
          ) : null}
          <p className="text-xs text-muted-foreground">
            {item.actorLabel} · {formatDateTime(item.occurredAt)}
          </p>
        </li>
      ))}
    </ol>
  )
}
