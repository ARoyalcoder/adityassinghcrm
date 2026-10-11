import { useState } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { PipelineStage } from '../api'

interface ChangeStageModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  targetStage: PipelineStage | null
  opportunityTitle: string
  onConfirm: (notes: string, lossReason?: string) => void
  isPending?: boolean
}

export function ChangeStageModal({
  open,
  onOpenChange,
  targetStage,
  opportunityTitle,
  onConfirm,
  isPending = false,
}: ChangeStageModalProps) {
  const [notes, setNotes] = useState('')
  const [lossReason, setLossReason] = useState('')

  if (!open || !targetStage) return null

  const isLost = targetStage.isLost

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    onConfirm(notes, isLost ? lossReason : undefined)
    setNotes('')
    setLossReason('')
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-h-[90vh] w-full max-w-md overflow-y-auto p-6 shadow-xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="flex items-center justify-between pb-3 border-b">
            <div>
              <h2 className="text-lg font-semibold">Move to &quot;{targetStage.name}&quot;</h2>
              <p className="text-xs text-muted-foreground">
                Transitioning <span className="font-medium text-foreground">{opportunityTitle}</span> to{' '}
                <span className="font-medium text-foreground">{targetStage.name}</span>.
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

          {isLost && (
            <div className="space-y-1.5">
              <Label htmlFor="loss-reason">Loss Reason *</Label>
              <Textarea
                id="loss-reason"
                required
                placeholder="Why was this opportunity lost? (e.g. Budget constraints, Competitor pricing)"
                value={lossReason}
                onChange={(e) => setLossReason(e.target.value)}
                rows={3}
              />
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="stage-notes">Transition Notes (Optional)</Label>
            <Textarea
              id="stage-notes"
              placeholder="Add details about this stage transition..."
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
              disabled={isPending || (isLost && !lossReason.trim())}
              variant={isLost ? 'destructive' : 'default'}
            >
              {isPending ? 'Updating...' : `Confirm Stage: ${targetStage.name}`}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
