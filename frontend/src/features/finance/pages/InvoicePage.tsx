import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Banknote, Pencil, Printer } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  addCreditNote,
  addDays,
  addDebitNote,
  cancelInvoice,
  discardInvoice,
  financeKeys,
  financeSettingsQuery,
  formatDay,
  formatMoney,
  invoiceQuery,
  isAnswered,
  issueInvoice,
  newIdempotencyKey,
  refreshFinance,
  type AdjustmentNote,
  type InvoiceDetail,
} from '@/features/finance/api'
import { Figures, InvoiceStatusBadge, Section } from '@/features/finance/components/FinanceParts'
import { ReasonForm } from '@/features/finance/components/ReasonForm'

type Panel = 'issue' | 'credit' | 'debit' | 'cancel' | 'discard' | null

/** One invoice (`/invoices/:id`): what was billed, what has been settled against it, and what can be done next. */
export function InvoicePage() {
  const { id = '' } = useParams()
  const invoice = useQuery(invoiceQuery(id))
  const back = { to: '/invoices', label: 'Invoices' }

  if (!invoice.data) {
    return (
      <div className="grid gap-6">
        <PageHeader title="Invoice" back={back} />
        <RecordLoadState noun="invoice" isPending={invoice.isPending} error={invoice.error} onRetry={() => void invoice.refetch()} />
      </div>
    )
  }
  return <InvoiceView detail={invoice.data} back={back} />
}

