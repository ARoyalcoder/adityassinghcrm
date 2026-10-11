import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { allocationProblems, toAllocations, type TypedAllocations } from '@/features/finance/allocations'
import {
  allocatePayment,
  financeKeys,
  formatDay,
  formatMoney,
  isAnswered,
  newIdempotencyKey,
  openInvoicesQuery,
  paymentMethodsQuery,
  paymentQuery,
  refreshFinance,
  refundPayment,
  releaseAllocation,
  reversePayment,
  toPaise,
  type PaymentDetail,
  type RefundInput,
} from '@/features/finance/api'
import { AllocationEditor } from '@/features/finance/components/AllocationEditor'
import { Figures, PaymentStatusBadge, Section } from '@/features/finance/components/FinanceParts'
import { ReasonForm } from '@/features/finance/components/ReasonForm'

/** Which form is open: one of the actions, or the release of one allocation (by its id). */
type Panel = 'allocate' | 'refund' | 'reverse' | { release: string } | null

/** One payment (`/payments/:id`): what was received, where it went, and how to put it right. */
export function PaymentPage() {
  const { id = '' } = useParams()
  const payment = useQuery(paymentQuery(id))
  const back = { to: '/payments', label: 'Payments' }

  if (!payment.data) {
    return (
      <div className="grid gap-6">
        <PageHeader title="Payment" back={back} />
        <RecordLoadState noun="payment" isPending={payment.isPending} error={payment.error} onRetry={() => void payment.refetch()} />
      </div>
    )
  }
  return <PaymentView detail={payment.data} back={back} />
}

