import { expect, test, type Page } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, mockVerticals, pageOf } from './support'

const SALES = ['CUSTOMER_VIEW', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE']
const ID = '66666666-6666-6666-6666-666666666666'
const BASE = `**/api/v1/customers/${ID}`

// Day headings are in the viewer's time zone; pin it so the test means the same thing everywhere.
test.use({ timezoneId: 'Asia/Kolkata', locale: 'en-IN' })

const CUSTOMER = {
  id: ID,
  customerNumber: 'CUS-001001',
  type: 'BUSINESS',
  displayName: 'Acme Solar',
  firstName: null,
  lastName: null,
  companyName: 'Acme Solar',
  taxId: '09ABCDE1234F1Z5',
  email: 'info@acme.example',
  phone: '+91 98765 43210',
  status: 'ACTIVE',
  source: 'REFERRAL',
  assignedTo: { id: '77777777-7777-7777-7777-777777777777', name: 'Asha Verma' },
  tags: ['VIP'],
  contactCount: 1,
  addressCount: 2,
  version: 3,
  createdAt: '2026-01-10T09:00:00Z',
  updatedAt: '2026-10-05T10:15:30Z',
}

function overview(overrides: { health?: object; summary?: object } = {}) {
  return {
    health: {
      level: 'NEEDS_ATTENTION',
      signals: [
        { code: 'CONTACT_STALE', impact: 'WARNING', message: 'Last contact was 120 days ago' },
        { code: 'PAYMENTS_ON_TIME', impact: 'POSITIVE', message: 'Every invoice paid on time' },
      ],
      ...overrides.health,
    },
    summary: {
      customerSince: '2026-01-10T09:00:00Z',
      lastContactAt: '2026-06-08T10:15:30Z',
      lastContactSubject: 'Roof survey',
      contactCount: 1,
      addressCount: 2,
      interactionCount: 4,
      noteCount: 1,
      primaryContact: { name: 'Meera Joshi', designation: 'Owner', phone: '9876543210', email: 'meera@acme.example' },
      billingAddress: '12 MG Road, Lucknow, Uttar Pradesh 226001',
      serviceAddress: 'Plot 4, Kanpur, Uttar Pradesh',
      ...overrides.summary,
    },
  }
}

type Event = {
  id: string
  type: string
  category: string
  occurredAt: string
  title: string
  description?: string
  actor?: string
  details?: Record<string, string>
}

/** What the server would return: one list, newest first, already merged from every module. */
const EVENTS: Event[] = [
  { id: 'amc:2', type: 'AMC_RENEWED', category: 'AMC', occurredAt: '2026-10-05T10:15:30Z', title: 'AMC renewed', description: 'AMC-0007 renewed for 12 months', actor: 'Ravi Kumar' },
  { id: 'ticket:2', type: 'TICKET_RESOLVED', category: 'SERVICE', occurredAt: '2026-10-05T08:00:00Z', title: 'Ticket resolved', description: 'TKT-0031 Inverter fault' },
  { id: 'payment:1', type: 'PAYMENT_RECEIVED', category: 'FINANCE', occurredAt: '2026-10-03T07:30:00Z', title: 'Payment received', description: 'Against INV-0012', details: { Amount: '₹1,85,000', Mode: 'Bank transfer' } },
  { id: 'invoice:1', type: 'INVOICE_GENERATED', category: 'FINANCE', occurredAt: '2026-10-02T07:30:00Z', title: 'Invoice generated', description: 'INV-0012' },
  { id: 'milestone:1', type: 'MILESTONE_COMPLETED', category: 'PROJECT', occurredAt: '2026-09-28T07:30:00Z', title: 'Milestone completed', description: 'Panels installed' },
  { id: 'order:1', type: 'ORDER_CREATED', category: 'SALES', occurredAt: '2026-09-20T07:30:00Z', title: 'Order created', description: 'ORD-0005' },
  { id: 'quote:2', type: 'QUOTATION_ACCEPTED', category: 'SALES', occurredAt: '2026-09-18T07:30:00Z', title: 'Quotation accepted', description: 'QUO-0009' },
  { id: 'quote:1', type: 'QUOTATION_SENT', category: 'SALES', occurredAt: '2026-09-15T07:30:00Z', title: 'Quotation sent', description: 'QUO-0009 for a 10 kW rooftop system' },
  { id: 'interaction:2', type: 'MEETING_COMPLETED', category: 'COMMUNICATION', occurredAt: '2026-09-12T07:30:00Z', title: 'Meeting completed', description: 'Roof survey', actor: 'Asha Verma' },
  { id: 'interaction:1', type: 'CALL_LOGGED', category: 'COMMUNICATION', occurredAt: '2026-09-10T07:30:00Z', title: 'Call logged', description: 'Asked about rooftop solar', actor: 'Asha Verma', details: { Direction: 'Incoming', Outcome: 'Wants a site visit' } },
  // A kind of event this version of the screen has never heard of: it must still be shown.
  { id: 'future:1', type: 'SITE_AUDIT_SCHEDULED', category: 'COMPLIANCE', occurredAt: '2026-09-09T07:30:00Z', title: 'Site audit scheduled' },
  { id: 'lead:1', type: 'LEAD_CREATED', category: 'SALES', occurredAt: '2026-09-08T07:30:00Z', title: 'Lead created', description: 'Website enquiry' },
  { id: 'record:1', type: 'CUSTOMER_CREATED', category: 'RECORD', occurredAt: '2026-01-10T09:00:00Z', title: 'Customer created', description: "Created Customer 'Acme Solar'", actor: 'asha@example.com' },
]

/** Serves the timeline the way the real endpoint does: filtered by category, paged by cursor. */
async function mockTimeline(page: Page, events: Event[] | (() => Event[]), requests: URL[] = []) {
  await page.route(`${BASE}/timeline**`, (route) => {
    const url = new URL(route.request().url())
    requests.push(url)
    const categories = url.searchParams.getAll('category')
    const limit = Number(url.searchParams.get('limit') ?? 25)
    const all = typeof events === 'function' ? events() : events
    const matching = all.filter((event) => categories.length === 0 || categories.includes(event.category))
    const start = Number(url.searchParams.get('cursor') ?? 0)
    const end = start + limit
    return route.fulfill({
      json: { events: matching.slice(start, end), ...(end < matching.length ? { nextCursor: String(end) } : {}) },
    })
  })
}

async function mockProfile(page: Page) {
  await page.route(BASE, (route) => route.fulfill({ json: CUSTOMER }))
  await page.route(`${BASE}/notes**`, (route) => route.fulfill({ json: pageOf([]) }))
}

test.beforeEach(async ({ page }) => {
  await mockSystemInfo(page)
  await mockVerticals(page)
})

test('the header, health and summary show the customer at a glance', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockProfile(page)
  await page.route(`${BASE}/overview`, (route) => route.fulfill({ json: overview() }))
  await mockTimeline(page, EVENTS)

  await page.goto(`/customers/${ID}`)

  // Header: who they are and how things stand, above every section.
  const header = page.locator('header').filter({ has: page.getByRole('heading', { level: 1 }) })
  await expect(header.getByRole('heading', { name: 'Acme Solar', level: 1 })).toBeVisible()
  await expect(header).toContainText('CUS-001001 · Business · Managed by Asha Verma')
  await expect(header.getByText('Active', { exact: true })).toBeVisible()
  await expect(header.getByText('Health: Needs attention')).toBeVisible()
  const contact = header.getByRole('list', { name: 'Contact details' })
  await expect(contact.getByRole('link', { name: '+91 98765 43210' })).toHaveAttribute('href', 'tel:+919876543210')
  await expect(contact.getByRole('link', { name: 'info@acme.example' })).toHaveAttribute('href', 'mailto:info@acme.example')
  await expect(contact).toContainText('Meera Joshi, Owner')
  await expect(header.getByRole('list', { name: 'Tags' })).toContainText('VIP')

  // The sections of the 360, in order.
  await expect(page.getByRole('tab')).toHaveText([
    /^Summary/, /^Timeline/, /^Business information/, /^Contacts\s*1$/, /^Sales history/, /^Project history/,
    /^Financial summary/, /^Service history/, /^AMC \/ Warranty/, /^Documents/, /^Activities/,
  ])
  await expect(page.getByRole('tab', { name: 'Summary', exact: true })).toHaveAttribute('aria-selected', 'true')

  // Health says why, most serious first, in words as well as colour.
  const panel = page.getByRole('tabpanel')
  await expect(panel.getByRole('list', { name: 'Health reasons' }).getByRole('listitem')).toHaveText([
    'Needs attention: Last contact was 120 days ago',
    'Good sign: Every invoice paid on time',
  ])

  // Summary facts come from the server.
  await expect(panel).toContainText('10 Jan 2026')
  await expect(panel).toContainText('Roof survey')
  await expect(panel).toContainText('12 MG Road, Lucknow, Uttar Pradesh 226001')
  await expect(panel).toContainText('Plot 4, Kanpur, Uttar Pradesh')
  await expect(panel.getByRole('link', { name: '4 activities' })).toHaveAttribute('href', `/customers/${ID}?tab=activities`)
  await expect(panel.getByRole('link', { name: '1 contact', exact: true })).toBeVisible()

  // The latest five events, with a way to the rest.
  const recent = panel.getByRole('list', { name: 'Recent timeline' })
  await expect(recent.getByRole('listitem')).toHaveCount(5)
  await expect(recent.getByRole('listitem').first()).toContainText('AMC renewed')
  await panel.getByRole('link', { name: 'View the full timeline' }).click()
  await expect(page.getByRole('tab', { name: 'Timeline' })).toHaveAttribute('aria-selected', 'true')
})

