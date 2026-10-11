import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react'
import { useFieldArray, useForm, useFormContext, useWatch, type Path } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { ErrorState } from '@/components/feedback/ErrorState'
import { FormActions, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { servicesQuery } from '@/features/catalog/api'
import {
  createTemplate,
  roleLabel,
  templateKeys,
  templateOptionsQuery,
  templateQuery,
  updateTemplate,
  type StageInput,
  type TemplateDetail,
} from '@/features/project-templates/api'
import { ApiError } from '@/lib/api/errors'
import { blankToNull } from '@/lib/forms'

const LIST = '/admin/project-templates'

const taskSchema = z.object({
  title: z.string().trim().min(1, 'Enter the task').max(250, 'At most 250 characters'),
  description: z.string().trim().max(1000, 'At most 1000 characters'),
  responsibleRole: z.string(),
  estimatedHours: z
    .string()
    .trim()
    .refine((text) => text === '' || (/^\d+(\.\d{1,2})?$/.test(text) && Number(text) <= 10000), 'Hours, e.g. 4 or 2.5'),
  /** One item per line. */
  checklist: z.string(),
})

const documentSchema = z.object({
  name: z.string().trim().min(1, 'Enter the document').max(200, 'At most 200 characters'),
  required: z.boolean(),
  description: z.string().trim().max(500, 'At most 500 characters'),
})

const stageSchema = z.object({
  /** Identifies the stage while the form is open, so "waits for" survives renaming and reordering. */
  uid: z.string(),
  /** The stored key of a stage that already exists; empty for a new one (derived from its name on save). */
  key: z.string(),
  name: z.string().trim().min(1, 'Enter the name of the stage').max(150, 'At most 150 characters'),
  description: z.string().trim().max(1000, 'At most 1000 characters'),
  durationDays: z
    .string()
    .trim()
    .refine((text) => /^\d+$/.test(text) && Number(text) <= 3650, 'Whole days, 0 to 3650'),
  responsibleRole: z.string(),
  /** uids of the stages this one waits for. */
  dependsOn: z.array(z.string()),
  /** One milestone per line. */
  milestones: z.string(),
  tasks: z.array(taskSchema).max(50, 'At most 50 tasks'),
  documents: z.array(documentSchema).max(30, 'At most 30 documents'),
})

const schema = z.object({
  serviceId: z.string().min(1, 'Choose the service this template is for'),
  name: z.string().trim().min(1, 'Enter a name').max(200, 'At most 200 characters'),
  description: z.string().trim().max(1000, 'At most 1000 characters'),
  stages: z.array(stageSchema).min(1, 'Add at least one stage').max(40, 'At most 40 stages'),
})
type Values = z.infer<typeof schema>
type StageValues = Values['stages'][number]

function lines(text: string) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '')
}

function newUid(): string {
  return crypto.randomUUID()
}

function blankStage(waitsFor: string[]): StageValues {
  return {
    uid: newUid(), key: '', name: '', description: '', durationDays: '1', responsibleRole: '', dependsOn: waitsFor,
    milestones: '', tasks: [], documents: [],
  }
}

/** The saved template put into the shape the form edits. */
function toForm(template: TemplateDetail): Values {
  const uids = new Map<string, string>(template.plan.stages.map((scheduled) => [scheduled.stage.key, newUid()]))
  return {
    serviceId: template.service.id,
    name: template.name,
    description: template.description ?? '',
    stages: template.plan.stages.map(({ stage }) => ({
      uid: uids.get(stage.key) ?? newUid(),
      key: stage.key,
      name: stage.name,
      description: stage.description ?? '',
      durationDays: String(stage.durationDays),
      responsibleRole: stage.responsibleRole ?? '',
      dependsOn: stage.dependsOn.map((key) => uids.get(key)).filter((uid): uid is string => uid !== undefined),
      milestones: stage.milestones.join('\n'),
      tasks: stage.tasks.map((task) => ({
        title: task.title,
        description: task.description ?? '',
        responsibleRole: task.responsibleRole ?? '',
        estimatedHours: task.estimatedHours?.toString() ?? '',
        checklist: task.checklist.join('\n'),
      })),
      documents: stage.documents.map((document) => ({
        name: document.name,
        required: document.required,
        description: document.description ?? '',
      })),
    })),
  }
}

/**
 * What is sent: a stage that already exists keeps its key; a new one gets a key made from its
 * name. "Waits for" is translated from the form's own ids to those keys.
 */
