import { expect, test, type Page } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, mockVerticals, pageOf } from './support'

const SALES = ['LEAD_VIEW', 'LEAD_CREATE', 'LEAD_UPDATE', 'CATALOG_VIEW']
const MANAGER = [...SALES, 'LEAD_DELETE', 'LEAD_CONFIGURE']
const ID = '99999999-9999-9999-9999-999999999999'
const SOLAR = '22222222-2222-2222-2222-222222222222'
const CCTV = '33333333-3333-3333-3333-333333333333'
const LEAD = `**/api/v1/leads/${ID}`

test.use({ timezoneId: 'Asia/Kolkata', locale: 'en-IN' })

function lead(overrides: Record<string, unknown> = {}) {
  return {
    id: ID,
    leadNumber: 'LD-001001',
    contactName: 'Asha Verma',
    companyName: 'Acme Solar',
    email: null,
    phone: '9876543210',
    service: { id: SOLAR, name: 'Rooftop solar', verticalName: 'Solar' },
    estimatedValue: 750000,
    status: 'OPEN',
    closedAt: null,
    score: 45,
    priority: 'MEDIUM',
    health: 'GOOD',
    qualificationStatus: 'QUALIFIED',
    customFields: [],
    version: 0,
    createdAt: '2026-10-08T09:00:00Z',
    updatedAt: '2026-10-08T09:00:00Z',
    ...overrides,
  }
}

const DECISION_MAKER = {
  id: 'q1', key: 'decision_maker', label: 'Decision maker identified', helpText: 'Have we spoken to the person who signs?',
  required: true, options: [{ value: 'YES', label: 'Yes' }, { value: 'NO', label: 'No' }], answer: null as string | null,
}

type Factor = { code: string; category: string; label: string; points: number }

/** The server's explanation of a lead. The screen must show exactly this and add nothing. */
function qualification(factors: Factor[], overrides: Record<string, unknown> = {}) {
  const total = factors.reduce((sum, factor) => sum + factor.points, 0)
  return {
    score: Math.max(0, Math.min(100, total)),
    total,
    factors,
    engine: 'rules-v1',
    priority: 'MEDIUM',
    priorityReason: 'Score 45 is between 30 and 59',
    health: 'GOOD',
    healthReason: 'Last activity was yesterday',
    qualificationStatus: 'QUALIFIED',
    qualificationReason: 'All 1 required questions answered, and no answer rules the lead out',
    computedAt: '2026-10-09T04:30:00Z',
    questions: [{ ...DECISION_MAKER, answer: 'YES' }],
    ...overrides,
  }
}

const HIGH_VALUE = { code: 'HIGH_VALUE', category: 'VALUE', label: 'High estimated value (₹5,00,000 or more)', points: 20 }
const DECIDER = { code: 'QUESTION:decision_maker', category: 'QUALIFICATION', label: 'Decision maker identified: Yes', points: 15 }
const RECENT = { code: 'RECENT_ACTIVITY', category: 'ENGAGEMENT', label: 'Recent activity (yesterday)', points: 10 }
const SILENT = { code: 'NO_ACTIVITY', category: 'ENGAGEMENT', label: 'No activity for 3 days', points: -10 }

async function mockLead(page: Page, overrides: Record<string, unknown> = {}) {
  await page.route(LEAD, (route) => route.fulfill({ json: lead(overrides) }))
  await page.route(`${LEAD}/activities`, (route) => route.fulfill({ json: [] }))
}

test.beforeEach(async ({ page }) => {
  await mockSystemInfo(page)
  await mockVerticals(page)
})

