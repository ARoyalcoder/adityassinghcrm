import { keepPreviousData, queryOptions, type QueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { ApiError } from '@/lib/api/errors'
import { pageSchema, queryString } from '@/lib/api/page'

// These lists must match the backend enums in finance.api.

export const INVOICE_STATUSES = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'ISSUED', label: 'Issued' },
  { value: 'PARTIALLY_PAID', label: 'Partially paid' },
  { value: 'PAID', label: 'Paid' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'CANCELLED', label: 'Cancelled' },
] as const

export const PAYMENT_STATUSES = [
  { value: 'RECEIVED', label: 'Received' },
  { value: 'REVERSED', label: 'Reversed' },
] as const

export function labelOf(options: readonly { value: string; label: string }[], value: string | null | undefined) {
  return options.find((option) => option.value === value)?.label ?? value ?? '—'
}

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })
const dayFormat = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })

/** ₹1,18,000.00. Money in the books is always shown to the paisa. */
export function formatMoney(amount: number) {
  return rupees.format(amount)
}

/** "10 Oct 2026" from the server's "2026-10-10": a date, not a moment, so no time zone is applied. */
export function formatDay(date: string | null | undefined) {
  if (!date) {
    return '—'
  }
  const [year, month, day] = date.split('-').map(Number)
  return dayFormat.format(new Date(year, month - 1, day))
}