function toStages(stages: StageValues[]): StageInput[] {
  const taken = new Set(stages.map((stage) => stage.key).filter((key) => key !== ''))
  const keys = new Map<string, string>()
  for (const stage of stages) {
    let key = stage.key
    if (key === '') {
      const base = (stage.name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^[^a-z]+|_+$/g, '') || 'stage').slice(0, 50)
      key = base
      for (let suffix = 2; taken.has(key); suffix++) {
        key = `${base}_${suffix}`
      }
      taken.add(key)
    }
    keys.set(stage.uid, key)
  }
  return stages.map((stage) => ({
    key: keys.get(stage.uid) ?? stage.key,
    name: stage.name,
    description: blankToNull(stage.description),
    durationDays: Number(stage.durationDays),
    responsibleRole: blankToNull(stage.responsibleRole),
    dependsOn: stage.dependsOn.map((uid) => keys.get(uid)).filter((key): key is string => key !== undefined),
    milestones: lines(stage.milestones),
    tasks: stage.tasks.map((task) => ({
      title: task.title,
      description: blankToNull(task.description),
      responsibleRole: blankToNull(task.responsibleRole),
      estimatedHours: task.estimatedHours === '' ? null : Number(task.estimatedHours),
      checklist: lines(task.checklist),
    })),
    documents: stage.documents.map((document) => ({
      name: document.name,
      required: document.required,
      description: blankToNull(document.description),
    })),
  }))
}

/** Create a template (`/admin/project-templates/new`) or edit one (`/admin/project-templates/:id/edit`). */
export function ProjectTemplateFormPage() {
  const { id } = useParams()
  const existing = useQuery({ ...templateQuery(id ?? ''), enabled: id !== undefined })
  const back = id ? { to: `${LIST}/${id}`, label: 'Template' } : { to: LIST, label: 'Project templates' }

  return (
    <div className="grid gap-6">
      <PageHeader
        title={id ? 'Edit project template' : 'New project template'}
        description="Define the stages in the order they are usually done. Each stage waits for the stages you tick under it."
        back={back}
      />
      {id && !existing.data ? (
        <RecordLoadState
          noun="template"
          isPending={existing.isPending}
          error={existing.error}
          onRetry={() => void existing.refetch()}
        />
      ) : (
        <TemplateForm key={existing.data?.version ?? 'new'} template={existing.data} cancelTo={back.to} />
      )}
    </div>
  )
}

