import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  createQuotationVersion,
  type CreateVersionPayload,
  type ItemInput,
  type QuotationDetail,
} from '../api'
import { QuotationItemsEditor } from './QuotationItemsEditor'

interface CreateVersionModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  quotation: QuotationDetail
  onSuccess: (updated: QuotationDetail) => void
}

export function CreateVersionModal({
  open,
  onOpenChange,
  quotation,
  onSuccess,
}: CreateVersionModalProps) {
  const currentVer = quotation.currentVersion

  const [changeSummary, setChangeSummary] = useState('')
  const [validUntil, setValidUntil] = useState(currentVer?.validUntil ?? '')
  const [terms, setTerms] = useState(currentVer?.terms ?? '')
  const [notes, setNotes] = useState(currentVer?.notes ?? '')
  const [nextFollowUpDate] = useState(quotation.nextFollowUpDate ?? '')
  const [discountPercentage] = useState<number>(currentVer?.discountPercentage ?? 0)
  const [taxPercentage] = useState<number>(currentVer?.taxPercentage ?? 18)

  const [items, setItems] = useState<ItemInput[]>(() => {
    if (currentVer?.items && currentVer.items.length > 0) {
      return currentVer.items.map((i) => ({
        serviceId: i.serviceId,
        serviceName: i.serviceName,
        description: i.description,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        discountPercentage: i.discountPercentage,
        taxPercentage: i.taxPercentage,
      }))
    }
    return []
  })

  const versionMut = useMutation({
    mutationFn: (payload: CreateVersionPayload) => createQuotationVersion(quotation.id, payload),
    onSuccess: (updated) => {
      onSuccess(updated)
      onOpenChange(false)
    },
  })

  if (!open) return null

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (items.length === 0) return

    versionMut.mutate({
      changeSummary: changeSummary.trim() || `Revision ${quotation.currentVersionNumber + 1}`,
      validUntil: validUntil || null,
      terms: terms || null,
      notes: notes || null,
      nextFollowUpDate: nextFollowUpDate || null,
      discountPercentage,
      taxPercentage,
      items,
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-h-[90vh] w-full max-w-3xl overflow-y-auto p-6 shadow-xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="flex items-center justify-between pb-3 border-b">
            <div>
              <h2 className="text-lg font-semibold">
                Create New Version (v{quotation.currentVersionNumber + 1}) for {quotation.quotationNumber}
              </h2>
              <p className="text-xs text-muted-foreground">
                A new revision preserves previous version records completely and resets the quotation to draft.
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

          {versionMut.error && (
            <div className="rounded-md bg-destructive/10 p-3 text-xs text-destructive">
              {(versionMut.error as Error).message || 'Failed to create revision'}
            </div>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="v-summary">Reason for Revision / Change Summary *</Label>
              <Input
                id="v-summary"
                required
                placeholder="e.g. Revised discounts and swapped cable grade"
                value={changeSummary}
                onChange={(e) => setChangeSummary(e.target.value)}
              />
            </div>

            <div>
              <Label htmlFor="v-validity">Validity Date</Label>
              <Input
                id="v-validity"
                type="date"
                value={validUntil}
                onChange={(e) => setValidUntil(e.target.value)}
              />
            </div>
          </div>

          <div className="rounded-lg border p-3 bg-muted/10">
            <QuotationItemsEditor items={items} onChange={setItems} />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="v-terms">Updated Terms & Conditions</Label>
              <Textarea
                id="v-terms"
                value={terms}
                onChange={(e) => setTerms(e.target.value)}
                rows={3}
              />
            </div>

            <div>
              <Label htmlFor="v-notes">Revision Notes</Label>
              <Textarea
                id="v-notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t">
            <Button
              type="button"
              variant="outline"
              disabled={versionMut.isPending}
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={versionMut.isPending || !changeSummary.trim() || items.length === 0}
            >
              {versionMut.isPending ? 'Creating Version...' : `Publish Version ${quotation.currentVersionNumber + 1}`}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
