import { eventLook } from '@/components/timeline/event-types'
import { TIMELINE_CATEGORIES, type TimelineEvent } from '@/components/timeline/model'
import { cn } from '@/lib/utils'

const dayFormat = new Intl.DateTimeFormat('en-IN', { dateStyle: 'full' })
const timeFormat = new Intl.DateTimeFormat('en-IN', { timeStyle: 'short' })

function categoryLabel(category: string) {
  return TIMELINE_CATEGORIES.find((option) => option.value === category)?.label ?? category
}

/** Consecutive events of the same calendar day (in the viewer's time zone) share one heading. */
function groupByDay(events: TimelineEvent[]) {
  const days: { day: string; events: TimelineEvent[] }[] = []
  for (const event of events) {
    const day = dayFormat.format(new Date(event.occurredAt))
    const last = days.at(-1)
    if (last && last.day === day) {
      last.events.push(event)
    } else {
      days.push({ day, events: [event] })
    }
  }
  return days
}

type TimelineProps = {
  /** Newest first, as the backend returns them. */
  events: TimelineEvent[]
  /** Names the list for screen readers, e.g. "Customer timeline". */
  label: string
  /** Leave out the day headings and details: for a short "recent" list. */
  compact?: boolean
}

/**
 * Draws timeline events. It knows nothing about where they came from or what record they belong
 * to, so the same component serves a customer's timeline today and a project's or a ticket's later.
 */
export function Timeline({ events, label, compact = false }: TimelineProps) {
  if (compact) {
    return (
      <ol className="grid gap-4" aria-label={label}>
        {events.map((event) => (
          <TimelineItem key={event.id} event={event} compact />
        ))}
      </ol>
    )
  }
  return (
    <div className="grid gap-6" role="group" aria-label={label}>
      {groupByDay(events).map((group) => (
        <section key={group.day} className="grid gap-3" aria-label={group.day}>
          <h3 className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{group.day}</h3>
          <ol className="grid gap-4">
            {group.events.map((event) => (
              <TimelineItem key={event.id} event={event} />
            ))}
          </ol>
        </section>
      ))}
    </div>
  )
}

function TimelineItem({ event, compact = false }: { event: TimelineEvent; compact?: boolean }) {
  const { icon: Icon, tone } = eventLook(event.type, event.category)
  const when = new Date(event.occurredAt)
  const details = Object.entries(event.details ?? {})

  return (
    <li className="flex gap-3 text-sm" data-event-type={event.type}>
      <span className={cn('flex size-8 shrink-0 items-center justify-center rounded-full', tone)}>
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <div className="grid min-w-0 gap-1">
        <p className="flex flex-wrap items-baseline gap-x-2">
          <span className="font-medium">{event.title}</span>
          <span className="text-xs text-muted-foreground">{categoryLabel(event.category)}</span>
        </p>
        {event.description ? (
          <p className={cn('whitespace-pre-wrap', compact && 'line-clamp-2')}>{event.description}</p>
        ) : null}
        {!compact && details.length > 0 ? (
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-xs">
            {details.map(([name, value]) => (
              <div key={name} className="contents">
                <dt className="text-muted-foreground">{name}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        <p className="text-xs text-muted-foreground">
          {event.actor ? `${event.actor} · ` : ''}
          <time dateTime={event.occurredAt}>
            {compact ? `${dayFormat.format(when)}, ${timeFormat.format(when)}` : timeFormat.format(when)}
          </time>
        </p>
      </div>
    </li>
  )
}
