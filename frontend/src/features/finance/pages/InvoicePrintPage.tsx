import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Printer } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { RecordLoadState } from '@/components/forms/FormParts'
import { Button } from '@/components/ui/button'
import { formatDay, formatMoney, invoiceQuery, type InvoiceDetail } from '@/features/finance/api'

/**
 * The invoice as the customer receives it (`/invoices/:id/print`): one white sheet with nothing of
 * the application around it. Printing it, or saving it as a PDF from the print dialog, gives the
 * document to send.
 */
export function InvoicePrintPage() {
  const { id = '' } = useParams()
  const invoice = useQuery(invoiceQuery(id))

  return (
    <div className="min-h-screen bg-muted/40 px-4 py-6 print:bg-white print:p-0">
      <div className="mx-auto mb-4 flex max-w-3xl items-center justify-between gap-3 print:hidden">
        <Link to={`/invoices/${id}`} className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-4" aria-hidden="true" />
          Back to the invoice
        </Link>
        <Button onClick={() => window.print()} disabled={!invoice.data}>
          <Printer aria-hidden="true" />
          Print or save as PDF
        </Button>
      </div>
      {invoice.data ? (
        <Sheet detail={invoice.data} />
      ) : (
        <div className="mx-auto max-w-3xl">
          <RecordLoadState noun="invoice" isPending={invoice.isPending} error={invoice.error} onRetry={() => void invoice.refetch()} />
        </div>
      )}
    </div>
  )
}

function Sheet({ detail }: { detail: InvoiceDetail }) {
  const { invoice, seller } = detail
  const settled = invoice.status !== 'DRAFT' && invoice.status !== 'CANCELLED'

  return (
    <article className="mx-auto grid max-w-3xl gap-8 rounded-2xl bg-white p-8 text-sm text-black shadow-soft print:max-w-none print:rounded-none print:p-0 print:shadow-none">
      {invoice.status === 'DRAFT' ? (
        <p className="rounded-md border border-dashed border-black/40 p-2 text-center font-medium">
          Draft. Not an invoice until it is issued.
        </p>
      ) : null}
      {invoice.status === 'CANCELLED' ? (
        <p className="rounded-md border border-black/40 p-2 text-center font-medium">Cancelled. Nothing is owed on this invoice.</p>
      ) : null}

      <header className="flex flex-wrap items-start justify-between gap-6">
        <div className="grid gap-0.5">
          <span className="text-lg font-semibold">{seller?.name ?? 'Pawan Putra'}</span>
          {seller?.address ? <span className="whitespace-pre-line text-black/70">{seller.address}</span> : null}
          {seller?.taxId ? <span className="text-black/70">GSTIN {seller.taxId}</span> : null}
          {seller?.phone || seller?.email ? (
            <span className="text-black/70">{[seller.phone, seller.email].filter(Boolean).join(' · ')}</span>
          ) : null}
        </div>
        <div className="grid gap-0.5 text-right">
          <h1 className="text-2xl font-semibold tracking-tight">Tax invoice</h1>
          <span className="font-mono">{invoice.invoiceNumber ?? 'Not yet numbered'}</span>
        </div>
      </header>

      <section className="grid gap-6 sm:grid-cols-2 print:grid-cols-2">
        <div className="grid gap-0.5">
          <span className="text-xs tracking-wide text-black/60 uppercase">Billed to</span>
          <span className="font-medium">{invoice.customerName}</span>
          {detail.billingAddress ? <span className="whitespace-pre-line text-black/70">{detail.billingAddress}</span> : null}
          {detail.customerTaxId ? <span className="text-black/70">GSTIN {detail.customerTaxId}</span> : null}
        </div>
        <dl className="grid h-fit grid-cols-2 gap-x-4 gap-y-1 sm:justify-self-end print:justify-self-end">
          <dt className="text-black/60">Invoice date</dt>
          <dd className="text-right">{formatDay(invoice.issueDate)}</dd>
          <dt className="text-black/60">Due date</dt>
          <dd className="text-right">{formatDay(invoice.dueDate)}</dd>
          {detail.reference ? (
            <>
              <dt className="text-black/60">Your reference</dt>
              <dd className="text-right">{detail.reference}</dd>
            </>
          ) : null}
        </dl>
      </section>

      <table className="w-full">
        <caption className="sr-only">What is billed</caption>
        <thead className="border-b border-black/30 text-left text-xs text-black/60">
          <tr>
            <th scope="col" className="py-2 font-medium">Description</th>
            <th scope="col" className="py-2 text-right font-medium">Qty</th>
            <th scope="col" className="py-2 text-right font-medium">Rate</th>
            <th scope="col" className="py-2 text-right font-medium">Disc.</th>
            <th scope="col" className="py-2 text-right font-medium">Tax</th>
            <th scope="col" className="py-2 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody>
          {detail.items.map((item) => (
            <tr key={item.id} className="border-b border-black/10 align-top">
              <td className="py-2 pr-3">
                {item.description}
                {item.hsnSac ? <span className="block text-xs text-black/60">HSN/SAC {item.hsnSac}</span> : null}
              </td>
              <td className="py-2 text-right tabular-nums whitespace-nowrap">
                {item.quantity}
                {item.unit ? ` ${item.unit}` : ''}
              </td>
              <td className="py-2 text-right tabular-nums">{formatMoney(item.unitPrice)}</td>
              <td className="py-2 text-right tabular-nums">{item.discountPercent ? `${item.discountPercent}%` : ''}</td>
              <td className="py-2 text-right tabular-nums">{item.taxPercent ? `${item.taxPercent}%` : ''}</td>
              <td className="py-2 text-right tabular-nums">{formatMoney(item.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <dl className="ml-auto grid w-full max-w-xs gap-1.5">
        <Row label="Subtotal" value={formatMoney(detail.subtotal)} />
        {detail.discountTotal ? <Row label="Discount" value={`− ${formatMoney(detail.discountTotal)}`} /> : null}
        <Row label="Tax" value={formatMoney(detail.taxTotal)} />
        <Row label="Total" value={formatMoney(invoice.total)} strong />
        {detail.debitTotal ? <Row label="Debit notes" value={`+ ${formatMoney(detail.debitTotal)}`} /> : null}
        {detail.creditTotal ? <Row label="Credit notes" value={`− ${formatMoney(detail.creditTotal)}`} /> : null}
        {settled && invoice.amountPaid ? <Row label="Received" value={`− ${formatMoney(invoice.amountPaid)}`} /> : null}
        {settled ? <Row label="Balance due" value={formatMoney(invoice.outstanding)} strong /> : null}
      </dl>

      {detail.notes || detail.terms ? (
        <footer className="grid gap-3 border-t border-black/20 pt-4">
          {detail.notes ? <p className="whitespace-pre-line">{detail.notes}</p> : null}
          {detail.terms ? <p className="whitespace-pre-line text-black/70">{detail.terms}</p> : null}
        </footer>
      ) : null}
    </article>
  )
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={strong ? 'flex justify-between gap-4 border-t border-black/30 pt-1.5 font-semibold' : 'flex justify-between gap-4'}>
      <dt className={strong ? undefined : 'text-black/60'}>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  )
}
