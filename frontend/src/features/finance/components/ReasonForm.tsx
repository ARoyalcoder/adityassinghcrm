import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type ReasonFormProps = {
  title: string
  /** What will happen, in plain words, before the person commits to it. */
  explanation: string
  submitLabel: string
  busy: boolean
  onSubmit: (reason: string) => void
}

/**
 * For the actions that take something back off the books (cancel, release, reverse). Each one must
 * say why, and the reason is kept on the record for whoever reads it later.
 */
export function ReasonForm({ title, explanation, submitLabel, busy, onSubmit }: ReasonFormProps) {
  const field = useId()
  const [reason, setReason] = useState('')
  const [problem, setProblem] = useState<string | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (reason.trim() === '') {
      setProblem('Say why: the reason is kept on record.')
      return
    }
    setProblem(null)
    onSubmit(reason.trim())
  }

  return (
    <form onSubmit={submit} className="grid gap-3 rounded-xl border p-3" aria-label={title}>
      <p className="text-sm">{explanation}</p>
      <div className="grid gap-1.5">
        <Label htmlFor={field}>Reason</Label>
        <Input id={field} maxLength={400} value={reason} onChange={(event) => setReason(event.target.value)} />
      </div>
      {problem ? (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      ) : null}
      <Button type="submit" variant="destructive" className="w-fit" disabled={busy}>
        {busy ? 'Working…' : submitLabel}
      </Button>
    </form>
  )
}