test('"Why this score?" lists every reason with its points, and the reason for each conclusion', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockLead(page)
  await page.route(`${LEAD}/qualification`, (route) => route.fulfill({ json: qualification([HIGH_VALUE, DECIDER, RECENT]) }))

  await page.goto(`/leads/${ID}`)

  await expect(page.getByRole('heading', { name: 'Asha Verma', level: 1 })).toBeVisible()
  await expect(page.getByText('LD-001001 · Acme Solar · Rooftop solar')).toBeVisible()
  const badges = page.getByRole('group', { name: 'Assessment' })
  await expect(badges).toContainText('Score: 45/100')
  await expect(badges).toContainText('Priority: Medium priority')
  await expect(badges).toContainText('Health: Good')
  await expect(badges).toContainText('Qualification: Qualified')

  const panel = page.locator('[data-slot=card]').filter({ has: page.getByRole('heading', { name: 'Why this score?' }) })
  await expect(panel).toContainText('45out of 100')
  // The example from the brief, reason by reason.
  await expect(panel.getByRole('list', { name: 'Score factors' }).getByRole('listitem')).toHaveText([
    '+20High estimated value (₹5,00,000 or more)',
    '+15Decision maker identified: Yes',
    '+10Recent activity (yesterday)',
  ])
  await expect(panel).toContainText('45Total')
  // Each conclusion says why.
  await expect(panel).toContainText('Score 45 is between 30 and 59')
  await expect(panel).toContainText('Last activity was yesterday')
  await expect(panel).toContainText('All 1 required questions answered, and no answer rules the lead out')
  // And how the score was produced.
  await expect(panel).toContainText('Worked out by fixed rules, not by a model')
  await expect(panel).toContainText('As of 9 Oct 2026')
})

test('points taken away are shown as such, and a neglected lead is called out', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockLead(page)
  await page.route(`${LEAD}/qualification`, (route) =>
    route.fulfill({
      json: qualification([HIGH_VALUE, DECIDER, SILENT], {
        priority: 'LOW', priorityReason: 'Score 25 is below 30',
        health: 'NEEDS_ATTENTION', healthReason: 'No activity for 3 days (3 or more needs a follow-up)',
      }),
    }),
  )

  await page.goto(`/leads/${ID}`)

  const panel = page.locator('[data-slot=card]').filter({ has: page.getByRole('heading', { name: 'Why this score?' }) })
  await expect(panel.getByRole('list', { name: 'Score factors' }).getByRole('listitem')).toHaveText([
    '+20High estimated value (₹5,00,000 or more)',
    '+15Decision maker identified: Yes',
    '−10No activity for 3 days',
  ])
  await expect(panel).toContainText('25Total')
  await expect(panel).toContainText('No activity for 3 days (3 or more needs a follow-up)')
  // The header shows the assessment as of now (the stored one on the lead said 45 and Good).
  const badges = page.getByRole('group', { name: 'Assessment' })
  await expect(badges).toContainText('Score: 25/100')
  await expect(badges).toContainText('Health: Needs attention')
  await expect(badges).toContainText('Priority: Low priority')
})

test('a score outside 0 to 100 says so, a lead with no reasons says so, and another scorer is named', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockLead(page)
  let response = qualification([{ ...DECIDER, label: 'Fit: Perfect', points: 100 }, HIGH_VALUE, DECIDER, RECENT])
  await page.route(`${LEAD}/qualification`, (route) => route.fulfill({ json: response }))

  await page.goto(`/leads/${ID}`)
  const panel = page.locator('[data-slot=card]').filter({ has: page.getByRole('heading', { name: 'Why this score?' }) })
  await expect(panel).toContainText('100out of 100')
  await expect(panel).toContainText('145Total')
  await expect(panel).toContainText('The reasons add up to 145. A score is kept between 0 and 100, so it is shown as 100.')

  response = qualification([], { engine: 'model-2027', questions: [] })
  await page.reload()
  await expect(panel).toContainText('0out of 100')
  await expect(panel).toContainText("Nothing adds to or takes from this lead's score yet.")
  await expect(panel.getByRole('list', { name: 'Score factors' })).toHaveCount(0)
  // A scorer this version of the screen has never heard of is named rather than misdescribed.
  await expect(panel).toContainText('Scored by model-2027.')
  await expect(panel).not.toContainText('fixed rules')
})

