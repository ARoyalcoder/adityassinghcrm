import { expect, test, type Page } from '@playwright/test'
import { mockSignedIn, mockSystemInfo, mockVerticals } from './support'

const ADMIN = ['CATALOG_VIEW', 'CATALOG_CREATE', 'CATALOG_UPDATE', 'CATALOG_DELETE']
const SOLAR_ID = '22222222-2222-2222-2222-222222222222'
const CCTV_ID = '33333333-3333-3333-3333-333333333333'

function service(id: string, name: string, vertical: { code: string; name: string }) {
  return {
    id,
    code: name.toUpperCase().replace(/[^A-Z]+/g, '_'),
    name,
    description: null,
    displayOrder: 0,
    active: true,
    billingType: 'QUOTE_BASED',
    unitLabel: null,
    basePrice: null,
    requiresSiteVisit: false,
    estimatedDurationDays: null,
    category: { id: '11111111-1111-1111-1111-111111111111', code: 'GENERAL', name: 'General' },
    vertical,
    version: 0,
  }
}

type Definition = Record<string, unknown> & { id: string; key: string; label: string; type: string; active: boolean }
type Item = { field: Definition; version: number }

let nextId = 1
/** A field as the server returns it. Which fields exist is decided here, in test data, never in the app. */
function field(key: string, type: string, label: string, extra: Record<string, unknown> = {}): Item {
  return {
    field: {
      id: `f0000000-0000-0000-0000-${String(nextId++).padStart(12, '0')}`,
      key, label, type, helpText: null, required: false, options: null, minValue: null, maxValue: null,
      maxLength: null, unit: null, allowedFileTypes: null, maxFileSizeMb: null, displayOrder: nextId * 10, active: true,
      ...extra,
    },
    version: 0,
  }
}

function options(...labels: string[]) {
  return labels.map((label) => ({ label, value: label.toUpperCase().replace(/[^A-Z0-9-]+/g, '_') }))
}

function base(id: string) {
  return `**/api/v1/catalog/services/${id}`
}

async function mockService(page: Page, id: string, name: string, vertical: { code: string; name: string }, fields: Item[]) {
  await page.route(base(id), (route) => route.fulfill({ json: service(id, name, vertical) }))
  await page.route(`${base(id)}/custom-fields`, (route) => route.fulfill({ json: fields }))
}

test.beforeEach(async ({ page }) => {
  await mockSystemInfo(page)
  await mockVerticals(page)
})

test('each service gets the form its own fields describe', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await mockService(page, SOLAR_ID, 'Rooftop solar', { code: 'SOLAR', name: 'Solar' }, [
    field('monthly_electricity_bill', 'CURRENCY', 'Monthly electricity bill', { required: true }),
    field('roof_area', 'NUMBER', 'Roof area', { unit: 'sq ft', minValue: 100 }),
    field('required_capacity', 'NUMBER', 'Required capacity', { unit: 'kW' }),
    field('site_availability', 'BOOLEAN', 'Site available for survey'),
  ])
  await mockService(page, CCTV_ID, 'CCTV installation', { code: 'CCTV_SECURITY', name: 'CCTV & Security' }, [
    field('camera_count', 'NUMBER', 'Camera count', { required: true }),
    field('site_type', 'SELECT', 'Site type', { options: options('Home', 'Office', 'Shop') }),
    field('placement', 'SELECT', 'Indoor / outdoor', { options: options('Indoor', 'Outdoor', 'Both') }),
    field('storage_requirement', 'NUMBER', 'Storage requirement', { unit: 'days', helpText: 'How long recordings are kept' }),
    // A question nobody has thought of yet, of a type this version of the screen has never seen.
    field('guard_dog_name', 'HOLOGRAM', 'Name of the guard dog'),
  ])

  await page.goto(`/admin/services/${SOLAR_ID}/fields`)
  await expect(page.getByRole('heading', { name: 'Lead fields: Rooftop solar', level: 1 })).toBeVisible()
  const preview = page.getByRole('form', { name: 'Preview' })
  await expect(preview.locator('[data-field-type]')).toHaveCount(4)
  await expect(preview.getByLabel('Monthly electricity bill')).toBeVisible()
  await expect(preview.locator('[data-field-type=CURRENCY]')).toContainText('₹')
  await expect(preview.locator('[data-field-type=NUMBER]').first()).toContainText('sq ft')
  await expect(preview.getByRole('radiogroup', { name: 'Site available for survey' }).getByRole('radio')).toHaveCount(3)
  await expect(preview.getByLabel('Camera count')).toHaveCount(0)

  // Another service, other data: another form, drawn by the same component.
  await page.goto(`/admin/services/${CCTV_ID}/fields`)
  await expect(page.getByRole('heading', { name: 'Lead fields: CCTV installation', level: 1 })).toBeVisible()
  await expect(preview.locator('[data-field-type]')).toHaveCount(5)
  await expect(preview.getByLabel('Site type').getByRole('option')).toHaveText(['Not specified', 'Home', 'Office', 'Shop'])
  await expect(preview.getByLabel('Indoor / outdoor').getByRole('option')).toHaveText(['Not specified', 'Indoor', 'Outdoor', 'Both'])
  await expect(preview.locator('[data-field-type=NUMBER]').nth(1)).toContainText('days')
  await expect(preview).toContainText('How long recordings are kept')
  await expect(preview.getByLabel('Name of the guard dog')).toBeEditable() // unknown type: a text box, not nothing
  await expect(preview.getByLabel('Monthly electricity bill')).toHaveCount(0)

  // The list describes each field from its definition too.
  const list = page.getByRole('list', { name: 'Fields' })
  await expect(list.getByRole('listitem').filter({ hasText: 'Site type' })).toContainText('Home, Office, Shop')
  await expect(list.getByRole('listitem').filter({ hasText: 'Camera count' })).toContainText('Required')
  await expect(list.getByRole('listitem').filter({ hasText: 'camera_count' })).toContainText('Number')
})