test('a customer with nothing on record is not called healthy or unhealthy', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockProfile(page)
  await page.route(`${BASE}/overview`, (route) =>
    route.fulfill({
      json: overview({
        health: { level: 'UNKNOWN', signals: [] },
        summary: { lastContactAt: null, lastContactSubject: null, primaryContact: null, billingAddress: null, serviceAddress: null, interactionCount: 0 },
      }),
    }),
  )
  await mockTimeline(page, [])

  await page.goto(`/customers/${ID}`)

  await expect(page.getByText('Health: Not enough history').first()).toBeVisible()
  const panel = page.getByRole('tabpanel')
  await expect(panel).toContainText('There is not enough on record yet to judge how this relationship is going.')
  await expect(panel).toContainText('None logged')
  await expect(panel).toContainText('Nothing has happened yet.')
})

test('the timeline shows events from every module in order, with filters and paging', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockProfile(page)
  await page.route(`${BASE}/overview`, (route) => route.fulfill({ json: overview() }))
  const requests: URL[] = []
  // Thirty more old events, so the first page of 25 is not everything.
  const older: Event[] = Array.from({ length: 30 }, (_, index) => ({
    id: `old:${index}`,
    type: 'EMAIL_LOGGED',
    category: 'COMMUNICATION',
    occurredAt: new Date(Date.UTC(2025, 11, 30 - index, 6)).toISOString(),
    title: 'Email logged',
    description: `Old email ${index + 1}`,
  }))
  await mockTimeline(page, [...EVENTS, ...older], requests)

  await page.goto(`/customers/${ID}?tab=timeline`)

  const timeline = page.getByRole('group', { name: 'Customer timeline' })
  const items = timeline.getByRole('listitem')
  await expect(items).toHaveCount(25)
  // One list, newest first, whatever module each event came from.
  await expect(items.nth(0)).toContainText('AMC renewed')
  await expect(items.nth(1)).toContainText('Ticket resolved')
  await expect(items.nth(2)).toContainText('Payment received')
  await expect(items.nth(7)).toContainText('Quotation sent')
  await expect(items.nth(12)).toContainText('Customer created')
  // Grouped under the day they happened; two events on the same day share a heading.
  const day = timeline.getByRole('region', { name: 'Monday, 5 October 2026' })
  await expect(day.getByRole('listitem')).toHaveText([/AMC renewed/, /Ticket resolved/])
  // An event carries its description, extra facts, who did it and its category.
  await expect(items.nth(2)).toContainText('Against INV-0012')
  await expect(items.nth(2)).toContainText('₹1,85,000')
  await expect(items.nth(2)).toContainText('Bank transfer')
  await expect(items.nth(2)).toContainText('Finance')
  await expect(items.nth(9)).toContainText('Wants a site visit')
  await expect(items.nth(9)).toContainText('Asha Verma')
  // An event type and category this screen does not know is still shown.
  await expect(items.nth(10)).toContainText('Site audit scheduled')
  await expect(items.nth(10)).toContainText('COMPLIANCE')

  // Older events are fetched with the cursor the server gave, and added below.
  await page.getByRole('button', { name: 'Show older' }).click()
  await expect(items).toHaveCount(43)
  await expect(items.nth(25)).toContainText('Old email 13')
  await expect(page.getByRole('button', { name: 'Show older' })).toHaveCount(0)
  await expect(page.getByText('That is everything, back to the beginning.')).toBeVisible()
  expect(requests.some((url) => url.searchParams.get('cursor') === '25')).toBe(true)
  // Nothing is shown twice.
  const texts = await items.allTextContents()
  expect(new Set(texts).size).toBe(texts.length)

  // Filters are sent to the server, and can be combined.
  const filters = page.getByRole('group', { name: 'Show' })
  await expect(filters.getByRole('button', { name: 'Everything' })).toHaveAttribute('aria-pressed', 'true')
  await filters.getByRole('button', { name: 'Finance' }).click()
  await expect(items).toHaveText([/Payment received/, /Invoice generated/])
  await expect(filters.getByRole('button', { name: 'Everything' })).toHaveAttribute('aria-pressed', 'false')
  await filters.getByRole('button', { name: 'AMC', exact: true }).click()
  await expect(items).toHaveText([/AMC renewed/, /Payment received/, /Invoice generated/])
  expect(requests.at(-1)?.searchParams.getAll('category')).toEqual(['FINANCE', 'AMC'])

  await filters.getByRole('button', { name: 'AMC', exact: true }).click()
  await filters.getByRole('button', { name: 'Finance' }).click()
  await filters.getByRole('button', { name: 'Projects' }).click()
  await expect(items).toHaveText([/Milestone completed/])
  // Back to everything: the pages already loaded are still there.
  await filters.getByRole('button', { name: 'Everything' }).click()
  await expect(items).toHaveCount(43)
})

