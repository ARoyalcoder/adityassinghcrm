import { toPaise, type AllocationInput, type InvoiceSummary } from '@/features/finance/api'

/** What has been typed against each invoice, by invoice id. An empty box means nothing for that invoice. */
export type TypedAllocations = Record<string, string>

const AMOUNT = /^\d+(\.\d{1,2})?$/

/** The amount typed, in paise; 0 for an empty box and `null` for something that is not an amount. */
export function typedPaise(text: string | undefined): number | null {
  const trimmed = (text ?? '').trim()
  if (trimmed === '') {
    return 0
  }
  return AMOUNT.test(trimmed) ? toPaise(trimmed) : null
}

/**
 * What is wrong with the allocations as typed, if anything: per invoice (not an amount, more than
 * the invoice is owed) and overall (more than the payment has to give).
 *
 * @param availablePaise how much there is to allocate; `null` if the amount is not known yet
 */
export function allocationProblems(invoices: InvoiceSummary[], typed: TypedAllocations, availablePaise: number | null) {
  const rows: Record<string, string> = {}
  let total = 0
  for (const invoice of invoices) {
    const paise = typedPaise(typed[invoice.id])
    if (paise === null) {
      rows[invoice.id] = 'Enter an amount, with at most 2 decimals'
    } else if (paise > toPaise(invoice.outstanding)) {
      rows[invoice.id] = 'More than this invoice is owed'
    } else {
      total += paise
    }
  }
  const overall = availablePaise !== null && total > availablePaise ? 'The allocations add up to more than there is to allocate.' : null
  return { rows, overall, totalPaise: total, valid: Object.keys(rows).length === 0 && overall === null }
}

/** The allocations to send: every invoice with something typed against it. */
export function toAllocations(invoices: InvoiceSummary[], typed: TypedAllocations): AllocationInput[] {
  return invoices
    .map((invoice) => ({ invoiceId: invoice.id, paise: typedPaise(typed[invoice.id]) ?? 0 }))
    .filter((allocation) => allocation.paise > 0)
    .map((allocation) => ({ invoiceId: allocation.invoiceId, amount: allocation.paise / 100 }))
}

/**
 * Oldest debt first: the invoices arrive ordered by due date, and each is settled in full before
 * the next gets anything. The same rule the server applies when asked to allocate by itself.
 */
export function oldestFirst(invoices: InvoiceSummary[], availablePaise: number): TypedAllocations {
  const typed: TypedAllocations = {}
  let left = availablePaise
  for (const invoice of invoices) {
    const share = Math.min(left, toPaise(invoice.outstanding))
    if (share > 0) {
      typed[invoice.id] = (share / 100).toFixed(2)
      left -= share
    }
  }
  return typed
}
