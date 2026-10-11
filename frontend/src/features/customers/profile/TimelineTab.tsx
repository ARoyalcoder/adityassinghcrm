import { TimelineFeed } from '@/components/timeline/TimelineFeed'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { customerTimelineKey, fetchCustomerTimeline } from '@/features/customers/api'
import type { CustomerTabProps } from '@/features/customers/profile/tabs'

/**
 * Everything that has happened with the customer, in one list, newest first. The server builds it
 * from the records themselves (calls, notes, changes, and each further module as it is built), so
 * there is nothing to keep in step here: a new kind of event simply starts to appear.
 */
export function TimelineTab({ customer }: CustomerTabProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Customer timeline</CardTitle>
        <CardDescription>Everything that has happened with this customer, newest first.</CardDescription>
      </CardHeader>
      <CardContent>
        <TimelineFeed
          label="Customer timeline"
          queryKey={customerTimelineKey(customer.id)}
          fetchPage={(request) => fetchCustomerTimeline(customer.id, request)}
          emptyMessage="Nothing has happened with this customer yet."
        />
      </CardContent>
    </Card>
  )
}
