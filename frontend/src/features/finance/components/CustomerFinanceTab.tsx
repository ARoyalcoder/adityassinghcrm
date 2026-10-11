import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useHasPermission } from '@/features/auth/auth-context'
import type { CustomerDetail } from '@/features/customers/api'
import { formatDay, formatMoney, invoicesQuery, paymentsQuery } from '@/features/finance/api'
import { InvoiceStatusBadge, PaymentStatusBadge, Section, StatTile } from '@/features/finance/components/FinanceParts'

const LATEST = 10

/**
 * The "Financial summary" section of the customer profile: what this customer owes, their latest
 * invoices and payments. Finance plugs it into the profile's list of tabs; the customer module
 * does not know what is in it.
 */
export function CustomerFinanceTab({ customer }: { customer: CustomerDetail }) {
  const canView = useHasPermission('FINANCE_VIEW')
  const canManage = useHasPermission('FINANCE_MANAGE')
  const owing = useQuery({ ...invoicesQuery({ customerId: customer.id, outstanding: 'true', sort: 'dueDate,asc', size: 100 }), enabled: canView })
  const invoices = useQuery({ ...invoicesQuery({ customerId: customer.id, size: LATEST }), enabled: canView })
  const payments = useQuery({ ...paymentsQuery({ customerId: customer.id, size: LATEST }), enabled: canView })

  if (!canView) {
    return (
      <p className="rounded-2xl bg-card p-6 text-sm text-muted-foreground shadow-soft">
        Invoices and payments are visible to people who work with finance.
      </p>
    )
  }
  const failed = owing.error ?? invoices.error ?? payments.error
  if (failed) {
    return (
      <ErrorState
        title="Could not load this customer’s invoices and payments"
        error={failed}
        onRetry={() => void Promise.all([owing.refetch(), invoices.refetch(), payments.refetch()])}
      />
    )
  }
  if (!owing.data || !invoices.data || !payments.data) {
    return <Skeleton className="h-48 w-full rounded-2xl" aria-busy="true" aria-label="Loading invoices and payments" />
  }
  const outstanding = owing.data.items.reduce((sum, invoice) => sum + invoice.outstanding, 0)
  const overdue = owing.data.items.filter((invoice) => invoice.status === 'OVERDUE')
  const unallocated = payments.data.items.reduce((sum, payment) => sum + payment.unallocated, 0)

  return (
    <div className="grid gap-5">
      <div className="grid gap-3 sm:grid-cols-3" aria-label="What this customer owes">
        <StatTile
          label="Outstanding"
          value={formatMoney(outstanding)}
          hint={`${owing.data.totalItems} ${owing.data.totalItems === 1 ? 'invoice' : 'invoices'} unpaid`}
        />
        <StatTile
          label="Overdue"
          value={formatMoney(overdue.reduce((sum, invoice) => sum + invoice.outstanding, 0))}
          hint={`${overdue.length} past the due date`}
          tone={overdue.length > 0 ? 'bad' : undefined}
        />
        <StatTile label="Unallocated payments" value={formatMoney(unallocated)} hint={`In the latest ${LATEST} payments`} />
      </div>

      <Section
        title="Invoices"
        action={
          <div className="flex flex-wrap gap-2">
            {canManage ? (
              <Button asChild size="sm">
                <Link to={`/invoices/new?customerId=${customer.id}`}>New invoice</Link>
              </Button>
            ) : null}
            <Button asChild size="sm" variant="outline">
              <Link to={`/invoices?customerId=${customer.id}`}>All {invoices.data.totalItems}</Link>
            </Button>
          </div>
        }
      >
        {invoices.data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing has been invoiced to this customer.</p>
        ) : (
          <ul className="grid gap-2 text-sm">
            {invoices.data.items.map((invoice) => (
              <li key={invoice.id} className="flex flex-wrap items-center justify-between gap-2 border-t pt-2 first:border-t-0 first:pt-0">
                <span className="flex flex-wrap items-center gap-2">
                  <Link to={`/invoices/${invoice.id}`} className="font-medium underline-offset-2 hover:underline">
                    {invoice.invoiceNumber ? <span className="font-mono">{invoice.invoiceNumber}</span> : 'Draft'}
                  </Link>
                  <InvoiceStatusBadge status={invoice.status} daysOverdue={invoice.daysOverdue} />
                  <span className="text-muted-foreground">{invoice.dueDate ? `due ${formatDay(invoice.dueDate)}` : ''}</span>
                </span>
                <span className="tabular-nums">{formatMoney(invoice.total)}</span>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section
        title="Payments"
        action={
          <div className="flex flex-wrap gap-2">
            {canManage ? (
              <Button asChild size="sm">
                <Link to={`/payments/new?customerId=${customer.id}`}>Record payment</Link>
              </Button>
            ) : null}
            <Button asChild size="sm" variant="outline">
              <Link to={`/payments?customerId=${customer.id}`}>All {payments.data.totalItems}</Link>
            </Button>
          </div>
        }
      >
        {payments.data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No payment has been received from this customer.</p>
        ) : (
          <ul className="grid gap-2 text-sm">
            {payments.data.items.map((payment) => (
              <li key={payment.id} className="flex flex-wrap items-center justify-between gap-2 border-t pt-2 first:border-t-0 first:pt-0">
                <span className="flex flex-wrap items-center gap-2">
                  <Link to={`/payments/${payment.id}`} className="font-mono font-medium underline-offset-2 hover:underline">
                    {payment.paymentNumber}
                  </Link>
                  <PaymentStatusBadge status={payment.status} />
                  <span className="text-muted-foreground">
                    {formatDay(payment.receivedOn)} · {payment.methodName}
                  </span>
                </span>
                <span className="tabular-nums">{formatMoney(payment.amount)}</span>
              </li>
            ))}
          </ul>
        )}
      </Section>
    </div>
  )
}