test('answering a qualification question saves at once and the score follows', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await mockLead(page, { score: 20, priority: 'LOW', qualificationStatus: 'NOT_STARTED' })
  let answer: string | null = null
  const current = () =>
    answer === 'YES'
      ? qualification([HIGH_VALUE, DECIDER], { priorityReason: 'Score 35 is between 30 and 59' })
      : qualification([HIGH_VALUE], {
          priority: 'LOW', priorityReason: 'Score 20 is below 30',
          qualificationStatus: answer === 'NO' ? 'QUALIFIED' : 'NOT_STARTED',
          qualificationReason: 'No qualification question has been answered yet',
          questions: [{ ...DECISION_MAKER, answer }],
        })
  await page.route(`${LEAD}/qualification`, (route) => route.fulfill({ json: current() }))
  const sent: unknown[] = []
  await page.route(`${LEAD}/qualification/answers`, (route) => {
    const body = route.request().postDataJSON() as { answers: Record<string, string | null> }
    sent.push(body)
    answer = body.answers.decision_maker
    return route.fulfill({ json: current() })
  })

  await page.goto(`/leads/${ID}`)
  const badges = page.getByRole('group', { name: 'Assessment' })
  await expect(badges).toContainText('Score: 20/100')
  const question = page.getByRole('group', { name: 'Decision maker identified (required)' })
  await expect(question).toContainText('Have we spoken to the person who signs?')
  await expect(question.getByRole('radio')).toHaveCount(2)
  await expect(question.getByLabel('Yes')).not.toBeChecked()
  // What an answer is worth is not shown to the person answering.
  await expect(question).not.toContainText('15')

  await question.getByLabel('Yes').check()

  await expect(badges).toContainText('Score: 35/100')
  await expect(badges).toContainText('Qualification: Qualified')
  await expect(page.getByRole('list', { name: 'Score factors' })).toContainText('Decision maker identified: Yes')
  expect(sent).toEqual([{ answers: { decision_maker: 'YES' } }])

  await question.getByRole('button', { name: 'Clear the answer to Decision maker identified' }).click()
  await expect(badges).toContainText('Score: 20/100')
  await expect(question.getByLabel('Yes')).not.toBeChecked()
  expect(sent.at(-1)).toEqual({ answers: { decision_maker: null } })
})

test('logging an activity rescored the lead and the page shows it', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await page.route(LEAD, (route) => route.fulfill({ json: lead() }))
  const activities: Record<string, unknown>[] = []
  let posted: unknown
  await page.route(`${LEAD}/activities`, (route) => {
    if (route.request().method() === 'POST') {
      posted = route.request().postDataJSON()
      if (activities.length === 0) {
        activities.push({ id: 'a1', type: 'SITE_VISIT', occurredAt: '2026-10-09T04:00:00Z', subject: 'Roof survey', loggedByName: 'Asha Verma' })
      }
      return route.fulfill({ status: 201, json: activities[0] })
    }
    return route.fulfill({ json: activities })
  })
  await page.route(`${LEAD}/qualification`, (route) =>
    route.fulfill({
      json: activities.length > 0
        ? qualification([HIGH_VALUE, DECIDER, { ...RECENT, label: 'Recent activity (today)' }])
        : qualification([HIGH_VALUE, DECIDER, SILENT], { health: 'NEEDS_ATTENTION', healthReason: 'No activity for 3 days (3 or more needs a follow-up)' }),
    }),
  )

  await page.goto(`/leads/${ID}`)
  await expect(page.getByText('Nothing has been done about this lead yet.')).toBeVisible()
  await expect(page.getByRole('group', { name: 'Assessment' })).toContainText('Health: Needs attention')

  const form = page.getByRole('form', { name: 'Log activity' })
  await expect(form.getByRole('button', { name: 'Log activity' })).toBeDisabled()
  await form.getByLabel('Type').selectOption('Site visit')
  await form.getByLabel('What happened').fill('  Roof survey ')
  await form.getByRole('button', { name: 'Log activity' }).click()

  await expect(page.getByRole('list', { name: 'Activity' })).toContainText('Site visit: Roof survey')
  expect(posted).toEqual({ type: 'SITE_VISIT', subject: 'Roof survey' })
  await expect(page.getByRole('list', { name: 'Score factors' })).toContainText('Recent activity (today)')
  await expect(page.getByRole('group', { name: 'Assessment' })).toContainText('Health: Good')
  await expect(form.getByLabel('What happened')).toHaveValue('')
})

