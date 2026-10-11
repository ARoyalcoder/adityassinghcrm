import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { auditLogQuery, formatDateTime, formatValue, type AuditDetail } from '@/features/audit/api'
import { ActionBadge } from '@/features/audit/components/ActionBadge'

const LIST = '/admin/audit'

/** One audit event in full: who, what, from where, and the values before and after. */
export function AuditLogDetailPage() {
  const { id = '' } = useParams()
  const detail = useQuery(auditLogQuery(id))

  return (
    <div className="grid gap-6">
      <PageHeader title="Audit event" back={{ to: LIST, label: 'Audit log' }} />
      {detail.data ? (
        <AuditDetailView detail={detail.data} />
      ) : (
        <RecordLoadState
          noun="audit event"
          isPending={detail.isPending}
          error={detail.error}
          onRetry={() => void detail.refetch()}
        />
      )}
    </div>
  )
}

function AuditDetailView({ detail }: { detail: AuditDetail }) {
  const { entry, before, after, metadata } = detail
  const fields = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])]
  const metadataKeys = Object.keys(metadata ?? {})

  return (
    <div className="grid max-w-4xl gap-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex flex-wrap items-center gap-2 leading-normal">
            <ActionBadge action={entry.action} />
            {entry.summary}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1.5 text-sm">
            <dt className="text-muted-foreground">When</dt>
            <dd>{formatDateTime(entry.occurredAt)}</dd>
            <dt className="text-muted-foreground">User</dt>
            <dd>{entry.actorLabel}</dd>
            <dt className="text-muted-foreground">Module</dt>
            <dd className="capitalize">{entry.module}</dd>
            {entry.entityType ? (
              <>
                <dt className="text-muted-foreground">Entity</dt>
                <dd>
                  {entry.entityType}
                  {entry.entityLabel ? ` · ${entry.entityLabel}` : ''}
                  {entry.entityId ? (
                    <>
                      {' '}
                      <Link
                        className="font-mono text-xs underline underline-offset-2"
                        to={`${LIST}?entityType=${encodeURIComponent(entry.entityType)}&entityId=${encodeURIComponent(entry.entityId)}`}
                      >
                        all events for {entry.entityId}
                      </Link>
                    </>
                  ) : null}
                </dd>
              </>
            ) : null}
            <dt className="text-muted-foreground">IP address</dt>
            <dd className="font-mono">{entry.ipAddress ?? '—'}</dd>
            <dt className="text-muted-foreground">Browser</dt>
            <dd className="break-all">{detail.userAgent ?? '—'}</dd>
            <dt className="text-muted-foreground">Request</dt>
            <dd className="font-mono text-xs">{detail.requestId ?? '—'}</dd>
          </dl>
        </CardContent>
      </Card>

      {fields.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>Values</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <caption className="sr-only">Values before and after</caption>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead scope="col">Field</TableHead>
                  <TableHead scope="col">Before</TableHead>
                  <TableHead scope="col">After</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {fields.map((field) => (
                  <TableRow key={field}>
                    <TableCell className="font-mono text-xs">{field}</TableCell>
                    <TableCell className="break-all text-muted-foreground">{formatValue(before?.[field])}</TableCell>
                    <TableCell className="break-all">{formatValue(after?.[field])}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      {metadataKeys.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>Additional details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1.5 text-sm">
              {metadataKeys.map((key) => (
                <div key={key} className="contents">
                  <dt className="font-mono text-xs text-muted-foreground">{key}</dt>
                  <dd className="break-all">{formatValue(metadata?.[key])}</dd>
                </div>
              ))}
            </dl>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}