test('a filter with no matching events says so', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockProfile(page)
  await page.route(`${BASE}/overview`, (route) => route.fulfill({ json: overview() }))
  await mockTimeline(page, EVENTS.filter((event) => event.category === 'RECORD'))

  await page.goto(`/customers/${ID}?tab=timeline`)

  await expect(page.getByRole('group', { name: 'Customer timeline' }).getByRole('listitem')).toHaveCount(1)
  await page.getByRole('group', { name: 'Show' }).getByRole('button', { name: 'Finance' }).click()
  await expect(page.getByText('Nothing of this kind has happened yet.')).toBeVisible()
})

test('logging an activity adds it to the list, the timeline and the health', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockProfile(page)
  type Interaction = { id: string; type: string; direction: string | null; occurredAt: string; subject: string; details: string | null; outcome: string | null; loggedByName: string; createdAt: string }
  const interactions: Interaction[] = []
  let posted: Record<string, unknown> | undefined
  await page.route(`${BASE}/interactions**`, (route) => {
    const method = route.request().method()
    if (method === 'POST') {
      posted = route.request().postDataJSON() as Record<string, unknown>
      interactions.unshift({
        id: 'i1', type: 'CALL', direction: 'OUTBOUND', occurredAt: '2026-10-08T09:00:00Z',
        subject: 'Followed up on rooftop quote', details: 'Spoke to Meera.', outcome: 'Will confirm by Friday',
        loggedByName: 'Asha Verma', createdAt: '2026-10-08T09:00:00Z',
      })
      return route.fulfill({ status: 201, json: interactions[0] })
    }
    if (method === 'DELETE') {
      interactions.length = 0
      return route.fulfill({ status: 204 })
    }
    return route.fulfill({ json: pageOf(interactions) })
  })
  // Health and timeline are worked out by the server from what is on record.
  await page.route(`${BASE}/overview`, (route) =>
    route.fulfill({
      json: overview({
        health: interactions.length > 0
          ? { level: 'GOOD', signals: [{ code: 'CONTACT_RECENT', impact: 'POSITIVE', message: 'In contact today' }] }
          : { level: 'UNKNOWN', signals: [] },
      }),
    }),
  )
  await mockTimeline(page, () =>
    interactions.map((interaction) => ({
      id: `interaction:${interaction.id}`, type: 'CALL_LOGGED', category: 'COMMUNICATION',
      occurredAt: interaction.occurredAt, title: 'Call logged', description: interaction.subject, actor: interaction.loggedByName,
    })),
  )

  await page.goto(`/customers/${ID}`)
  await expect(page.getByText('Health: Not enough history').first()).toBeVisible()

  // The header's shortcut goes to the Activities section.
  await page.getByRole('link', { name: 'Log activity' }).click()
  await expect(page.getByRole('tab', { name: 'Activities' })).toHaveAttribute('aria-selected', 'true')
  const panel = page.getByRole('tabpanel')
  await expect(panel).toContainText('No activities logged yet.')

  await panel.getByRole('button', { name: 'Log activity' }).click()
  const form = panel.getByRole('form', { name: 'Log activity' })
  await form.getByRole('button', { name: 'Save activity' }).click()
  await expect(form.getByText('Say briefly what it was about')).toBeVisible()
  await form.getByLabel('When (leave empty for now)').fill('2099-01-01T10:00')
  await form.getByLabel('Subject').fill('Followed up on rooftop quote')
  await form.getByRole('button', { name: 'Save activity' }).click()
  await expect(form.getByText('This cannot be in the future')).toBeVisible()
  expect(posted).toBeUndefined()

  await form.getByLabel('When (leave empty for now)').fill('')
  await form.getByLabel('Direction').selectOption('Outgoing')
  await form.getByLabel('Details').fill('Spoke to Meera.')
  await form.getByLabel('Outcome').fill('Will confirm by Friday')
  await form.getByRole('button', { name: 'Save activity' }).click()

  const list = panel.getByRole('list', { name: 'Activities' })
  await expect(list.getByRole('listitem')).toHaveCount(1)
  await expect(list).toContainText('Call')
  await expect(list).toContainText('Outgoing')
  await expect(list).toContainText('Followed up on rooftop quote')
  await expect(list).toContainText('Outcome: Will confirm by Friday')
  await expect(list).toContainText('logged by Asha Verma')
  expect(posted).toEqual({
    type: 'CALL', direction: 'OUTBOUND', occurredAt: null, subject: 'Followed up on rooftop quote',
    details: 'Spoke to Meera.', outcome: 'Will confirm by Friday',
  })
  // The rest of the 360 catches up without a reload.
  await expect(page.getByText('Health: Healthy')).toBeVisible()
  await page.getByRole('tab', { name: 'Timeline' }).click()
  await expect(page.getByRole('group', { name: 'Customer timeline' }).getByRole('listitem')).toHaveText([/Call logged/])

  // Removing it removes it everywhere: the timeline holds no copy.
  await page.getByRole('tab', { name: 'Activities' }).click()
  await panel.getByRole('button', { name: 'Remove Followed up on rooftop quote' }).click()
  await expect(panel).toContainText('No activities logged yet.')
  await expect(page.getByText('Health: Not enough history')).toBeVisible()
  await page.getByRole('tab', { name: 'Timeline' }).click()
  await expect(panel).toContainText('Nothing has happened with this customer yet.')
})