test('the leads list shows each assessment and filters by it', async ({ page }) => {
  await mockSignedIn(page, SALES)
  const requests: URL[] = []
  await page.route('**/api/v1/leads?*', (route) => {
    const url = new URL(route.request().url())
    requests.push(url)
    const hot = lead({ id: 'l1', contactName: 'Asha Verma', score: 72, priority: 'HIGH' })
    const cold = lead({ id: 'l2', leadNumber: 'LD-001002', contactName: 'Ravi Kumar', companyName: null, estimatedValue: null, score: 5, priority: 'LOW', health: 'AT_RISK', qualificationStatus: 'DISQUALIFIED' })
    return route.fulfill({ json: pageOf(url.searchParams.get('priority') === 'HIGH' ? [hot] : [hot, cold]) })
  })
  await page.route('**/api/v1/leads', (route) => route.fulfill({ json: pageOf([]) }))

  await page.goto('/leads')

  const rows = page.getByRole('table', { name: 'Leads' }).getByRole('row')
  await expect(rows).toHaveCount(3)
  await expect(rows.nth(1)).toContainText('Asha Verma')
  await expect(rows.nth(1)).toContainText('₹7,50,000')
  await expect(rows.nth(1)).toContainText('Score: 72/100')
  await expect(rows.nth(1)).toContainText('Priority: High priority')
  await expect(rows.nth(2)).toContainText('Health: At risk')
  await expect(rows.nth(2)).toContainText('Qualification: Disqualified')
  await expect(page.getByLabel('Sort by')).toHaveValue('') // the server's default: highest score first

  await page.getByLabel('Priority').selectOption('High')
  await expect(rows).toHaveCount(2)
  await expect(page).toHaveURL(/priority=HIGH/)
  expect(requests.at(-1)?.searchParams.get('priority')).toBe('HIGH')
  await page.getByLabel('Sort by').selectOption({ label: 'Sort: Highest value' })
  await expect.poll(() => requests.at(-1)?.searchParams.get('sort')).toBe('estimatedValue,desc')

  await expect(page.getByRole('link', { name: 'New lead' })).toHaveAttribute('href', '/leads/new')
  await expect(page.getByRole('link', { name: 'Asha Verma' })).toHaveAttribute('href', '/leads/l1')
})

function definition(id: string, key: string, type: string, label: string, extra: Record<string, unknown> = {}) {
  return {
    field: {
      id, key, label, type, helpText: null, required: false, options: null, minValue: null, maxValue: null,
      maxLength: null, unit: null, allowedFileTypes: null, maxFileSizeMb: null, displayOrder: 10, active: true, ...extra,
    },
    version: 0,
  }
}

function service(id: string, name: string, vertical: string) {
  return {
    id, code: name.toUpperCase().replace(/[^A-Z]+/g, '_'), name, description: null, displayOrder: 0, active: true,
    billingType: 'QUOTE_BASED', unitLabel: null, basePrice: null, requiresSiteVisit: false, estimatedDurationDays: null,
    category: { id: 'c1', code: 'GENERAL', name: 'General' }, vertical: { code: vertical.toUpperCase(), name: vertical }, version: 0,
  }
}

