import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Timeline } from '@/components/timeline/Timeline'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/features/audit/api'
import {
  customerOverviewQuery,
  customerTimelineKey,
  fetchCustomerTimeline,
  type CustomerOverview,
} from '@/features/customers/api'
import { CustomerHealthBadge, CustomerHealthReasons } from '@/features/customers/components/CustomerHealth'
import { NotesCard } from '@/features/customers/profile/NotesCard'
import type { CustomerTabProps } from '@/features/customers/profile/tabs'

const RECENT_EVENTS = 5

const dateFormat = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' })

/** The customer at a glance: health and why, the key facts, what happened lately, and notes. */
export function SummaryTab({ customer }: CustomerTabProps) {
  const overview = useQuery(customerOverviewQuery(customer.id))

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader className="flex-row items-center justify-between gap-3">
          <CardTitle>Customer health</CardTitle>
          {overview.data ? <CustomerHealthBadge level={overview.data.health.level} /> : null}
        </CardHeader>
        <CardContent>
          {overview.isPending ? (
            <Skeleton className="h-12 w-full" aria-busy="true" aria-label="Loading health" />
          ) : overview.isError ? (
            <ErrorState title="Could not load health" error={overview.error} onRetry={() => void overview.refetch()} />
          ) : (
            <CustomerHealthReasons health={overview.data.health} />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Customer summary</CardTitle>
        </CardHeader>
        <CardContent>
          {overview.isPending ? (
            <Skeleton className="h-24 w-full" aria-busy="true" aria-label="Loading summary" />
          ) : overview.isError ? (
            <ErrorState title="Could not load the summary" error={overview.error} onRetry={() => void overview.refetch()} />
          ) : (
            <SummaryFacts customerId={customer.id} summary={overview.data.summary} />
          )}
        </CardContent>
      </Card>

      <RecentTimelineCard customerId={customer.id} />
      <NotesCard customerId={customer.id} />
    </div>
  )
}

function SummaryFacts({ customerId, summary }: { customerId: string; summary: CustomerOverview['summary'] }) {
  const primary = summary.primaryContact
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
      <dt className="text-muted-foreground">Customer since</dt>
      <dd>{dateFormat.format(new Date(summary.customerSince))}</dd>
      <dt className="text-muted-foreground">Last contact</dt>
      <dd>
        {summary.lastContactAt ? (
          <>
            {formatDateTime(summary.lastContactAt)}
            {summary.lastContactSubject ? (
              <span className="block text-muted-foreground">{summary.lastContactSubject}</span>
            ) : null}
          </>
        ) : (
          'None logged'
        )}
      </dd>
      <dt className="text-muted-foreground">Primary contact</dt>
      <dd>
        {primary ? (
          <>
            {primary.name}
            {primary.designation ? `, ${primary.designation}` : ''}
            <span className="block text-muted-foreground">
              {[primary.phone, primary.email].filter(Boolean).join(' · ') || 'No phone or email'}
            </span>
          </>
        ) : (
          'None'
        )}
      </dd>
      <dt className="text-muted-foreground">Billing address</dt>
      <dd>{summary.billingAddress ?? 'None'}</dd>
      <dt className="text-muted-foreground">Service address</dt>
      <dd>{summary.serviceAddress ?? 'None'}</dd>
      <dt className="text-muted-foreground">On record</dt>
      <dd className="flex flex-wrap gap-x-4 gap-y-1">
        <Link className="underline underline-offset-2" to={`/customers/${customerId}?tab=contacts`}>
          {count(summary.contactCount, 'contact')}
        </Link>
        <Link className="underline underline-offset-2" to={`/customers/${customerId}?tab=business`}>
          {count(summary.addressCount, 'address', 'addresses')}
        </Link>
        <Link className="underline underline-offset-2" to={`/customers/${customerId}?tab=activities`}>
          {count(summary.interactionCount, 'activity', 'activities')}
        </Link>
        <span>{count(summary.noteCount, 'note')}</span>
      </dd>
    </dl>
  )
}

function count(value: number, singular: string, plural = `${singular}s`) {
  return `${value} ${value === 1 ? singular : plural}`
}

function RecentTimelineCard({ customerId }: { customerId: string }) {
  const recent = useQuery({
    queryKey: [...customerTimelineKey(customerId), 'recent'],
    queryFn: ({ signal }) => fetchCustomerTimeline(customerId, { limit: RECENT_EVENTS, signal }),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent timeline</CardTitle>
        <CardDescription>
          The latest that has happened with this customer.{' '}
          <Link className="underline underline-offset-2" to={`/customers/${customerId}?tab=timeline`}>
            View the full timeline
          </Link>
        </CardDescription>
      </CardHeader>
      <CardContent>
        {recent.isPending ? (
          <Skeleton className="h-24 w-full" aria-busy="true" aria-label="Loading timeline" />
        ) : recent.isError ? (
          <ErrorState title="Could not load the timeline" error={recent.error} onRetry={() => void recent.refetch()} />
        ) : recent.data.events.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing has happened yet.</p>
        ) : (
          <Timeline events={recent.data.events} label="Recent timeline" compact />
        )}
      </CardContent>
    </Card>
  )
}
