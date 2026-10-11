import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Mail, Pencil, Phone, UserRound } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useHasPermission } from '@/features/auth/auth-context'
import { customerOverviewQuery, CUSTOMER_TYPES, labelOf, type CustomerDetail } from '@/features/customers/api'
import { CustomerHealthBadge } from '@/features/customers/components/CustomerHealth'
import { CustomerStatusBadge } from '@/features/customers/components/CustomerStatusBadge'

/** Who the customer is and how things stand, visible above every section of the profile. */
export function CustomerHeader({ customer }: { customer: CustomerDetail }) {
  const canEdit = useHasPermission('CUSTOMER_UPDATE')
  const overview = useQuery(customerOverviewQuery(customer.id))
  const primary = overview.data?.summary.primaryContact

  return (
    <header className="grid gap-3">
      <Link
        to="/customers"
        className="inline-flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Customers
      </Link>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="grid gap-2">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
            <h1 className="text-2xl font-semibold tracking-tight">{customer.displayName}</h1>
            <CustomerStatusBadge status={customer.status} />
            {/* Health is worked out by the server; until it answers, nothing is claimed. */}
            {overview.data ? <CustomerHealthBadge level={overview.data.health.level} /> : null}
          </div>
          <p className="text-sm text-muted-foreground">
            {customer.customerNumber} · {labelOf(CUSTOMER_TYPES, customer.type)}
            {customer.assignedTo ? ` · Managed by ${customer.assignedTo.name}` : ' · Unassigned'}
          </p>
          <ul className="flex flex-wrap gap-x-5 gap-y-1 text-sm" aria-label="Contact details">
            {customer.phone ? (
              <li className="flex items-center gap-1.5">
                <Phone className="size-3.5 text-muted-foreground" aria-hidden="true" />
                <a href={`tel:${customer.phone.replace(/[^+0-9]/g, '')}`} className="hover:underline">
                  {customer.phone}
                </a>
              </li>
            ) : null}
            {customer.email ? (
              <li className="flex items-center gap-1.5">
                <Mail className="size-3.5 text-muted-foreground" aria-hidden="true" />
                <a href={`mailto:${customer.email}`} className="break-all hover:underline">
                  {customer.email}
                </a>
              </li>
            ) : null}
            {primary ? (
              <li className="flex items-center gap-1.5">
                <UserRound className="size-3.5 text-muted-foreground" aria-hidden="true" />
                <span>
                  {primary.name}
                  {primary.designation ? `, ${primary.designation}` : ''}
                </span>
              </li>
            ) : null}
          </ul>
          {customer.tags.length > 0 ? (
            <ul className="flex flex-wrap gap-1" aria-label="Tags">
              {customer.tags.map((tag) => (
                <li key={tag}>
                  <Badge variant="secondary">{tag}</Badge>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
        {canEdit ? (
          <div className="flex gap-2">
            <Button asChild variant="outline">
              <Link to={`/customers/${customer.id}?tab=activities`}>Log activity</Link>
            </Button>
            <Button asChild variant="outline">
              <Link to={`/customers/${customer.id}/edit`}>
                <Pencil aria-hidden="true" />
                Edit
              </Link>
            </Button>
          </div>
        ) : null}
      </div>
    </header>
  )
}