test('the lead form asks what the chosen service asks, and sends the answers with the lead', async ({ page }) => {
  await mockSignedIn(page, SALES)
  await page.route('**/api/v1/catalog/services?*', (route) =>
    route.fulfill({ json: pageOf([service(SOLAR, 'Rooftop solar', 'Solar'), service(CCTV, 'CCTV installation', 'CCTV & Security')]) }),
  )
  await page.route(`**/api/v1/catalog/services/${SOLAR}/custom-fields`, (route) =>
    route.fulfill({
      json: [
        definition('f1', 'roof_area', 'NUMBER', 'Roof area', { unit: 'sq ft', required: true, minValue: 100 }),
        definition('f2', 'site_availability', 'BOOLEAN', 'Site available for survey'),
        definition('f3', 'old_question', 'TEXT', 'No longer asked', { active: false }),
      ],
    }),
  )
  await page.route(`**/api/v1/catalog/services/${CCTV}/custom-fields`, (route) =>
    route.fulfill({ json: [definition('f4', 'camera_count', 'NUMBER', 'Camera count', { required: true })] }),
  )
  let posted: Record<string, unknown> | undefined
  let reject = true
  await page.route('**/api/v1/leads', (route) => {
    posted = route.request().postDataJSON() as Record<string, unknown>
    if (reject) {
      return route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        json: { status: 400, code: 'VALIDATION_FAILED', detail: 'One or more values are invalid', errors: [{ field: 'customFields.roof_area', message: 'must be at most 50000' }] },
      })
    }
    return route.fulfill({ status: 201, json: lead() })
  })
  await mockLead(page)
  await page.route(`${LEAD}/qualification`, (route) => route.fulfill({ json: qualification([HIGH_VALUE]) }))

  await page.goto('/leads/new')
  const form = page.getByRole('form', { name: 'New lead' })
  // No service chosen yet: nothing service-specific is asked.
  await expect(form.locator('[data-field-type]')).toHaveCount(0)
  await form.getByLabel('Contact name').fill('Asha Verma')
  await form.getByLabel('Estimated value (₹)').fill('750000')

  // CCTV asks about cameras...
  await form.getByLabel('Service').selectOption({ label: 'CCTV installation (CCTV & Security)' })
  await expect(form.getByLabel('Camera count')).toBeVisible()
  await expect(form.getByLabel('Roof area')).toHaveCount(0)
  // ...solar about the roof. What was already typed survives the change.
  await form.getByLabel('Service').selectOption({ label: 'Rooftop solar (Solar)' })
  await expect(form.getByLabel('Roof area')).toBeVisible()
  await expect(form.getByLabel('Camera count')).toHaveCount(0)
  await expect(form.getByLabel('No longer asked')).toHaveCount(0)
  await expect(form.getByLabel('Contact name')).toHaveValue('Asha Verma')
  await expect(form.getByLabel('Estimated value (₹)')).toHaveValue('750000')

  // The lead's own rules and the service's rules are checked together.
  await form.getByRole('button', { name: 'Create lead' }).click()
  await expect(form.getByText('Enter a phone number or an email address')).toBeVisible()
  await expect(form.locator('[data-field-type=NUMBER]')).toContainText('This is required')
  expect(posted).toBeUndefined()

  await form.getByLabel('Phone').fill('9876543210')
  await form.getByLabel('Roof area').fill('99000')
  await form.getByRole('radiogroup', { name: 'Site available for survey' }).getByLabel('Yes').check()
  await form.getByRole('button', { name: 'Create lead' }).click()
  // The server's objection to a service field lands on that field.
  await expect(form.locator('[data-field-type=NUMBER]')).toContainText('Must be at most 50000')
  expect(posted).toEqual({
    contactName: 'Asha Verma', companyName: null, phone: '9876543210', email: null, serviceId: SOLAR,
    estimatedValue: 750000, status: 'OPEN', customFields: { roof_area: 99000, site_availability: true },
  })

  reject = false
  await form.getByLabel('Roof area').fill('1200')
  await form.getByRole('button', { name: 'Create lead' }).click()
  await expect(page).toHaveURL(new RegExp(`/leads/${ID}$`))
  expect((posted as { customFields: unknown }).customFields).toEqual({ roof_area: 1200, site_availability: true })
})

test('editing a lead brings back its answers, and the detail page shows them by type', async ({ page }) => {
  await mockSignedIn(page, SALES)
  const roof = definition('f1', 'roof_area', 'NUMBER', 'Roof area', { unit: 'sq ft' })
  const site = definition('f2', 'site_type', 'SELECT', 'Site type', { options: [{ value: 'SHOP', label: 'Shop' }, { value: 'HOME', label: 'Home' }] })
  const available = definition('f3', 'site_availability', 'BOOLEAN', 'Site available for survey')
  const stored = { customFields: [{ field: roof.field, value: 1200 }, { field: site.field, value: 'SHOP' }, { field: available.field, value: false }], version: 4 }
  await mockLead(page, stored)
  await page.route(`${LEAD}/qualification`, (route) => route.fulfill({ json: qualification([HIGH_VALUE]) }))
  await page.route('**/api/v1/catalog/services?*', (route) => route.fulfill({ json: pageOf([service(SOLAR, 'Rooftop solar', 'Solar')]) }))
  await page.route(`**/api/v1/catalog/services/${SOLAR}/custom-fields`, (route) => route.fulfill({ json: [roof, site, available] }))
  let put: Record<string, unknown> | undefined
  await page.route(LEAD, (route) => {
    if (route.request().method() === 'PUT') {
      put = route.request().postDataJSON() as Record<string, unknown>
    }
    return route.fulfill({ json: lead(stored) })
  })

  await page.goto(`/leads/${ID}`)
  const details = page.locator('[data-slot=card]').filter({ has: page.getByRole('heading', { name: 'Details' }) })
  await expect(details).toContainText('Roof area1200 sq ft')
  await expect(details).toContainText('Site typeShop')
  await expect(details).toContainText('Site available for surveyNo')
  await expect(details).toContainText('₹7,50,000.00')

  await page.getByRole('link', { name: 'Edit' }).click()
  const form = page.getByRole('form', { name: 'Edit lead' })
  await expect(form.getByLabel('Roof area')).toHaveValue('1200')
  await expect(form.getByLabel('Site type')).toHaveValue('SHOP')
  await expect(form.getByRole('radiogroup', { name: 'Site available for survey' }).getByLabel('No', { exact: true })).toBeChecked()
  await form.getByLabel('Status').selectOption('Won')
  await form.getByLabel('Roof area').fill('1500')
  await form.getByRole('button', { name: 'Save lead' }).click()

  await expect(page).toHaveURL(new RegExp(`/leads/${ID}$`))
  expect(put).toMatchObject({
    status: 'WON', version: 4, customFields: { roof_area: 1500, site_type: 'SHOP', site_availability: false },
  })
})