function TemplateForm({ template, cancelTo }: { template?: TemplateDetail; cancelTo: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const services = useQuery(servicesQuery({ active: 'true', size: 100 }))
  const options = useQuery(templateOptionsQuery)
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: template ? toForm(template) : { serviceId: '', name: '', description: '', stages: [blankStage([])] },
  })
  const stages = useFieldArray({ control: form.control, name: 'stages' })

  async function onSubmit(values: Values) {
    setFormError(null)
    const input = { name: values.name, description: blankToNull(values.description), stages: toStages(values.stages) }
    try {
      const saved = template
        ? await updateTemplate(template.id, { ...input, version: template.version })
        : await createTemplate({ ...input, serviceId: values.serviceId })
      await queryClient.invalidateQueries({ queryKey: templateKeys.all })
      void navigate(`${LIST}/${saved.id}`)
    } catch (error) {
      setFormError(showServerErrors(error))
    }
  }

  /**
   * The server names each problem by its place ("stages[2].dependsOn"). Where the form has an input
   * at that place the message goes next to it; the rest are listed at the top.
   */
  function showServerErrors(error: unknown): string | null {
    if (!(error instanceof ApiError)) {
      return 'Something went wrong. Please try again.'
    }
    if (error.fieldErrors.length === 0) {
      return error.message
    }
    const placeable =
      /^(serviceId|name|description|stages\.\d+\.(name|description|durationDays|responsibleRole|dependsOn|milestones|tasks\.\d+\.(title|description|responsibleRole|estimatedHours|checklist)|documents\.\d+\.(name|description)))$/
    const unplaced: string[] = []
    for (const violation of error.fieldErrors) {
      // "stages[2].tasks[0].title" is "stages.2.tasks.0.title" here; a stage's key is made from its name.
      const path = violation.field.replace(/\[(\d+)\]/g, '.$1').replace(/\.key$/, '.name')
      if (placeable.test(path)) {
        form.setError(path as Path<Values>, { type: 'server', message: violation.message })
      } else {
        unplaced.push(`${violation.field}: ${violation.message}`)
      }
    }
    return unplaced.length > 0 ? unplaced.join('; ') : 'Some stages need attention. See the messages below.'
  }

  if (services.isError) {
    return <ErrorState title="Could not load the services" error={services.error} onRetry={() => void services.refetch()} />
  }
  if (services.isPending || options.isPending) {
    return <Skeleton className="h-64 w-full" aria-busy="true" aria-label="Loading the form" />
  }
  const roles = options.data?.roles ?? []
  const stagesError = form.formState.errors.stages?.root?.message ?? form.formState.errors.stages?.message

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-6" aria-label={template ? 'Edit template' : 'New template'} noValidate>
        <FormError message={formError} />
        <Card>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <FormField
              control={form.control}
              name="serviceId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Service</FormLabel>
                  <FormControl>
                    {template ? (
                      <Input value={template.service.name} disabled readOnly />
                    ) : (
                      <Select {...field}>
                        <option value="">Choose…</option>
                        {services.data.items.map((service) => (
                          <option key={service.id} value={service.id}>
                            {service.name} ({service.vertical.name})
                          </option>
                        ))}
                      </Select>
                    )}
                  </FormControl>
                  <FormDescription>
                    {template ? 'A template stays with its service.' : 'Each service has one template. It cannot be moved later.'}
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Template name</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Textarea {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
        </Card>

        <ol className="grid gap-4" aria-label="Stages">
          {stages.fields.map((row, index) => (
            <li key={row.id}>
              <StageEditor
                index={index}
                count={stages.fields.length}
                roles={roles}
                onMove={(to) => stages.move(index, to)}
                onRemove={() => {
                  // Nothing may be left waiting for a stage that is gone.
                  const removed = form.getValues(`stages.${index}.uid`)
                  form.getValues('stages').forEach((stage, other) => {
                    if (stage.dependsOn.includes(removed)) {
                      form.setValue(`stages.${other}.dependsOn`, stage.dependsOn.filter((uid) => uid !== removed))
                    }
                  })
                  stages.remove(index)
                }}
              />
            </li>
          ))}
        </ol>
        {stagesError ? (
          <p role="alert" className="text-sm text-destructive">
            {stagesError}
          </p>
        ) : null}
        <Button
          type="button"
          variant="outline"
          className="w-fit"
          onClick={() => {
            // A new stage usually follows the last one, so it starts out waiting for it.
            const last = form.getValues('stages').at(-1)
            stages.append(blankStage(last ? [last.uid] : []))
          }}
        >
          <Plus aria-hidden="true" />
          Add stage
        </Button>

        <FormActions submitLabel={template ? 'Save template' : 'Create template'} submitting={form.formState.isSubmitting} cancelTo={cancelTo} />
      </form>
    </Form>
  )
}

type StageEditorProps = { index: number; count: number; roles: string[]; onMove: (to: number) => void; onRemove: () => void }

