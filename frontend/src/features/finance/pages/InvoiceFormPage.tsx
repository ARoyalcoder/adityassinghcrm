import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { FormActions, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { customerQuery } from '@/features/customers/api'
import {
  createInvoice,
  financeSettingsQuery,
  formatMoney,
  invoiceQuery,
  previewQuery,
  refreshFinance,
  updateInvoice,
  type InvoiceDetail,
  type InvoiceItemInput,
} from '@/features/finance/api'
import { CustomerPicker, Figures, Section } from '@/features/finance/components/FinanceParts'
import { applyServerErrors, blankToNull } from '@/lib/forms'

/** A number typed into a text box: optional or not, within a range, with at most so many decimals. */
function decimal(options: { decimals: number; min: number; exclusive?: boolean; max?: number; blank?: boolean; message: string }) {
  const pattern = new RegExp(`^\\d+(\\.\\d{1,${options.decimals}})?$`)
  return z
    .string()
    .trim()
    .refine((text) => {
      if (text === '') {
        return options.blank === true
      }
      const value = Number(text)
      return (
        pattern.test(text) &&
        (options.exclusive ? value > options.min : value >= options.min) &&
        (options.max === undefined || value <= options.max)
      )
    }, options.message)
}

const percent = decimal({ decimals: 2, min: 0, max: 100, blank: true, message: 'A percentage from 0 to 100' })

const lineSchema = z.object({
  description: z.string().trim().min(1, 'Say what is being billed').max(500, 'At most 500 characters'),
  hsnSac: z.string().trim().refine((text) => text === '' || /^[0-9]{4,8}$/.test(text), '4 to 8 digits'),
  quantity: decimal({ decimals: 3, min: 0, exclusive: true, message: 'More than zero, at most 3 decimals' }),
  unit: z.string().trim().max(20, 'At most 20 characters'),
  unitPrice: decimal({ decimals: 2, min: 0, message: 'An amount, at most 2 decimals' }),
  discountPercent: percent,
  taxPercent: percent,
})

const schema = z.object({
  reference: z.string().trim().max(100, 'At most 100 characters'),
  paymentTermsDays: z.string().trim().refine((text) => /^\d{1,3}$/.test(text) && Number(text) <= 365, 'Days, from 0 to 365'),
  billingAddress: z.string().trim().max(1000, 'At most 1000 characters'),
  customerTaxId: z.string().trim().max(30, 'At most 30 characters'),
  notes: z.string().trim().max(2000, 'At most 2000 characters'),
  terms: z.string().trim().max(4000, 'At most 4000 characters'),
  items: z.array(lineSchema).min(1, 'Add at least one line').max(100, 'At most 100 lines'),
})
type Values = z.infer<typeof schema>
type Line = Values['items'][number]

function toItems(lines: Line[]): InvoiceItemInput[] {
  return lines.map((line) => ({
    description: line.description.trim(),
    hsnSac: blankToNull(line.hsnSac),
    quantity: Number(line.quantity),
    unit: blankToNull(line.unit),
    unitPrice: Number(line.unitPrice),
    discountPercent: line.discountPercent.trim() === '' ? 0 : Number(line.discountPercent),
    taxPercent: line.taxPercent.trim() === '' ? 0 : Number(line.taxPercent),
  }))
}

/** The value, a moment after it last changed: so the totals are asked for when typing pauses, not on every key. */
function useSettled<T>(value: T, delay = 350) {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return settled
}

/** Prepare an invoice (`/invoices/new`) or change a draft (`/invoices/:id/edit`). */
export function InvoiceFormPage() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const presetCustomerId = id ? undefined : (params.get('customerId') ?? undefined)
  const existing = useQuery({ ...invoiceQuery(id ?? ''), enabled: id !== undefined })
  const settings = useQuery(financeSettingsQuery)
  const preset = useQuery({ ...customerQuery(presetCustomerId ?? ''), enabled: presetCustomerId !== undefined })
  const back = id ? { to: `/invoices/${id}`, label: 'Invoice' } : { to: '/invoices', label: 'Invoices' }
  const waitingFor = id && !existing.data ? existing : !settings.data ? settings : presetCustomerId && preset.isPending ? preset : null

  return (
    <div className="grid gap-6">
      <PageHeader title={id ? 'Edit draft invoice' : 'New invoice'} back={back} />
      {waitingFor ? (
        <RecordLoadState noun="invoice" isPending={waitingFor.isPending} error={waitingFor.error} onRetry={() => void waitingFor.refetch()} />
      ) : existing.data && !existing.data.actions.edit ? (
        <p role="alert" className="rounded-2xl bg-card p-6 text-sm shadow-soft">
          This invoice has been issued and can no longer be changed. Use a credit note or a debit note instead.
        </p>
      ) : (
        <InvoiceForm
          key={existing.data?.version ?? 'new'}
          detail={existing.data}
          defaults={{ terms: settings.data?.defaultPaymentTermsDays ?? 0, tax: settings.data?.defaultTaxPercent ?? 0 }}
          presetCustomer={preset.data ? { id: preset.data.id, name: preset.data.displayName } : null}
          cancelTo={back.to}
        />
      )}
    </div>
  )
}

