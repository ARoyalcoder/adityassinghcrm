import { expect, test, type Page, type Route } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, mockVerticals, pageOf } from './support'

const TECHNICIAN = ['FIELD_JOB_WORK']
const DISPATCHER = ['FIELD_JOB_VIEW', 'FIELD_JOB_MANAGE']
const ID = 'cccccccc-cccc-cccc-cccc-cccccccccccc'
const JOB = `**/api/v1/field-jobs/${ID}`
const NEXT: Record<string, string | null> = {
  ASSIGNED: 'ACCEPTED', ACCEPTED: 'EN_ROUTE', EN_ROUTE: 'ARRIVED', ARRIVED: 'IN_PROGRESS', IN_PROGRESS: 'COMPLETED',
  COMPLETED: 'CUSTOMER_CONFIRMED', CUSTOMER_CONFIRMED: null, UNASSIGNED: null, CANCELLED: null,
}
// A one-pixel PNG, served as the uploaded photo.
const PIXEL = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64')

// A phone held upright: the technician's screens are made for this.
test.use({ viewport: { width: 390, height: 844 }, timezoneId: 'Asia/Kolkata', locale: 'en-IN' })

function summary(overrides: Record<string, unknown> = {}) {
  return {
    id: ID, jobNumber: 'JOB-001001', jobType: 'JOB', title: 'Install 8 cameras', customerName: 'Acme Solar',
    siteAddress: '12 MG Road, Lucknow', scheduledStart: '2026-10-10T08:30:00Z', scheduledEnd: '2026-10-10T11:30:00Z',
    technicianId: 't1', technicianName: 'Ravi Kumar', status: 'ASSIGNED', delayed: false, ...overrides,
  }
}

type Detail = ReturnType<typeof detail>

function detail(overrides: Record<string, unknown> = {}) {
  return {
    job: summary(),
    instructions: 'Use the conduit on the east wall.',
    customerId: null as string | null,
    siteContactName: 'Meera Joshi' as string | null,
    siteContactPhone: '98765 43210' as string | null,
    completionNotes: null as string | null,
    signOff: null as Record<string, unknown> | null,
    checklist: [
      { id: 'i1', label: 'Mount cameras', done: false, doneAt: null, doneByName: null },
      { id: 'i2', label: 'Configure the recorder', done: false, doneAt: null, doneByName: null },
    ],
    materials: [] as Record<string, unknown>[],
    photos: [] as Record<string, unknown>[],
    notes: [] as Record<string, unknown>[],
    nextStatus: 'ACCEPTED' as string | null,
    canAct: true,
    version: 0,
    ...overrides,
  }
}

/** A stand-in for the server that keeps one job and applies each request to it, like the real one. */
async function mockJob(page: Page, state: Detail, log: { method: string; path: string; body: unknown }[] = []) {
  const reply = (route: Route) => route.fulfill({ json: state })
  await page.route(JOB, reply)
  await page.route(`${JOB}/**`, (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.split(`${ID}/`)[1]
    const method = request.method()
    if (method === 'GET' && path.endsWith('/content')) {
      return route.fulfill({ contentType: 'image/png', body: PIXEL })
    }
    const multipart = (request.headers()['content-type'] ?? '').includes('multipart/form-data')
    const body = multipart ? 'multipart' : method === 'DELETE' ? null : (request.postDataJSON() as Record<string, unknown>)
    log.push({ method, path, body })
    if (path === 'advance' && body && typeof body === 'object') {
      const to = body.to as string
      state.job = { ...state.job, status: to }
      state.nextStatus = NEXT[to]
      if (to === 'COMPLETED') {
        state.completionNotes = (body.completionNotes as string | null) ?? null
      }
      if (to === 'CUSTOMER_CONFIRMED') {
        state.signOff = { signedByName: body.signedByName, remarks: body.remarks ?? null, signature: body.signature ?? null, confirmedAt: '2026-10-10T11:45:00Z' }
      }
    } else if (path.startsWith('checklist/') && body && typeof body === 'object') {
      const item = state.checklist.find((candidate) => candidate.id === path.split('/')[1])
      if (item) {
        item.done = body.done as boolean
      }
    } else if (path === 'materials' && body && typeof body === 'object') {
      state.materials = [...state.materials, { id: `m${state.materials.length + 1}`, notes: null, recordedByName: 'Ravi Kumar', ...body }]
    } else if (path.startsWith('materials/')) {
      state.materials = state.materials.filter((material) => material.id !== path.split('/')[1])
    } else if (path === 'notes' && body && typeof body === 'object') {
      state.notes = [{ id: `n${state.notes.length + 1}`, body: body.body, authorName: 'Ravi Kumar', createdAt: '2026-10-10T09:00:00Z' }, ...state.notes]
    } else if (path === 'photos') {
      state.photos = [...state.photos, { id: `p${state.photos.length + 1}`, fileName: 'IMG_0042.jpg', caption: 'After installation', uploadedByName: 'Ravi Kumar', createdAt: '2026-10-10T09:30:00Z' }]
    } else if (path.startsWith('photos/')) {
      state.photos = state.photos.filter((photo) => photo.id !== path.split('/')[1])
    } else if (path === 'assign' && body && typeof body === 'object') {
      state.job = { ...state.job, technicianId: body.technicianId as string, technicianName: 'Asha Verma', status: 'ASSIGNED' }
      state.nextStatus = 'ACCEPTED'
    } else if (path === 'cancel') {
      state.job = { ...state.job, status: 'CANCELLED' }
      state.nextStatus = null
    }
    return reply(route)
  })
}