/** One stage. Which inputs it shows is the same for every stage of every template. */
function StageEditor({ index, count, roles, onMove, onRemove }: StageEditorProps) {
  const form = useFormContext<Values>()
  const at = `stages.${index}` as const
  const all = useWatch({ control: form.control, name: 'stages' })
  const own = all[index]
  const others = all.filter((_, other) => other !== index)
  const tasks = useFieldArray({ control: form.control, name: `${at}.tasks` })
  const documents = useFieldArray({ control: form.control, name: `${at}.documents` })
  const title = own?.name.trim() ? own.name.trim() : `Stage ${index + 1}`

  const roleSelect = (name: `stages.${number}.responsibleRole` | `stages.${number}.tasks.${number}.responsibleRole`, label: string, none: string) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Select {...field}>
              <option value="">{none}</option>
              {roles.map((role) => (
                <option key={role} value={role}>
                  {roleLabel(role)}
                </option>
              ))}
            </Select>
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )

  return (
    <Card role="group" aria-label={`Stage ${index + 1}`}>
      <CardHeader className="flex-row items-center justify-between gap-3">
        <CardTitle>
          {index + 1}. {title}
        </CardTitle>
        <div className="flex gap-1">
          <Button type="button" variant="ghost" size="icon" aria-label={`Move ${title} up`} disabled={index === 0} onClick={() => onMove(index - 1)}>
            <ArrowUp aria-hidden="true" />
          </Button>
          <Button type="button" variant="ghost" size="icon" aria-label={`Move ${title} down`} disabled={index === count - 1} onClick={() => onMove(index + 1)}>
            <ArrowDown aria-hidden="true" />
          </Button>
          <Button type="button" variant="ghost" size="icon" aria-label={`Remove ${title}`} onClick={onRemove}>
            <Trash2 aria-hidden="true" />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="grid gap-5">
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField
            control={form.control}
            name={`${at}.name`}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Stage name</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name={`${at}.durationDays`}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Estimated duration (days)</FormLabel>
                <FormControl>
                  <Input inputMode="numeric" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          {roleSelect(`${at}.responsibleRole`, 'Responsible role', 'Nobody in particular')}
        </div>

        <FormField
          control={form.control}
          name={`${at}.dependsOn`}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Waits for</FormLabel>
              {others.length === 0 ? (
                <p className="text-sm text-muted-foreground">This is the only stage: it starts with the project.</p>
              ) : (
                <div role="group" aria-label={`${title} waits for`} className="flex flex-wrap gap-x-5 gap-y-2">
                  {others.map((other, position) => (
                    <label key={other.uid} className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        className="size-4 accent-primary"
                        checked={field.value.includes(other.uid)}
                        onChange={(event) =>
                          field.onChange(
                            event.target.checked
                              ? [...field.value, other.uid]
                              : field.value.filter((uid) => uid !== other.uid),
                          )
                        }
                      />
                      {other.name.trim() || `Unnamed stage ${position + 1}`}
                    </label>
                  ))}
                </div>
              )}
              <FormDescription>Leave everything unticked for a stage that starts with the project.</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name={`${at}.milestones`}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Milestones</FormLabel>
              <FormControl>
                <Textarea rows={2} {...field} />
              </FormControl>
              <FormDescription>Checkpoints reached in this stage, one per line.</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        <fieldset className="grid gap-3">
          <legend className="mb-2 text-sm font-medium">Tasks</legend>
          {tasks.fields.map((row, taskIndex) => (
            <div key={row.id} role="group" aria-label={`Task ${taskIndex + 1} of ${title}`} className="grid gap-3 rounded-md border p-3">
              <div className="grid gap-3 sm:grid-cols-[2fr_1fr_1fr_auto] sm:items-start">
                <FormField
                  control={form.control}
                  name={`${at}.tasks.${taskIndex}.title`}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Task</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {roleSelect(`${at}.tasks.${taskIndex}.responsibleRole`, 'Done by', "The stage's role")}
                <FormField
                  control={form.control}
                  name={`${at}.tasks.${taskIndex}.estimatedHours`}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Hours</FormLabel>
                      <FormControl>
                        <Input inputMode="decimal" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="button" variant="ghost" size="icon" className="sm:mt-6" aria-label={`Remove task ${taskIndex + 1} of ${title}`} onClick={() => tasks.remove(taskIndex)}>
                  <Trash2 aria-hidden="true" />
                </Button>
              </div>
              <FormField
                control={form.control}
                name={`${at}.tasks.${taskIndex}.checklist`}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Checklist</FormLabel>
                    <FormControl>
                      <Textarea rows={2} {...field} />
                    </FormControl>
                    <FormDescription>What must be ticked off before the task is done, one item per line.</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="w-fit"
            onClick={() => tasks.append({ title: '', description: '', responsibleRole: '', estimatedHours: '', checklist: '' })}
          >
            <Plus aria-hidden="true" />
            Add task
          </Button>
        </fieldset>

        <fieldset className="grid gap-3">
          <legend className="mb-2 text-sm font-medium">Required documents</legend>
          {documents.fields.map((row, documentIndex) => (
            <div key={row.id} role="group" aria-label={`Document ${documentIndex + 1} of ${title}`} className="flex flex-wrap items-start gap-3">
              <FormField
                control={form.control}
                name={`${at}.documents.${documentIndex}.name`}
                render={({ field }) => (
                  <FormItem className="min-w-48 flex-1">
                    <FormLabel className="sr-only">Document</FormLabel>
                    <FormControl>
                      <Input placeholder="Document" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name={`${at}.documents.${documentIndex}.required`}
                render={({ field }) => (
                  <FormItem className="flex h-9 items-center gap-2">
                    <FormControl>
                      <input
                        type="checkbox"
                        className="size-4 accent-primary"
                        checked={field.value}
                        onChange={(event) => field.onChange(event.target.checked)}
                        onBlur={field.onBlur}
                        name={field.name}
                        ref={field.ref}
                      />
                    </FormControl>
                    <FormLabel>Must be provided</FormLabel>
                  </FormItem>
                )}
              />
              <Button type="button" variant="ghost" size="icon" aria-label={`Remove document ${documentIndex + 1} of ${title}`} onClick={() => documents.remove(documentIndex)}>
                <Trash2 aria-hidden="true" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="w-fit"
            onClick={() => documents.append({ name: '', required: true, description: '' })}
          >
            <Plus aria-hidden="true" />
            Add document
          </Button>
        </fieldset>
      </CardContent>
    </Card>
  )
}
