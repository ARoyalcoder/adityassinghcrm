import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm, type Path } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { DynamicFields } from '@/components/dynamic-fields/DynamicFields'
import type { FieldDefinition } from '@/components/dynamic-fields/model'
import { buildAnswersSchema, initialAnswers, toAnswers, type FormAnswers } from '@/components/dynamic-fields/values'
import { ErrorState } from '@/components/feedback/ErrorState'
import { FormActions, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { customFieldsQuery, servicesQuery } from '@/features/catalog/api'
import { optionalAmount, toNumberOrNull } from '@/features/catalog/form-fields'
import {
  createLead,
  LEAD_STATUSES,
  leadKeys,
  leadQuery,
  updateLead,
  uploadLeadFieldFile,
  type LeadDetail,
} from '@/features/leads/api'
import { ApiError } from '@/lib/api/errors'
import { blankToNull } from '@/lib/forms'

/** The part of a lead that every lead has. (The rest depends on its service, and is not known here.) */
const baseSchema = z
  .object({
    contactName: z.string().trim().min(1, 'Enter the name of the person who enquired').max(200, 'At most 200 characters'),
    companyName: z.string().trim().max(200, 'At most 200 characters'),
    phone: z
      .string()
      .trim()
      .refine((value) => value === '' || /^[+0-9][0-9 ()-]{5,28}$/.test(value), 'Enter a phone number'),
    email: z
      .string()
      .trim()
      .max(254)
      .refine((value) => value === '' || z.string().email().safeParse(value).success, 'Enter a valid email address'),
    serviceId: z.string().min(1, 'Choose the service they enquired about'),
    estimatedValue: optionalAmount,
    status: z.string(),
  })
  .superRefine((values, context) => {
    if (values.phone === '' && values.email === '') {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['phone'], message: 'Enter a phone number or an email address' })
    }
  })
type Base = z.infer<typeof baseSchema>
type Values = Base & { customFields: FormAnswers }

/** Create a lead (`/leads/new`) or edit one (`/leads/:id/edit`). */
export function LeadFormPage() {
  const { id } = useParams()
  const existing = useQuery({ ...leadQuery(id ?? ''), enabled: id !== undefined })
  const back = id ? { to: `/leads/${id}`, label: 'Lead' } : { to: '/leads', label: 'Leads' }

  return (
    <div className="grid gap-6">
      <PageHeader title={id ? 'Edit lead' : 'New lead'} back={back} />
      {id && !existing.data ? (
        <RecordLoadState noun="lead" isPending={existing.isPending} error={existing.error} onRetry={() => void existing.refetch()} />
      ) : (
        <LeadForm key={existing.data?.version ?? 'new'} lead={existing.data} cancelTo={back.to} />
      )}
    </div>
  )
}

/**
 * Decides which service's fields the form shows. The form itself (below) is rebuilt whenever the
 * service changes, because its validation is generated from that service's field definitions;
 * what has been typed so far is carried across in `base`.
 */
function LeadForm({ lead, cancelTo }: { lead?: LeadDetail; cancelTo: string }) {
  const [base, setBase] = useState<Base>({
    contactName: lead?.contactName ?? '',
    companyName: lead?.companyName ?? '',
    phone: lead?.phone ?? '',
    email: lead?.email ?? '',
    serviceId: lead?.service.id ?? '',
    estimatedValue: lead?.estimatedValue?.toString() ?? '',
    status: lead?.status ?? 'OPEN',
  })
  const services = useQuery(servicesQuery({ active: 'true', size: 100 }))
  const fields = useQuery({ ...customFieldsQuery(base.serviceId), enabled: base.serviceId !== '' })

  const offered = (services.data?.items ?? []).map((service) => ({ id: service.id, name: `${service.name} (${service.vertical.name})` }))
  // A lead keeps its service even if that service is no longer offered.
  const serviceOptions = lead && !offered.some((service) => service.id === lead.service.id)
    ? [{ id: lead.service.id, name: lead.service.name }, ...offered]
    : offered

  if (services.isError) {
    return <ErrorState title="Could not load the services" error={services.error} onRetry={() => void services.refetch()} />
  }
  if (base.serviceId !== '' && fields.isError) {
    return <ErrorState title="Could not load this service's fields" error={fields.error} onRetry={() => void fields.refetch()} />
  }
  if (services.isPending || (base.serviceId !== '' && !fields.data)) {
    return (
      <Card className="max-w-3xl">
        <CardContent>
          <Skeleton className="h-64 w-full" aria-busy="true" aria-label="Loading the form" />
        </CardContent>
      </Card>
    )
  }

  const definitions = (fields.data ?? []).filter((item) => item.field.active).map((item) => item.field)
  // Stored answers belong to the service they were given for.
  const stored = lead && lead.service.id === base.serviceId
    ? Object.fromEntries(lead.customFields.map((answer) => [answer.field.key, answer.value]))
    : {}

  return (
    <LeadFields
      key={`${base.serviceId}:${definitions.map((field) => field.id).join(',')}`}
      lead={lead}
      base={base}
      definitions={definitions}
      stored={stored}
      serviceOptions={serviceOptions}
      onServiceChange={setBase}
      cancelTo={cancelTo}
    />
  )
}

