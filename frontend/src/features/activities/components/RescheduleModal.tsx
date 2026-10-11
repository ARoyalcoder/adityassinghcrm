import { useState } from 'react'
import { Calendar, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import type { ActivityDetail } from '@/features/activities/api'

type RescheduleModalProps = {
  activity: ActivityDetail
  onClose: () => void
  onReschedule: (dueDate: string, reminderAt?: string | null) => Promise<void>
}

export function RescheduleModal({ activity, onClose, onReschedule }: RescheduleModalProps) {
  const [dueDate, setDueDate] = useState(activity.dueDate ? activity.dueDate.slice(0, 16) : '')
  const [reminderAt, setReminderAt] = useState(activity.reminderAt ? activity.reminderAt.slice(0, 16) : '')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function quickAddDays(days: number) {
    const d = new Date()
    d.setDate(d.getDate() + days)
    d.setHours(10, 0, 0, 0)
    setDueDate(d.toISOString().slice(0, 16))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!dueDate) {
      setError('Please choose a due date')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await onReschedule(
        new Date(dueDate).toISOString(),
        reminderAt ? new Date(reminderAt).toISOString() : null
      )
      onClose()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to reschedule')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="w-full max-w-md p-6 shadow-xl">
        <div className="flex items-center justify-between pb-3 border-b">
          <div className="flex items-center gap-2">
            <Calendar className="h-5 w-5 text-primary" />
            <h2 className="text-base font-semibold">Reschedule Due Date</h2>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close">
            <X className="h-4 w-4" />
          </Button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 pt-4">
          <div>
            <p className="text-sm font-medium">{activity.title}</p>
            {activity.dueDate ? (
              <p className="text-xs text-muted-foreground">
                Current due date: {new Date(activity.dueDate).toLocaleString()}
              </p>
            ) : null}
          </div>

          <div className="flex gap-2 text-xs">
            <Button type="button" variant="outline" size="sm" onClick={() => quickAddDays(1)}>
              Tomorrow
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={() => quickAddDays(3)}>
              In 3 Days
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={() => quickAddDays(7)}>
              Next Week
            </Button>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">New Due Date & Time *</label>
            <Input
              type="datetime-local"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              required
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Updated Reminder (Optional)</label>
            <Input
              type="datetime-local"
              value={reminderAt}
              onChange={(e) => setReminderAt(e.target.value)}
            />
          </div>

          {error ? <p className="text-xs text-destructive">{error}</p> : null}

          <div className="flex justify-end gap-2 pt-2 border-t">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={submitting}>
              {submitting ? 'Saving…' : 'Save Changes'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