test('business information, contacts and the sections not built yet', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockProfile(page)
  await page.route(`${BASE}/overview`, (route) => route.fulfill({ json: overview() }))
  await mockTimeline(page, EVENTS)
  await page.route(`${BASE}/addresses`, (route) =>
    route.fulfill({
      json: [
        { id: 'a1', type: 'BILLING', label: 'Head office', line1: '12 MG Road', city: 'Lucknow', state: 'Uttar Pradesh', postalCode: '226001', countryCode: 'IN', defaultAddress: true, version: 0 },
        { id: 'a2', type: 'SERVICE', label: 'Warehouse', line1: 'Plot 4', city: 'Kanpur', state: 'Uttar Pradesh', countryCode: 'IN', defaultAddress: true, version: 0 },
      ],
    }),
  )
  const contacts = [
    { id: 'c1', name: 'Meera Joshi', designation: 'Owner', email: 'meera@acme.example', phone: '9876543210', primaryContact: true, version: 0 },
  ]
  let newContact: Record<string, unknown> | undefined
  await page.route(`${BASE}/contacts`, (route) => {
    if (route.request().method() === 'POST') {
      newContact = route.request().postDataJSON() as Record<string, unknown>
      if (contacts.length === 1) {
        contacts.push({ id: 'c2', name: 'Ravi Kumar', designation: null as unknown as string, email: null as unknown as string, phone: '9000000001', primaryContact: false, version: 0 })
      }
      return route.fulfill({ status: 201, json: contacts[1] })
    }
    return route.fulfill({ json: contacts })
  })

  await page.goto(`/customers/${ID}?tab=business`)
  const panel = page.getByRole('tabpanel')

  // Business information: the record's facts and its addresses.
  await expect(page.getByRole('tab', { name: 'Business information' })).toHaveAttribute('aria-selected', 'true')
  await expect(panel).toContainText('09ABCDE1234F1Z5')
  await expect(panel).toContainText('Referral')
  await expect(panel).toContainText('Acme Solar')
  const addresses = panel.getByRole('list', { name: 'Addresses' })
  await expect(addresses.getByRole('listitem').filter({ hasText: 'Billing' })).toContainText('12 MG Road')
  await expect(addresses.getByRole('listitem').filter({ hasText: 'Service' })).toContainText('Kanpur')

  // Contacts
  await page.getByRole('tab', { name: /^Contacts/ }).click()
  await expect(page).toHaveURL(/tab=contacts/)
  await expect(panel.getByRole('list', { name: 'Contacts' })).toContainText('Primary')
  await panel.getByRole('button', { name: 'Add contact' }).click()
  const contactForm = panel.getByRole('form', { name: 'New contact' })
  await contactForm.getByRole('button', { name: 'Add contact' }).click()
  await expect(contactForm.getByText('Enter a name')).toBeVisible()
  await contactForm.getByLabel('Name').fill('Ravi Kumar')
  await contactForm.getByLabel('Phone').fill('9000000001')
  await contactForm.getByRole('button', { name: 'Add contact' }).click()
  await expect(panel.getByRole('list', { name: 'Contacts' })).toContainText('Ravi Kumar')
  expect(newContact).toEqual({ name: 'Ravi Kumar', designation: null, phone: '9000000001', email: null, primaryContact: false })

  // Sections whose module is not built say so. They show no figures, not even zeros.
  // (Project history is no longer a placeholder: it lists the customer's projects.)
  for (const section of ['Sales history', 'Financial summary', 'Service history', 'AMC / Warranty', 'Documents']) {
    await page.getByRole('tab', { name: section }).click()
    await expect(panel.getByRole('heading', { name: `${section} is not available yet` })).toBeVisible()
    await expect(panel).not.toContainText('₹')
    await expect(panel.getByRole('table')).toHaveCount(0)
  }
})

