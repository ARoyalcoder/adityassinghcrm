import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useForm, useWatch } from 'react-hook-form'
import { useParams } from 'react-router-dom'
import { z } from 'zod'
import { DynamicFields } from '@/components/dynamic-fields/DynamicFields'
import { FIELD_TYPES, fieldTypeLabel, type FieldDefinition } from '@/components/dynamic-fields/model'
import {
  applyAnswerErrors,
  buildAnswersSchema,
  initialAnswers,
  toAnswers,
  type FormAnswers,
} from '@/components/dynamic-fields/values'
import { ErrorState } from '@/components/feedback/ErrorState'
import { StatusBadge } from '@/components/feedback/StatusBadge'
import { FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  createCustomField,
  customFieldsKey,
  customFieldsQuery,
  deleteCustomField,
  serviceQuery,
  setCustomFieldActive,
  updateCustomField,
  uploadFieldFile,
  validateAnswers,
  type CustomField,
  type CustomFieldInput,
} from '@/features/catalog/api'
import { toNumberOrNull } from '@/features/catalog/form-fields'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const HAS_OPTIONS = ['SELECT', 'MULTI_SELECT']
const HAS_RANGE = ['NUMBER', 'CURRENCY']
const HAS_LENGTH = ['TEXT', 'TEXTAREA']

const optionalNumber = z
  .string()
  .trim()
  .refine((value) => value === '' || /^-?\d+(\.\d{1,4})?$/.test(value), 'Enter a number')
const optionalCount = (min: number, max: number) =>
  z
    .string()
    .trim()
    .refine(
      (value) => value === '' || (/^\d+$/.test(value) && Number(value) >= min && Number(value) <= max),
      `Enter a whole number from ${min} to ${max}`,
    )

const schema = z
  .object({
    label: z.string().trim().min(1, 'Enter a label').max(120, 'At most 120 characters'),
    key: z
      .string()
      .trim()
      .min(1, 'Enter a key')
      .max(60, 'At most 60 characters')
      .regex(/^[a-z][a-z0-9_]*$/, 'Lower-case letters, digits and underscores, starting with a letter'),
    type: z.string().min(1, 'Choose a type'),
    helpText: z.string().trim().max(300, 'At most 300 characters'),
    required: z.boolean(),
    options: z.string(),
    minValue: optionalNumber,
    maxValue: optionalNumber,
    maxLength: optionalCount(1, 10000),
    unit: z.string().trim().max(30, 'At most 30 characters'),
    allowedFileTypes: z
      .string()
      .trim()
      .refine((value) => value === '' || /^[A-Za-z0-9]{1,10}(\s*,\s*[A-Za-z0-9]{1,10})*$/.test(value), 'Extensions separated by commas, e.g. pdf, jpg'),
    maxFileSizeMb: optionalCount(1, 25),
    displayOrder: optionalCount(0, 9999),
  })
  .superRefine((values, context) => {
    if (HAS_OPTIONS.includes(values.type) && optionLines(values.options).length === 0) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['options'], message: 'Add at least one option' })
    }
    if (values.minValue !== '' && values.maxValue !== '' && Number(values.minValue) > Number(values.maxValue)) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['maxValue'], message: 'Must not be less than the minimum' })
    }
  })
type Values = z.infer<typeof schema>

function optionLines(text: string) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '')
}

/** What is stored for an option: derived from its label once, then kept even if the label is reworded slightly. */
function optionValue(label: string, position: number) {
  const value = label
    .toUpperCase()
    .replace(/[^A-Z0-9-]+/g, '_')
    .replace(/^_+|_+$/g, '')
  return (value || `OPTION_${position + 1}`).slice(0, 60)
}

function keyFromLabel(label: string) {
  return label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^[^a-z]+|_+$/g, '')
    .slice(0, 60)
}

/** Only the settings that belong to the type are sent; the rest are null, whatever was typed earlier. */
function toInput(values: Values, existing?: FieldDefinition): CustomFieldInput {
  const type = values.type
  const known = new Map((existing?.options ?? []).map((option) => [option.label, option.value]))
  const fileTypes = values.allowedFileTypes
    .split(',')
    .map((part) => part.trim().toLowerCase())
    .filter((part) => part !== '')
  return {
    label: values.label,
    helpText: blankToNull(values.helpText),
    required: values.required,
    options: HAS_OPTIONS.includes(type)
      ? optionLines(values.options).map((label, index) => ({ label, value: known.get(label) ?? optionValue(label, index) }))
      : null,
    minValue: HAS_RANGE.includes(type) ? toNumberOrNull(values.minValue) : null,
    maxValue: HAS_RANGE.includes(type) ? toNumberOrNull(values.maxValue) : null,
    maxLength: HAS_LENGTH.includes(type) ? toNumberOrNull(values.maxLength) : null,
    unit: type === 'NUMBER' ? blankToNull(values.unit) : null,
    allowedFileTypes: type === 'FILE' && fileTypes.length > 0 ? fileTypes : null,
    maxFileSizeMb: type === 'FILE' ? toNumberOrNull(values.maxFileSizeMb) : null,
    displayOrder: toNumberOrNull(values.displayOrder),
  }
}

