import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { BUCKETS, myCountsQuery, myJobsQuery, type Bucket } from '@/features/field-jobs/api'
import { JobCard } from '@/features/field-jobs/components/JobParts'
import { cn } from '@/lib/utils'

const EMPTY: Record<Bucket, string> = {
  TODAY: 'Nothing is scheduled for you today.',
  UPCOMING: 'Nothing is scheduled for the coming days.',
  DELAYED: 'Nothing is overdue.',
  COMPLETED: 'You have not completed any jobs yet.',
}

/**
 * The technician's dashboard (`/my-jobs`): their own jobs in four lists. Laid out for a phone:
 * one column, large cards, the lists as a row of tabs that scrolls sideways if it must.
 */
export function MyJobsPage() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const bucket = BUCKETS.find((candidate) => candidate.value === params.get('list')?.toUpperCase())?.value ?? 'TODAY'
  const counts = useQuery(myCountsQuery)
  const jobs = useQuery(myJobsQuery(bucket))

  return (
    <div className="mx-auto grid w-full max-w-2xl gap-5">
      <header className="grid gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">My jobs</h1>
        <p className="text-sm text-muted-foreground">
          {user ? `${user.fullName}, here` : 'Here'} is your work. Tap a job to open it.
        </p>
      </header>

      <div role="tablist" aria-label="Job lists" className="flex gap-2 overflow-x-auto pb-1">
        {BUCKETS.map((option) => {
          const selected = option.value === bucket
          const count = counts.data?.[option.count]
          return (
            <button
              key={option.value}
              type="button"
              role="tab"
              aria-selected={selected}
              onClick={() => setParams(option.value === 'TODAY' ? {} : { list: option.value.toLowerCase() }, { replace: true })}
              className={cn(
                'flex min-h-11 shrink-0 items-center gap-2 rounded-xl px-4 text-sm font-medium outline-none transition-colors focus-visible:ring-[3px] focus-visible:ring-ring/50',
                selected ? 'bg-primary text-primary-foreground shadow-soft' : 'bg-card text-muted-foreground shadow-soft hover:text-foreground',
              )}
            >
              {option.label}
              {count !== undefined ? (
                <span
                  className={cn(
                    'rounded-full px-2 py-0.5 text-xs tabular-nums',
                    selected ? 'bg-primary-foreground/20' : option.value === 'DELAYED' && count > 0 ? 'bg-red-100 text-red-800' : 'bg-muted',
                  )}
                >
                  {count}
                </span>
              ) : null}
            </button>
          )
        })}
      </div>

      <div role="tabpanel" aria-label={BUCKETS.find((option) => option.value === bucket)?.label}>
        {jobs.isPending ? (
          <Skeleton className="h-40 w-full rounded-2xl" aria-busy="true" aria-label="Loading jobs" />
        ) : jobs.isError ? (
          <ErrorState title="Could not load your jobs" error={jobs.error} onRetry={() => void jobs.refetch()} />
        ) : jobs.data.length === 0 ? (
          <p className="rounded-2xl bg-card p-6 text-center text-sm text-muted-foreground shadow-soft">{EMPTY[bucket]}</p>
        ) : (
          <ul className="grid gap-3" aria-label="Jobs">
            {jobs.data.map((job) => (
              <li key={job.id}>
                <JobCard job={job} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