test('every field type has its input, is checked in the browser, and is sent as the right kind of value', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  const plan = field('floor_plan', 'FILE', 'Floor plan', { allowedFileTypes: ['pdf'], maxFileSizeMb: 1 })
  await mockService(page, SOLAR_ID, 'Everything', { code: 'INTERIOR_DESIGN', name: 'Interior Design' }, [
    field('client_name', 'TEXT', 'Client name', { required: true, maxLength: 10 }),
    field('camera_count', 'NUMBER', 'Camera count', { minValue: 1, maxValue: 64 }),
    field('monthly_budget', 'CURRENCY', 'Monthly budget'),
    field('property_type', 'SELECT', 'Property type', { required: true, options: options('Apartment', 'Villa') }),
    field('rooms', 'MULTI_SELECT', 'Rooms', { options: options('Kitchen', 'Bedroom', 'Living room') }),
    field('furnished', 'BOOLEAN', 'Already furnished', { required: true }),
    field('possession_date', 'DATE', 'Possession date'),
    field('design_requirement', 'TEXTAREA', 'Design requirement'),
    plan,
  ])
  let uploads = 0
  await page.route(`${base(SOLAR_ID)}/custom-fields/floor_plan/files`, (route) => {
    uploads++
    expect(route.request().headers()['content-type']).toContain('multipart/form-data')
    return route.fulfill({
      status: 201,
      json: { storageKey: `lead-fields/${plan.field.id}/abc.pdf`, fileName: 'plan.pdf', contentType: 'application/pdf', sizeBytes: 2048 },
    })
  })
  let sent: Record<string, unknown> | undefined
  let serverSays: 'ok' | 'no' = 'ok'
  await page.route(`${base(SOLAR_ID)}/custom-fields/validate`, (route) => {
    sent = (route.request().postDataJSON() as { values: Record<string, unknown> }).values
    if (serverSays === 'no') {
      return route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        json: {
          status: 400, code: 'VALIDATION_FAILED', detail: 'One or more values are invalid',
          errors: [{ field: 'camera_count', message: 'must be at most 32' }],
        },
      })
    }
    return route.fulfill({ json: { values: sent } })
  })

  await page.goto(`/admin/services/${SOLAR_ID}/fields`)
  const preview = page.getByRole('form', { name: 'Preview' })
  const check = preview.getByRole('button', { name: 'Check answers' })

  // Required fields, whatever their type, are caught before anything is sent.
  await check.click()
  await expect(preview.locator('[data-field-type=TEXT]')).toContainText('This is required')
  await expect(preview.locator('[data-field-type=SELECT]')).toContainText('This is required')
  await expect(preview.locator('[data-field-type=BOOLEAN]')).toContainText('This is required')
  await expect(preview.getByText('This is required')).toHaveCount(3)
  expect(sent).toBeUndefined()

  // Each type's own rules, taken from its definition.
  await preview.getByLabel('Client name').fill('A name that is too long')
  await preview.getByLabel('Camera count').fill('lots')
  await preview.getByLabel('Monthly budget').fill('10.123')
  await check.click()
  await expect(preview.locator('[data-field-type=TEXT]')).toContainText('At most 10 characters')
  await expect(preview.locator('[data-field-type=NUMBER]')).toContainText('Enter a number')
  await expect(preview.locator('[data-field-type=CURRENCY]')).toContainText('At most 2 decimal places')
  await preview.getByLabel('Camera count').fill('0')
  await preview.getByLabel('Monthly budget').fill('-5')
  await check.click()
  await expect(preview.locator('[data-field-type=NUMBER]')).toContainText('Must be at least 1')
  await expect(preview.locator('[data-field-type=CURRENCY]')).toContainText('Must be at least 0')
  expect(sent).toBeUndefined()

  // A file of the wrong type is refused without being uploaded; the right one is uploaded at once.
  const file = preview.getByLabel('Floor plan')
  await expect(file).toHaveAttribute('accept', '.pdf')
  await file.setInputFiles({ name: 'plan.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('x') })
  await expect(preview.locator('[data-field-type=FILE]')).toContainText('Must be a file of type pdf')
  expect(uploads).toBe(0)
  await file.setInputFiles({ name: 'plan.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7') })
  await expect(preview.locator('[data-field-type=FILE]')).toContainText('plan.pdf (2 KB)')
  expect(uploads).toBe(1)

  await preview.getByLabel('Client name').fill(' Asha ')
  await preview.getByLabel('Camera count').fill('8')
  await preview.getByLabel('Monthly budget').fill('2500.50')
  await preview.getByLabel('Property type').selectOption('Villa')
  await preview.getByRole('group', { name: 'Rooms' }).getByLabel('Kitchen').check()
  await preview.getByRole('group', { name: 'Rooms' }).getByLabel('Living room').check()
  await preview.getByRole('radiogroup', { name: 'Already furnished' }).getByLabel('No').check()
  await preview.getByLabel('Possession date').fill('2026-11-05')
  await check.click()

  await expect(preview.getByRole('status')).toContainText('These answers are valid.')
  // Numbers as numbers, yes/no as a boolean, a list for the multiple choice, the file as its reference,
  // and nothing at all for the question left unanswered.
  expect(sent).toEqual({
    client_name: 'Asha',
    camera_count: 8,
    monthly_budget: 2500.5,
    property_type: 'VILLA',
    rooms: ['KITCHEN', 'LIVING_ROOM'],
    furnished: false,
    possession_date: '2026-11-05',
    floor_plan: { storageKey: `lead-fields/${plan.field.id}/abc.pdf`, fileName: 'plan.pdf', contentType: 'application/pdf', sizeBytes: 2048 },
  })

  // The server has the last word, and its verdict is shown on the field it is about.
  serverSays = 'no'
  await check.click()
  await expect(preview.locator('[data-field-type=NUMBER]')).toContainText('Must be at most 32')
  await expect(preview.getByRole('status')).toHaveCount(0)
})

