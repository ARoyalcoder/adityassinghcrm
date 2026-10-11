import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { FormActions, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { createJob, jobKeys, jobQuery, JOB_TYPES, techniciansQuery, updateJob, type JobDetail } from '@/features/field-jobs/api'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const schema = z
  .object({
    jobType: z.string(),
    title: z.string().trim().min(1, 'Say what the job is').max(200, 'At most 200 characters'),
    customerName: z.string().trim().min(1, 'Enter the customer').max(200, 'At most 200 characters'),
    siteAddress: z.string().trim().min(1, 'Enter where the job is').max(500, 'At most 500 characters'),
    siteContactName: z.string().trim().max(200, 'At most 200 characters'),
    siteContactPhone: z
      .string()
      .trim()
      .refine((value) => value === '' || /^[+0-9][0-9 ()-]{5,28}$/.test(value), 'Enter a phone number'),
    scheduledStart: z.string().refine((value) => value !== '' && !Number.isNaN(new Date(value).getTime()), 'Choose when the job starts'),
    scheduledEnd: z.string().refine((value) => value === '' || !Number.isNaN(new Date(value).getTime()), 'Enter a date and time'),
    technicianId: z.string(),
    instructions: z.string().trim().max(5000, 'At most 5000 characters'),
    /** One item per line. */
    checklist: z.string(),
  })
  .superRefine((values, context) => {
    if (values.scheduledEnd !== '' && values.scheduledStart !== '' && new Date(values.scheduledEnd) < new Date(values.scheduledStart)) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['scheduledEnd'], message: 'Must not be before the start' })
    }
    if (lines(values.checklist).length > 50) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['checklist'], message: 'At most 50 items' })
    }
  })
type Values = z.infer<typeof schema>

function lines(text: string) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '')
}

/** An instant as the value of a datetime-local input, in the viewer's own time zone. */
function toLocalInput(instant: string | null | undefined) {
  if (!instant) {
    return ''
  }
  const date = new Date(instant)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** Schedule a job (`/field-jobs/new`) or change one (`/field-jobs/:id/edit`). */
export function FieldJobFormPage() {
  const { id } = useParams()
  const existing = useQuery({ ...jobQuery(id ?? ''), enabled: id !== undefined })
  const back = id ? { to: `/field-jobs/${id}`, label: 'Job' } : { to: '/field-jobs', label: 'Field jobs' }

  return (
    <div className="grid gap-6">
      <PageHeader title={id ? 'Edit job' : 'New job'} back={back} />
      {id && !existing.data ? (
        <RecordLoadState noun="job" isPending={existing.isPending} error={existing.error} onRetry={() => void existing.refetch()} />
      ) : (
        <JobForm key={existing.data?.version ?? 'new'} detail={existing.data} cancelTo={back.to} />
      )}
    </div>
  )
}

function JobForm({ detail, cancelTo }: { detail?: JobDetail; cancelTo: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const technicians = useQuery(techniciansQuery)
  const [formError, setFormError] = useState<string | null>(null)
  // Once the technician has set off, the checklist they are working from is fixed.
  const checklistLocked = detail !== undefined && !['UNASSIGNED', 'ASSIGNED', 'ACCEPTED'].includes(detail.job.status)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      jobType: detail?.job.jobType ?? 'JOB',
      title: detail?.job.title ?? '',
      customerName: detail?.job.customerName ?? '',
      siteAddress: detail?.job.siteAddress ?? '',
      siteContactName: detail?.siteContactName ?? '',
      siteContactPhone: detail?.siteContactPhone ?? '',
      scheduledStart: toLocalInput(detail?.job.scheduledStart),
      scheduledEnd: toLocalInput(detail?.job.scheduledEnd),
      technicianId: detail?.job.technicianId ?? '',
      instructions: detail?.instructions ?? '',
      checklist: (detail?.checklist ?? []).map((item) => item.label).join('\n'),
    },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    const input = {
      jobType: values.jobType,
      title: values.title,
      instructions: blankToNull(values.instructions),
      customerName: values.customerName,
      siteAddress: values.siteAddress,
      siteContactName: blankToNull(values.siteContactName),
      siteContactPhone: blankToNull(values.siteContactPhone),
      // The picker gives local time; the server is sent the instant.
      scheduledStart: new Date(values.scheduledStart).toISOString(),
      scheduledEnd: values.scheduledEnd === '' ? null : new Date(values.scheduledEnd).toISOString(),
      checklist: checklistLocked ? null : lines(values.checklist),
    }
    try {
      const saved = detail
        ? await updateJob(detail.job.id, { ...input, version: detail.version })
        : await createJob({ ...input, technicianId: blankToNull(values.technicianId) })
      await queryClient.invalidateQueries({ queryKey: jobKeys.all })
      void navigate(`/field-jobs/${saved.job.id}`)
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  const text = (
    name: 'title' | 'customerName' | 'siteAddress' | 'siteContactName' | 'siteContactPhone' | 'scheduledStart' | 'scheduledEnd',
    label: string,
    type = 'text',
  ) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input type={type} {...field} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )

  return (
    <Card className="max-w-3xl">
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" aria-label={detail ? 'Edit job' : 'New job'} noValidate>
            <FormError message={formError} />
            <div className="grid gap-4 sm:grid-cols-2">
              {text('title', 'Job')}
              <FormField
                control={form.control}
                name="jobType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Type</FormLabel>
                    <FormControl>
                      <Select {...field}>
                        {JOB_TYPES.map((type) => (
                          <option key={type.value} value={type.value}>
                            {type.label}
                          </option>
                        ))}
                      </Select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {text('customerName', 'Customer')}
              {text('siteAddress', 'Site address')}
              {text('siteContactName', 'Contact on site')}
              {text('siteContactPhone', 'Contact phone')}
              {text('scheduledStart', 'Starts', 'datetime-local')}
              {text('scheduledEnd', 'Ends (optional)', 'datetime-local')}
              {detail ? null : (
                <FormField
                  control={form.control}
                  name="technicianId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Technician</FormLabel>
                      <FormControl>
                        <Select {...field}>
                          <option value="">Assign later</option>
                          {(technicians.data ?? []).map((person) => (
                            <option key={person.id} value={person.id}>
                              {person.name}
                            </option>
                          ))}
                        </Select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}
            </div>
            <FormField
              control={form.control}
              name="instructions"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Instructions</FormLabel>
                  <FormControl>
                    <Textarea rows={4} {...field} />
                  </FormControl>
                  <FormDescription>What the technician needs to know before going.</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="checklist"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Checklist</FormLabel>
                  <FormControl>
                    <Textarea rows={5} disabled={checklistLocked} {...field} />
                  </FormControl>
                  <FormDescription>
                    {checklistLocked
                      ? 'The technician has set off, so the checklist can no longer be changed.'
                      : 'One item per line. All of them must be ticked before the job can be completed.'}
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormActions submitLabel={detail ? 'Save job' : 'Create job'} submitting={form.formState.isSubmitting} cancelTo={cancelTo} />
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}
