import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { dataTableColumns } from '@/components/data-table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { AdminList } from '@/components/list/AdminList'
import { FilterSelect, ListToolbar, SearchInput } from '@/components/list/ListControls'
import { Button } from '@/components/ui/button'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  financeSummaryQuery,
  formatDay,
  formatMoney,
  PAYMENT_STATUSES,
  paymentsQuery,
  type PaymentFilters,
  type PaymentSummary,
} from '@/features/finance/api'
import { PaymentStatusBadge, StatTile } from '@/features/finance/components/FinanceParts'
import { useListParams } from '@/lib/use-list-params'

const column = dataTableColumns<PaymentSummary>()

const columns = column.columns([
  column.accessor('paymentNumber', {
    header: 'Receipt',
    cell: ({ row }) => (
      <Link to={`/payments/${row.original.id}`} className="font-mono font-medium underline-offset-2 hover:underline">
        {row.original.paymentNumber}
      </Link>
    ),
  }),
  column.accessor('customerName', { header: 'Customer' }),
  column.accessor('receivedOn', { header: 'Received', cell: ({ getValue }) => formatDay(getValue()) }),
  column.accessor('methodName', {
    header: 'Method',
    cell: ({ row }) => (
      <span>
        {row.original.methodName}
        {row.original.reference ? <span className="block text-xs text-muted-foreground">{row.original.reference}</span> : null}
      </span>
    ),
  }),
  column.accessor('amount', {
    header: () => <span className="block text-right">Amount</span>,
    cell: ({ getValue }) => <span className="block text-right tabular-nums">{formatMoney(getValue())}</span>,
  }),
  column.accessor('unallocated', {
    header: () => <span className="block text-right">Unallocated</span>,
    cell: ({ getValue }) => (
      <span className="block text-right tabular-nums">
        {getValue() > 0 ? formatMoney(getValue()) : <span className="text-muted-foreground">—</span>}
      </span>
    ),
  }),
  column.accessor('status', { header: 'Status', cell: ({ getValue }) => <PaymentStatusBadge status={getValue()} /> }),
])

/** Every payment received (`/payments`). */
export function PaymentsPage() {
  const params = useListParams()
  const canManage = useHasPermission('FINANCE_MANAGE')
  const filters: PaymentFilters = {
    search: params.get('search'),
    status: params.get('status'),
    customerId: params.get('customerId'),
    unallocated: params.get('unallocated'),
    page: params.page,
  }
  const payments = useQuery(paymentsQuery(filters))
  const summary = useQuery(financeSummaryQuery)

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Payments"
        description="Money received from customers, and which invoices it has settled."
        action={
          canManage ? (
            <Button asChild>
              <Link to="/payments/new">
                <Plus aria-hidden="true" />
                Record payment
              </Link>
            </Button>
          ) : null
        }
      />
      {summary.data ? (
        <div className="grid gap-3 sm:grid-cols-2" aria-label="Summary">
          <StatTile label="Received this month" value={formatMoney(summary.data.receivedThisMonth)} />
          <StatTile
            label="Unallocated"
            value={formatMoney(summary.data.unallocatedPayments)}
            hint="Received, and not yet put against an invoice or refunded"
          />
        </div>
      ) : null}
      <AdminList
        noun="payments"
        singular="payment"
        query={payments}
        columns={columns}
        onPageChange={params.setPage}
        toolbar={
          <ListToolbar>
            <SearchInput label="Search payments" value={params.get('search')} onChange={(value) => params.set('search', value)} />
            <FilterSelect
              label="Status"
              allLabel="Any status"
              value={params.get('status')}
              onChange={(value) => params.set('status', value)}
              options={[...PAYMENT_STATUSES]}
            />
            <FilterSelect
              label="Allocation"
              allLabel="Allocated or not"
              value={params.get('unallocated')}
              onChange={(value) => params.set('unallocated', value)}
              options={[{ value: 'true', label: 'With money unallocated' }]}
            />
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