test('a manager defines the questions and what each answer is worth', async ({ page }) => {
  await mockSignedIn(page, MANAGER)
  const questions: Record<string, unknown>[] = []
  const requests: { method: string; path: string; body: Record<string, unknown> | null }[] = []
  await page.route('**/api/v1/lead-qualification/questions', (route) => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({ json: questions })
    }
    const body = route.request().postDataJSON() as Record<string, unknown>
    requests.push({ method: 'POST', path: '', body })
    if (!questions.some((question) => question.key === body.key)) {
      // Like the real server, a question created without a position is put last.
      questions.push({ ...body, id: `q${questions.length + 1}`, displayOrder: (questions.length + 1) * 10, active: true, version: 0 })
    }
    return route.fulfill({ status: 201, json: questions.at(-1) })
  })
  await page.route('**/api/v1/lead-qualification/questions/**', (route) => {
    const method = route.request().method()
    const [id, action] = new URL(route.request().url()).pathname.split('/questions/')[1].split('/')
    const index = questions.findIndex((question) => question.id === id)
    const body = method === 'PUT' ? (route.request().postDataJSON() as Record<string, unknown>) : null
    requests.push({ method, path: action ?? '', body })
    if (method === 'DELETE') {
      questions.splice(index, 1)
      return route.fulfill({ status: 204 })
    }
    questions[index] = body
      ? { ...questions[index], ...body, displayOrder: questions[index].displayOrder, version: (body.version as number) + 1 }
      : { ...questions[index], active: action === 'activate' }
    return route.fulfill({ json: questions[index] })
  })

  await page.goto('/')
  await page.getByRole('link', { name: 'Qualification' }).click()
  await expect(page.getByRole('heading', { name: 'Qualification questions', level: 1 })).toBeVisible()
  await expect(page.getByText('No questions yet.')).toBeVisible()

  // A yes/no question is the starting point; the key follows the question.
  await page.getByRole('button', { name: 'Add question' }).click()
  const editor = page.getByRole('form', { name: 'New question' })
  await editor.getByLabel('Question', { exact: true }).fill('Decision maker identified?')
  await expect(editor.getByLabel('Key')).toHaveValue('decision_maker_identified')
  const first = editor.getByRole('group', { name: 'Answer 1' })
  await expect(first.getByRole('textbox', { name: 'Answer' })).toHaveValue('Yes')
  await first.getByLabel('Points').fill('15')
  await editor.getByLabel('Required before a lead counts as qualified').check()
  await editor.getByRole('button', { name: 'Add question' }).click()

  const list = page.getByRole('list', { name: 'Questions' })
  await expect(list.getByRole('listitem').first()).toContainText('Decision maker identified?')
  await expect(page.getByRole('list', { name: 'Answers to Decision maker identified?' }).getByRole('listitem')).toHaveText(['Yes +15', 'No 0'])
  expect(requests.at(-1)?.body).toEqual({
    key: 'decision_maker_identified', label: 'Decision maker identified?', helpText: null, required: true, displayOrder: null,
    options: [{ value: 'YES', label: 'Yes', points: 15, disqualifies: false }, { value: 'NO', label: 'No', points: 0, disqualifies: false }],
  })

  // A question with its own answers, one of which rules a lead out.
  await page.getByRole('button', { name: 'Add question' }).click()
  await editor.getByLabel('Question', { exact: true }).fill('Budget')
  await editor.getByRole('group', { name: 'Answer 1' }).getByRole('textbox', { name: 'Answer' }).fill('Confirmed')
  await editor.getByRole('group', { name: 'Answer 1' }).getByLabel('Points').fill('20')
  await editor.getByRole('group', { name: 'Answer 2' }).getByRole('textbox', { name: 'Answer' }).fill('No budget')
  await editor.getByRole('group', { name: 'Answer 2' }).getByLabel('Points').fill('-10')
  await editor.getByRole('group', { name: 'Answer 2' }).getByLabel('Rules the lead out').check()
  await editor.getByRole('button', { name: 'Add answer' }).click()
  await editor.getByRole('group', { name: 'Answer 3' }).getByLabel('Points').fill('lots')
  await editor.getByRole('button', { name: 'Add question' }).click()
  await expect(editor.getByRole('group', { name: 'Answer 3' })).toContainText('Enter the answer')
  await expect(editor.getByRole('group', { name: 'Answer 3' })).toContainText('A whole number from -100 to 100')
  await editor.getByRole('button', { name: 'Remove answer 3' }).click()
  await editor.getByRole('button', { name: 'Add question' }).click()
  await expect(page.getByRole('list', { name: 'Answers to Budget' }).getByRole('listitem')).toHaveText([
    'Confirmed +20', 'No budget −10 · rules the lead out',
  ])
  // The stored values are the ones the answers started with: rewording "Yes" to "Confirmed" keeps answers attached.
  expect(requests.at(-1)?.body).toMatchObject({
    key: 'budget',
    options: [{ value: 'YES', label: 'Confirmed', points: 20, disqualifies: false }, { value: 'NO', label: 'No budget', points: -10, disqualifies: true }],
  })

  // Editing keeps the key; fewer than two answers is not a question.
  await list.getByRole('button', { name: 'Edit Budget' }).click()
  const edit = page.getByRole('form', { name: 'Edit Budget' })
  await expect(edit.getByLabel('Key')).toBeDisabled()
  await edit.getByRole('button', { name: 'Remove answer 2' }).click()
  await edit.getByRole('button', { name: 'Save question' }).click()
  await expect(edit.getByText('Give at least 2 answers to choose from')).toBeVisible()
  await edit.getByRole('button', { name: 'Add answer' }).click()
  await edit.getByRole('group', { name: 'Answer 2' }).getByRole('textbox', { name: 'Answer' }).fill('Not sure yet')
  await edit.getByRole('group', { name: 'Answer 2' }).getByLabel('Points').fill('5')
  await edit.getByRole('button', { name: 'Save question' }).click()
  await expect(page.getByRole('list', { name: 'Answers to Budget' }).getByRole('listitem')).toHaveText(['Confirmed +20', 'Not sure yet +5'])
  expect(requests.at(-1)).toMatchObject({
    method: 'PUT',
    body: { version: 0, options: [{ value: 'YES', label: 'Confirmed' }, { value: 'NOT_SURE_YET', label: 'Not sure yet', points: 5 }] },
  })
  expect(requests.at(-1)?.body).not.toHaveProperty('key')

  await list.getByRole('button', { name: 'Switch off Budget' }).click()
  await expect(list.getByRole('listitem').filter({ hasText: 'Budget' }).first()).toContainText('Inactive')
  await list.getByRole('button', { name: 'Remove Budget' }).click()
  await expect(list.getByRole('listitem').filter({ hasText: 'budget' })).toHaveCount(0)
})