test('an administrator adds, edits, switches off and removes fields', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await page.route(base(SOLAR_ID), (route) => route.fulfill({ json: service(SOLAR_ID, 'Rooftop solar', { code: 'SOLAR', name: 'Solar' }) }))
  const fields: Item[] = []
  const requests: { method: string; path: string; body: Record<string, unknown> | null }[] = []
  await page.route(`${base(SOLAR_ID)}/custom-fields`, (route) => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({ json: fields })
    }
    const body = route.request().postDataJSON() as Record<string, unknown>
    requests.push({ method: 'POST', path: '', body })
    // StrictMode never repeats a submit, but a retry must not add the field twice.
    let created = fields.find((item) => item.field.key === body.key)
    if (!created) {
      // Like the real server, a field created without a position is put last.
      created = field(body.key as string, body.type as string, body.label as string, {
        ...body,
        displayOrder: body.displayOrder ?? (fields.length + 1) * 10,
      })
      fields.push(created)
    }
    return route.fulfill({ status: 201, json: created })
  })
  await page.route(`${base(SOLAR_ID)}/custom-fields/**`, (route) => {
    const method = route.request().method()
    const path = new URL(route.request().url()).pathname.split('/custom-fields/')[1]
    const [fieldId, action] = path.split('/')
    const index = fields.findIndex((item) => item.field.id === fieldId)
    const body = method === 'PUT' ? (route.request().postDataJSON() as Record<string, unknown>) : null
    requests.push({ method, path: action ?? '', body })
    if (method === 'DELETE') {
      fields.splice(index, 1)
      return route.fulfill({ status: 204 })
    }
    if (method === 'PUT' && body) {
      const { version, ...changes } = body
      fields[index] = { field: { ...fields[index].field, ...changes }, version: (version as number) + 1 }
    } else {
      fields[index] = { ...fields[index], field: { ...fields[index].field, active: action === 'activate' } }
    }
    return route.fulfill({ json: fields[index] })
  })

  await page.goto(`/admin/services/${SOLAR_ID}/fields`)
  await expect(page.getByText('This service asks nothing extra yet. Add the first field to start.')).toBeVisible()
  await expect(page.getByRole('form', { name: 'Preview' })).toHaveCount(0)

  // A number field: the key follows the label, and only a number's settings are offered.
  await page.getByRole('button', { name: 'Add field' }).click()
  const editor = page.getByRole('form', { name: 'New field' })
  await editor.getByLabel('Label', { exact: true }).fill('Roof area')
  await expect(editor.getByLabel('Key')).toHaveValue('roof_area')
  await expect(editor.getByLabel('Type').getByRole('option')).toHaveText([
    'Text', 'Number', 'Currency', 'Select', 'Multi-select', 'Yes / No', 'Date', 'Long text', 'File',
  ])
  await expect(editor.getByLabel('Maximum length')).toBeVisible()
  await editor.getByLabel('Type').selectOption('Number')
  await expect(editor.getByLabel('Maximum length')).toHaveCount(0)
  await expect(editor.getByLabel('Options')).toHaveCount(0)
  await editor.getByLabel('Minimum').fill('100')
  await editor.getByLabel('Unit').fill('sq ft')
  await editor.getByLabel('Required').check()
  await editor.getByRole('button', { name: 'Add field' }).click()

  const list = page.getByRole('list', { name: 'Fields' })
  await expect(list.getByRole('listitem')).toHaveCount(1)
  await expect(list.getByRole('listitem').first()).toContainText('at least 100 · in sq ft')
  expect(requests.at(-1)?.body).toEqual({
    key: 'roof_area', type: 'NUMBER', label: 'Roof area', helpText: null, required: true, options: null,
    minValue: 100, maxValue: null, maxLength: null, unit: 'sq ft', allowedFileTypes: null, maxFileSizeMb: null,
    displayOrder: null,
  })
  // The preview is drawn from what was just saved.
  const preview = page.getByRole('form', { name: 'Preview' })
  await expect(preview.getByLabel('Roof area')).toBeVisible()
  await expect(preview).toContainText('sq ft')

  // A select field needs options.
  await page.getByRole('button', { name: 'Add field' }).click()
  await editor.getByLabel('Label', { exact: true }).fill('Site type')
  await editor.getByLabel('Type').selectOption('Select')
  await editor.getByRole('button', { name: 'Add field' }).click()
  await expect(editor.getByText('Add at least one option')).toBeVisible()
  await editor.getByLabel('Options').fill('Home\n\n  Shop floor  ')
  await editor.getByLabel('Key').fill('site')
  await editor.getByLabel('Label', { exact: true }).fill('Type of site') // the key was typed by hand: it stays
  await expect(editor.getByLabel('Key')).toHaveValue('site')
  await editor.getByRole('button', { name: 'Add field' }).click()
  await expect(list.getByRole('listitem')).toHaveCount(2)
  expect(requests.at(-1)?.body).toMatchObject({
    key: 'site', type: 'SELECT', label: 'Type of site',
    options: [{ label: 'Home', value: 'HOME' }, { label: 'Shop floor', value: 'SHOP_FLOOR' }],
    minValue: null, unit: null,
  })
  await expect(preview.getByLabel('Type of site').getByRole('option')).toHaveText(['Not specified', 'Home', 'Shop floor'])

  // Editing: the key and type are fixed, and an option that is kept keeps its stored value.
  await list.getByRole('button', { name: 'Edit Type of site' }).click()
  const edit = page.getByRole('form', { name: 'Edit Type of site' })
  await expect(edit.getByLabel('Key')).toBeDisabled()
  await expect(edit.getByLabel('Type')).toBeDisabled()
  await expect(edit.getByLabel('Options')).toHaveValue('Home\nShop floor')
  await edit.getByLabel('Options').fill('Home\nShop floor\nWarehouse')
  await edit.getByRole('button', { name: 'Save field' }).click()
  await expect(preview.getByLabel('Type of site').getByRole('option')).toHaveText(['Not specified', 'Home', 'Shop floor', 'Warehouse'])
  const put = requests.at(-1)
  expect(put?.method).toBe('PUT')
  expect(put?.body).toMatchObject({
    version: 0,
    options: [{ label: 'Home', value: 'HOME' }, { label: 'Shop floor', value: 'SHOP_FLOOR' }, { label: 'Warehouse', value: 'WAREHOUSE' }],
  })
  expect(put?.body).not.toHaveProperty('key')
  expect(put?.body).not.toHaveProperty('type')

  // Switched off: still listed, no longer asked.
  await list.getByRole('button', { name: 'Switch off Roof area' }).click()
  await expect(list.getByRole('listitem').filter({ hasText: 'Roof area' })).toContainText('Inactive')
  await expect(preview.getByLabel('Roof area')).toHaveCount(0)
  await expect(preview.getByLabel('Type of site')).toBeVisible()
  expect(requests.at(-1)).toMatchObject({ method: 'POST', path: 'deactivate' })

  await list.getByRole('button', { name: 'Remove Type of site' }).click()
  await expect(list.getByRole('listitem')).toHaveCount(1)
  await expect(page.getByRole('form', { name: 'Preview' })).toHaveCount(0) // nothing active is left to ask
})

