import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { dataTableColumns } from '@/components/data-table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { AdminList } from '@/components/list/AdminList'
import { FilterSelect, ListToolbar, SearchInput } from '@/components/list/ListControls'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  AUDIT_ACTIONS,
  auditFacetsQuery,
  auditLogsQuery,
  dayEnd,
  dayStart,
  formatDateTime,
  type AuditEntry,
} from '@/features/audit/api'
import { ActionBadge } from '@/features/audit/components/ActionBadge'
import { useListParams } from '@/lib/use-list-params'

const column = dataTableColumns<AuditEntry>()

const columns = column.columns([
  column.accessor('occurredAt', {
    header: 'When',
    cell: ({ getValue }) => <span className="whitespace-nowrap tabular-nums">{formatDateTime(getValue())}</span>,
  }),
  column.accessor('actorLabel', { header: 'User' }),
  column.accessor('action', { header: 'Action', cell: ({ getValue }) => <ActionBadge action={getValue()} /> }),
  column.accessor('module', { header: 'Module', cell: ({ getValue }) => <span className="capitalize">{getValue()}</span> }),
  column.accessor('summary', {
    header: 'What happened',
    cell: ({ row }) => (
      <div className="grid">
        <span>{row.original.summary}</span>
        {row.original.entityType ? (
          <span className="text-xs text-muted-foreground">
            {row.original.entityType}
            {row.original.entityId ? <span className="font-mono"> · {row.original.entityId}</span> : null}
          </span>
        ) : null}
      </div>
    ),
  }),
  column.accessor('ipAddress', {
    header: 'IP',
    cell: ({ getValue }) => <span className="font-mono text-xs text-muted-foreground">{getValue() ?? '—'}</span>,
  }),
  column.display({
    id: 'actions',
    header: () => <span className="sr-only">Actions</span>,
    cell: ({ row }) => (
      <div className="flex justify-end">
        <Button asChild variant="outline" size="sm">
          <Link to={`/admin/audit/${row.original.id}`} aria-label={`View details: ${row.original.summary}`}>
            Details
          </Link>
        </Button>
      </div>
    ),
  }),
])

/** Who did what, when. Read-only: audit events are written by the system and cannot be edited. */
export function AuditLogPage() {
  const params = useListParams()
  const facets = useQuery(auditFacetsQuery)
  const fromDate = params.get('from')
  const toDate = params.get('to')

  const logs = useQuery(
    auditLogsQuery({
      search: params.get('search'),
      actorId: params.get('actorId'),
      module: params.get('module'),
      action: params.get('action'),
      entityType: params.get('entityType'),
      entityId: params.get('entityId'),
      from: dayStart(fromDate),
      to: dayEnd(toDate),
      page: params.page,
    }),
  )

  const filtersActive = ['search', 'actorId', 'module', 'action', 'entityType', 'entityId', 'from', 'to'].some(
    (key) => params.get(key) !== '',
  )

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Audit log"
        description="Every change, sign-in and sensitive action in your organization, newest first."
      />
      <AdminList
        noun="events"
        singular="event"
        query={logs}
        columns={columns}
        onPageChange={params.setPage}
        toolbar={
          <ListToolbar>
            <SearchInput
              label="Search audit log"
              value={params.get('search')}
              onChange={(value) => params.set('search', value)}
            />
            <FilterSelect
              label="User"
              allLabel="All users"
              value={params.get('actorId')}
              onChange={(value) => params.set('actorId', value)}
              options={(facets.data?.actors ?? []).map((actor) => ({ value: actor.id, label: actor.label }))}
            />
            <FilterSelect
              label="Module"
              allLabel="All modules"
              value={params.get('module')}
              onChange={(value) => params.set('module', value)}
              options={(facets.data?.modules ?? []).map((module) => ({
                value: module,
                label: module.charAt(0).toUpperCase() + module.slice(1),
              }))}
            />
            <FilterSelect
              label="Action"
              allLabel="All actions"
              value={params.get('action')}
              onChange={(value) => params.set('action', value)}
              options={AUDIT_ACTIONS.map((action) => ({ value: action.value, label: action.label }))}
            />
            <FilterSelect
              label="Entity"
              allLabel="All entities"
              value={params.get('entityType')}
              // A record id only makes sense together with the kind of record it belongs to.
              onChange={(value) => params.setMany({ entityType: value, entityId: '' })}
              options={(facets.data?.entityTypes ?? []).map((type) => ({ value: type, label: type }))}
            />
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              From
              <Input
                type="date"
                aria-label="From date"
                className="sm:w-40"
                value={fromDate}
                max={toDate || undefined}
                onChange={(event) => params.set('from', event.target.value)}
              />
            </label>
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              To
              <Input
                type="date"
                aria-label="To date"
                className="sm:w-40"
                value={toDate}
                min={fromDate || undefined}
                onChange={(event) => params.set('to', event.target.value)}
              />
            </label>
            {params.get('entityId') ? (
              <span className="rounded-md border px-2 py-1 font-mono text-xs">record {params.get('entityId')}</span>
            ) : null}
            {filtersActive ? (
              <Button
                variant="ghost"
                size="sm"
                onClick={() =>
                  params.setMany({
                    search: '',
                    actorId: '',
                    module: '',
                    action: '',
                    entityType: '',
                    entityId: '',
                    from: '',
                    to: '',
                  })
                }
              >
                Clear filters
              </Button>
            ) : null}
          </ListToolbar>
        }
      />
    </div>
  )
}
