import { useState } from 'react'
import { CheckCircle2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import type { ActivityDetail } from '@/features/activities/api'

type CompleteTaskModalProps = {
  activity: ActivityDetail
  onClose: () => void
  onComplete: (outcome: string) => Promise<void>
}

export function CompleteTaskModal({ activity, onClose, onComplete }: CompleteTaskModalProps) {
  const [outcome, setOutcome] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await onComplete(outcome)
      onClose()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to complete task')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="w-full max-w-md p-6 shadow-xl">
        <div className="flex items-center justify-between pb-3 border-b">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-emerald-600" />
            <h2 className="text-base font-semibold">Complete Activity / Task</h2>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close">
            <X className="h-4 w-4" />
          </Button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 pt-4">
          <div>
            <p className="text-sm font-medium">{activity.title}</p>
            {activity.entityName ? (
              <p className="text-xs text-muted-foreground">Related to: {activity.entityName}</p>
            ) : null}
            {activity.isRecurring ? (
              <p className="text-xs text-emerald-600 font-medium mt-1">
                ✓ Recurring task: Completing this will automatically schedule the next occurrence.
              </p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Outcome / Completion Notes (Optional)</label>
            <Textarea
              placeholder="e.g. Call was successful, customer requested formal quote by Monday..."
              rows={3}
              value={outcome}
              onChange={(e) => setOutcome(e.target.value)}
            />
          </div>

          {error ? <p className="text-xs text-destructive">{error}</p> : null}

          <div className="flex justify-end gap-2 pt-2 border-t">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={submitting}>
              {submitting ? 'Completing…' : 'Mark as Completed'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
