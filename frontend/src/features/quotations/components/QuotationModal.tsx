import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { customersQuery } from '@/features/customers/api'
import { leadsQuery } from '@/features/leads/api'
import { opportunitiesQuery } from '@/features/opportunities/api'
import {
  createQuotation,
  updateDraftQuotation,
  type CreateQuotationPayload,
  type ItemInput,
  type QuotationDetail,
} from '../api'
import { QuotationItemsEditor } from './QuotationItemsEditor'

interface QuotationModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  quotation?: QuotationDetail | null
  onSuccess: (quotation: QuotationDetail) => void
}

export function QuotationModal({
  open,
  onOpenChange,
  quotation,
  onSuccess,
}: QuotationModalProps) {
  const isEditing = Boolean(quotation)

  const [title, setTitle] = useState(quotation?.title ?? '')
  const [customerId, setCustomerId] = useState(quotation?.customerId ?? '')
  const [customerName, setCustomerName] = useState(quotation?.customerName ?? '')
  const [leadId, setLeadId] = useState(quotation?.leadId ?? '')
  const [leadName, setLeadName] = useState(quotation?.leadName ?? '')
  const [opportunityId, setOpportunityId] = useState(quotation?.opportunityId ?? '')
  const [opportunityTitle, setOpportunityTitle] = useState(quotation?.opportunityTitle ?? '')
  const [validUntil, setValidUntil] = useState(quotation?.validUntil ?? '')
  const [nextFollowUpDate, setNextFollowUpDate] = useState(quotation?.nextFollowUpDate ?? '')
  const [terms, setTerms] = useState(quotation?.currentVersion?.terms ?? 'Standard payment terms: 50% advance, balance on completion.')
  const [notes, setNotes] = useState(quotation?.currentVersion?.notes ?? '')

  // Overall discount & tax
  const [discountPercentage] = useState<number>(
    quotation?.currentVersion?.discountPercentage ?? 0
  )
  const [taxPercentage] = useState<number>(
    quotation?.currentVersion?.taxPercentage ?? 18
  )

  // Items
  const [items, setItems] = useState<ItemInput[]>(() => {
    if (quotation?.currentVersion?.items && quotation.currentVersion.items.length > 0) {
      return quotation.currentVersion.items.map((i) => ({
        serviceId: i.serviceId,
        serviceName: i.serviceName,
        description: i.description,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        discountPercentage: i.discountPercentage,
        taxPercentage: i.taxPercentage,
      }))
    }
    return [
      {
        serviceId: null,
        serviceName: '',
        description: '',
        quantity: 1,
        unitPrice: 0,
        discountPercentage: 0,
        taxPercentage: 18,
      },
    ]
  })

  // Dropdown sources
  const customersList = useQuery(customersQuery({}))
  const leadsList = useQuery(leadsQuery({}))
  const oppsList = useQuery(opportunitiesQuery({}))

  const createMut = useMutation({
    mutationFn: (payload: CreateQuotationPayload) => createQuotation(payload),
    onSuccess: (created) => {
      onSuccess(created)
      onOpenChange(false)
    },
  })

  const updateMut = useMutation({
    mutationFn: (payload: { id: string; data: CreateQuotationPayload }) =>
      updateDraftQuotation(payload.id, payload.data),
    onSuccess: (updated) => {
      onSuccess(updated)
      onOpenChange(false)
    },
  })

  const isPending = createMut.isPending || updateMut.isPending
  const error = createMut.error || updateMut.error

  if (!open) return null

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!title.trim() || items.length === 0) return

    const payload: CreateQuotationPayload = {
      title: title.trim(),
      customerId: customerId || null,
      customerName: customerName || null,
      leadId: leadId || null,
      leadName: leadName || null,
      opportunityId: opportunityId || null,
      opportunityTitle: opportunityTitle || null,
      validUntil: validUntil || null,
      nextFollowUpDate: nextFollowUpDate || null,
      terms: terms || null,
      notes: notes || null,
      discountPercentage,
      taxPercentage,
      items: items.map((i) => ({
        serviceId: i.serviceId || null,
        serviceName: i.serviceName.trim() || 'Service Item',
        description: i.description || null,
        quantity: Number(i.quantity) || 1,
        unitPrice: Number(i.unitPrice) || 0,
        discountPercentage: Number(i.discountPercentage) || 0,
        taxPercentage: Number(i.taxPercentage) || 0,
      })),
    }

    if (isEditing && quotation) {
      updateMut.mutate({ id: quotation.id, data: payload })
    } else {
      createMut.mutate(payload)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-h-[90vh] w-full max-w-3xl overflow-y-auto p-6 shadow-xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="flex items-center justify-between pb-3 border-b">
            <div>
              <h2 className="text-lg font-semibold">{isEditing ? `Edit Quotation ${quotation?.quotationNumber}` : 'New Quotation'}</h2>
              <p className="text-xs text-muted-foreground">
                {isEditing
                  ? 'Modify draft quotation items, terms, and customer details.'
                  : 'Create a formal price proposal with line items, tax, and terms.'}
              </p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={() => onOpenChange(false)}
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>

          {error && (
            <div className="rounded-md bg-destructive/10 p-3 text-xs text-destructive">
              {(error as Error).message || 'Failed to save quotation'}
            </div>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="q-title">Quotation Subject / Title *</Label>
              <Input
                id="q-title"
                required
                placeholder="e.g. CCTV Surveillance and Access Control Setup"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>

            <div>
              <Label htmlFor="q-validity">Validity Date</Label>
              <Input
                id="q-validity"
                type="date"
                value={validUntil}
                onChange={(e) => setValidUntil(e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div>
              <Label htmlFor="q-cust">Customer</Label>
              <Select
                id="q-cust"
                value={customerId}
                onChange={(e) => {
                  const val = e.target.value
                  setCustomerId(val)
                  const c = customersList.data?.items.find((item) => item.id === val)
                  setCustomerName(c ? c.displayName : '')
                }}
              >
                <option value="">None / Unlinked</option>
                {customersList.data?.items.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.displayName}
                  </option>
                ))}
              </Select>
            </div>

            <div>
              <Label htmlFor="q-lead">Lead</Label>
              <Select
                id="q-lead"
                value={leadId}
                onChange={(e) => {
                  const val = e.target.value
                  setLeadId(val)
                  const l = leadsList.data?.items.find((item) => item.id === val)
                  setLeadName(l ? l.contactName : '')
                }}
              >
                <option value="">None / Unlinked</option>
                {leadsList.data?.items.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.contactName}
                  </option>
                ))}
              </Select>
            </div>

            <div>
              <Label htmlFor="q-opp">Opportunity</Label>
              <Select
                id="q-opp"
                value={opportunityId}
                onChange={(e) => {
                  const val = e.target.value
                  setOpportunityId(val)
                  const o = oppsList.data?.items.find((item) => item.id === val)
                  setOpportunityTitle(o ? o.title : '')
                }}
              >
                <option value="">None / Unlinked</option>
                {oppsList.data?.items.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.title}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {/* Line items editor */}
          <div className="rounded-lg border p-3 bg-muted/10">
            <QuotationItemsEditor items={items} onChange={setItems} />
          </div>

          {/* Terms, Notes & Next Follow-up */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="q-terms">Commercial Terms & Conditions</Label>
              <Textarea
                id="q-terms"
                placeholder="Payment terms, delivery schedule, warranty..."
                value={terms}
                onChange={(e) => setTerms(e.target.value)}
                rows={3}
              />
            </div>

            <div>
              <Label htmlFor="q-notes">Internal Notes</Label>
              <Textarea
                id="q-notes"
                placeholder="Internal pricing rationale or instructions..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
              />
            </div>
          </div>

          <div>
            <Label htmlFor="q-follow-up">Scheduled Follow-up Date</Label>
            <Input
              id="q-follow-up"
              type="date"
              value={nextFollowUpDate}
              onChange={(e) => setNextFollowUpDate(e.target.value)}
              className="w-48"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t">
            <Button
              type="button"
              variant="outline"
              disabled={isPending}
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isPending || !title.trim() || items.length === 0}>
              {isPending ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Quotation'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
