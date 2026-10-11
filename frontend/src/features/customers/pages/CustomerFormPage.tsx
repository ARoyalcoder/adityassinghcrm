import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { TriangleAlert } from 'lucide-react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { FormActions, FormCard, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  assigneesQuery,
  checkDuplicates,
  createCustomer,
  CUSTOMER_SOURCES,
  CUSTOMER_STATUSES,
  CUSTOMER_TYPES,
  customerKeys,
  customerQuery,
  customerTagsQuery,
  updateCustomer,
  type CustomerDetail,
  type CustomerInput,
  type DuplicateMatch,
} from '@/features/customers/api'
import { ApiError } from '@/lib/api/errors'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const MATCH_LABELS: Record<string, string> = { PHONE: 'same phone', EMAIL: 'same email', COMPANY_NAME: 'same company name' }

const schema = z
  .object({
    type: z.enum(['INDIVIDUAL', 'BUSINESS']),
    firstName: z.string().trim().max(100, 'At most 100 characters'),
    lastName: z.string().trim().max(100, 'At most 100 characters'),
    companyName: z.string().trim().max(200, 'At most 200 characters'),
    taxId: z.string().trim().max(30, 'At most 30 characters'),
    phone: z
      .string()
      .trim()
      .refine((value) => value === '' || /^[+0-9][0-9 ()-]{5,28}$/.test(value), 'Enter a phone number'),
    email: z
      .string()
      .trim()
      .max(254)
      .refine((value) => value === '' || z.string().email().safeParse(value).success, 'Enter a valid email address'),
    status: z.string().min(1),
    source: z.string(),
    assignedUserId: z.string(),
    tags: z.string().max(400, 'Too many tags'),
  })
  .superRefine((values, context) => {
    if (values.type === 'INDIVIDUAL' && values.firstName === '') {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['firstName'], message: 'Enter the first name' })
    }
    if (values.type === 'BUSINESS' && values.companyName === '') {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['companyName'], message: 'Enter the company name' })
    }
  })
type Values = z.infer<typeof schema>

