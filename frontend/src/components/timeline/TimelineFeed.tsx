import { useState } from 'react'
import { useInfiniteQuery, type QueryKey } from '@tanstack/react-query'
import { ErrorState } from '@/components/feedback/ErrorState'
import { TIMELINE_CATEGORIES, type TimelinePage } from '@/components/timeline/model'
import { Timeline } from '@/components/timeline/Timeline'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

type TimelineFeedProps = {
  /** Names the timeline for screen readers, e.g. "Customer timeline". */
  label: string
  /** Identifies this timeline in the query cache; the chosen filters are appended to it. */
  queryKey: QueryKey
  /** Fetches one page. `cursor` is null for the first (newest) page. */
  fetchPage: (request: { cursor: string | null; categories: string[]; signal: AbortSignal }) => Promise<TimelinePage>
  /** What to say when there are no events at all. */
  emptyMessage: string
}

/**
 * A complete timeline: category filters, the events, and "Show older". Give it a function that
 * fetches a page and it does the rest, so every record's timeline behaves the same way:
 *
 *   <TimelineFeed label="Customer timeline" queryKey={[...]} fetchPage={(r) => fetchCustomerTimeline(id, r)} ... />
 */
export function TimelineFeed({ label, queryKey, fetchPage, emptyMessage }: TimelineFeedProps) {
  const [categories, setCategories] = useState<string[]>([])
  const timeline = useInfiniteQuery({
    queryKey: [...queryKey, { categories }],
    queryFn: ({ pageParam, signal }) => fetchPage({ cursor: pageParam, categories, signal }),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
  const events = timeline.data?.pages.flatMap((page) => page.events) ?? []

  function toggle(category: string) {
    setCategories((current) =>
      current.includes(category) ? current.filter((value) => value !== category) : [...current, category],
    )
  }

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap gap-2" role="group" aria-label="Show">
        <FilterChip pressed={categories.length === 0} onClick={() => setCategories([])}>
          Everything
        </FilterChip>
        {TIMELINE_CATEGORIES.map((category) => (
          <FilterChip
            key={category.value}
            pressed={categories.includes(category.value)}
            onClick={() => toggle(category.value)}
          >
            {category.label}
          </FilterChip>
        ))}
      </div>

      {timeline.isPending ? (
        <Skeleton className="h-32 w-full" aria-busy="true" aria-label="Loading timeline" />
      ) : timeline.isError && events.length === 0 ? (
        <ErrorState title="Could not load the timeline" error={timeline.error} onRetry={() => void timeline.refetch()} />
      ) : events.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {categories.length === 0 ? emptyMessage : 'Nothing of this kind has happened yet.'}
        </p>
      ) : (
        <>
          <Timeline events={events} label={label} />
          {timeline.isFetchNextPageError ? (
            <ErrorState
              title="Could not load older events"
              error={timeline.error}
              onRetry={() => void timeline.fetchNextPage()}
            />
          ) : timeline.hasNextPage ? (
            <Button
              variant="outline"
              size="sm"
              className="w-fit"
              disabled={timeline.isFetchingNextPage}
              onClick={() => void timeline.fetchNextPage()}
            >
              {timeline.isFetchingNextPage ? 'Loading…' : 'Show older'}
            </Button>
          ) : (
            <p className="text-xs text-muted-foreground">That is everything, back to the beginning.</p>
          )}
        </>
      )}
    </div>
  )
}

function FilterChip({ pressed, onClick, children }: { pressed: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        'rounded-full border px-3 py-1 text-xs font-medium transition-colors outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50',
        pressed ? 'border-primary bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
      )}
    >
      {children}
    </button>
  )
}
