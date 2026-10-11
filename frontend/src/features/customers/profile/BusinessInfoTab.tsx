import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDateTime } from '@/features/audit/api'
import { CUSTOMER_SOURCES, CUSTOMER_TYPES, labelOf } from '@/features/customers/api'
import { CustomerStatusBadge } from '@/features/customers/components/CustomerStatusBadge'
import { AddressesTab } from '@/features/customers/profile/AddressesTab'
import type { CustomerTabProps } from '@/features/customers/profile/tabs'

/** The facts held about the customer as a business: identity, tax details, ownership and addresses. */
export function BusinessInfoTab({ customer }: CustomerTabProps) {
  const business = customer.type === 'BUSINESS'
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card className="h-fit">
        <CardHeader>
          <CardTitle>Business information</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-muted-foreground">Customer no.</dt>
            <dd className="font-mono">{customer.customerNumber}</dd>
            <dt className="text-muted-foreground">Type</dt>
            <dd>{labelOf(CUSTOMER_TYPES, customer.type)}</dd>
            {business ? (
              <>
                <dt className="text-muted-foreground">Company name</dt>
                <dd>{customer.companyName ?? '—'}</dd>
                <dt className="text-muted-foreground">GSTIN</dt>
                <dd className="font-mono">{customer.taxId ?? '—'}</dd>
              </>
            ) : (
              <>
                <dt className="text-muted-foreground">Name</dt>
                <dd>{[customer.firstName, customer.lastName].filter(Boolean).join(' ') || '—'}</dd>
              </>
            )}
            <dt className="text-muted-foreground">Phone</dt>
            <dd>{customer.phone ?? '—'}</dd>
            <dt className="text-muted-foreground">Email</dt>
            <dd className="break-all">{customer.email ?? '—'}</dd>
            <dt className="text-muted-foreground">Status</dt>
            <dd>
              <CustomerStatusBadge status={customer.status} />
            </dd>
            <dt className="text-muted-foreground">Source</dt>
            <dd>{customer.source ? labelOf(CUSTOMER_SOURCES, customer.source) : '—'}</dd>
            <dt className="text-muted-foreground">Assigned to</dt>
            <dd>{customer.assignedTo?.name ?? 'Unassigned'}</dd>
            <dt className="text-muted-foreground">Tags</dt>
            <dd className="flex flex-wrap gap-1">
              {customer.tags.length > 0
                ? customer.tags.map((tag) => (
                    <Badge key={tag} variant="secondary">
                      {tag}
                    </Badge>
                  ))
                : '—'}
            </dd>
            <dt className="text-muted-foreground">Added on</dt>
            <dd>{formatDateTime(customer.createdAt)}</dd>
            <dt className="text-muted-foreground">Last changed</dt>
            <dd>{formatDateTime(customer.updatedAt)}</dd>
          </dl>
        </CardContent>
      </Card>

      <AddressesTab customer={customer} />
    </div>
  )
}
