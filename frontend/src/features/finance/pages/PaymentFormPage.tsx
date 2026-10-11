import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { FormActions, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { customerQuery } from '@/features/customers/api'
import { allocationProblems, toAllocations, typedPaise, type TypedAllocations } from '@/features/finance/allocations'
import {
  financeSettingsQuery,
  isAnswered,
  newIdempotencyKey,
  openInvoicesQuery,
  paymentMethodsQuery,
  recordPayment,
  refreshFinance,
  type InvoiceSummary,
  type PaymentMethod,
} from '@/features/finance/api'
import { AllocationEditor } from '@/features/finance/components/AllocationEditor'
import { CustomerPicker, Section } from '@/features/finance/components/FinanceParts'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const schema = z.object({
  amount: z
    .string()
    .trim()
    .refine((text) => /^\d+(\.\d{1,2})?$/.test(text) && Number(text) > 0, 'Enter the amount received, with at most 2 decimals'),
  receivedOn: z.string().refine((text) => /^\d{4}-\d{2}-\d{2}$/.test(text), 'Choose the date the money was received'),
  methodId: z.string().min(1, 'Choose how it was paid'),
  reference: z.string().trim().max(100, 'At most 100 characters'),
  notes: z.string().trim().max(1000, 'At most 1000 characters'),
})
type Values = z.infer<typeof schema>

/**
 * Record money received (`/payments/new`). Arriving from an invoice (`?customerId=…&invoiceId=…`)
 * starts with that customer, that invoice and what it is owed.
 */
export function PaymentFormPage() {
  const [params] = useSearchParams()
  const customerId = params.get('customerId') ?? undefined
  const invoiceId = params.get('invoiceId') ?? undefined
  const settings = useQuery(financeSettingsQuery)
  const methods = useQuery(paymentMethodsQuery())
  const presetCustomer = useQuery({ ...customerQuery(customerId ?? ''), enabled: customerId !== undefined })
  const presetInvoices = useQuery({ ...openInvoicesQuery(customerId ?? ''), enabled: customerId !== undefined && invoiceId !== undefined })
  const back = invoiceId ? { to: `/invoices/${invoiceId}`, label: 'Invoice' } : { to: '/payments', label: 'Payments' }
  const waitingFor = !settings.data
    ? settings
    : !methods.data
      ? methods
      : customerId && presetCustomer.isPending
        ? presetCustomer
        : customerId && invoiceId && presetInvoices.isPending
          ? presetInvoices
          : null

  return (
    <div className="grid gap-6">
      <PageHeader title="Record payment" back={back} />
      {waitingFor ? (
        <RecordLoadState noun="form" isPending={waitingFor.isPending} error={waitingFor.error} onRetry={() => void waitingFor.refetch()} />
      ) : (methods.data ?? []).length === 0 ? (
        <p role="alert" className="max-w-2xl rounded-2xl bg-card p-6 text-sm shadow-soft">
          No payment method is set up, so a payment cannot be recorded yet.{' '}
          <Link to="/admin/payment-methods" className="text-primary underline-offset-2 hover:underline">
            Set up payment methods
          </Link>
        </p>
      ) : (
        <PaymentForm
          today={settings.data?.today ?? ''}
          methods={methods.data ?? []}
          presetCustomer={presetCustomer.data ? { id: presetCustomer.data.id, name: presetCustomer.data.displayName } : null}
          presetInvoice={presetInvoices.data?.find((invoice) => invoice.id === invoiceId) ?? null}
          cancelTo={back.to}
        />
      )}
    </div>
  )
}

type PaymentFormProps = {
  today: string
  methods: PaymentMethod[]
  presetCustomer: { id: string; name: string } | null
  presetInvoice: InvoiceSummary | null
  cancelTo: string
}

function PaymentForm({ today, methods, presetCustomer, presetInvoice, cancelTo }: PaymentFormProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [customer, setCustomer] = useState(presetCustomer)
  const [customerError, setCustomerError] = useState<string | undefined>()
  const [typed, setTyped] = useState<TypedAllocations>(
    presetInvoice ? { [presetInvoice.id]: presetInvoice.outstanding.toFixed(2) } : {},
  )
  // One key for this form: however many times "Record" is pressed, one payment is recorded.
  const [key, setKey] = useState(newIdempotencyKey)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      amount: presetInvoice ? presetInvoice.outstanding.toFixed(2) : '',
      receivedOn: today,
      methodId: methods.length === 1 ? methods[0].id : '',
      reference: '',
      notes: '',
    },
  })
  const invoices = useQuery({ ...openInvoicesQuery(customer?.id ?? ''), enabled: customer !== null })
  const amountText = useWatch({ control: form.control, name: 'amount' })
  const methodId = useWatch({ control: form.control, name: 'methodId' })
  const method = methods.find((candidate) => candidate.id === methodId)
  const amountPaise = /^\d+(\.\d{1,2})?$/.test(amountText.trim()) ? typedPaise(amountText) : null

  async function onSubmit(values: Values) {
    setFormError(null)
    if (!customer) {
      setCustomerError('Choose who paid')
      return
    }
    if (values.receivedOn > today) {
      form.setError('receivedOn', { message: 'Must not be in the future' })
      return
    }
    if (method?.requiresReference && values.reference.trim() === '') {
      form.setError('reference', { message: `A reference is required for ${method.name}` })
      return
    }
    const open = invoices.data ?? []
    if (!allocationProblems(open, typed, typedPaise(values.amount)).valid) {
      setFormError('Check the amounts against the invoices below.')
      return
    }
    try {
      const saved = await recordPayment(
        {
          customerId: customer.id,
          amount: Number(values.amount),
          receivedOn: values.receivedOn,
          methodId: values.methodId,
          reference: blankToNull(values.reference),
          notes: blankToNull(values.notes),
          allocations: toAllocations(open, typed),
        },
        key,
      )
      await refreshFinance(queryClient)
      void navigate(`/payments/${saved.payment.id}`)
    } catch (error) {
      // If the server answered, it recorded nothing, and what is sent next is a new request.
      if (isAnswered(error)) {
        setKey(newIdempotencyKey())
      }
      setFormError(applyServerErrors(form, error))
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="grid max-w-3xl gap-5" aria-label="Record payment" noValidate>
        <FormError message={formError} />
        <Section title="Received from">
          <CustomerPicker
            value={customer}
            error={customer ? undefined : customerError}
            onChange={(chosen) => {
              setCustomer(chosen)
              setCustomerError(undefined)
              setTyped({})
            }}
          />
        </Section>

        <Section title="Payment">
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              control={form.control}
              name="amount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Amount received (₹)</FormLabel>
                  <FormControl>
                    <Input inputMode="decimal" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="receivedOn"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Received on</FormLabel>
                  <FormControl>
                    <Input type="date" max={today} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="methodId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Method</FormLabel>
                  <FormControl>
                    <Select {...field}>
                      <option value="">Choose…</option>
                      {methods.map((candidate) => (
                        <option key={candidate.id} value={candidate.id}>
                          {candidate.name}
                        </option>
                      ))}
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="reference"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reference{method?.requiresReference ? '' : ' (optional)'}</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>Cheque number, UTR or transaction id.</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <FormField
            control={form.control}
            name="notes"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Notes (optional)</FormLabel>
                <FormControl>
                  <Textarea rows={2} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </Section>

        <Section title="Settles">
          {!customer ? (
            <p className="text-sm text-muted-foreground">Choose the customer to see what they owe.</p>
          ) : invoices.isPending ? (
            <p className="text-sm text-muted-foreground">Loading what they owe…</p>
          ) : invoices.isError ? (
            <p role="alert" className="text-sm text-destructive">
              Could not load the customer’s invoices. {invoices.error.message}
            </p>
          ) : (
            <AllocationEditor invoices={invoices.data} availablePaise={amountPaise} value={typed} onChange={setTyped} />
          )}
          <p className="text-sm text-muted-foreground">
            Whatever is not put against an invoice stays with the payment as an advance. It can be allocated or refunded later.
          </p>
        </Section>

        <p className="text-sm text-muted-foreground">
          A recorded payment cannot be edited or deleted. If it turns out to be wrong, it is reversed, and the reversal stays on record.
        </p>
        <FormActions submitLabel="Record payment" submitting={form.formState.isSubmitting} cancelTo={cancelTo} />
      </form>
    </Form>
  )
}