/** The server's "2026-10-10" moved by a number of days. */
export function addDays(date: string, days: number) {
  const [year, month, day] = date.split('-').map(Number)
  const moved = new Date(year, month - 1, day + days)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${moved.getFullYear()}-${pad(moved.getMonth() + 1)}-${pad(moved.getDate())}`
}

/** Amounts are added up in whole paise, so 0.1 + 0.2 is never 0.30000000000000004. */
export function toPaise(amount: number | string) {
  return Math.round(Number(amount) * 100)
}

/**
 * The key that makes a request that moves money take effect once. A form makes one when it opens
 * and sends it with every attempt to save, so a double click or a retry after a lost connection
 * cannot record twice.
 */
export function newIdempotencyKey() {
  return crypto.randomUUID()
}

/**
 * After a failed save: whether the next attempt is a new request. If the server answered, nothing
 * was recorded and whatever is sent next (perhaps corrected) deserves a new key. If it did not
 * answer, the request may have arrived, so the same key must be sent again.
 */
export function isAnswered(error: unknown) {
  return error instanceof ApiError && error.status !== 0
}

// ------------------------------------------------------------------ invoices

const invoiceSummarySchema = z.object({
  id: z.string(),
  invoiceNumber: z.string().nullish(),
  /** As it stands today: OVERDUE where money is owing past the due date. */
  status: z.string(),
  customerId: z.string(),
  customerName: z.string(),
  issueDate: z.string().nullish(),
  dueDate: z.string().nullish(),
  currency: z.string(),
  total: z.number(),
  amountPaid: z.number(),
  /** What is still owed; zero for a draft or a cancelled invoice. */
  outstanding: z.number(),
  daysOverdue: z.number().nullish(),
})
export type InvoiceSummary = z.infer<typeof invoiceSummarySchema>

const noteSchema = z.object({
  id: z.string(),
  noteNumber: z.string(),
  noteDate: z.string(),
  amount: z.number(),
  reason: z.string(),
  issuedByName: z.string(),
  createdAt: z.string(),
})
export type AdjustmentNote = z.infer<typeof noteSchema>

const invoiceDetailSchema = z.object({
  invoice: invoiceSummarySchema,
  billingAddress: z.string().nullish(),
  customerTaxId: z.string().nullish(),
  reference: z.string().nullish(),
  paymentTermsDays: z.number(),
  notes: z.string().nullish(),
  terms: z.string().nullish(),
  subtotal: z.number(),
  discountTotal: z.number(),
  taxTotal: z.number(),
  creditTotal: z.number(),
  debitTotal: z.number(),
  items: z.array(
    z.object({
      id: z.string(),
      serviceId: z.string().nullish(),
      description: z.string(),
      hsnSac: z.string().nullish(),
      quantity: z.number(),
      unit: z.string().nullish(),
      unitPrice: z.number(),
      discountPercent: z.number(),
      taxPercent: z.number(),
      lineSubtotal: z.number(),
      discountAmount: z.number(),
      taxAmount: z.number(),
      lineTotal: z.number(),
    }),
  ),
  /** Payments allocated to the invoice; a released one no longer counts. */
  payments: z.array(
    z.object({
      allocationId: z.string(),
      paymentId: z.string(),
      paymentNumber: z.string(),
      receivedOn: z.string(),
      methodName: z.string(),
      amount: z.number(),
      released: z.boolean(),
      allocatedByName: z.string(),
      allocatedAt: z.string().nullish(),
    }),
  ),
  creditNotes: z.array(noteSchema),
  debitNotes: z.array(noteSchema),
  /** The company's own details, for the head of the printed invoice. */
  seller: z
    .object({
      name: z.string(),
      taxId: z.string().nullish(),
      email: z.string().nullish(),
      phone: z.string().nullish(),
      address: z.string().nullish(),
    })
    .nullish(),
  issuedAt: z.string().nullish(),
  issuedByName: z.string().nullish(),
  cancelledAt: z.string().nullish(),
  cancellationReason: z.string().nullish(),
  /** What the signed-in person may do with the invoice as it stands. */
  actions: z.object({
    edit: z.boolean(),
    issue: z.boolean(),
    discard: z.boolean(),
    recordPayment: z.boolean(),
    creditNote: z.boolean(),
    debitNote: z.boolean(),
    cancel: z.boolean(),
  }),
  version: z.number(),
})
export type InvoiceDetail = z.infer<typeof invoiceDetailSchema>

const previewSchema = z.object({
  lines: z.array(z.object({ lineSubtotal: z.number(), discountAmount: z.number(), taxAmount: z.number(), lineTotal: z.number() })),
  subtotal: z.number(),
  discountTotal: z.number(),
  taxTotal: z.number(),
  total: z.number(),
})
export type InvoicePreview = z.infer<typeof previewSchema>

export const financeKeys = {
  all: ['finance'] as const,
  invoice: (id: string) => ['finance', 'invoice', id] as const,
  payment: (id: string) => ['finance', 'payment', id] as const,
}

/**
 * Called after anything that changes the books. Every finance screen shows figures that depend on
 * every other (a payment changes an invoice, the summary and the ageing), so they are all refreshed.
 * The customer profile shows what the customer owes, so it is refreshed too.
 */
export function refreshFinance(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: financeKeys.all }),
    queryClient.invalidateQueries({ queryKey: ['customers'] }),
  ])
}

export type InvoiceFilters = {
  search?: string
  status?: string
  customerId?: string
  /** "true": only invoices that still have money owing. */
  outstanding?: string
  sort?: string
  page?: number
  size?: number
}

export function invoicesQuery(filters: InvoiceFilters) {
  return queryOptions({
    queryKey: [...financeKeys.all, 'invoices', filters],
    queryFn: ({ signal }) => apiRequest(`/invoices${queryString(filters)}`, pageSchema(invoiceSummarySchema), { signal }),
    placeholderData: keepPreviousData,
  })
}

export function invoiceQuery(id: string) {
  return queryOptions({
    queryKey: financeKeys.invoice(id),
    queryFn: ({ signal }) => apiRequest(`/invoices/${id}`, invoiceDetailSchema, { signal }),
  })
}

/** A customer's invoices that can take a payment, the one due earliest first. */
export function openInvoicesQuery(customerId: string) {
  return queryOptions({
    queryKey: [...financeKeys.all, 'open-invoices', customerId],
    queryFn: ({ signal }) => apiRequest(`/invoices/open${queryString({ customerId })}`, z.array(invoiceSummarySchema), { signal }),
  })
}

export type InvoiceItemInput = {
  serviceId?: string | null
  description: string
  hsnSac: string | null
  quantity: number
  unit: string | null
  unitPrice: number
  discountPercent: number
  taxPercent: number
}

export type InvoiceInput = {
  customerId: string
  billingAddress: string | null
  customerTaxId: string | null
  reference: string | null
  paymentTermsDays: number
  notes: string | null
  terms: string | null
  items: InvoiceItemInput[]
}

/** What the lines add up to, worked out by the server so the form shows exactly what will be saved. */
export function previewQuery(items: InvoiceItemInput[]) {
  return queryOptions({
    queryKey: [...financeKeys.all, 'preview', items],
    queryFn: ({ signal }) => apiRequest('/invoices/preview', previewSchema, { method: 'POST', body: { items }, signal }),
    placeholderData: keepPreviousData,
    staleTime: Infinity,
  })
}

export function createInvoice(input: InvoiceInput) {
  return apiRequest('/invoices', invoiceDetailSchema, { method: 'POST', body: input })
}

export function updateInvoice(id: string, input: InvoiceInput & { version: number }) {
  return apiRequest(`/invoices/${id}`, invoiceDetailSchema, { method: 'PUT', body: input })
}

export function discardInvoice(id: string) {
  return apiRequest(`/invoices/${id}`, z.undefined(), { method: 'DELETE' })
}

export function issueInvoice(id: string, input: { issueDate: string; dueDate: string }) {
  return apiRequest(`/invoices/${id}/issue`, invoiceDetailSchema, { method: 'POST', body: input })
}

export function cancelInvoice(id: string, reason: string) {
  return apiRequest(`/invoices/${id}/cancel`, invoiceDetailSchema, { method: 'POST', body: { reason } })
}

export type NoteInput = { amount: number; reason: string }

export function addCreditNote(id: string, input: NoteInput, idempotencyKey: string) {
  return apiRequest(`/invoices/${id}/credit-notes`, invoiceDetailSchema, { method: 'POST', body: input, idempotencyKey })
}

export function addDebitNote(id: string, input: NoteInput, idempotencyKey: string) {
  return apiRequest(`/invoices/${id}/debit-notes`, invoiceDetailSchema, { method: 'POST', body: input, idempotencyKey })
}

// ------------------------------------------------------------------ payments

const paymentSummarySchema = z.object({
  id: z.string(),
  paymentNumber: z.string(),
  status: z.string(),
  customerId: z.string(),
  customerName: z.string(),
  receivedOn: z.string(),
  currency: z.string(),
  amount: z.number(),
  allocated: z.number(),
  refunded: z.number(),
  /** What is left to allocate or refund. */
  unallocated: z.number(),
  methodName: z.string(),
  reference: z.string().nullish(),
})
export type PaymentSummary = z.infer<typeof paymentSummarySchema>

const paymentDetailSchema = z.object({
  payment: paymentSummarySchema,
  notes: z.string().nullish(),
  /** MANUAL or GATEWAY. */
  source: z.string(),
  gateway: z.string().nullish(),
  recordedByName: z.string(),
  recordedAt: z.string().nullish(),
  reversedAt: z.string().nullish(),
  reversedByName: z.string().nullish(),
  reversalReason: z.string().nullish(),
  allocations: z.array(
    z.object({
      id: z.string(),
      invoiceId: z.string(),
      invoiceNumber: z.string().nullish(),
      amount: z.number(),
      released: z.boolean(),
      releaseReason: z.string().nullish(),
      allocatedByName: z.string(),
      allocatedAt: z.string().nullish(),
    }),
  ),
  refunds: z.array(
    z.object({
      id: z.string(),
      refundNumber: z.string(),
      refundDate: z.string(),
      amount: z.number(),
      methodName: z.string(),
      reference: z.string().nullish(),
      reason: z.string(),
      refundedByName: z.string(),
      createdAt: z.string().nullish(),
    }),
  ),
  actions: z.object({ allocate: z.boolean(), release: z.boolean(), refund: z.boolean(), reverse: z.boolean() }),
})
export type PaymentDetail = z.infer<typeof paymentDetailSchema>

export type PaymentFilters = {
  search?: string
  status?: string
  customerId?: string
  /** "true": only payments with money still to allocate or refund. */
  unallocated?: string
  sort?: string
  page?: number
  size?: number
}

export function paymentsQuery(filters: PaymentFilters) {
  return queryOptions({
    queryKey: [...financeKeys.all, 'payments', filters],
    queryFn: ({ signal }) => apiRequest(`/payments${queryString(filters)}`, pageSchema(paymentSummarySchema), { signal }),
    placeholderData: keepPreviousData,
  })
}

export function paymentQuery(id: string) {
  return queryOptions({
    queryKey: financeKeys.payment(id),
    queryFn: ({ signal }) => apiRequest(`/payments/${id}`, paymentDetailSchema, { signal }),
  })
}

export type AllocationInput = { invoiceId: string; amount: number }

export type PaymentInput = {
  customerId: string
  amount: number
  receivedOn: string
  methodId: string
  reference: string | null
  notes: string | null
  allocations: AllocationInput[]
}

export function recordPayment(input: PaymentInput, idempotencyKey: string) {
  return apiRequest('/payments', paymentDetailSchema, { method: 'POST', body: input, idempotencyKey })
}

export function allocatePayment(id: string, allocations: AllocationInput[], idempotencyKey: string) {
  return apiRequest(`/payments/${id}/allocations`, paymentDetailSchema, { method: 'POST', body: { allocations }, idempotencyKey })
}

export function releaseAllocation(id: string, allocationId: string, reason: string) {
  return apiRequest(`/payments/${id}/allocations/${allocationId}/release`, paymentDetailSchema, { method: 'POST', body: { reason } })
}

export type RefundInput = { amount: number; methodId: string; reference: string | null; reason: string }

export function refundPayment(id: string, input: RefundInput, idempotencyKey: string) {
  return apiRequest(`/payments/${id}/refunds`, paymentDetailSchema, { method: 'POST', body: input, idempotencyKey })
}

export function reversePayment(id: string, reason: string) {
  return apiRequest(`/payments/${id}/reverse`, paymentDetailSchema, { method: 'POST', body: { reason } })
}

// ------------------------------------------------------------------ payment methods

const methodSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  /** A cheque number, a UTR: must be given with every payment by this method. */
  requiresReference: z.boolean(),
  active: z.boolean(),
  displayOrder: z.number(),
  version: z.number(),
})
export type PaymentMethod = z.infer<typeof methodSchema>

export function paymentMethodsQuery(includeInactive = false) {
  return queryOptions({
    queryKey: [...financeKeys.all, 'methods', includeInactive],
    queryFn: ({ signal }) =>
      apiRequest(`/payment-methods${queryString({ includeInactive: includeInactive ? 'true' : '' })}`, z.array(methodSchema), { signal }),
  })
}

export type MethodInput = { code: string; name: string; requiresReference: boolean; active: boolean; displayOrder: number }

export function saveMethod(input: MethodInput, existing?: PaymentMethod) {
  return existing
    ? apiRequest(`/payment-methods/${existing.id}`, methodSchema, { method: 'PUT', body: { ...input, version: existing.version } })
    : apiRequest('/payment-methods', methodSchema, { method: 'POST', body: input })
}

export function deleteMethod(id: string) {
  return apiRequest(`/payment-methods/${id}`, z.undefined(), { method: 'DELETE' })
}

// ------------------------------------------------------------------ reports

const summarySchema = z.object({
  outstanding: z.number(),
  overdue: z.number(),
  /** Falls due within the next seven days. */
  dueSoon: z.number(),
  openInvoices: z.number(),
  overdueInvoices: z.number(),
  draftInvoices: z.number(),
  unallocatedPayments: z.number(),
  invoicedThisMonth: z.number(),
  receivedThisMonth: z.number(),
  currency: z.string(),
  asOf: z.string(),
})
export type FinanceSummary = z.infer<typeof summarySchema>

export const financeSummaryQuery = queryOptions({
  queryKey: [...financeKeys.all, 'summary'],
  queryFn: ({ signal }) => apiRequest('/finance/summary', summarySchema, { signal }),
})

const ageingSchema = z.object({
  asOf: z.string(),
  /** "Not due" first, then the bands by age. */
  buckets: z.array(z.object({ label: z.string(), fromDays: z.number().nullish(), toDays: z.number().nullish() })),
  totals: z.array(z.number()),
  total: z.number(),
  overdue: z.number(),
  customers: z.array(
    z.object({
      customerId: z.string(),
      customerName: z.string(),
      amounts: z.array(z.number()),
      total: z.number(),
      oldestDaysOverdue: z.number(),
      invoices: z.number(),
    }),
  ),
})
export type AgeingReport = z.infer<typeof ageingSchema>

export const ageingQuery = queryOptions({
  queryKey: [...financeKeys.all, 'ageing'],
  queryFn: ({ signal }) => apiRequest('/finance/ageing', ageingSchema, { signal }),
})

/** The defaults a new invoice starts from, and the date the books take as today. */
export const financeSettingsQuery = queryOptions({
  queryKey: [...financeKeys.all, 'settings'],
  queryFn: ({ signal }) =>
    apiRequest(
      '/finance/settings',
      z.object({ currency: z.string(), defaultPaymentTermsDays: z.number(), defaultTaxPercent: z.number(), today: z.string() }),
      { signal },
    ),
  staleTime: 5 * 60 * 1000,
})
