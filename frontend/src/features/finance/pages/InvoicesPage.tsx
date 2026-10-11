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
  financeSummaryQuery,
  formatDay,
  formatMoney,
  INVOICE_STATUSES,
  invoicesQuery,
  type InvoiceFilters,
  type InvoiceSummary,
} from '@/features/finance/api'
import { InvoiceStatusBadge, StatTile } from '@/features/finance/components/FinanceParts'
import { useListParams } from '@/lib/use-list-params'

const SORTS = [
  { value: '', label: 'Newest first' },
  { value: 'dueDate,asc', label: 'Due soonest' },
  { value: 'total,desc', label: 'Largest first' },
  { value: 'customer,asc', label: 'Customer (A–Z)' },
  { value: 'invoiceNumber,desc', label: 'Number (latest)' },
]

const column = dataTableColumns<InvoiceSummary>()

const columns = column.columns([
  column.accessor('invoiceNumber', {
    header: 'Invoice',
    cell: ({ row }) => (
      <Link to={`/invoices/${row.original.id}`} className="font-medium underline-offset-2 hover:underline">
        {row.original.invoiceNumber ? <span className="font-mono">{row.original.invoiceNumber}</span> : 'Draft'}
      </Link>
    ),
  }),
  column.accessor('customerName', { header: 'Customer' }),
  column.accessor('issueDate', { header: 'Issued', cell: ({ getValue }) => formatDay(getValue()) }),
  column.accessor('dueDate', { header: 'Due', cell: ({ getValue }) => formatDay(getValue()) }),
  column.accessor('total', {
    header: () => <span className="block text-right">Total</span>,
    cell: ({ getValue }) => <span className="block text-right tabular-nums">{formatMoney(getValue())}</span>,
  }),
  column.accessor('outstanding', {
    header: () => <span className="block text-right">Outstanding</span>,
    cell: ({ row }) => (
      <span className="block text-right tabular-nums">
        {row.original.status === 'DRAFT' || row.original.status === 'CANCELLED' ? (
          <span className="text-muted-foreground">—</span>
        ) : (
          formatMoney(row.original.outstanding)
        )}
      </span>
    ),
  }),
  column.accessor('status', {
    header: 'Status',
    cell: ({ row }) => <InvoiceStatusBadge status={row.original.status} daysOverdue={row.original.daysOverdue} />,
  }),
])

/** Every invoice (`/invoices`), with what is owed across all of them at the top. */
export function InvoicesPage() {
  const params = useListParams()
  const canManage = useHasPermission('FINANCE_MANAGE')
  const filters: InvoiceFilters = {
    search: params.get('search'),
    status: params.get('status'),
    customerId: params.get('customerId'),
    outstanding: params.get('outstanding'),
    sort: params.get('sort'),
    page: params.page,
  }
  const invoices = useQuery(invoicesQuery(filters))
  const summary = useQuery(financeSummaryQuery)

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Invoices"
        description="What customers have been billed, and what they still owe."
        action={
          canManage ? (
            <Button asChild>
              <Link to="/invoices/new">
                <Plus aria-hidden="true" />
                New invoice
              </Link>
            </Button>
          ) : null
        }
      />
      {summary.data ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-label="Summary">
          <StatTile
            label="Outstanding"
            value={formatMoney(summary.data.outstanding)}
            hint={`${summary.data.openInvoices} ${summary.data.openInvoices === 1 ? 'invoice' : 'invoices'} unpaid`}
          />
          <StatTile
            label="Overdue"
            value={formatMoney(summary.data.overdue)}
            hint={`${summary.data.overdueInvoices} past the due date`}
            tone={summary.data.overdue > 0 ? 'bad' : undefined}
          />
          <StatTile label="Due in the next 7 days" value={formatMoney(summary.data.dueSoon)} />
          <StatTile
            label="Invoiced this month"
            value={formatMoney(summary.data.invoicedThisMonth)}
            hint={`${formatMoney(summary.data.receivedThisMonth)} received`}
          />
        </div>
      ) : null}
      <AdminList
        noun="invoices"
        singular="invoice"
        query={invoices}
        columns={columns}
        onPageChange={params.setPage}
        toolbar={
          <ListToolbar>
            <SearchInput label="Search invoices" value={params.get('search')} onChange={(value) => params.set('search', value)} />
            <FilterSelect
              label="Status"
              allLabel="Any status"
              value={params.get('status')}
              onChange={(value) => params.set('status', value)}
              options={[...INVOICE_STATUSES]}
            />
            <FilterSelect
              label="Owing"
              allLabel="Owing or not"
              value={params.get('outstanding')}
              onChange={(value) => params.set('outstanding', value)}
              options={[{ value: 'true', label: 'Still owing' }]}
            />
            <Select
              aria-label="Sort by"
              className="sm:w-48"
              value={params.get('sort')}
              onChange={(event) => params.set('sort', event.target.value)}
            >
              {SORTS.map((sort) => (
                <option key={sort.value} value={sort.value}>
                  Sort: {sort.label}
                </option>
              ))}
            </Select>
            {params.get('customerId') ? (
              <Button variant="outline" size="sm" onClick={() => params.set('customerId', '')}>
                Showing one customer · show all
              </Button>
            ) : null}
          </ListToolbar>
        }
      />
    </div>
  )
}