function PaymentView({ detail, back }: { detail: PaymentDetail; back: { to: string; label: string } }) {
  const queryClient = useQueryClient()
  const [panel, setPanel] = useState<Panel>(null)
  const { payment, actions } = detail
  const id = payment.id
  const reversed = payment.status === 'REVERSED'

  /** Every action answers with the whole payment, so the page is replaced by the answer. */
  const act = useMutation({
    mutationFn: (action: () => Promise<PaymentDetail>) => action(),
    onSuccess: (updated) => {
      queryClient.setQueryData(financeKeys.payment(id), updated)
      setPanel(null)
      return refreshFinance(queryClient)
    },
  })
  const open = (next: Panel) => {
    act.reset()
    setPanel(next)
  }
  const releasing = panel !== null && typeof panel === 'object' ? panel.release : null

  return (
    <div className="mx-auto grid w-full max-w-3xl gap-5">
      <PageHeader
        title={payment.paymentNumber}
        description={payment.customerName}
        back={back}
        action={<PaymentStatusBadge status={payment.status} />}
      />
      {reversed ? (
        <p className="rounded-2xl bg-card p-4 text-sm shadow-soft">
          Reversed{detail.reversedAt ? ` on ${formatDay(detail.reversedAt.slice(0, 10))}` : ''}
          {detail.reversedByName ? ` by ${detail.reversedByName}` : ''}: {detail.reversalReason}. It settles nothing and stays here
          as a record.
        </p>
      ) : null}
      {act.error ? <ErrorState title="That could not be done" error={act.error} /> : null}

      <Section title="Received">
        <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
          <Fact label="From">
            <Link to={`/customers/${payment.customerId}`} className="underline-offset-2 hover:underline">
              {payment.customerName}
            </Link>
          </Fact>
          <Fact label="On">{formatDay(payment.receivedOn)}</Fact>
          <Fact label="By">
            {payment.methodName}
            {payment.reference ? <span className="block text-xs text-muted-foreground">{payment.reference}</span> : null}
          </Fact>
          <Fact label="Recorded by">{detail.recordedByName}</Fact>
          {detail.notes ? <Fact label="Notes">{detail.notes}</Fact> : null}
        </dl>
        <div className="ml-auto w-full max-w-xs" aria-label="Amounts">
          <Figures
            rows={[
              { label: 'Received', value: formatMoney(payment.amount) },
              { label: 'Allocated to invoices', value: `− ${formatMoney(payment.allocated)}` },
              ...(payment.refunded ? [{ label: 'Refunded', value: `− ${formatMoney(payment.refunded)}` }] : []),
              { label: 'Unallocated', value: formatMoney(payment.unallocated), strong: true },
            ]}
          />
        </div>
      </Section>

      <Section
        title="Allocated to"
        action={
          actions.allocate ? (
            <Button size="sm" variant="outline" onClick={() => open(panel === 'allocate' ? null : 'allocate')}>
              Allocate to invoices
            </Button>
          ) : null
        }
      >
        {detail.allocations.length === 0 ? (
          <p className="text-sm text-muted-foreground">Not put against any invoice.</p>
        ) : (
          <ul className="grid gap-2 text-sm">
            {detail.allocations.map((allocation) => (
              <li key={allocation.id} className="grid gap-2 border-t pt-2 first:border-t-0 first:pt-0">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span>
                    <Link to={`/invoices/${allocation.invoiceId}`} className="font-mono underline-offset-2 hover:underline">
                      {allocation.invoiceNumber ?? 'Invoice'}
                    </Link>
                    {allocation.released ? (
                      <span className="ml-2 rounded-md border px-1.5 py-0.5 text-xs">Released: {allocation.releaseReason}</span>
                    ) : null}
                  </span>
                  <span className="flex items-center gap-3">
                    <span className={allocation.released ? 'text-muted-foreground tabular-nums line-through' : 'tabular-nums'}>
                      {formatMoney(allocation.amount)}
                    </span>
                    {actions.release && !allocation.released ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => open(releasing === allocation.id ? null : { release: allocation.id })}
                        aria-label={`Release ${formatMoney(allocation.amount)} from ${allocation.invoiceNumber ?? 'the invoice'}`}
                      >
                        Release
                      </Button>
                    ) : null}
                  </span>
                </div>
                {releasing === allocation.id ? (
                  <ReasonForm
                    title="Release allocation"
                    explanation={`${allocation.invoiceNumber ?? 'The invoice'} will be owed ${formatMoney(allocation.amount)} again, and this payment will have it to allocate elsewhere or refund. The allocation stays on record, marked released.`}
                    submitLabel="Release"
                    busy={act.isPending}
                    onSubmit={(reason) => act.mutate(() => releaseAllocation(id, allocation.id, reason))}
                  />
                ) : null}
              </li>
            ))}
          </ul>
        )}
        {panel === 'allocate' ? (
          <AllocateForm detail={detail} busy={act.isPending} failed={act.error} onAllocate={(action) => act.mutate(action)} />
        ) : null}
      </Section>

      {detail.refunds.length > 0 || actions.refund ? (
        <Section
          title="Refunds"
          action={
            actions.refund ? (
              <Button size="sm" variant="outline" onClick={() => open(panel === 'refund' ? null : 'refund')}>
                Refund
              </Button>
            ) : null
          }
        >
          {detail.refunds.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nothing has been refunded.</p>
          ) : (
            <ul className="grid gap-2 text-sm">
              {detail.refunds.map((refund) => (
                <li key={refund.id} className="flex flex-wrap items-baseline justify-between gap-2 border-t pt-2 first:border-t-0 first:pt-0">
                  <span>
                    <span className="font-mono">{refund.refundNumber}</span>
                    <span className="text-muted-foreground">
                      {' '}
                      · {formatDay(refund.refundDate)} · {refund.methodName}
                      {refund.reference ? ` · ${refund.reference}` : ''}
                    </span>
                    <span className="block">{refund.reason}</span>
                  </span>
                  <span className="tabular-nums">{formatMoney(refund.amount)}</span>
                </li>
              ))}
            </ul>
          )}
          {panel === 'refund' ? (
            <RefundForm
              unallocated={payment.unallocated}
              busy={act.isPending}
              failed={act.error}
              onRefund={(input, key) => act.mutate(() => refundPayment(id, input, key))}
            />
          ) : null}
        </Section>
      ) : null}

      {actions.reverse ? (
        <Section title="Reverse">
          <p className="text-sm text-muted-foreground">
            For a payment that should not stand: a cheque that bounced, an entry made by mistake.
          </p>
          <Button variant="outline" className="w-fit" onClick={() => open(panel === 'reverse' ? null : 'reverse')}>
            Reverse payment
          </Button>
          {panel === 'reverse' ? (
            <ReasonForm
              title="Reverse payment"
              explanation="Every invoice this payment settled will be owed again. The payment stays on record, marked reversed, with your reason. This cannot be undone."
              submitLabel="Reverse this payment"
              busy={act.isPending}
              onSubmit={(reason) => act.mutate(() => reversePayment(id, reason))}
            />
          ) : null}
        </Section>
      ) : null}
    </div>
  )
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

