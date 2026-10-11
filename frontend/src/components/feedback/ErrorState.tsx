import { CircleAlert, RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api/errors'

type ErrorStateProps = {
  /** What failed, in the user's terms: "Could not load business verticals". */
  title: string
  error: unknown
  onRetry?: () => void
}

/** Standard way to show a failed query: what happened, how to retry, and the id to quote to support. */
export function ErrorState({ title, error, onRetry }: ErrorStateProps) {
  const message = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  const requestId = error instanceof ApiError ? error.requestId : undefined

  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
      <div className="flex items-start gap-3">
        <CircleAlert className="mt-0.5 size-4 shrink-0 text-destructive" aria-hidden="true" />
        <div className="grid gap-1">
          <p className="text-sm font-medium">{title}</p>
          <p className="text-sm text-muted-foreground">{message}</p>
          {requestId ? (
            <p className="text-xs text-muted-foreground">
              Reference: <code className="font-mono">{requestId}</code>
            </p>
          ) : null}
        </div>
      </div>
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RotateCw aria-hidden="true" />
          Try again
        </Button>
      ) : null}
    </div>
  )
}