test('the server\'s objection to a field definition is shown on the form', async ({ page }) => {
  await mockSignedIn(page, ADMIN)
  await mockService(page, SOLAR_ID, 'Rooftop solar', { code: 'SOLAR', name: 'Solar' }, [])
  await page.route(`${base(SOLAR_ID)}/custom-fields`, (route) =>
    route.request().method() === 'POST'
      ? route.fulfill({
          status: 409,
          contentType: 'application/problem+json',
          json: { status: 409, code: 'CONFLICT', detail: "This service already has a field with the key 'roof_area'" },
        })
      : route.fulfill({ json: [] }),
  )

  await page.goto(`/admin/services/${SOLAR_ID}/fields`)
  await page.getByRole('button', { name: 'Add field' }).click()
  const editor = page.getByRole('form', { name: 'New field' })
  await editor.getByLabel('Label', { exact: true }).fill('Roof area')
  await editor.getByLabel('Key').fill('Roof Area')
  await editor.getByRole('button', { name: 'Add field' }).click()
  await expect(editor.getByText('Lower-case letters, digits and underscores, starting with a letter')).toBeVisible()
  await editor.getByLabel('Key').fill('roof_area')
  await editor.getByRole('button', { name: 'Add field' }).click()
  await expect(editor.getByRole('alert')).toContainText("already has a field with the key 'roof_area'")
})

