import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { dataTableColumns } from '@/components/data-table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { AdminList } from '@/components/list/AdminList'
import { FilterSelect, ListToolbar, SearchInput } from '@/components/list/ListControls'
import { Button } from '@/components/ui/button'
import { Select } from '@/components/ui/select'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  LEAD_HEALTHS,
  LEAD_PRIORITIES,
  LEAD_STATUSES,
  leadsQuery,
  QUALIFICATION_STATUSES,
  type LeadFilters,
  type LeadSummary,
} from '@/features/leads/api'
import {
  HealthBadge,
  LeadStatusBadge,
  PriorityBadge,
  QualificationBadge,
  ScoreBadge,
} from '@/features/leads/components/LeadBadges'
import { useListParams } from '@/lib/use-list-params'

const SORTS = [
  { value: '', label: 'Highest score' },
  { value: 'score,asc', label: 'Lowest score' },
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'estimatedValue,desc', label: 'Highest value' },
  { value: 'name,asc', label: 'Name (A–Z)' },
]

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

const column = dataTableColumns<LeadSummary>()

const columns = column.columns([
  column.accessor('contactName', {
    header: 'Lead',
    cell: ({ row }) => (
      <div className="grid">
        <Link to={`/leads/${row.original.id}`} className="font-medium underline-offset-2 hover:underline">
          {row.original.contactName}
        </Link>
        <span className="text-xs text-muted-foreground">
          <span className="font-mono">{row.original.leadNumber}</span>
          {row.original.companyName ? ` · ${row.original.companyName}` : ''}
        </span>
      </div>
    ),
  }),
  column.accessor((lead) => lead.service.name, { id: 'service', header: 'Service' }),
  column.accessor('estimatedValue', {
    header: 'Est. value',
    cell: ({ getValue }) => {
      const value = getValue()
      return value === null || value === undefined ? <span className="text-muted-foreground">—</span> : rupees.format(value)
    },
  }),
  column.accessor('score', { header: 'Score', cell: ({ getValue }) => <ScoreBadge score={getValue()} /> }),
  column.accessor('priority', { header: 'Priority', cell: ({ getValue }) => <PriorityBadge priority={getValue()} /> }),
  column.accessor('health', { header: 'Health', cell: ({ getValue }) => <HealthBadge health={getValue()} /> }),
  column.accessor('qualificationStatus', {
    header: 'Qualification',
    cell: ({ getValue }) => <QualificationBadge status={getValue()} />,
  }),
  column.accessor('status', { header: 'Status', cell: ({ getValue }) => <LeadStatusBadge status={getValue()} /> }),
])

/** Every lead, the ones most worth attention first. */
export function LeadsPage() {
  const params = useListParams()
  const canCreate = useHasPermission('LEAD_CREATE')

  const filters: LeadFilters = {
    search: params.get('search'),
    status: params.get('status'),
    priority: params.get('priority'),
    health: params.get('health'),
    qualificationStatus: params.get('qualificationStatus'),
    sort: params.get('sort'),
    page: params.page,
  }
  const leads = useQuery(leadsQuery(filters))

  const filter = (key: 'status' | 'priority' | 'health' | 'qualificationStatus', label: string, allLabel: string, options: readonly { value: string; label: string }[]) => (
    <FilterSelect
      label={label}
      allLabel={allLabel}
      value={params.get(key)}
      onChange={(value) => params.set(key, value)}
      options={[...options]}
    />
  )

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Leads"
        description="Enquiries that may become business, scored so the best ones are worked first."
        action={
          canCreate ? (
            <Button asChild>
              <Link to="/leads/new">
                <Plus aria-hidden="true" />
                New lead
              </Link>
            </Button>
          ) : null
        }
      />
      <AdminList
        noun="leads"
        singular="lead"
        query={leads}
        columns={columns}
        onPageChange={params.setPage}
        toolbar={
          <ListToolbar>
            <SearchInput label="Search leads" value={params.get('search')} onChange={(value) => params.set('search', value)} />
            {filter('priority', 'Priority', 'Any priority', LEAD_PRIORITIES)}
            {filter('health', 'Health', 'Any health', LEAD_HEALTHS)}
            {filter('qualificationStatus', 'Qualification', 'Any qualification', QUALIFICATION_STATUSES)}
            {filter('status', 'Status', 'Any status', LEAD_STATUSES)}
            <Select
              aria-label="Sort by"
              className="sm:w-44"
              value={params.get('sort')}
              onChange={(event) => params.set('sort', event.target.value)}
            >
              {SORTS.map((sort) => (
                <option key={sort.value} value={sort.value}>
                  Sort: {sort.label}
                </option>
              ))}
            </Select>
          </ListToolbar>
        }
      />
    </div>
  )
}
