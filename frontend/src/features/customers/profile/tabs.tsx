import type { ComponentType } from 'react'
import type { CustomerDetail } from '@/features/customers/api'
import { ActivitiesTab } from '@/features/customers/profile/ActivitiesTab'
import { BusinessInfoTab } from '@/features/customers/profile/BusinessInfoTab'
import { ContactsTab } from '@/features/customers/profile/ContactsTab'
import { NotBuiltYetTab } from '@/features/customers/profile/NotBuiltYetTab'
import { SummaryTab } from '@/features/customers/profile/SummaryTab'
import { TimelineTab } from '@/features/customers/profile/TimelineTab'

import { CustomerProjectsTab } from '@/features/customers/profile/CustomerProjectsTab'

export type CustomerTabProps = { customer: CustomerDetail }

export type CustomerTab = {
  /** Used in the URL: /customers/:id?tab=contacts */
  key: string
  label: string
  /** A number shown next to the label, when it is known. */
  count?: (customer: CustomerDetail) => number
  component: ComponentType<CustomerTabProps>
}

/** A section whose module does not exist yet. It says so plainly instead of pretending to be empty. */
function notBuilt(key: string, label: string, contents: string): CustomerTab {
  return { key, label, component: () => <NotBuiltYetTab title={label} contents={contents} /> }
}

/**
 * The sections of the Customer 360, in order. (The header, with status and health, sits above them all.)
 *
 * This is where a module plugs itself into the profile: when Sales is built, it replaces its
 * `notBuilt(...)` line with `{ key: 'sales', label: 'Sales history', component: CustomerSalesTab }`,
 * and its component receives the customer. Nothing else in the profile changes. Its events reach
 * the Timeline section separately, through a CustomerTimelineSource on the backend.
 */
export const customerTabs: CustomerTab[] = [
  { key: 'summary', label: 'Summary', component: SummaryTab },
  { key: 'timeline', label: 'Timeline', component: TimelineTab },
  { key: 'business', label: 'Business information', component: BusinessInfoTab },
  { key: 'contacts', label: 'Contacts', count: (customer) => customer.contactCount, component: ContactsTab },
  notBuilt('sales', 'Sales history', 'Leads, opportunities, quotations and orders'),
  { key: 'projects', label: 'Project history', component: CustomerProjectsTab },
  notBuilt('financials', 'Financial summary', 'Invoices, payments and the outstanding balance'),
  notBuilt('service', 'Service history', 'Support tickets and service visits'),
  notBuilt('amc', 'AMC / Warranty', 'Maintenance contracts and warranties'),
  notBuilt('documents', 'Documents', 'Files'),
  { key: 'activities', label: 'Activities', component: ActivitiesTab },
]