test('someone who may only view the catalog sees the fields and the preview, but no controls', async ({ page }) => {
  await mockSignedIn(page, ['CATALOG_VIEW'])
  await mockService(page, CCTV_ID, 'CCTV installation', { code: 'CCTV_SECURITY', name: 'CCTV & Security' }, [
    field('camera_count', 'NUMBER', 'Camera count'),
    field('site_photo', 'FILE', 'Site photo'),
  ])
  let uploads = 0
  await page.route(`${base(CCTV_ID)}/custom-fields/site_photo/files`, (route) => {
    uploads++
    return route.fulfill({ status: 403 })
  })

  await page.goto(`/admin/services/${CCTV_ID}/fields`)

  await expect(page.getByRole('list', { name: 'Fields' }).getByRole('listitem')).toHaveCount(2)
  await expect(page.getByRole('button', { name: 'Add field' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /^(Edit|Remove|Switch off)/ })).toHaveCount(0)
  const preview = page.getByRole('form', { name: 'Preview' })
  await expect(preview.getByLabel('Camera count')).toBeEditable()
  // Without the permission to upload, a chosen file is refused in the browser.
  await preview.getByLabel('Site photo').setInputFiles({ name: 'site.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('x') })
  await expect(preview.getByText('Files cannot be uploaded here.')).toBeVisible()
  expect(uploads).toBe(0)
})

test('the services list links to each service\'s lead fields', async ({ page }) => {
  await mockSignedIn(page, ['CATALOG_VIEW'])
  await page.route('**/api/v1/catalog/categories**', (route) =>
    route.fulfill({ json: { items: [], page: 0, size: 100, totalItems: 0, totalPages: 1 } }),
  )
  await page.route('**/api/v1/catalog/services**', (route) =>
    route.fulfill({
      json: { items: [service(SOLAR_ID, 'Rooftop solar', { code: 'SOLAR', name: 'Solar' })], page: 0, size: 20, totalItems: 1, totalPages: 1 },
    }),
  )

  await page.goto('/admin/services')

  await expect(page.getByRole('link', { name: 'Lead fields of Rooftop solar' })).toHaveAttribute(
    'href',
    `/admin/services/${SOLAR_ID}/fields`,
  )
})
