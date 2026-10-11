import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { FormActions, FormCard, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { ActivityTimeline } from '@/features/audit/components/ActivityTimeline'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  BILLING_TYPES,
  catalogKeys,
  categoriesQuery,
  createService,
  serviceQuery,
  updateService,
  type Service,
  type ServiceInput,
} from '@/features/catalog/api'
import {
  codeField,
  optionalAmount,
  optionalText,
  optionalWholeNumber,
  requiredText,
  toNumberOrNull,
} from '@/features/catalog/form-fields'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const LIST = '/admin/services'

const schema = z
  .object({
    code: codeField(60),
    categoryId: z.string().min(1, 'Choose a category'),
    name: requiredText('a name', 200),
    description: optionalText(1000),
    billingType: z.string().min(1, 'Choose how the service is billed'),
    unitLabel: optionalText(50),
    basePrice: optionalAmount,
    requiresSiteVisit: z.boolean(),
    estimatedDurationDays: optionalWholeNumber(1, 3650),
    displayOrder: optionalWholeNumber(0, 9999),
  })
  .superRefine((values, context) => {
    if (values.billingType !== 'QUOTE_BASED' && values.basePrice === '') {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['basePrice'],
        message: 'Enter a base price, or choose quote-based billing',
      })
    }
  })
type Values = z.infer<typeof schema>

/** Create a service (`/admin/services/new`) or edit one (`/admin/services/:id/edit`). */
export function ServiceFormPage() {
  const { id } = useParams()
  const existing = useQuery({ ...serviceQuery(id ?? ''), enabled: id !== undefined })

  return (
    <div className="grid gap-6">
      <PageHeader
        title={id ? 'Edit service' : 'New service'}
        description={id ? undefined : 'Add something the company sells.'}
        back={{ to: LIST, label: 'Services' }}
      />
      {id && !existing.data ? (
        <RecordLoadState
          noun="service"
          isPending={existing.isPending}
          error={existing.error}
          onRetry={() => void existing.refetch()}
        />
      ) : (
        <>
          <ServiceForm key={existing.data?.version ?? 'new'} service={existing.data} />
          {existing.data ? <ActivityTimeline entityType="Service" entityId={existing.data.id} /> : null}
        </>
      )}
    </div>
  )
}

function ServiceForm({ service }: { service?: Service }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  // Enough for a select; a very large catalog would need a searchable picker here.
  const categories = useQuery(categoriesQuery({ size: 100 }))

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: service?.code ?? '',
      categoryId: service?.category.id ?? '',
      name: service?.name ?? '',
      description: service?.description ?? '',
      billingType: service?.billingType ?? 'QUOTE_BASED',
      unitLabel: service?.unitLabel ?? '',
      basePrice: service?.basePrice != null ? String(service.basePrice) : '',
      requiresSiteVisit: service?.requiresSiteVisit ?? false,
      estimatedDurationDays: service?.estimatedDurationDays != null ? String(service.estimatedDurationDays) : '',
      displayOrder: service ? String(service.displayOrder) : '',
    },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    const input: ServiceInput = {
      categoryId: values.categoryId,
      name: values.name,
      description: blankToNull(values.description),
      displayOrder: toNumberOrNull(values.displayOrder) ?? 0,
      billingType: values.billingType,
      unitLabel: blankToNull(values.unitLabel),
      basePrice: toNumberOrNull(values.basePrice),
      requiresSiteVisit: values.requiresSiteVisit,
      estimatedDurationDays: toNumberOrNull(values.estimatedDurationDays),
    }
    try {
      if (service) {
        await updateService(service.id, { ...input, version: service.version })
      } else {
        await createService({ ...input, code: values.code })
      }
      await queryClient.invalidateQueries({ queryKey: catalogKeys.services })
      navigate(LIST)
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  // Group the categories by vertical so the six verticals are visible in the picker.
  const categoriesByVertical = new Map<string, { id: string; name: string; active: boolean }[]>()
  for (const category of categories.data?.items ?? []) {
    const group = categoriesByVertical.get(category.vertical.name) ?? []
    group.push({ id: category.id, name: category.name, active: category.active })
    categoriesByVertical.set(category.vertical.name, group)
  }

  return (
    <FormCard>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" noValidate>
          <FormError message={formError} />

          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Name</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="code"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Code</FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    disabled={!!service}
                    className="font-mono"
                    onChange={(event) => field.onChange(event.target.value.toUpperCase().replace(/[\s-]+/g, '_'))}
                  />
                </FormControl>
                <FormDescription>
                  {service
                    ? 'The code identifies the service everywhere and cannot be changed.'
                    : 'A short identifier such as SOLAR_ROOFTOP_3KW. It cannot be changed later.'}
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="categoryId"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Category</FormLabel>
                <FormControl>
                  <Select {...field}>
                    <option value="">Choose a category…</option>
                    {[...categoriesByVertical.entries()].map(([verticalName, items]) => (
                      <optgroup key={verticalName} label={verticalName}>
                        {items.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name}
                            {item.active ? '' : ' (inactive)'}
                          </option>
                        ))}
                      </optgroup>
                    ))}
                  </Select>
                </FormControl>
                <FormDescription>The category decides which vertical the service belongs to.</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="description"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Description</FormLabel>
                <FormControl>
                  <Textarea {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              control={form.control}
              name="billingType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Billing</FormLabel>
                  <FormControl>
                    <Select {...field}>
                      {BILLING_TYPES.map((type) => (
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
            <FormField
              control={form.control}
              name="basePrice"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Base price (₹)</FormLabel>
                  <FormControl>
                    <Input inputMode="decimal" {...field} />
                  </FormControl>
                  <FormDescription>Leave empty for quote-based services.</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="unitLabel"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Unit</FormLabel>
                  <FormControl>
                    <Input placeholder="per camera, per kW, per month" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="estimatedDurationDays"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Typical duration (days)</FormLabel>
                  <FormControl>
                    <Input inputMode="numeric" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="displayOrder"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Display order</FormLabel>
                  <FormControl>
                    <Input inputMode="numeric" {...field} />
                  </FormControl>
                  <FormDescription>Lower numbers are listed first.</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>

          <FormField
            control={form.control}
            name="requiresSiteVisit"
            render={({ field }) => (
              <FormItem className="flex items-center gap-2">
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
                <FormLabel>Needs a site visit before work starts</FormLabel>
              </FormItem>
            )}
          />

          <FormActions
            submitLabel={service ? 'Save changes' : 'Create service'}
            submitting={form.formState.isSubmitting}
            cancelTo={LIST}
          />
        </form>
      </Form>
    </FormCard>
  )
}