test('someone who may only view leads sees the explanation but changes nothing', async ({ page }) => {
  await mockSignedIn(page, ['LEAD_VIEW'])
  await mockLead(page)
  await page.route(`${LEAD}/qualification`, (route) => route.fulfill({ json: qualification([HIGH_VALUE, DECIDER, RECENT]) }))

  await page.goto(`/leads/${ID}`)

  await expect(page.getByRole('list', { name: 'Score factors' }).getByRole('listitem')).toHaveCount(3)
  await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0)
  await expect(page.getByRole('form', { name: 'Log activity' })).toHaveCount(0)
  const question = page.getByRole('group', { name: 'Decision maker identified (required)' })
  await expect(question.getByLabel('Yes')).toBeChecked()
  await expect(question.getByLabel('No', { exact: true })).toBeDisabled()
  await expect(question.getByRole('button', { name: /Clear/ })).toHaveCount(0)
  // No way into the configuration either.
  await expect(page.getByRole('link', { name: 'Qualification' })).toHaveCount(0)
  await page.goto('/admin/qualification-questions')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
  await page.goto('/leads/new')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})

test('leads are hidden from users without the permission', async ({ page }) => {
  await mockSignedIn(page, ['CUSTOMER_VIEW'])

  await page.goto('/')
  await expect(page.getByRole('link', { name: 'Leads' })).toHaveCount(0)
  await page.goto('/leads')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})