test('a view-only user can read the whole 360 but cannot log or change anything', async ({ page }) => {
  await mockSignedIn(page, ['CUSTOMER_VIEW'])
  await mockProfile(page)
  await page.route(`${BASE}/overview`, (route) => route.fulfill({ json: overview() }))
  await mockTimeline(page, EVENTS)
  await page.route(`${BASE}/interactions**`, (route) =>
    route.fulfill({
      json: pageOf([
        { id: 'i1', type: 'MEETING', direction: null, occurredAt: '2026-09-12T07:30:00Z', subject: 'Roof survey', details: null, outcome: null, loggedByName: 'Asha Verma', createdAt: '2026-09-12T08:00:00Z' },
      ]),
    }),
  )

  await page.goto(`/customers/${ID}`)

  await expect(page.getByText('Health: Needs attention').first()).toBeVisible()
  await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Log activity' })).toHaveCount(0)
  await expect(page.getByLabel('New note')).toHaveCount(0)

  await page.getByRole('tab', { name: 'Timeline' }).click()
  await expect(page.getByRole('group', { name: 'Customer timeline' }).getByRole('listitem')).toHaveCount(13)

  await page.getByRole('tab', { name: 'Activities' }).click()
  const panel = page.getByRole('tabpanel')
  await expect(panel.getByRole('list', { name: 'Activities' })).toContainText('Roof survey')
  await expect(panel.getByRole('button', { name: 'Log activity' })).toHaveCount(0)
  await expect(panel.getByRole('button', { name: /^Remove/ })).toHaveCount(0)
})
