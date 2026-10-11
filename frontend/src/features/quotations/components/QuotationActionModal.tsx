import { useState } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

export type QuotationActionType =
  | 'request_approval'
  | 'approve'
  | 'reject_approval'
  | 'send'
  | 'accept'
  | 'reject'
  | 'cancel'

interface QuotationActionModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  actionType: QuotationActionType
  quotationNumber: string
  onConfirm: (data: { notes?: string; reason?: string; nextFollowUpDate?: string }) => void
  isPending?: boolean
}

export function QuotationActionModal({
  open,
  onOpenChange,
  actionType,
  quotationNumber,
  onConfirm,
  isPending = false,
}: QuotationActionModalProps) {
  const [notes, setNotes] = useState('')
  const [reason, setReason] = useState('')
  const [followUpDate, setFollowUpDate] = useState('')

  if (!open) return null

  let title = ''
  let description = ''
  let requiresReason = false
  let showFollowUp = false
  let confirmLabel = 'Confirm'
  let confirmVariant: 'default' | 'destructive' = 'default'

  switch (actionType) {
    case 'request_approval':
      title = `Submit ${quotationNumber} for Approval`
      description = 'Notify managers and finance that this quotation is ready for review.'
      confirmLabel = 'Submit for Approval'
      break
    case 'approve':
      title = `Approve ${quotationNumber}`
      description = 'Authorize this quotation so sales can dispatch it to the client.'
      confirmLabel = 'Approve Quotation'
      break
    case 'reject_approval':
      title = `Reject Approval for ${quotationNumber}`
      description = 'Send this quotation back to draft with requested corrections.'
      requiresReason = true
      confirmLabel = 'Reject & Return to Draft'
      confirmVariant = 'destructive'
      break
    case 'send':
      title = `Send ${quotationNumber} to Client`
      description = 'Mark quotation as officially dispatched and set a follow-up date.'
      showFollowUp = true
      confirmLabel = 'Mark as Sent'
      break
    case 'accept':
      title = `Client Accepted ${quotationNumber}`
      description = 'Record client acceptance to mark this quotation as won.'
      confirmLabel = 'Mark as Accepted'
      break
    case 'reject':
      title = `Client Rejected ${quotationNumber}`
      description = 'Record the reason why the client declined this proposal.'
      requiresReason = true
      confirmLabel = 'Mark as Rejected'
      confirmVariant = 'destructive'
      break
    case 'cancel':
      title = `Cancel ${quotationNumber}`
      description = 'Cancel this quotation permanently.'
      requiresReason = true
      confirmLabel = 'Cancel Quotation'
      confirmVariant = 'destructive'
      break
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    onConfirm({
      notes: notes.trim() || undefined,
      reason: reason.trim() || undefined,
      nextFollowUpDate: followUpDate || undefined,
    })
    setNotes('')
    setReason('')
    setFollowUpDate('')
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-h-[90vh] w-full max-w-md overflow-y-auto p-6 shadow-xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="flex items-center justify-between pb-3 border-b">
            <div>
              <h2 className="text-lg font-semibold">{title}</h2>
              <p className="text-xs text-muted-foreground">{description}</p>
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

          {showFollowUp && (
            <div className="space-y-1.5">
              <Label htmlFor="action-follow-up">Follow-up Date</Label>
              <Input
                id="action-follow-up"
                type="date"
                value={followUpDate}
                onChange={(e) => setFollowUpDate(e.target.value)}
              />
            </div>
          )}

          {requiresReason && (
            <div className="space-y-1.5">
              <Label htmlFor="action-reason">Reason *</Label>
              <Textarea
                id="action-reason"
                required
                placeholder="Explain the reason for this action..."
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
              />
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="action-notes">Comments / Notes</Label>
            <Textarea
              id="action-notes"
              placeholder="Additional comments (optional)..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
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
            <Button
              type="submit"
              variant={confirmVariant}
              disabled={isPending || (requiresReason && !reason.trim())}
            >
              {isPending ? 'Processing...' : confirmLabel}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
