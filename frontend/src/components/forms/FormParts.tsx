import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

/** The white card an admin form sits in. */
export function FormCard({ children }: { children: ReactNode }) {
  return (
    <Card className="max-w-2xl">
      <CardContent>{children}</CardContent>
    </Card>
  )
}

/** A problem that is not about one field: a conflict, a broken rule, a lost connection. */
export function FormError({ message }: { message: string | null }) {
  if (!message) {
    return null
  }
  return (
    <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm">
      {message}
    </p>
  )
}

type FormActionsProps = {
  submitLabel: string
  submitting: boolean
  cancelTo: string
}

export function FormActions({ submitLabel, submitting, cancelTo }: FormActionsProps) {
  return (
    <div className="flex gap-2 pt-2">
      <Button type="submit" disabled={submitting}>
        {submitting ? 'Saving…' : submitLabel}
      </Button>
      <Button asChild variant="outline">
        <Link to={cancelTo}>Cancel</Link>
      </Button>
    </div>
  )
}

type LoadingRecordProps = {
  /** What is being loaded, e.g. "service". */
  noun: string
  isPending: boolean
  error: unknown
  onRetry: () => void
}

/** Shown by an edit page while its record loads, or if it cannot be loaded. Renders nothing once loaded. */
export function RecordLoadState({ noun, isPending, error, onRetry }: LoadingRecordProps) {
  if (isPending) {
    return (
      <FormCard>
        <div className="grid gap-3" aria-busy="true" aria-label={`Loading ${noun}`}>
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-2/3" />
        </div>
      </FormCard>
    )
  }
  if (error) {
    return <ErrorState title={`Could not load this ${noun}`} error={error} onRetry={onRetry} />
  }
  return null
}
