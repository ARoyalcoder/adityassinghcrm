import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import { ageingQuery, formatDay, formatMoney, type AgeingReport } from '@/features/finance/api'
import { Section, StatTile } from '@/features/finance/components/FinanceParts'

// One hue, darker as the debt gets older; money that is not due yet is left neutral.
const AGE_SHADES = ['bg-violet-300', 'bg-violet-500', 'bg-violet-700', 'bg-violet-900']

function shade(index: number, bands: number) {
  if (index === 0) {
    return 'bg-slate-300'
  }
  // Spread the shades over however many bands are configured; the oldest band is always the darkest.
  const step = bands <= 1 ? AGE_SHADES.length - 1 : Math.round(((index - 1) * (AGE_SHADES.length - 1)) / (bands - 1))
  return AGE_SHADES[step]
}

/** Ageing (`/finance/ageing`): what each customer owes, by how long past its due date it is. */
export function AgeingPage() {
  const ageing = useQuery(ageingQuery)

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Ageing"
        description={
          ageing.data
            ? `What customers owe as of ${formatDay(ageing.data.asOf)}, by how long it has been due.`
            : 'What customers owe, by how long it has been due.'
        }
      />
      {ageing.isPending ? (
        <Skeleton className="h-60 w-full rounded-2xl" aria-busy="true" aria-label="Loading the ageing report" />
      ) : ageing.isError ? (
        <ErrorState title="Could not load the ageing report" error={ageing.error} onRetry={() => void ageing.refetch()} />
      ) : (
        <Report report={ageing.data} />
      )}
    </div>
  )
}

function Report({ report }: { report: AgeingReport }) {
  const bands = report.buckets.length - 1

  return (
    <>
      <div className="grid gap-3 sm:grid-cols-3" aria-label="Summary">
        <StatTile label="Outstanding" value={formatMoney(report.total)} hint={`${report.customers.length} ${report.customers.length === 1 ? 'customer owes' : 'customers owe'}`} />
        <StatTile label="Overdue" value={formatMoney(report.overdue)} tone={report.overdue > 0 ? 'bad' : undefined} hint="Past its due date" />
        <StatTile label="Not due yet" value={formatMoney(report.totals[0] ?? 0)} />
      </div>

      <Section title="By age">
        {report.total > 0 ? (
          // A picture of the same five numbers that are written out beneath it.
          <div className="flex h-3 w-full gap-0.5 overflow-hidden rounded-full" aria-hidden="true">
            {report.totals.map((amount, index) =>
              amount > 0 ? (
                <div key={report.buckets[index].label} className={shade(index, bands)} style={{ width: `${(amount / report.total) * 100}%` }} />
              ) : null,
            )}
          </div>
        ) : null}
        <dl className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {report.buckets.map((bucket, index) => (
            <div key={bucket.label} className="grid gap-0.5">
              <dt className="flex items-center gap-2 text-xs text-muted-foreground">
                <span className={`size-2.5 rounded-sm ${shade(index, bands)}`} aria-hidden="true" />
                {bucket.label}
              </dt>
              <dd className="text-sm font-medium tabular-nums">{formatMoney(report.totals[index] ?? 0)}</dd>
            </div>
          ))}
        </dl>
      </Section>

      <Section title="By customer">
        {report.customers.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nobody owes anything.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[44rem] text-sm">
              <caption className="sr-only">What each customer owes, by age</caption>
              <thead className="text-left text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="pb-2 font-medium">Customer</th>
                  {report.buckets.map((bucket) => (
                    <th key={bucket.label} scope="col" className="pb-2 text-right font-medium">
                      {bucket.label}
                    </th>
                  ))}
                  <th scope="col" className="pb-2 text-right font-medium">Total</th>
                </tr>
              </thead>
              <tbody>
                {report.customers.map((customer) => (
                  <tr key={customer.customerId} className="border-t align-top">
                    <th scope="row" className="py-2 pr-3 text-left font-normal">
                      <Link
                        to={`/invoices?customerId=${customer.customerId}&outstanding=true`}
                        className="font-medium underline-offset-2 hover:underline"
                      >
                        {customer.customerName}
                      </Link>
                      <span className="block text-xs text-muted-foreground">
                        {customer.invoices} {customer.invoices === 1 ? 'invoice' : 'invoices'}
                        {customer.oldestDaysOverdue > 0 ? ` · oldest ${customer.oldestDaysOverdue} days overdue` : ''}
                      </span>
                    </th>
                    {customer.amounts.map((amount, index) => (
                      <td key={report.buckets[index].label} className="py-2 text-right tabular-nums">
                        {amount > 0 ? formatMoney(amount) : <span className="text-muted-foreground">—</span>}
                      </td>
                    ))}
                    <td className="py-2 text-right font-medium tabular-nums">{formatMoney(customer.total)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t font-semibold">
                  <th scope="row" className="py-2 text-left">
                    Total
                  </th>
                  {report.totals.map((amount, index) => (
                    <td key={report.buckets[index].label} className="py-2 text-right tabular-nums">
                      {formatMoney(amount)}
                    </td>
                  ))}
                  <td className="py-2 text-right tabular-nums">{formatMoney(report.total)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </Section>
    </>
  )
}
