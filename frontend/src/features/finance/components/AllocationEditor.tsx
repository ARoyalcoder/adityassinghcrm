import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { allocationProblems, oldestFirst, type TypedAllocations } from '@/features/finance/allocations'
import { formatDay, formatMoney, type InvoiceSummary } from '@/features/finance/api'
import { InvoiceStatusBadge } from '@/features/finance/components/FinanceParts'

type AllocationEditorProps = {
  /** The customer's invoices that can take a payment, the one due earliest first. */
  invoices: InvoiceSummary[]
  /** How much there is to allocate, in paise; `null` while the amount is not known. */
  availablePaise: number | null
  value: TypedAllocations
  onChange: (value: TypedAllocations) => void
}

/** Says which invoices a payment settles: an amount against each, or "oldest first" to fill them in. */
export function AllocationEditor({ invoices, availablePaise, value, onChange }: AllocationEditorProps) {
  const problems = allocationProblems(invoices, value, availablePaise)

  if (invoices.length === 0) {
    return <p className="text-sm text-muted-foreground">This customer has no invoices waiting for payment.</p>
  }
  return (
    <div className="grid gap-3">
      <ul className="grid gap-2" aria-label="Invoices to settle">
        {invoices.map((invoice) => (
          <li key={invoice.id} className="grid gap-2 rounded-xl border p-3 sm:grid-cols-[1fr_auto] sm:items-center">
            <div className="grid gap-1 text-sm">
              <span className="flex flex-wrap items-center gap-2">
                <Link to={`/invoices/${invoice.id}`} className="font-mono font-medium underline-offset-2 hover:underline">
                  {invoice.invoiceNumber}
                </Link>
                <InvoiceStatusBadge status={invoice.status} daysOverdue={invoice.daysOverdue} />
              </span>
              <span className="text-muted-foreground">
                Due {formatDay(invoice.dueDate)} · {formatMoney(invoice.outstanding)} outstanding
              </span>
            </div>
            <div className="grid gap-1 sm:w-40">
              <Input
                inputMode="decimal"
                aria-label={`Amount for ${invoice.invoiceNumber}`}
                aria-invalid={problems.rows[invoice.id] ? true : undefined}
                placeholder="0.00"
                value={value[invoice.id] ?? ''}
                onChange={(event) => onChange({ ...value, [invoice.id]: event.target.value })}
              />
              {problems.rows[invoice.id] ? <span className="text-xs text-destructive">{problems.rows[invoice.id]}</span> : null}
            </div>
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={availablePaise === null || availablePaise <= 0}
            onClick={() => onChange(oldestFirst(invoices, availablePaise ?? 0))}
          >
            Settle oldest first
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => onChange({})}>
            Clear
          </Button>
        </div>
        <p className="text-sm" aria-live="polite">
          <span className="tabular-nums">{formatMoney(problems.totalPaise / 100)}</span> allocated
          {availablePaise !== null && availablePaise >= problems.totalPaise ? (
            <span className="text-muted-foreground">
              {' '}
              · <span className="tabular-nums">{formatMoney((availablePaise - problems.totalPaise) / 100)}</span> left unallocated
            </span>
          ) : null}
        </p>
      </div>
      {problems.overall ? (
        <p role="alert" className="text-sm text-destructive">
          {problems.overall}
        </p>
      ) : null}
    </div>
  )
}