type AllocateFormProps = {
  detail: PaymentDetail
  busy: boolean
  failed: unknown
  onAllocate: (action: () => Promise<PaymentDetail>) => void
}

function AllocateForm({ detail, busy, failed, onAllocate }: AllocateFormProps) {
  const invoices = useQuery(openInvoicesQuery(detail.payment.customerId))
  const [typed, setTyped] = useState<TypedAllocations>({})
  const [key, setKey] = useState(newIdempotencyKey)
  const available = toPaise(detail.payment.unallocated)

  if (invoices.isPending) {
    return <p className="text-sm text-muted-foreground">Loading what the customer owes…</p>
  }
  if (invoices.isError) {
    return <ErrorState title="Could not load the customer’s invoices" error={invoices.error} onRetry={() => void invoices.refetch()} />
  }
  const problems = allocationProblems(invoices.data, typed, available)

  function submit(event: FormEvent) {
    event.preventDefault()
    const allocations = toAllocations(invoices.data ?? [], typed)
    const next = isAnswered(failed) ? newIdempotencyKey() : key
    setKey(next)
    onAllocate(() => allocatePayment(detail.payment.id, allocations, next))
  }

  return (
    <form onSubmit={submit} className="grid gap-3 rounded-xl border p-3" aria-label="Allocate to invoices">
      <AllocationEditor invoices={invoices.data} availablePaise={available} value={typed} onChange={setTyped} />
      {invoices.data.length > 0 ? (
        <Button type="submit" className="w-fit" disabled={busy || !problems.valid || problems.totalPaise === 0}>
          {busy ? 'Allocating…' : 'Allocate'}
        </Button>
      ) : null}
    </form>
  )
}

type RefundFormProps = {
  unallocated: number
  busy: boolean
  failed: unknown
  onRefund: (input: RefundInput, idempotencyKey: string) => void
}

function RefundForm({ unallocated, busy, failed, onRefund }: RefundFormProps) {
  const methods = useQuery(paymentMethodsQuery())
  const [amount, setAmount] = useState(unallocated.toFixed(2))
  const [methodId, setMethodId] = useState('')
  const [reference, setReference] = useState('')
  const [reason, setReason] = useState('')
  const [problem, setProblem] = useState<string | null>(null)
  const [key, setKey] = useState(newIdempotencyKey)
  const method = (methods.data ?? []).find((candidate) => candidate.id === methodId)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!/^\d+(\.\d{1,2})?$/.test(amount.trim()) || Number(amount) <= 0) {
      setProblem('Enter an amount of more than zero, with at most 2 decimals.')
    } else if (toPaise(amount) > toPaise(unallocated)) {
      setProblem(`Only ${formatMoney(unallocated)} is unallocated. To refund more, release that much from its invoices first.`)
    } else if (!method) {
      setProblem('Choose how the money is returned.')
    } else if (method.requiresReference && reference.trim() === '') {
      setProblem(`A reference is required for ${method.name}.`)
    } else if (reason.trim() === '') {
      setProblem('Say why: the reason is kept with the refund.')
    } else {
      setProblem(null)
      const next = isAnswered(failed) ? newIdempotencyKey() : key
      setKey(next)
      onRefund({ amount: Number(amount), methodId, reference: reference.trim() === '' ? null : reference.trim(), reason: reason.trim() }, next)
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-3 rounded-xl border p-3" aria-label="Refund">
      <p className="text-sm">
        Returns unallocated money to the customer. A refund is final once recorded: it cannot be edited or deleted.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="grid gap-1.5">
          <Label htmlFor="refund-amount">Amount (₹)</Label>
          <Input id="refund-amount" inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="refund-method">Returned by</Label>
          <Select id="refund-method" value={methodId} onChange={(event) => setMethodId(event.target.value)}>
            <option value="">Choose…</option>
            {(methods.data ?? []).map((candidate) => (
              <option key={candidate.id} value={candidate.id}>
                {candidate.name}
              </option>
            ))}
          </Select>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="refund-reference">Reference{method?.requiresReference ? '' : ' (optional)'}</Label>
          <Input id="refund-reference" maxLength={100} value={reference} onChange={(event) => setReference(event.target.value)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="refund-reason">Reason</Label>
          <Input id="refund-reason" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />
        </div>
      </div>
      {problem ? (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      ) : null}
      <Button type="submit" variant="destructive" className="w-fit" disabled={busy}>
        {busy ? 'Recording…' : 'Record refund'}
      </Button>
    </form>
  )
}
