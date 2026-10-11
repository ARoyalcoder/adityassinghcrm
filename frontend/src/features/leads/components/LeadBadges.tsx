import { LEAD_HEALTHS, LEAD_PRIORITIES, LEAD_STATUSES, QUALIFICATION_STATUSES, labelOf } from '@/features/leads/api'
import { cn } from '@/lib/utils'

const BASE = 'inline-flex w-fit items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap'

const GOOD = 'border-emerald-200 bg-emerald-50 text-emerald-800'
const WARN = 'border-amber-200 bg-amber-50 text-amber-900'
const BAD = 'border-red-200 bg-red-50 text-red-800'
const NEUTRAL = 'bg-muted text-muted-foreground'

const TONES: Record<string, string> = {
  // priority
  HIGH: BAD,
  MEDIUM: WARN,
  LOW: NEUTRAL,
  // health
  GOOD,
  NEEDS_ATTENTION: WARN,
  AT_RISK: BAD,
  // qualification status
  NOT_STARTED: NEUTRAL,
  IN_PROGRESS: WARN,
  QUALIFIED: GOOD,
  DISQUALIFIED: BAD,
  // lead status
  OPEN: NEUTRAL,
  WON: GOOD,
  LOST: BAD,
}

/** Every badge says what it is in words (for screen readers and at a glance), never by colour alone. */
function Labelled({ kind, value, text }: { kind: string; value: string; text: string }) {
  return (
    <span className={cn(BASE, TONES[value] ?? NEUTRAL)}>
      <span className="sr-only">{kind}: </span>
      {text}
    </span>
  )
}

export function PriorityBadge({ priority }: { priority: string }) {
  return <Labelled kind="Priority" value={priority} text={`${labelOf(LEAD_PRIORITIES, priority)} priority`} />
}

export function HealthBadge({ health }: { health: string }) {
  return <Labelled kind="Health" value={health} text={labelOf(LEAD_HEALTHS, health)} />
}

export function QualificationBadge({ status }: { status: string }) {
  return <Labelled kind="Qualification" value={status} text={labelOf(QUALIFICATION_STATUSES, status)} />
}

export function LeadStatusBadge({ status }: { status: string }) {
  return <Labelled kind="Status" value={status} text={labelOf(LEAD_STATUSES, status)} />
}

/** The score as a number out of 100. */
export function ScoreBadge({ score }: { score: number }) {
  return (
    <span className={cn(BASE, 'font-mono tabular-nums')}>
      <span className="sr-only">Score: </span>
      {score}
      <span className="text-muted-foreground">/100</span>
    </span>
  )
}