/** `'new'` while adding, a field while editing it, `null` when just listing. */
type Editing = CustomField | 'new' | null

/**
 * The lead fields of one service (`/admin/services/:id/fields`): what to ask when someone enquires
 * about it. Administrators define the fields here; the preview below is drawn from those
 * definitions by the same component a lead form uses.
 */
export function ServiceFieldsPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const canEdit = useHasPermission('CATALOG_UPDATE')
  const service = useQuery(serviceQuery(id))
  const fields = useQuery(customFieldsQuery(id))
  const [editing, setEditing] = useState<Editing>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: customFieldsKey(id) })
  const toggle = useMutation({
    mutationFn: (field: CustomField) => setCustomFieldActive(id, field.field.id, !field.field.active),
    onSuccess: refresh,
  })
  const remove = useMutation({ mutationFn: (fieldId: string) => deleteCustomField(id, fieldId), onSuccess: refresh })

  if (!service.data) {
    return (
      <div className="grid gap-6">
        <PageHeader title="Lead fields" back={{ to: '/admin/services', label: 'Services' }} />
        <RecordLoadState
          noun="service"
          isPending={service.isPending}
          error={service.error}
          onRetry={() => void service.refetch()}
        />
      </div>
    )
  }

  const active = (fields.data ?? []).filter((field) => field.field.active).map((field) => field.field)

  return (
    <div className="grid gap-6">
      <PageHeader
        title={`Lead fields: ${service.data.name}`}
        description={`${service.data.vertical.name} · ${service.data.category.name}. What to ask when someone enquires about this service.`}
        back={{ to: '/admin/services', label: 'Services' }}
        action={
          canEdit && editing === null ? (
            <Button onClick={() => setEditing('new')}>
              <Plus aria-hidden="true" />
              Add field
            </Button>
          ) : null
        }
      />

      {editing !== null ? (
        <FieldEditor
          key={editing === 'new' ? 'new' : editing.field.id}
          serviceId={id}
          existing={editing === 'new' ? undefined : editing}
          onDone={() => {
            setEditing(null)
            void refresh()
          }}
          onCancel={() => setEditing(null)}
        />
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Fields</CardTitle>
          <CardDescription>In the order they are asked. A field that is switched off is not asked.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          {toggle.error || remove.error ? (
            <ErrorState title="The field could not be changed" error={toggle.error ?? remove.error} />
          ) : null}
          {fields.isPending ? (
            <Skeleton className="h-16 w-full" aria-busy="true" aria-label="Loading fields" />
          ) : fields.isError ? (
            <ErrorState title="Could not load the fields" error={fields.error} onRetry={() => void fields.refetch()} />
          ) : fields.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              This service asks nothing extra yet.{canEdit ? ' Add the first field to start.' : ''}
            </p>
          ) : (
            <ul className="grid gap-3" aria-label="Fields">
              {fields.data.map((item) => (
                <li key={item.field.id} className="flex flex-wrap items-start justify-between gap-3 rounded-md border p-3 text-sm">
                  <div className="grid gap-1">
                    <p className="flex flex-wrap items-center gap-2 font-medium">
                      {item.field.label}
                      <Badge variant="secondary">{fieldTypeLabel(item.field.type)}</Badge>
                      {item.field.required ? <Badge variant="outline">Required</Badge> : null}
                      <StatusBadge active={item.field.active} />
                    </p>
                    <p className="font-mono text-xs text-muted-foreground">{item.field.key}</p>
                    <p className="text-muted-foreground">{describe(item.field)}</p>
                  </div>
                  {canEdit ? (
                    <div className="flex gap-2">
                      <Button variant="outline" size="sm" aria-label={`Edit ${item.field.label}`} onClick={() => setEditing(item)}>
                        Edit
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={toggle.isPending}
                        aria-label={`${item.field.active ? 'Switch off' : 'Switch on'} ${item.field.label}`}
                        onClick={() => toggle.mutate(item)}
                      >
                        {item.field.active ? 'Switch off' : 'Switch on'}
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={remove.isPending}
                        aria-label={`Remove ${item.field.label}`}
                        onClick={() => remove.mutate(item.field.id)}
                      >
                        Remove
                      </Button>
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {active.length > 0 ? (
        // Rebuilt whenever a definition changes, so the preview always shows the current form.
        <Preview
          key={(fields.data ?? []).map((item) => `${item.field.id}:${item.version}:${item.field.active}`).join('|')}
          serviceId={id}
          definitions={active}
          canUpload={canEdit}
        />
      ) : null}
    </div>
  )
}

/** The field's settings in a sentence, whatever its type. */
function describe(field: FieldDefinition) {
  const parts: string[] = []
  if (field.options?.length) {
    parts.push(field.options.map((option) => option.label).join(', '))
  }
  if (field.minValue !== null && field.minValue !== undefined) {
    parts.push(`at least ${field.minValue}`)
  }
  if (field.maxValue !== null && field.maxValue !== undefined) {
    parts.push(`at most ${field.maxValue}`)
  }
  if (field.unit) {
    parts.push(`in ${field.unit}`)
  }
  if (field.maxLength) {
    parts.push(`up to ${field.maxLength} characters`)
  }
  if (field.allowedFileTypes?.length) {
    parts.push(field.allowedFileTypes.join(', '))
  }
  if (field.maxFileSizeMb) {
    parts.push(`up to ${field.maxFileSizeMb} MB`)
  }
  if (field.helpText) {
    parts.push(field.helpText)
  }
  return parts.join(' · ') || 'No extra settings'
}

type FieldEditorProps = { serviceId: string; existing?: CustomField; onDone: () => void; onCancel: () => void }

function FieldEditor({ serviceId, existing, onDone, onCancel }: FieldEditorProps) {
  const field = existing?.field
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      label: field?.label ?? '',
      key: field?.key ?? '',
      type: field?.type ?? 'TEXT',
      helpText: field?.helpText ?? '',
      required: field?.required ?? false,
      options: (field?.options ?? []).map((option) => option.label).join('\n'),
      minValue: field?.minValue?.toString() ?? '',
      maxValue: field?.maxValue?.toString() ?? '',
      maxLength: field?.maxLength?.toString() ?? '',
      unit: field?.unit ?? '',
      allowedFileTypes: (field?.allowedFileTypes ?? []).join(', '),
      maxFileSizeMb: field?.maxFileSizeMb?.toString() ?? '',
      displayOrder: field?.displayOrder.toString() ?? '',
    },
  })
  const type = useWatch({ control: form.control, name: 'type' })
  // Until the key has been typed by hand, it follows the label.
  const [keyTyped, setKeyTyped] = useState(false)

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      if (existing) {
        await updateCustomField(serviceId, existing.field.id, { ...toInput(values, existing.field), version: existing.version })
      } else {
        await createCustomField(serviceId, { ...toInput(values), key: values.key, type: values.type })
      }
      onDone()
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  const text = (name: 'unit' | 'minValue' | 'maxValue' | 'maxLength' | 'maxFileSizeMb' | 'allowedFileTypes' | 'displayOrder' | 'helpText', label: string, hint?: string) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field: input }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input {...input} />
          </FormControl>
          {hint ? <FormDescription>{hint}</FormDescription> : null}
          <FormMessage />
        </FormItem>
      )}
    />
  )

  return (
    <Card>
      <CardHeader>
        <CardTitle>{field ? `Edit ${field.label}` : 'New field'}</CardTitle>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" aria-label={field ? `Edit ${field.label}` : 'New field'} noValidate>
            <FormError message={formError} />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="label"
                render={({ field: input }) => (
                  <FormItem>
                    <FormLabel>Label</FormLabel>
                    <FormControl>
                      <Input
                        {...input}
                        onChange={(event) => {
                          input.onChange(event)
                          if (!existing && !keyTyped) {
                            form.setValue('key', keyFromLabel(event.target.value))
                          }
                        }}
                      />
                    </FormControl>
                    <FormDescription>The question as staff will see it.</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="key"
                render={({ field: input }) => (
                  <FormItem>
                    <FormLabel>Key</FormLabel>
                    <FormControl>
                      <Input
                        {...input}
                        disabled={!!existing}
                        className="font-mono"
                        onChange={(event) => {
                          setKeyTyped(true)
                          input.onChange(event)
                        }}
                      />
                    </FormControl>
                    <FormDescription>
                      {existing ? 'The key cannot be changed.' : 'Identifies the answer in reports and the API. It cannot be changed later.'}
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="type"
                render={({ field: input }) => (
                  <FormItem>
                    <FormLabel>Type</FormLabel>
                    <FormControl>
                      <Select {...input} disabled={!!existing}>
                        {FIELD_TYPES.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </Select>
                    </FormControl>
                    <FormDescription>
                      {existing ? 'The type cannot be changed.' : 'It cannot be changed later.'}
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {text('displayOrder', 'Position', 'Lower numbers come first. Leave empty to put a new field last.')}
            </div>

            {HAS_OPTIONS.includes(type) ? (
              <FormField
                control={form.control}
                name="options"
                render={({ field: input }) => (
                  <FormItem>
                    <FormLabel>Options</FormLabel>
                    <FormControl>
                      <Textarea {...input} rows={5} />
                    </FormControl>
                    <FormDescription>One option per line.</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}
            {HAS_RANGE.includes(type) ? (
              <div className="grid gap-4 sm:grid-cols-3">
                {text('minValue', 'Minimum')}
                {text('maxValue', 'Maximum')}
                {type === 'NUMBER' ? text('unit', 'Unit', 'Shown next to the input, e.g. sq ft or kW.') : null}
              </div>
            ) : null}
            {HAS_LENGTH.includes(type) ? (
              <div className="grid gap-4 sm:grid-cols-3">{text('maxLength', 'Maximum length', 'In characters.')}</div>
            ) : null}
            {type === 'FILE' ? (
              <div className="grid gap-4 sm:grid-cols-2">
                {text('allowedFileTypes', 'Allowed file types', 'Extensions separated by commas, e.g. pdf, jpg. Leave empty for any.')}
                {text('maxFileSizeMb', 'Maximum size (MB)', 'Leave empty for 10 MB.')}
              </div>
            ) : null}

            {text('helpText', 'Help text', 'Optional. Shown under the input.')}
            <FormField
              control={form.control}
              name="required"
              render={({ field: input }) => (
                <FormItem className="flex items-center gap-2">
                  <FormControl>
                    <input
                      type="checkbox"
                      className="size-4 accent-primary"
                      checked={input.value}
                      onChange={(event) => input.onChange(event.target.checked)}
                      onBlur={input.onBlur}
                      name={input.name}
                      ref={input.ref}
                    />
                  </FormControl>
                  <FormLabel>Required</FormLabel>
                </FormItem>
              )}
            />
            <div className="flex gap-2">
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Saving…' : field ? 'Save field' : 'Add field'}
              </Button>
              <Button type="button" variant="outline" onClick={onCancel}>
                Cancel
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}

type PreviewProps = { serviceId: string; definitions: FieldDefinition[]; canUpload: boolean }

/** The form as staff will see it, drawn from the definitions, with answers checked by the server. */
function Preview({ serviceId, definitions, canUpload }: PreviewProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const [accepted, setAccepted] = useState<Record<string, unknown> | null>(null)
  const form = useForm<{ answers: FormAnswers }>({
    resolver: zodResolver(z.object({ answers: buildAnswersSchema(definitions) })),
    defaultValues: { answers: initialAnswers(definitions) },
  })

  async function onSubmit(values: { answers: FormAnswers }) {
    setFormError(null)
    setAccepted(null)
    try {
      const result = await validateAnswers(serviceId, toAnswers(definitions, values.answers))
      setAccepted(result.values)
    } catch (error) {
      setFormError(applyAnswerErrors(form, 'answers', definitions, error))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Preview</CardTitle>
        <CardDescription>
          How these fields appear on a lead for this service. Try some answers; nothing is saved.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" aria-label="Preview" noValidate>
            <FormError message={formError} />
            <DynamicFields
              definitions={definitions}
              name="answers"
              uploadFile={canUpload ? (field, file) => uploadFieldFile(serviceId, field.key, file) : undefined}
            />
            <div>
              <Button type="submit" variant="outline" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Checking…' : 'Check answers'}
              </Button>
            </div>
            {accepted ? (
              <div role="status" className="grid gap-2 rounded-md border bg-muted/30 p-3 text-sm">
                <p className="font-medium">These answers are valid.</p>
                <pre className="overflow-x-auto font-mono text-xs">{JSON.stringify(accepted, null, 2)}</pre>
              </div>
            ) : null}
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}
