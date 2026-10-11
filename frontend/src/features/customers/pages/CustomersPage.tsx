import { useMutation, useQuery } from '@tanstack/react-query'
import { Download, Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { dataTableColumns } from '@/components/data-table/DataTable'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { AdminList } from '@/components/list/AdminList'
import { FilterSelect, ListToolbar, SearchInput } from '@/components/list/ListControls'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Select } from '@/components/ui/select'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  assigneesQuery,
  CUSTOMER_SOURCES,
  CUSTOMER_STATUSES,
  CUSTOMER_TYPES,
  customersQuery,
  customerTagsQuery,
  exportCustomers,
  labelOf,
  type CustomerFilters,
  type CustomerSummary,
} from '@/features/customers/api'
import { CustomerStatusBadge } from '@/features/customers/components/CustomerStatusBadge'
import { useListParams } from '@/lib/use-list-params'

const SORTS = [
  { value: '', label: 'Name (A–Z)' },
  { value: 'name,desc', label: 'Name (Z–A)' },
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
  { value: 'customerNumber,asc', label: 'Customer number' },
  { value: 'status,asc', label: 'Status' },
]

const column = dataTableColumns<CustomerSummary>()

const columns = column.columns([
  column.accessor('displayName', {
    header: 'Customer',
    cell: ({ row }) => (
      <div className="grid">
        <Link to={`/customers/${row.original.id}`} className="font-medium underline-offset-2 hover:underline">
          {row.original.displayName}
        </Link>
        <span className="font-mono text-xs text-muted-foreground">{row.original.customerNumber}</span>
      </div>
    ),
  }),
  column.accessor('type', { header: 'Type', cell: ({ getValue }) => labelOf(CUSTOMER_TYPES, getValue()) }),
  column.accessor('phone', {
    header: 'Contact',
    cell: ({ row }) => (
      <div className="grid text-sm">
        <span>{row.original.phone ?? '—'}</span>
        {row.original.email ? <span className="text-xs text-muted-foreground">{row.original.email}</span> : null}
      </div>
    ),
  }),
  column.accessor('status', { header: 'Status', cell: ({ getValue }) => <CustomerStatusBadge status={getValue()} /> }),
  column.accessor((customer) => customer.assignedTo?.name ?? '', {
    id: 'assignedTo',
    header: 'Assigned to',
    cell: ({ getValue }) => getValue() || <span className="text-muted-foreground">Unassigned</span>,
  }),
  column.accessor('tags', {
    header: 'Tags',
    cell: ({ getValue }) => (
      <div className="flex flex-wrap gap-1">
        {getValue().map((tag) => (
          <Badge key={tag} variant="secondary">
            {tag}
          </Badge>
        ))}
      </div>
    ),
  }),
])

export function CustomersPage() {
  const params = useListParams()
  const canCreate = useHasPermission('CUSTOMER_CREATE')
  const canExport = useHasPermission('CUSTOMER_EXPORT')

  const filters: CustomerFilters = {
    search: params.get('search'),
    type: params.get('type'),
    status: params.get('status'),
    source: params.get('source'),
    assignedTo: params.get('assignedTo'),
    tagId: params.get('tagId'),
    sort: params.get('sort'),
    page: params.page,
  }
  const customers = useQuery(customersQuery(filters))
  const tags = useQuery(customerTagsQuery)
  const assignees = useQuery(assigneesQuery)
  const exporting = useMutation({ mutationFn: () => exportCustomers(filters) })

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Customers"
        description="People and companies the business works with."
        action={
          <div className="flex gap-2">
            {canExport ? (
              <Button variant="outline" disabled={exporting.isPending} onClick={() => exporting.mutate()}>
                <Download aria-hidden="true" />
                {exporting.isPending ? 'Exporting…' : 'Export CSV'}
              </Button>
            ) : null}
            {canCreate ? (
              <Button asChild>
                <Link to="/customers/new">
                  <Plus aria-hidden="true" />
                  New customer
                </Link>
              </Button>
            ) : null}
          </div>
        }
      />
      {exporting.error ? <ErrorState title="The export could not be created" error={exporting.error} /> : null}
      <AdminList
        noun="customers"
        singular="customer"
        query={customers}
        columns={columns}
        onPageChange={params.setPage}
        toolbar={
          <ListToolbar>
            <SearchInput
              label="Search customers"
              value={params.get('search')}
              onChange={(value) => params.set('search', value)}
            />
            <FilterSelect
              label="Type"
              allLabel="All types"
              value={params.get('type')}
              onChange={(value) => params.set('type', value)}
              options={[...CUSTOMER_TYPES]}
            />
            <FilterSelect
              label="Status"
              allLabel="Any status"
              value={params.get('status')}
              onChange={(value) => params.set('status', value)}
              options={[...CUSTOMER_STATUSES]}
            />
            <FilterSelect
              label="Source"
              allLabel="Any source"
              value={params.get('source')}
              onChange={(value) => params.set('source', value)}
              options={[...CUSTOMER_SOURCES]}
            />
            <FilterSelect
              label="Assigned to"
              allLabel="Anyone"
              value={params.get('assignedTo')}
              onChange={(value) => params.set('assignedTo', value)}
              options={(assignees.data ?? []).map((person) => ({ value: person.id, label: person.name }))}
            />
            <FilterSelect
              label="Tag"
              allLabel="Any tag"
              value={params.get('tagId')}
              onChange={(value) => params.set('tagId', value)}
              options={(tags.data ?? []).map((tag) => ({ value: tag.id, label: tag.name }))}
            />
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
