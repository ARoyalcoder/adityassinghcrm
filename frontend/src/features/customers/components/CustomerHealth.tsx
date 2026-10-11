import { CircleAlert, CircleCheck, CircleHelp, TriangleAlert, type LucideIcon } from 'lucide-react'
import type { CustomerOverview, HealthSignal } from '@/features/customers/api'
import { cn } from '@/lib/utils'

/** Must match the backend enum CustomerOverviewService.HealthLevel. */
const LEVELS: Record<string, { label: string; icon: LucideIcon; tone: string }> = {
  GOOD: { label: 'Healthy', icon: CircleCheck, tone: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
  NEEDS_ATTENTION: { label: 'Needs attention', icon: TriangleAlert, tone: 'border-amber-200 bg-amber-50 text-amber-900' },
  AT_RISK: { label: 'At risk', icon: CircleAlert, tone: 'border-red-200 bg-red-50 text-red-800' },
  UNKNOWN: { label: 'Not enough history', icon: CircleHelp, tone: 'bg-muted text-muted-foreground' },
}

const IMPACTS: Record<string, { icon: LucideIcon; tone: string; label: string }> = {
  POSITIVE: { icon: CircleCheck, tone: 'text-emerald-700', label: 'Good sign' },
  WARNING: { icon: TriangleAlert, tone: 'text-amber-700', label: 'Needs attention' },
  CRITICAL: { icon: CircleAlert, tone: 'text-red-700', label: 'At risk' },
}

/** The customer's health in a word, with an icon and colour that agree with it. */
export function CustomerHealthBadge({ level }: { level: string }) {
  const look = LEVELS[level] ?? LEVELS.UNKNOWN
  const Icon = look.icon
  return (
    <span
      className={cn('inline-flex w-fit items-center gap-1.5 rounded-md border px-2 py-0.5 text-xs font-medium', look.tone)}
    >
      <Icon className="size-3.5" aria-hidden="true" />
      <span className="sr-only">Health: </span>
      {look.label}
    </span>
  )
}

/** Why the health is what it is: every signal the server found, most serious first. */
export function CustomerHealthReasons({ health }: { health: CustomerOverview['health'] }) {
  if (health.signals.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        There is not enough on record yet to judge how this relationship is going. Log calls, meetings and visits
        as they happen and this will fill in.
      </p>
    )
  }
  return (
    <ul className="grid gap-2" aria-label="Health reasons">
      {health.signals.map((signal) => (
        <Reason key={signal.code} signal={signal} />
      ))}
    </ul>
  )
}

function Reason({ signal }: { signal: HealthSignal }) {
  const look = IMPACTS[signal.impact] ?? IMPACTS.WARNING
  const Icon = look.icon
  return (
    <li className="flex items-start gap-2 text-sm">
      <Icon className={cn('mt-0.5 size-4 shrink-0', look.tone)} aria-hidden="true" />
      <span>
        <span className="sr-only">{look.label}: </span>
        {signal.message}
      </span>
    </li>
  )
}