/** Tags are typed as one comma-separated line; new names are created on save. */
function parseTags(text: string): string[] {
  const seen = new Set<string>()
  return text
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag) => {
      const key = tag.toLowerCase()
      if (tag === '' || seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
}

/** Create a customer (`/customers/new`) or edit one (`/customers/:id/edit`). */
export function CustomerFormPage() {
  const { id } = useParams()
  const existing = useQuery({ ...customerQuery(id ?? ''), enabled: id !== undefined })
  const back = id ? { to: `/customers/${id}`, label: 'Customer' } : { to: '/customers', label: 'Customers' }

  return (
    <div className="grid gap-6">
      <PageHeader title={id ? 'Edit customer' : 'New customer'} back={back} />
      {id && !existing.data ? (
        <RecordLoadState
          noun="customer"
          isPending={existing.isPending}
          error={existing.error}
          onRetry={() => void existing.refetch()}
        />
      ) : (
        <CustomerForm key={existing.data?.version ?? 'new'} customer={existing.data} cancelTo={back.to} />
      )}
    </div>
  )
}

function CustomerForm({ customer, cancelTo }: { customer?: CustomerDetail; cancelTo: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  /** Possible duplicates the user has to look at before the customer is saved. */
  const [duplicates, setDuplicates] = useState<DuplicateMatch[] | null>(null)
  const assignees = useQuery(assigneesQuery)
  const knownTags = useQuery(customerTagsQuery)

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: customer?.type === 'BUSINESS' ? 'BUSINESS' : 'INDIVIDUAL',
      firstName: customer?.firstName ?? '',
      lastName: customer?.lastName ?? '',
      companyName: customer?.companyName ?? '',
      taxId: customer?.taxId ?? '',
      phone: customer?.phone ?? '',
      email: customer?.email ?? '',
      status: customer?.status ?? 'ACTIVE',
      source: customer?.source ?? '',
      assignedUserId: customer?.assignedTo?.id ?? '',
      tags: customer?.tags.join(', ') ?? '',
    },
  })
  const type = useWatch({ control: form.control, name: 'type' })

  async function save(values: Values, confirmDuplicates: boolean) {
    setFormError(null)
    // Only the fields of the chosen type are sent; whatever was typed for the other type is dropped.
    const business = values.type === 'BUSINESS'
    const input: CustomerInput = {
      type: values.type,
      firstName: business ? null : blankToNull(values.firstName),
      lastName: business ? null : blankToNull(values.lastName),
      companyName: business ? blankToNull(values.companyName) : null,
      taxId: business ? blankToNull(values.taxId) : null,
      phone: blankToNull(values.phone),
      email: blankToNull(values.email),
      status: values.status,
      source: blankToNull(values.source),
      assignedUserId: blankToNull(values.assignedUserId),
      tags: parseTags(values.tags),
      confirmDuplicates,
    }
    try {
      if (!confirmDuplicates) {
        // Show who the user may be duplicating before anything is saved. The server enforces the
        // same rule, so skipping this check would only produce a less helpful error.
        const identityChanged =
          !customer ||
          input.phone !== (customer.phone ?? null) ||
          input.email?.toLowerCase() !== (customer.email ?? undefined) ||
          input.companyName !== (customer.companyName ?? null)
        if (identityChanged) {
          const matches = await checkDuplicates({
            phone: input.phone,
            email: input.email,
            companyName: input.companyName,
            excludeCustomerId: customer?.id,
          })
          if (matches.length > 0) {
            setDuplicates(matches)
            return
          }
        }
      }
      const saved = customer
        ? await updateCustomer(customer.id, { ...input, version: customer.version })
        : await createCustomer(input)
      await queryClient.invalidateQueries({ queryKey: customerKeys.all })
      navigate(`/customers/${saved.id}`)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'POSSIBLE_DUPLICATE' && !confirmDuplicates) {
        // Someone created the matching customer between our check and the save: look again.
        setDuplicates(
          await checkDuplicates({
            phone: input.phone,
            email: input.email,
            companyName: input.companyName,
            excludeCustomerId: customer?.id,
          }),
        )
        return
      }
      setDuplicates(null)
      setFormError(applyServerErrors(form, error))
    }
  }

  return (
    <FormCard>
      <Form {...form}>
        <form onSubmit={form.handleSubmit((values) => save(values, false))} className="grid gap-4" noValidate>
          <FormError message={formError} />

          <FormField
            control={form.control}
            name="type"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Customer type</FormLabel>
                <FormControl>
                  <Select {...field}>
                    {CUSTOMER_TYPES.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </Select>
                </FormControl>
                <FormDescription>
                  {type === 'BUSINESS'
                    ? 'A company. Add the people you deal with as contacts after saving.'
                    : 'A person.'}
                </FormDescription>
              </FormItem>
            )}
          />

          {type === 'BUSINESS' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField form={form} name="companyName" label="Company name" />
              <TextField form={form} name="taxId" label="GSTIN" />
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField form={form} name="firstName" label="First name" />
              <TextField form={form} name="lastName" label="Last name" />
            </div>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <TextField form={form} name="phone" label="Phone" inputMode="tel" />
            <TextField form={form} name="email" label="Email" inputMode="email" />
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <SelectField form={form} name="status" label="Status" options={[...CUSTOMER_STATUSES]} />
            <SelectField form={form} name="source" label="Source" emptyLabel="Not recorded" options={[...CUSTOMER_SOURCES]} />
            <SelectField
              form={form}
              name="assignedUserId"
              label="Assigned to"
              emptyLabel="Unassigned"
              options={(assignees.data ?? []).map((person) => ({ value: person.id, label: person.name }))}
            />
          </div>

          <FormField
            control={form.control}
            name="tags"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Tags</FormLabel>
                <FormControl>
                  <Input placeholder="VIP, Builder" {...field} />
                </FormControl>
                <FormDescription>
                  Separate tags with commas.
                  {knownTags.data && knownTags.data.length > 0
                    ? ` In use: ${knownTags.data.map((tag) => tag.name).join(', ')}.`
                    : ''}
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          {duplicates ? (
            <section
              role="alertdialog"
              aria-labelledby="duplicates-title"
              className="grid gap-3 rounded-lg border border-destructive/40 bg-destructive/5 p-4"
            >
              <h2 id="duplicates-title" className="flex items-center gap-2 text-sm font-semibold">
                <TriangleAlert className="size-4 text-destructive" aria-hidden="true" />
                {duplicates.length === 1 ? 'This may already be a customer' : 'These may already be customers'}
              </h2>
              <ul className="grid gap-2 text-sm">
                {duplicates.map((match) => (
                  <li key={match.id} className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <Link
                      to={`/customers/${match.id}`}
                      target="_blank"
                      rel="noreferrer"
                      className="font-medium underline underline-offset-2"
                    >
                      {match.displayName}
                    </Link>
                    <span className="font-mono text-xs text-muted-foreground">{match.customerNumber}</span>
                    <span className="text-xs text-muted-foreground">{[match.phone, match.email].filter(Boolean).join(' · ')}</span>
                    {match.matchedOn.map((reason) => (
                      <Badge key={reason} variant="secondary">
                        {MATCH_LABELS[reason] ?? reason}
                      </Badge>
                    ))}
                  </li>
                ))}
              </ul>
              <p className="text-sm text-muted-foreground">
                Open the existing customer to check. If this really is a different customer, you can save it anyway.
              </p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  disabled={form.formState.isSubmitting}
                  onClick={() => void form.handleSubmit((values) => save(values, true))()}
                >
                  Save anyway
                </Button>
                <Button type="button" variant="ghost" onClick={() => setDuplicates(null)}>
                  Go back and edit
                </Button>
              </div>
            </section>
          ) : (
            <FormActions
              submitLabel={customer ? 'Save changes' : 'Create customer'}
              submitting={form.formState.isSubmitting}
              cancelTo={cancelTo}
            />
          )}
        </form>
      </Form>
    </FormCard>
  )
}

type FieldProps = {
  form: ReturnType<typeof useForm<Values>>
  name: Exclude<keyof Values, 'type'>
  label: string
}

function TextField({ form, name, label, inputMode }: FieldProps & { inputMode?: 'tel' | 'email' }) {
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input inputMode={inputMode} {...field} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}

function SelectField({
  form,
  name,
  label,
  options,
  emptyLabel,
}: FieldProps & { options: { value: string; label: string }[]; emptyLabel?: string }) {
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Select {...field}>
              {emptyLabel ? <option value="">{emptyLabel}</option> : null}
              {options.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
