import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input } from '@/components/ui/input'
import { customersQuery } from '@/features/customers/api'
import { INVOICE_STATUSES, labelOf, PAYMENT_STATUSES } from '@/features/finance/api'
import { cn } from '@/lib/utils'

const GOOD = 'border-emerald-200 bg-emerald-50 text-emerald-800'
const WARN = 'border-amber-200 bg-amber-50 text-amber-900'
const BAD = 'border-red-200 bg-red-50 text-red-800'
const NEUTRAL = 'bg-muted text-muted-foreground'
const ACTIVE = 'border-violet-200 bg-violet-50 text-violet-800'

const INVOICE_TONES: Record<string, string> = {
  DRAFT: NEUTRAL,
  ISSUED: ACTIVE,
  PARTIALLY_PAID: WARN,
  PAID: GOOD,
  OVERDUE: BAD,
  CANCELLED: NEUTRAL,
}

const BADGE = 'inline-flex w-fit items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap'

/** The invoice's status in words; colour only repeats what the word says. */
export function InvoiceStatusBadge({ status, daysOverdue }: { status: string; daysOverdue?: number | null }) {
  return (
    <span className={cn(BADGE, INVOICE_TONES[status] ?? NEUTRAL, status === 'CANCELLED' && 'line-through')}>
      <span className="sr-only">Status: </span>
      {labelOf(INVOICE_STATUSES, status)}
      {status === 'OVERDUE' && daysOverdue ? ` · ${daysOverdue} ${daysOverdue === 1 ? 'day' : 'days'}` : ''}
    </span>
  )
}

export function PaymentStatusBadge({ status }: { status: string }) {
  return (
    <span className={cn(BADGE, status === 'REVERSED' ? BAD : GOOD)}>
      <span className="sr-only">Status: </span>
      {labelOf(PAYMENT_STATUSES, status)}
    </span>
  )
}

/** One headline figure: what it is, the amount, and a line of context. */
export function StatTile({ label, value, hint, tone }: { label: string; value: string; hint?: string; tone?: 'bad' }) {
  return (
    <div className="grid gap-1 rounded-2xl bg-card p-4 shadow-soft">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      <span className={cn('text-xl font-semibold tabular-nums', tone === 'bad' && 'text-red-700')}>{value}</span>
      {hint ? <span className="text-xs text-muted-foreground">{hint}</span> : null}
    </div>
  )
}

/** A titled white card, the building block of the invoice and payment pages. */
export function Section({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="grid gap-3 rounded-2xl bg-card p-4 shadow-soft sm:p-5" aria-label={title}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

/** "Label    value" rows: the totals of an invoice, the figures of a payment. */
export function Figures({ rows }: { rows: { label: string; value: string; strong?: boolean }[] }) {
  return (
    <dl className="grid gap-1.5 text-sm">
      {rows.map((row) => (
        <div key={row.label} className={cn('flex items-baseline justify-between gap-4', row.strong && 'border-t pt-2 font-semibold')}>
          <dt className={row.strong ? undefined : 'text-muted-foreground'}>{row.label}</dt>
          <dd className="tabular-nums">{row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

type CustomerPickerProps = {
  /** The chosen customer, if any. */
  value: { id: string; name: string } | null
  onChange: (customer: { id: string; name: string } | null) => void
  /** Shown under the box when no customer is chosen and the form was submitted. */
  error?: string
}

/** Finds a customer by typing part of the name, number, phone or email. */
export function CustomerPicker({ value, onChange, error }: CustomerPickerProps) {
  const [search, setSearch] = useState('')
  const term = search.trim()
  const matches = useQuery({ ...customersQuery({ search: term }), enabled: value === null && term.length >= 2 })

  if (value) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-xl border bg-muted/40 px-3 py-2 text-sm">
        <span className="font-medium">{value.name}</span>
        <button type="button" className="text-sm text-primary underline-offset-2 hover:underline" onClick={() => onChange(null)}>
          Change customer
        </button>
      </div>
    )
  }
  return (
    <div className="grid gap-2">
      <Input
        type="search"
        aria-label="Find a customer"
        aria-invalid={error ? true : undefined}
        placeholder="Type a name, number or phone"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
      />
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {term.length >= 2 ? (
        matches.isPending ? (
          <p className="text-sm text-muted-foreground">Searching…</p>
        ) : matches.isError ? (
          <p role="alert" className="text-sm text-destructive">
            Could not search customers. {matches.error.message}
          </p>
        ) : matches.data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No customer matches “{term}”.</p>
        ) : (
          <ul className="grid gap-1" aria-label="Matching customers">
            {matches.data.items.slice(0, 8).map((customer) => (
              <li key={customer.id}>
                <button
                  type="button"
                  className="flex w-full items-baseline justify-between gap-3 rounded-xl border px-3 py-2 text-left text-sm hover:bg-muted/60"
                  onClick={() => {
                    onChange({ id: customer.id, name: customer.displayName })
                    setSearch('')
                  }}
                >
                  <span className="font-medium">{customer.displayName}</span>
                  <span className="text-xs text-muted-foreground">{customer.customerNumber}</span>
                </button>
              </li>
            ))}
          </ul>
        )
      ) : null}
    </div>
  )
}
