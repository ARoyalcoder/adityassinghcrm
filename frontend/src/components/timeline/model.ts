import { z } from 'zod'

/**
 * The shape of a timeline as the backend sends it (platform.timeline.TimelinePage). It is the same
 * for every timeline in the system, whichever record it belongs to and whichever modules feed it.
 */
export const timelineEventSchema = z.object({
  /** Stable and unique across sources, e.g. "interaction:<uuid>". */
  id: z.string(),
  /** What happened, e.g. "QUOTATION_SENT". Unknown types are still shown, using their category's look. */
  type: z.string(),
  category: z.string(),
  occurredAt: z.string(),
  title: z.string(),
  description: z.string().nullish(),
  actor: z.string().nullish(),
  /** The record the event is about, when there is one to open. */
  reference: z.object({ entity: z.string(), id: z.string() }).nullish(),
  /** Extra facts to show, already worded for people: { "Outcome": "Wants a site visit" }. */
  details: z.record(z.string(), z.string()).nullish(),
})
export type TimelineEvent = z.infer<typeof timelineEventSchema>

export const timelinePageSchema = z.object({
  events: z.array(timelineEventSchema),
  /** Pass back to get the next (older) page; absent on the last page. */
  nextCursor: z.string().nullish(),
})
export type TimelinePage = z.infer<typeof timelinePageSchema>

/** Must match the backend enum platform.timeline.TimelineCategory. */
export const TIMELINE_CATEGORIES = [
  { value: 'COMMUNICATION', label: 'Communication' },
  { value: 'SALES', label: 'Sales' },
  { value: 'PROJECT', label: 'Projects' },
  { value: 'FINANCE', label: 'Finance' },
  { value: 'SERVICE', label: 'Service' },
  { value: 'AMC', label: 'AMC' },
  { value: 'RECORD', label: 'Record changes' },
] as const

/** Builds "?limit=25&category=SALES&category=FINANCE&cursor=..." for a timeline endpoint. */
export function timelineQueryString(params: { cursor?: string | null; limit?: number; categories?: string[] }) {
  const search = new URLSearchParams()
  if (params.limit) {
    search.set('limit', String(params.limit))
  }
  for (const category of params.categories ?? []) {
    search.append('category', category)
  }
  if (params.cursor) {
    search.set('cursor', params.cursor)
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}