type LeadFieldsProps = {
  lead?: LeadDetail
  base: Base
  definitions: FieldDefinition[]
  stored: Record<string, unknown>
  serviceOptions: { id: string; name: string }[]
  onServiceChange: (typedSoFar: Base) => void
  cancelTo: string
}

function LeadFields({ lead, base, definitions, stored, serviceOptions, onServiceChange, cancelTo }: LeadFieldsProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<Values>({
    // The second half of the schema is generated from the service's field definitions.
    resolver: zodResolver(baseSchema.and(z.object({ customFields: buildAnswersSchema(definitions) }))),
    defaultValues: { ...base, customFields: initialAnswers(definitions, stored) },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    const input = {
      contactName: values.contactName,
      companyName: blankToNull(values.companyName),
      phone: blankToNull(values.phone),
      email: blankToNull(values.email),
      serviceId: values.serviceId,
      estimatedValue: toNumberOrNull(values.estimatedValue),
      status: values.status,
      customFields: toAnswers(definitions, values.customFields),
    }
    try {
      const saved = lead ? await updateLead(lead.id, { ...input, version: lead.version }) : await createLead(input)
      await queryClient.invalidateQueries({ queryKey: leadKeys.all })
      void navigate(`/leads/${saved.id}`)
    } catch (error) {
      setFormError(showServerErrors(error))
    }
  }

  /** Puts each problem next to its input, whether it is a lead field or one of the service's own. */
  function showServerErrors(error: unknown): string | null {
    if (!(error instanceof ApiError)) {
      return 'Something went wrong. Please try again.'
    }
    if (error.fieldErrors.length === 0) {
      return error.message
    }
    const own = new Set(Object.keys(base))
    const custom = new Set(definitions.map((field) => `customFields.${field.key}`))
    const unplaced: string[] = []
    for (const violation of error.fieldErrors) {
      if (own.has(violation.field) || custom.has(violation.field)) {
        const message = violation.message.charAt(0).toUpperCase() + violation.message.slice(1)
        form.setError(violation.field as Path<Values>, { type: 'server', message })
      } else {
        unplaced.push(`${violation.field} ${violation.message}`)
      }
    }
    return unplaced.length > 0 ? unplaced.join('; ') : null
  }

  const text = (name: 'contactName' | 'companyName' | 'phone' | 'email' | 'estimatedValue', label: string, hint?: string) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input {...field} />
          </FormControl>
          {hint ? <FormDescription>{hint}</FormDescription> : null}
          <FormMessage />
        </FormItem>
      )}
    />
  )

  return (
    <Card className="max-w-3xl">
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-6" aria-label={lead ? 'Edit lead' : 'New lead'} noValidate>
            <FormError message={formError} />
            <div className="grid gap-4 sm:grid-cols-2">
              {text('contactName', 'Contact name')}
              {text('companyName', 'Company')}
              {text('phone', 'Phone')}
              {text('email', 'Email')}
              <FormField
                control={form.control}
                name="serviceId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Service</FormLabel>
                    <FormControl>
                      <Select
                        {...field}
                        onChange={(event) => {
                          // Another service asks other questions: hand what was typed so far to the
                          // parent, which rebuilds the form around the new service's fields.
                          const typed = form.getValues()
                          onServiceChange({
                            contactName: typed.contactName,
                            companyName: typed.companyName,
                            phone: typed.phone,
                            email: typed.email,
                            estimatedValue: typed.estimatedValue,
                            status: typed.status,
                            serviceId: event.target.value,
                          })
                        }}
                      >
                        <option value="">Choose…</option>
                        {serviceOptions.map((service) => (
                          <option key={service.id} value={service.id}>
                            {service.name}
                          </option>
                        ))}
                      </Select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {text('estimatedValue', 'Estimated value (₹)', 'What the business may be worth. Leave empty if not known yet.')}
              {lead ? (
                <FormField
                  control={form.control}
                  name="status"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Status</FormLabel>
                      <FormControl>
                        <Select {...field}>
                          {LEAD_STATUSES.map((status) => (
                            <option key={status.value} value={status.value}>
                              {status.label}
                            </option>
                          ))}
                        </Select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              ) : null}
            </div>

            {definitions.length > 0 ? (
              <fieldset className="grid gap-4 border-t pt-5">
                <legend className="float-left mb-1 text-sm font-semibold">About this enquiry</legend>
                <DynamicFields
                  className="clear-both"
                  definitions={definitions}
                  name="customFields"
                  uploadFile={(field, file) => uploadLeadFieldFile(form.getValues('serviceId'), field.key, file)}
                />
              </fieldset>
            ) : null}

            <FormActions submitLabel={lead ? 'Save lead' : 'Create lead'} submitting={form.formState.isSubmitting} cancelTo={cancelTo} />
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}