function InvoiceView({ detail, back }: { detail: InvoiceDetail; back: { to: string; label: string } }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const settings = useQuery(financeSettingsQuery)
  const [panel, setPanel] = useState<Panel>(null)
  const { invoice, actions } = detail
  const id = invoice.id
  const draft = invoice.status === 'DRAFT'
  const today = settings.data?.today ?? new Date().toISOString().slice(0, 10)

  /** Every action answers with the whole invoice, so the page is replaced by the answer. */
  const act = useMutation({
    mutationFn: (action: () => Promise<InvoiceDetail>) => action(),
    onSuccess: (updated) => {
      queryClient.setQueryData(financeKeys.invoice(id), updated)
      setPanel(null)
      return refreshFinance(queryClient)
    },
  })
  const discard = useMutation({
    mutationFn: () => discardInvoice(id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: financeKeys.invoice(id) })
      await refreshFinance(queryClient)
      void navigate('/invoices')
    },
  })
  const open = (next: Panel) => {
    act.reset()
    discard.reset()
    setPanel(panel === next ? null : next)
  }

  return (
    <div className="mx-auto grid w-full max-w-4xl gap-5">
      <PageHeader
        title={invoice.invoiceNumber ?? 'Draft invoice'}
        description={invoice.customerName}
        back={back}
        action={
          <div className="flex flex-wrap items-center gap-2">
            <InvoiceStatusBadge status={invoice.status} daysOverdue={invoice.daysOverdue} />
            {actions.edit ? (
              <Button asChild variant="outline" size="sm">
                <Link to={`/invoices/${id}/edit`}>
                  <Pencil aria-hidden="true" />
                  Edit
                </Link>
              </Button>
            ) : null}
            {draft ? null : (
              <Button asChild variant="outline" size="sm">
                <Link to={`/invoices/${id}/print`}>
                  <Printer aria-hidden="true" />
                  Print
                </Link>
              </Button>
            )}
          </div>
        }
      />

      {draft ? (
        <p className="rounded-2xl bg-card p-4 text-sm text-muted-foreground shadow-soft">
          This is a draft. It has no number and is not owed until it is issued; once issued it can no longer be changed.
        </p>
      ) : null}
      {invoice.status === 'CANCELLED' ? (
        <p className="rounded-2xl bg-card p-4 text-sm shadow-soft">
          Cancelled{detail.cancelledAt ? ` on ${formatDay(detail.cancelledAt.slice(0, 10))}` : ''}: {detail.cancellationReason}
        </p>
      ) : null}

      <Section title="Details">
        <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
          <Fact label="Customer">
            <Link to={`/customers/${invoice.customerId}`} className="underline-offset-2 hover:underline">
              {invoice.customerName}
            </Link>
            {detail.customerTaxId ? <span className="block text-xs text-muted-foreground">GSTIN {detail.customerTaxId}</span> : null}
          </Fact>
          <Fact label="Issued">{formatDay(invoice.issueDate)}</Fact>
          <Fact label="Due">{draft ? `${detail.paymentTermsDays} days after issue` : formatDay(invoice.dueDate)}</Fact>
          <Fact label="Billing address">
            <span className="whitespace-pre-line">{detail.billingAddress ?? '—'}</span>
          </Fact>
          <Fact label="Their reference">{detail.reference ?? '—'}</Fact>
          <Fact label="Issued by">{detail.issuedByName ?? '—'}</Fact>
        </dl>
      </Section>

      <Section title="Lines">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[36rem] text-sm">
            <caption className="sr-only">Invoice lines</caption>
            <thead className="text-left text-xs text-muted-foreground">
              <tr>
                <th scope="col" className="pb-2 font-medium">Description</th>
                <th scope="col" className="pb-2 text-right font-medium">Qty</th>
                <th scope="col" className="pb-2 text-right font-medium">Unit price</th>
                <th scope="col" className="pb-2 text-right font-medium">Disc.</th>
                <th scope="col" className="pb-2 text-right font-medium">Tax</th>
                <th scope="col" className="pb-2 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody>
              {detail.items.map((item) => (
                <tr key={item.id} className="border-t align-top">
                  <td className="py-2 pr-3">
                    {item.description}
                    {item.hsnSac ? <span className="block text-xs text-muted-foreground">HSN/SAC {item.hsnSac}</span> : null}
                  </td>
                  <td className="py-2 text-right tabular-nums whitespace-nowrap">
                    {item.quantity}
                    {item.unit ? ` ${item.unit}` : ''}
                  </td>
                  <td className="py-2 text-right tabular-nums">{formatMoney(item.unitPrice)}</td>
                  <td className="py-2 text-right tabular-nums">{item.discountPercent ? `${item.discountPercent}%` : '—'}</td>
                  <td className="py-2 text-right tabular-nums">{item.taxPercent ? `${item.taxPercent}%` : '—'}</td>
                  <td className="py-2 text-right tabular-nums">{formatMoney(item.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="ml-auto w-full max-w-xs" aria-label="Totals">
          <Figures
            rows={[
              { label: 'Subtotal', value: formatMoney(detail.subtotal) },
              ...(detail.discountTotal ? [{ label: 'Discount', value: `− ${formatMoney(detail.discountTotal)}` }] : []),
              { label: 'Tax', value: formatMoney(detail.taxTotal) },
              { label: 'Invoice total', value: formatMoney(invoice.total), strong: true },
              ...(detail.debitTotal ? [{ label: 'Debit notes', value: `+ ${formatMoney(detail.debitTotal)}` }] : []),
              ...(detail.creditTotal ? [{ label: 'Credit notes', value: `− ${formatMoney(detail.creditTotal)}` }] : []),
              ...(draft ? [] : [{ label: 'Paid', value: `− ${formatMoney(invoice.amountPaid)}` }]),
              ...(draft || invoice.status === 'CANCELLED' ? [] : [{ label: 'Outstanding', value: formatMoney(invoice.outstanding), strong: true }]),
            ]}
          />
        </div>
      </Section>

      {draft ? null : (
        <Section
          title="Payments"
          action={
            actions.recordPayment ? (
              <Button asChild size="sm">
                <Link to={`/payments/new?customerId=${invoice.customerId}&invoiceId=${id}`}>
                  <Banknote aria-hidden="true" />
                  Record payment
                </Link>
              </Button>
            ) : null
          }
        >
          {detail.payments.length === 0 ? (
            <p className="text-sm text-muted-foreground">No payment has been put against this invoice.</p>
          ) : (
            <ul className="grid gap-2 text-sm">
              {detail.payments.map((payment) => (
                <li key={payment.allocationId} className="flex flex-wrap items-baseline justify-between gap-2 border-t pt-2 first:border-t-0 first:pt-0">
                  <span>
                    <Link to={`/payments/${payment.paymentId}`} className="font-mono underline-offset-2 hover:underline">
                      {payment.paymentNumber}
                    </Link>
                    <span className="text-muted-foreground">
                      {' '}
                      · {formatDay(payment.receivedOn)} · {payment.methodName}
                    </span>
                    {payment.released ? <span className="ml-2 rounded-md border px-1.5 py-0.5 text-xs">Released, no longer counts</span> : null}
                  </span>
                  <span className={payment.released ? 'text-muted-foreground tabular-nums line-through' : 'tabular-nums'}>
                    {formatMoney(payment.amount)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Section>
      )}

      {detail.creditNotes.length > 0 ? <Notes title="Credit notes" notes={detail.creditNotes} sign="−" /> : null}
      {detail.debitNotes.length > 0 ? <Notes title="Debit notes" notes={detail.debitNotes} sign="+" /> : null}

      {detail.notes || detail.terms ? (
        <Section title="On the invoice">
          {detail.notes ? <p className="text-sm whitespace-pre-line">{detail.notes}</p> : null}
          {detail.terms ? <p className="text-sm whitespace-pre-line text-muted-foreground">{detail.terms}</p> : null}
        </Section>
      ) : null}

      {actions.issue || actions.discard || actions.creditNote || actions.debitNote || actions.cancel ? (
        <Section title="Actions">
          <div className="flex flex-wrap gap-2">
            {actions.issue ? <Button onClick={() => open('issue')}>Issue invoice</Button> : null}
            {actions.creditNote ? (
              <Button variant="outline" onClick={() => open('credit')}>
                Credit note
              </Button>
            ) : null}
            {actions.debitNote ? (
              <Button variant="outline" onClick={() => open('debit')}>
                Debit note
              </Button>
            ) : null}
            {actions.cancel ? (
              <Button variant="outline" onClick={() => open('cancel')}>
                Cancel invoice
              </Button>
            ) : null}
            {actions.discard ? (
              <Button variant="outline" onClick={() => open('discard')}>
                Discard draft
              </Button>
            ) : null}
          </div>
          {act.error ? <ErrorState title="That could not be done" error={act.error} /> : null}
          {discard.error ? <ErrorState title="The draft could not be discarded" error={discard.error} /> : null}

          {panel === 'issue' ? (
            <IssueForm
              today={today}
              termsDays={detail.paymentTermsDays}
              total={invoice.total}
              busy={act.isPending}
              onIssue={(dates) => act.mutate(() => issueInvoice(id, dates))}
            />
          ) : null}
          {panel === 'credit' ? (
            <NoteForm
              title="Credit note"
              explanation={`Reduces what is owed on this invoice. At most ${formatMoney(invoice.outstanding)}, which is what is outstanding.`}
              submitLabel="Record credit note"
              busy={act.isPending}
              failed={act.error}
              onSubmit={(input, key) => act.mutate(() => addCreditNote(id, input, key))}
            />
          ) : null}
          {panel === 'debit' ? (
            <NoteForm
              title="Debit note"
              explanation="Adds to what is owed on this invoice: extra work, an under-billed quantity."
              submitLabel="Record debit note"
              busy={act.isPending}
              failed={act.error}
              onSubmit={(input, key) => act.mutate(() => addDebitNote(id, input, key))}
            />
          ) : null}
          {panel === 'cancel' ? (
            <ReasonForm
              title="Cancel invoice"
              explanation="The invoice keeps its number and stays on record, marked cancelled. Nothing will be owed on it. This cannot be undone."
              submitLabel="Cancel this invoice"
              busy={act.isPending}
              onSubmit={(reason) => act.mutate(() => cancelInvoice(id, reason))}
            />
          ) : null}
          {panel === 'discard' ? (
            <div className="grid gap-3 rounded-xl border p-3">
              <p className="text-sm">Discard this draft? It has no number and nothing is owed on it, so nothing is lost from the books.</p>
              <div className="flex gap-2">
                <Button variant="destructive" disabled={discard.isPending} onClick={() => discard.mutate()}>
                  {discard.isPending ? 'Discarding…' : 'Discard draft'}
                </Button>
                <Button variant="outline" onClick={() => setPanel(null)}>
                  Keep it
                </Button>
              </div>
            </div>
          ) : null}
        </Section>
      ) : null}
    </div>
  )
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

function Notes({ title, notes, sign }: { title: string; notes: AdjustmentNote[]; sign: string }) {
  return (
    <Section title={title}>
      <ul className="grid gap-2 text-sm">
        {notes.map((note) => (
          <li key={note.id} className="flex flex-wrap items-baseline justify-between gap-2 border-t pt-2 first:border-t-0 first:pt-0">
            <span>
              <span className="font-mono">{note.noteNumber}</span>
              <span className="text-muted-foreground">
                {' '}
                · {formatDay(note.noteDate)} · {note.issuedByName}
              </span>
              <span className="block">{note.reason}</span>
            </span>
            <span className="tabular-nums">
              {sign} {formatMoney(note.amount)}
            </span>
          </li>
        ))}
      </ul>
    </Section>
  )
}

type IssueFormProps = {
  today: string
  termsDays: number
  total: number
  busy: boolean
  onIssue: (dates: { issueDate: string; dueDate: string }) => void
}

function IssueForm({ today, termsDays, total, busy, onIssue }: IssueFormProps) {
  const [issueDate, setIssueDate] = useState(today)
  // The due date follows the issue date until someone sets it by hand.
  const [dueDate, setDueDate] = useState<string | null>(null)
  const due = dueDate ?? addDays(issueDate, termsDays)
  const problem = issueDate > today ? 'The issue date cannot be in the future.' : due < issueDate ? 'The due date cannot be before the issue date.' : null

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!problem) {
      onIssue({ issueDate, dueDate: due })
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-3 rounded-xl border p-3" aria-label="Issue invoice">
      <p className="text-sm">
        Issuing gives the invoice its number and makes {formatMoney(total)} owed. After this it cannot be changed, only corrected with a
        credit or debit note.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="grid gap-1.5">
          <Label htmlFor="issue-date">Issue date</Label>
          <Input id="issue-date" type="date" max={today} value={issueDate} onChange={(event) => setIssueDate(event.target.value || today)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="due-date">Due date</Label>
          <Input id="due-date" type="date" min={issueDate} value={due} onChange={(event) => setDueDate(event.target.value || null)} />
        </div>
      </div>
      {problem ? (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      ) : null}
      <Button type="submit" className="w-fit" disabled={busy || problem !== null}>
        {busy ? 'Issuing…' : 'Issue invoice'}
      </Button>
    </form>
  )
}

type NoteFormProps = {
  title: string
  explanation: string
  submitLabel: string
  busy: boolean
  /** The last attempt's error, if any: decides whether the next attempt is a retry or a new request. */
  failed: unknown
  onSubmit: (input: { amount: number; reason: string }, idempotencyKey: string) => void
}

function NoteForm({ title, explanation, submitLabel, busy, failed, onSubmit }: NoteFormProps) {
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [problem, setProblem] = useState<string | null>(null)
  const [key, setKey] = useState(newIdempotencyKey)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!/^\d+(\.\d{1,2})?$/.test(amount.trim()) || Number(amount) <= 0) {
      setProblem('Enter an amount of more than zero, with at most 2 decimals.')
      return
    }
    if (reason.trim() === '') {
      setProblem('Say why: the reason is kept with the note.')
      return
    }
    setProblem(null)
    // The server answered the last attempt, so it recorded nothing: this is a new request.
    const next = isAnswered(failed) ? newIdempotencyKey() : key
    setKey(next)
    onSubmit({ amount: Number(amount), reason: reason.trim() }, next)
  }

  return (
    <form onSubmit={submit} className="grid gap-3 rounded-xl border p-3" aria-label={title}>
      <p className="text-sm">{explanation}</p>
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="grid gap-1.5">
          <Label htmlFor="note-amount">Amount (₹)</Label>
          <Input id="note-amount" inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
        </div>
        <div className="grid gap-1.5 sm:col-span-2">
          <Label htmlFor="note-reason">Reason</Label>
          <Input id="note-reason" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />
        </div>
      </div>
      {problem ? (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      ) : null}
      <Button type="submit" className="w-fit" disabled={busy}>
        {busy ? 'Recording…' : submitLabel}
      </Button>
    </form>
  )
}