async function expectNoSidewaysScroll(page: Page) {
  // Evaluated in the page, where document and window exist.
  expect(await page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')).toBe(true)
}

test.beforeEach(async ({ page }) => {
  await mockSystemInfo(page)
  await mockVerticals(page)
})

test("the technician's dashboard lists today's, upcoming, delayed and completed jobs", async ({ page }) => {
  await mockSignedIn(page, TECHNICIAN)
  await page.route('**/api/v1/field-jobs/my/summary', (route) => route.fulfill({ json: { today: 2, upcoming: 3, delayed: 1, completed: 14 } }))
  await page.route('**/api/v1/field-jobs/my?*', (route) => {
    const bucket = new URL(route.request().url()).searchParams.get('bucket')
    const lists: Record<string, unknown[]> = {
      TODAY: [summary(), summary({ id: 'j2', jobNumber: 'JOB-001002', title: 'Roof survey', jobType: 'SITE_VISIT', status: 'ACCEPTED', scheduledStart: '2026-10-10T10:00:00Z', scheduledEnd: null })],
      UPCOMING: [summary({ id: 'j3', title: 'Service call', scheduledStart: '2026-10-12T05:00:00Z' })],
      DELAYED: [summary({ id: 'j4', title: 'Missed yesterday', delayed: true, scheduledStart: '2026-10-09T05:00:00Z' })],
      COMPLETED: [],
    }
    return route.fulfill({ json: lists[bucket ?? 'TODAY'] })
  })

  await page.goto('/my-jobs')

  await expect(page.getByRole('heading', { name: 'My jobs', level: 1 })).toBeVisible()
  await expect(page.getByRole('tab')).toHaveText([/Today's Jobs\s*2/, /Upcoming Jobs\s*3/, /Delayed Jobs\s*1/, /Completed Jobs\s*14/])
  await expect(page.getByRole('tab', { name: /Today's Jobs/ })).toHaveAttribute('aria-selected', 'true')

  const jobs = page.getByRole('list', { name: 'Jobs' }).getByRole('listitem')
  await expect(jobs).toHaveCount(2)
  await expect(jobs.nth(0)).toContainText('Install 8 cameras')
  await expect(jobs.nth(0)).toContainText('JOB-001001 · Job')
  await expect(jobs.nth(0)).toContainText('Sat, 10 Oct, 2:00 pm to 5:00 pm') // shown in the technician's own time zone
  await expect(jobs.nth(0)).toContainText('Acme Solar')
  await expect(jobs.nth(0)).toContainText('12 MG Road, Lucknow')
  await expect(jobs.nth(0)).toContainText('Status: Assigned')
  await expect(jobs.nth(1)).toContainText('JOB-001002 · Site visit')
  await expect(jobs.nth(0).getByRole('link')).toHaveAttribute('href', `/field-jobs/${ID}`)
  await expectNoSidewaysScroll(page)

  await page.getByRole('tab', { name: /Delayed Jobs/ }).click()
  await expect(page).toHaveURL(/list=delayed/)
  await expect(jobs).toHaveCount(1)
  await expect(jobs.first()).toContainText('Missed yesterday')
  await expect(jobs.first()).toContainText('Delayed')

  await page.getByRole('tab', { name: /Completed Jobs/ }).click()
  await expect(page.getByText('You have not completed any jobs yet.')).toBeVisible()
  await page.getByRole('tab', { name: /Upcoming Jobs/ }).click()
  await expect(jobs.first()).toContainText('Service call')
})

test('a technician works a job from accepting it to the customer confirming it', async ({ page }) => {
  await mockSignedIn(page, TECHNICIAN)
  const state = detail()
  const log: { method: string; path: string; body: unknown }[] = []
  await mockJob(page, state, log)

  await page.goto(`/field-jobs/${ID}`)

  // The customer, the site and the instructions.
  await expect(page.getByRole('heading', { name: 'Install 8 cameras', level: 1 })).toBeVisible()
  const site = page.getByRole('region', { name: 'Customer and site' })
  await expect(site).toContainText('Acme Solar · ask for Meera Joshi')
  await expect(site).toContainText('12 MG Road, Lucknow')
  await expect(site.getByRole('link', { name: 'Call 98765 43210' })).toHaveAttribute('href', 'tel:9876543210')
  await expect(site.getByRole('link', { name: 'Open in Maps' })).toHaveAttribute('href', /maps.*12%20MG%20Road/)
  await expect(page.getByRole('region', { name: 'Instructions' })).toContainText('Use the conduit on the east wall.')
  await expectNoSidewaysScroll(page)

  // Until the job is accepted nothing can be recorded on it.
  const checklist = page.getByRole('list', { name: 'Checklist' })
  await expect(checklist.getByLabel('Mount cameras')).toBeDisabled()
  await expect(page.getByText('Accept the job to start ticking things off.')).toBeVisible()
  await expect(page.getByRole('form', { name: 'Add material' })).toHaveCount(0)

  // One step at a time, each with one large button.
  await page.getByRole('button', { name: 'Accept job' }).click()
  await expect(page.getByText('Status: Accepted')).toBeVisible()
  await page.getByRole('button', { name: 'Start travelling' }).click()
  await page.getByRole('button', { name: 'I have arrived' }).click()
  await page.getByRole('button', { name: 'Start work' }).click()
  await expect(page.getByText('Status: In progress')).toBeVisible()
  expect(log.map((entry) => (entry.body as { to: string }).to)).toEqual(['ACCEPTED', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS'])

  // Checklist.
  await checklist.getByLabel('Mount cameras').check()
  await expect(page.getByRole('region', { name: 'Checklist (1 of 2)' })).toBeVisible()
  expect(log.at(-1)).toEqual({ method: 'PUT', path: 'checklist/i1', body: { done: true } })

  // Photos: taken with the phone's camera where there is one.
  const photoInput = page.getByLabel('Add photo')
  await expect(photoInput).toHaveAttribute('capture', 'environment')
  await photoInput.setInputFiles({ name: 'notes.pdf', mimeType: 'application/pdf', buffer: Buffer.from('x') })
  await expect(page.getByText('Choose a JPEG, PNG or WebP picture.')).toBeVisible()
  await page.getByLabel('Photo caption').fill('After installation')
  await photoInput.setInputFiles({ name: 'IMG_0042.jpg', mimeType: 'image/jpeg', buffer: PIXEL })
  await expect(page.getByRole('region', { name: 'Photos (1)' })).toBeVisible()
  await expect(page.getByRole('img', { name: 'After installation' })).toBeVisible()
  expect(log.at(-1)).toMatchObject({ method: 'POST', path: 'photos', body: 'multipart' })

  // Materials.
  const material = page.getByRole('form', { name: 'Add material' })
  await expect(material.getByRole('button', { name: 'Add material' })).toBeDisabled()
  await material.getByLabel('Material').fill('Dome camera')
  await material.getByLabel('Quantity').fill('8')
  await material.getByLabel('Unit').fill('pcs')
  await material.getByRole('button', { name: 'Add material' }).click()
  await expect(page.getByRole('list', { name: 'Materials used' })).toContainText('Dome camera × 8 pcs')
  expect(log.at(-1)).toEqual({ method: 'POST', path: 'materials', body: { name: 'Dome camera', quantity: 8, unit: 'pcs' } })

  // Notes.
  await page.getByLabel('New note').fill('Customer asked for one camera at the gate.')
  await page.getByRole('button', { name: 'Add note' }).click()
  await expect(page.getByRole('list', { name: 'Notes' })).toContainText('Customer asked for one camera at the gate.')

  // The job cannot be completed while the checklist is unfinished.
  await page.getByRole('button', { name: 'Complete job' }).click()
  const completing = page.getByRole('group', { name: 'Complete job' })
  await expect(completing).toContainText('1 checklist item is still open.')
  await expect(page.getByRole('button', { name: 'Complete job' })).toBeDisabled()
  await checklist.getByLabel('Configure the recorder').check()
  await completing.getByLabel('What was done').fill('All eight cameras live.')
  await page.getByRole('button', { name: 'Complete job' }).click()
  await expect(page.getByText('Status: Completed')).toBeVisible()
  expect(log.at(-1)?.body).toEqual({ to: 'COMPLETED', completionNotes: 'All eight cameras live.' })
  await expect(page.getByRole('region', { name: 'Completion notes' })).toContainText('All eight cameras live.')

  // Customer confirmation: a name is needed; the signature is drawn on the screen.
  await page.getByRole('button', { name: 'Get customer confirmation' }).click()
  const confirming = page.getByRole('group', { name: 'Customer confirmation' })
  await expect(page.getByRole('button', { name: 'Confirm work' })).toBeDisabled()
  await confirming.getByLabel('Name of the person confirming').fill('Meera Joshi')
  await confirming.getByLabel('Remarks').fill('Neat work.')
  const pad = confirming.getByRole('img', { name: 'Signature' })
  const box = (await pad.boundingBox())!
  await page.mouse.move(box.x + 30, box.y + 60)
  await page.mouse.down()
  await page.mouse.move(box.x + 120, box.y + 90, { steps: 5 })
  await page.mouse.move(box.x + 200, box.y + 50, { steps: 5 })
  await page.mouse.up()
  await page.getByRole('button', { name: 'Confirm work' }).click()

  await expect(page.getByText('Status: Customer confirmed')).toBeVisible()
  const sent = log.at(-1)?.body as { to: string; signedByName: string; remarks: string; signature: string }
  expect(sent).toMatchObject({ to: 'CUSTOMER_CONFIRMED', signedByName: 'Meera Joshi', remarks: 'Neat work.' })
  expect(sent.signature).toMatch(/^data:image\/png;base64,/)
  const signOff = page.getByRole('region', { name: 'Customer sign-off' })
  await expect(signOff).toContainText('Confirmed by Meera Joshi')
  await expect(signOff).toContainText('Neat work.')
  await expect(signOff.getByRole('img', { name: 'Signature of Meera Joshi' })).toBeVisible()

  // Closed: nothing left to press or change.
  await expect(page.getByRole('button', { name: /Accept job|Complete job|Confirm work|Get customer confirmation/ })).toHaveCount(0)
  await expect(checklist.getByLabel('Mount cameras')).toBeDisabled()
  await expect(page.getByRole('form', { name: 'Add material' })).toHaveCount(0)
  await expect(page.getByRole('form', { name: 'Add note' })).toHaveCount(0)
  await expectNoSidewaysScroll(page)
})

test('a dispatcher schedules a job with its checklist and assigns it', async ({ page }) => {
  await mockSignedIn(page, DISPATCHER)
  await page.route('**/api/v1/field-jobs/technicians', (route) =>
    route.fulfill({ json: [{ id: 't1', name: 'Ravi Kumar' }, { id: 't2', name: 'Asha Verma' }] }),
  )
  await page.route('**/api/v1/field-jobs?*', (route) => route.fulfill({ json: pageOf([summary({ technicianId: null, technicianName: null, status: 'UNASSIGNED' })]) }))
  const state = detail({ job: summary({ technicianId: null, technicianName: null, status: 'UNASSIGNED' }), nextStatus: null })
  const log: { method: string; path: string; body: unknown }[] = []
  await mockJob(page, state, log)
  let posted: Record<string, unknown> | undefined
  await page.route('**/api/v1/field-jobs', (route) => {
    if (route.request().method() === 'POST') {
      posted = route.request().postDataJSON() as Record<string, unknown>
      return route.fulfill({ status: 201, json: state })
    }
    return route.fulfill({ json: pageOf([state.job]) })
  })

  await page.goto('/field-jobs')
  await expect(page.getByRole('list', { name: 'Jobs' }).getByRole('listitem').first()).toContainText('Not assigned yet')
  await page.getByRole('link', { name: 'New job' }).click()

  const form = page.getByRole('form', { name: 'New job' })
  await form.getByRole('button', { name: 'Create job' }).click()
  await expect(form.getByText('Say what the job is')).toBeVisible()
  await expect(form.getByText('Choose when the job starts')).toBeVisible()
  expect(posted).toBeUndefined()

  await form.getByLabel('Job', { exact: true }).fill('Roof survey')
  await form.getByLabel('Type').selectOption('Site visit')
  await form.getByLabel('Customer', { exact: true }).fill('Acme Solar')
  await form.getByLabel('Site address').fill('12 MG Road, Lucknow')
  await form.getByLabel('Contact phone').fill('98765 43210')
  await form.getByLabel('Starts').fill('2026-10-12T10:30')
  await form.getByLabel('Ends (optional)').fill('2026-10-12T09:00')
  await form.getByLabel('Checklist').fill('Measure usable area\n\n  Photograph the meter ')
  await form.getByRole('button', { name: 'Create job' }).click()
  await expect(form.getByText('Must not be before the start')).toBeVisible()
  await form.getByLabel('Ends (optional)').fill('')
  await form.getByRole('button', { name: 'Create job' }).click()

  expect(posted).toEqual({
    jobType: 'SITE_VISIT', title: 'Roof survey', instructions: null, customerName: 'Acme Solar', siteAddress: '12 MG Road, Lucknow',
    siteContactName: null, siteContactPhone: '98765 43210',
    scheduledStart: '2026-10-12T05:00:00.000Z', // 10:30 in India, sent as the instant
    scheduledEnd: null, technicianId: null, checklist: ['Measure usable area', 'Photograph the meter'],
  })
  await expect(page).toHaveURL(new RegExp(`/field-jobs/${ID}$`))

  // On the job: assign it, and later give it to someone else or cancel it.
  await expect(page.getByText('Not assigned to anyone yet')).toBeVisible()
  const assignment = page.getByRole('region', { name: 'Assignment' })
  await expect(assignment.getByRole('button', { name: 'Assign' })).toBeDisabled()
  await assignment.getByLabel('Technician').selectOption('Asha Verma')
  await assignment.getByRole('button', { name: 'Assign' }).click()
  await expect(page.getByText('Assigned to Asha Verma')).toBeVisible()
  expect(log.at(-1)).toEqual({ method: 'POST', path: 'assign', body: { technicianId: 't2' } })
  // A dispatcher may also take a step for the technician.
  await expect(page.getByRole('button', { name: 'Accept job' })).toBeVisible()

  await page.getByRole('button', { name: 'Cancel this job' }).click()
  await expect(page.getByText('Status: Cancelled')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Cancel this job' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0)
})

test('someone who may only view jobs sees everything on a job and changes nothing', async ({ page }) => {
  await mockSignedIn(page, ['FIELD_JOB_VIEW'])
  const state = detail({
    job: summary({ status: 'IN_PROGRESS' }), nextStatus: 'COMPLETED', canAct: false,
    materials: [{ id: 'm1', name: 'Dome camera', quantity: 8, unit: 'pcs', notes: null, recordedByName: 'Ravi Kumar' }],
  })
  await mockJob(page, state)

  await page.goto(`/field-jobs/${ID}`)

  await expect(page.getByText('Status: In progress')).toBeVisible()
  await expect(page.getByRole('list', { name: 'Materials used' })).toContainText('Dome camera × 8 pcs')
  await expect(page.getByRole('list', { name: 'Checklist' }).getByLabel('Mount cameras')).toBeDisabled()
  await expect(page.getByRole('button', { name: /Complete job|Remove|Cancel this job|Add/ })).toHaveCount(0)
  await expect(page.getByLabel('Add photo')).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0)
  await page.goto('/my-jobs')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
  await page.goto('/field-jobs/new')
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})

test('field jobs are hidden from users without any field permission', async ({ page }) => {
  await mockSignedIn(page, ['CUSTOMER_VIEW'])
  await page.setViewportSize({ width: 1280, height: 800 })

  await page.goto('/')
  await expect(page.getByRole('link', { name: 'My jobs' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Field jobs' })).toHaveCount(0)
  await page.goto(`/field-jobs/${ID}`)
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible()
})