type InvoiceFormProps = {
  detail?: InvoiceDetail
  defaults: { terms: number; tax: number }
  presetCustomer: { id: string; name: string } | null
  cancelTo: string
}

function InvoiceForm({ detail, defaults, presetCustomer, cancelTo }: InvoiceFormProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [customer, setCustomer] = useState<{ id: string; name: string } | null>(
    detail ? { id: detail.invoice.customerId, name: detail.invoice.customerName } : presetCustomer,
  )
  const [customerError, setCustomerError] = useState<string | undefined>()
  const blankLine: Line = {
    description: '', hsnSac: '', quantity: '1', unit: '', unitPrice: '', discountPercent: '', taxPercent: String(defaults.tax),
  }
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      reference: detail?.reference ?? '',
      paymentTermsDays: String(detail?.paymentTermsDays ?? defaults.terms),
      billingAddress: detail?.billingAddress ?? '',
      customerTaxId: detail?.customerTaxId ?? '',
      notes: detail?.notes ?? '',
      terms: detail?.terms ?? '',
      items: detail
        ? detail.items.map((item) => ({
            description: item.description,
            hsnSac: item.hsnSac ?? '',
            quantity: String(item.quantity),
            unit: item.unit ?? '',
            unitPrice: String(item.unitPrice),
            discountPercent: item.discountPercent === 0 ? '' : String(item.discountPercent),
            taxPercent: String(item.taxPercent),
          }))
        : [blankLine],
    },
  })
  const lines = useFieldArray({ control: form.control, name: 'items' })

  // The totals come from the server, so the form shows to the paisa what will be saved.
  const typed = useWatch({ control: form.control, name: 'items' })
  const checked = z.array(lineSchema).min(1).safeParse(typed)
  const settled = useSettled(checked.success ? JSON.stringify(toItems(checked.data)) : null)
  const preview = useQuery({ ...previewQuery(settled ? (JSON.parse(settled) as InvoiceItemInput[]) : []), enabled: settled !== null })
  const totals = settled !== null && preview.data && preview.data.lines.length === lines.fields.length ? preview.data : null

  async function onSubmit(values: Values) {
    setFormError(null)
    if (!customer) {
      setCustomerError('Choose the customer to bill')
      return
    }
    const input = {
      customerId: customer.id,
      billingAddress: blankToNull(values.billingAddress),
      customerTaxId: blankToNull(values.customerTaxId),
      reference: blankToNull(values.reference),
      paymentTermsDays: Number(values.paymentTermsDays),
      notes: blankToNull(values.notes),
      terms: blankToNull(values.terms),
      items: toItems(values.items),
    }
    try {
      const saved = detail ? await updateInvoice(detail.invoice.id, { ...input, version: detail.version }) : await createInvoice(input)
      await refreshFinance(queryClient)
      void navigate(`/invoices/${saved.invoice.id}`)
    } catch (error) {
      const message = applyServerErrors(form, error)
      setFormError(message)
    }
  }

  const lineField = (index: number, name: keyof Line, label: string, className: string, inputMode?: 'decimal' | 'numeric') => (
    <FormField
      control={form.control}
      name={`items.${index}.${name}`}
      render={({ field }) => (
        <FormItem className={className}>
          <FormLabel className="text-xs text-muted-foreground">{label}</FormLabel>
          <FormControl>
            <Input inputMode={inputMode} {...field} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )

  const text = (name: 'reference' | 'paymentTermsDays' | 'customerTaxId', label: string, description?: string) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input inputMode={name === 'paymentTermsDays' ? 'numeric' : undefined} {...field} />
          </FormControl>
          {description ? <FormDescription>{description}</FormDescription> : null}
          <FormMessage />
        </FormItem>
      )}
    />
  )

  const area = (name: 'billingAddress' | 'notes' | 'terms', label: string, description: string) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Textarea rows={3} {...field} />
          </FormControl>
          <FormDescription>{description}</FormDescription>
          <FormMessage />
        </FormItem>
      )}
    />
  )

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="grid max-w-5xl gap-5"
        aria-label={detail ? 'Edit draft invoice' : 'New invoice'}
        noValidate
      >
        <FormError message={formError} />
        <Section title="Customer">
          <CustomerPicker
            value={customer}
            error={customer ? undefined : customerError}
            onChange={(chosen) => {
              setCustomer(chosen)
              setCustomerError(undefined)
            }}
          />
          <div className="grid gap-4 sm:grid-cols-3">
            {text('reference', 'Their reference', 'A purchase order number, for example.')}
            {text('paymentTermsDays', 'Payment terms (days)', 'Days from issue until the invoice is due.')}
            {text('customerTaxId', 'GSTIN', 'Left empty, the customer record is used.')}
          </div>
          {area('billingAddress', 'Billing address', "Left empty, the customer's billing address is used.")}
        </Section>

        <Section
          title="Lines"
          action={
            <Button type="button" variant="outline" size="sm" onClick={() => lines.append(blankLine)}>
              <Plus aria-hidden="true" />
              Add line
            </Button>
          }
        >
          <ol className="grid gap-3">
            {lines.fields.map((line, index) => (
              <li key={line.id} className="grid gap-3 rounded-xl border p-3" aria-label={`Line ${index + 1}`}>
                <div className="grid gap-3 sm:grid-cols-12">
                  {lineField(index, 'description', 'Description', 'sm:col-span-9')}
                  {lineField(index, 'hsnSac', 'HSN / SAC', 'sm:col-span-3', 'numeric')}
                  {lineField(index, 'quantity', 'Quantity', 'sm:col-span-2', 'decimal')}
                  {lineField(index, 'unit', 'Unit', 'sm:col-span-2')}
                  {lineField(index, 'unitPrice', 'Unit price (₹)', 'sm:col-span-3', 'decimal')}
                  {lineField(index, 'discountPercent', 'Discount %', 'sm:col-span-2', 'decimal')}
                  {lineField(index, 'taxPercent', 'Tax %', 'sm:col-span-3', 'decimal')}
                </div>
                <div className="flex items-center justify-between gap-3 text-sm">
                  <span className="text-muted-foreground">
                    Line total{' '}
                    <span className="font-medium text-foreground tabular-nums">
                      {totals ? formatMoney(totals.lines[index].lineTotal) : '—'}
                    </span>
                  </span>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled={lines.fields.length === 1}
                    onClick={() => lines.remove(index)}
                    aria-label={`Remove line ${index + 1}`}
                  >
                    <Trash2 aria-hidden="true" />
                    Remove
                  </Button>
                </div>
              </li>
            ))}
          </ol>
          <div className="ml-auto w-full max-w-xs" aria-live="polite" aria-label="Totals">
            {totals ? (
              <Figures
                rows={[
                  { label: 'Subtotal', value: formatMoney(totals.subtotal) },
                  { label: 'Discount', value: `− ${formatMoney(totals.discountTotal)}` },
                  { label: 'Tax', value: formatMoney(totals.taxTotal) },
                  { label: 'Total', value: formatMoney(totals.total), strong: true },
                ]}
              />
            ) : (
              <p className="text-right text-sm text-muted-foreground">
                {preview.isError ? 'The total could not be worked out.' : 'The total appears once every line is complete.'}
              </p>
            )}
          </div>
        </Section>

        <Section title="On the invoice">
          <div className="grid gap-4 sm:grid-cols-2">
            {area('notes', 'Notes', 'Printed on the invoice, for the customer.')}
            {area('terms', 'Terms', 'Payment instructions, bank details, conditions.')}
          </div>
        </Section>

        <p className="text-sm text-muted-foreground">
          This saves a draft. A draft has no number and can be changed or discarded; it becomes an invoice when you issue it.
        </p>
        <FormActions submitLabel={detail ? 'Save draft' : 'Create draft'} submitting={form.formState.isSubmitting} cancelTo={cancelTo} />
      </form>
    </Form>
  )
}
