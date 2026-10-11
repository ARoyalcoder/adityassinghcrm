import type { ReactNode } from 'react'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/features/audit/api'
import type { Qualification } from '@/features/leads/api'
import { HealthBadge, PriorityBadge, QualificationBadge } from '@/features/leads/components/LeadBadges'
import { cn } from '@/lib/utils'

/** What each scorer is, in words. A scorer this screen has not heard of is shown by its name. */
const ENGINES: Record<string, string> = {
  'rules-v1': 'Worked out by fixed rules, not by a model: the same facts always give the same score.',
}

type ScorePanelProps = {
  qualification: Qualification | undefined
  isPending: boolean
  error: unknown
  onRetry: () => void
}

/**
 * "Why this score?": the score, every reason that added or removed points, and the three
 * conclusions drawn from it with the reason for each. It shows what the server says and adds
 * nothing of its own, so it stays correct whichever scorer produced the score.
 */
export function ScorePanel({ qualification, isPending, error, onRetry }: ScorePanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Why this score?</CardTitle>
        <CardDescription>Every reason that adds to or takes from this lead's score.</CardDescription>
      </CardHeader>
      <CardContent>
        {qualification ? (
          <Explanation qualification={qualification} />
        ) : isPending ? (
          <Skeleton className="h-40 w-full" aria-busy="true" aria-label="Loading the score" />
        ) : (
          <ErrorState title="Could not load the score" error={error} onRetry={onRetry} />
        )}
      </CardContent>
    </Card>
  )
}

function signed(points: number) {
  return points > 0 ? `+${points}` : `−${Math.abs(points)}`
}

function Explanation({ qualification }: { qualification: Qualification }) {
  const { score, total, factors } = qualification
  return (
    <div className="grid gap-5">
      <p className="flex items-baseline gap-2">
        <span className="text-4xl font-semibold tabular-nums">{score}</span>
        <span className="text-sm text-muted-foreground">out of 100</span>
      </p>

      {factors.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Nothing adds to or takes from this lead's score yet. Estimate its value, answer the qualification
          questions and log what you do, and the reasons will appear here.
        </p>
      ) : (
        <div className="grid gap-2">
          <ul className="grid gap-1.5" aria-label="Score factors">
            {factors.map((factor, index) => (
              // A scorer may give two reasons of the same kind, so the code alone is not a safe key.
              <li key={`${factor.code}:${index}`} className="flex items-baseline gap-3 text-sm">
                <span
                  className={cn(
                    'w-10 shrink-0 text-right font-mono font-medium tabular-nums',
                    factor.points > 0 ? 'text-emerald-700' : 'text-red-700',
                  )}
                >
                  {signed(factor.points)}
                </span>
                <span>{factor.label}</span>
              </li>
            ))}
          </ul>
          <p className="flex items-baseline gap-3 border-t pt-2 text-sm font-medium">
            <span className="w-10 shrink-0 text-right font-mono tabular-nums">{total}</span>
            <span>Total</span>
          </p>
          {total !== score ? (
            <p className="text-xs text-muted-foreground">
              The reasons add up to {total}. A score is kept between 0 and 100, so it is shown as {score}.
            </p>
          ) : null}
        </div>
      )}

      <dl className="grid gap-3 border-t pt-4 text-sm">
        <Conclusion term="Priority" reason={qualification.priorityReason}>
          <PriorityBadge priority={qualification.priority} />
        </Conclusion>
        <Conclusion term="Health" reason={qualification.healthReason}>
          <HealthBadge health={qualification.health} />
        </Conclusion>
        <Conclusion term="Qualification" reason={qualification.qualificationReason}>
          <QualificationBadge status={qualification.qualificationStatus} />
        </Conclusion>
      </dl>

      <p className="text-xs text-muted-foreground">
        {ENGINES[qualification.engine] ?? `Scored by ${qualification.engine}.`} As of{' '}
        {formatDateTime(qualification.computedAt)}.
      </p>
    </div>
  )
}

function Conclusion({ term, reason, children }: { term: string; reason: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 sm:grid-cols-[7rem_auto_1fr] sm:items-baseline sm:gap-3">
      <dt className="text-muted-foreground">{term}</dt>
      <dd className="contents">
        {children}
        <span className="text-muted-foreground">{reason}</span>
      </dd>
    </div>
  )
}
