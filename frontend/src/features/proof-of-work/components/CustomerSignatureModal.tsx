import { useState } from 'react'
import { X, CheckCircle2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { SignaturePad } from '@/features/field-jobs/components/JobParts'
import type { CustomerConfirmationInput } from '../api'

interface CustomerSignatureModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (input: CustomerConfirmationInput) => Promise<void>
  isSubmitting?: boolean
}

export function CustomerSignatureModal({
  isOpen,
  onClose,
  onSubmit,
  isSubmitting = false,
}: CustomerSignatureModalProps) {
  const [customerName, setCustomerName] = useState('')
  const [customerRemarks, setCustomerRemarks] = useState('')
  const [signatureData, setSignatureData] = useState<string | null>(null)

  if (!isOpen) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!customerName.trim() || !signatureData) return

    await onSubmit({
      customerName: customerName.trim(),
      customerRemarks: customerRemarks.trim() || undefined,
      customerSignature: signatureData,
    })

    setCustomerName('')
    setCustomerRemarks('')
    setSignatureData(null)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
      <div className="bg-card w-full max-w-md rounded-xl border p-6 shadow-xl space-y-4">
        <div className="flex items-center justify-between pb-3 border-b">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-emerald-600" />
            Customer Sign-off
          </h2>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={isSubmitting}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <p className="text-xs text-muted-foreground">
            Customer sign-off confirms on-site work and checklist completion.
          </p>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Customer Full Name *</label>
            <Input
              required
              placeholder="e.g. Ramesh Sharma"
              value={customerName}
              onChange={(e) => setCustomerName(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Customer Remarks / Feedback</label>
            <Textarea
              rows={2}
              placeholder="Satisfied with installation, cleaned site after work..."
              value={customerRemarks}
              onChange={(e) => setCustomerRemarks(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Draw Customer Signature *</label>
            <SignaturePad onChange={(data) => setSignatureData(data)} />
            {!signatureData && (
              <p className="text-[11px] text-amber-600">Please provide signature in the box above.</p>
            )}
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t">
            <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isSubmitting || !customerName.trim() || !signatureData}
            >
              {isSubmitting ? 'Recording...' : 'Confirm & Sign'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
