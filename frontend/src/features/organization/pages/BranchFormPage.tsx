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
import { optionalText, requiredText } from '@/features/catalog/form-fields'
import {
  branchKeys,
  branchLocationsQuery,
  branchQuery,
  createBranch,
  updateBranch,
  type Branch,
  type BranchInput,
} from '@/features/organization/api'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const LIST = '/admin/branches'

const schema = z.object({
  code: z
    .string()
    .trim()
    .min(1, 'Enter a code')
    .max(50, 'At most 50 characters')
    .regex(/^[A-Z][A-Z0-9_-]*$/, 'Use capital letters, digits, hyphens and underscores, starting with a letter'),
  name: requiredText('a name', 200),
  addressLine1: optionalText(200),
  addressLine2: optionalText(200),
  city: requiredText('a city', 100),
  state: requiredText('a state', 100),
  postalCode: optionalText(20),
  phone: z
    .string()
    .trim()
    .refine((value) => value === '' || /^[+0-9][0-9 ()-]{5,28}$/.test(value), 'Enter a phone number'),
  email: z
    .string()
    .trim()
    .max(254)
    .refine((value) => value === '' || z.string().email().safeParse(value).success, 'Enter a valid email address'),
  headOffice: z.boolean(),
})
type Values = z.infer<typeof schema>

/** Open a branch (`/admin/branches/new`) or edit one (`/admin/branches/:id/edit`). */
export function BranchFormPage() {
  const { id } = useParams()
  const existing = useQuery({ ...branchQuery(id ?? ''), enabled: id !== undefined })

  return (
    <div className="grid gap-6">
      <PageHeader title={id ? 'Edit branch' : 'New branch'} back={{ to: LIST, label: 'Branches' }} />
      {id && !existing.data ? (
        <RecordLoadState
          noun="branch"
          isPending={existing.isPending}
          error={existing.error}
          onRetry={() => void existing.refetch()}
        />
      ) : (
        <>
          <BranchForm key={existing.data?.version ?? 'new'} branch={existing.data} />
          {existing.data ? <ActivityTimeline entityType="Branch" entityId={existing.data.id} /> : null}
        </>
      )}
    </div>
  )
}

function BranchForm({ branch }: { branch?: Branch }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  // Suggest places already in use so "Lucknow" is not also entered as "lucknow" or "Lko".
  const locations = useQuery(branchLocationsQuery)
  const knownStates = (locations.data ?? []).map((item) => item.state)
  const knownCities = [...new Set((locations.data ?? []).flatMap((item) => item.cities))]

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: branch?.code ?? '',
      name: branch?.name ?? '',
      addressLine1: branch?.addressLine1 ?? '',
      addressLine2: branch?.addressLine2 ?? '',
      city: branch?.city ?? '',
      state: branch?.state ?? '',
      postalCode: branch?.postalCode ?? '',
      phone: branch?.phone ?? '',
      email: branch?.email ?? '',
      headOffice: branch?.headOffice ?? false,
    },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    const input: BranchInput = {
      name: values.name,
      addressLine1: blankToNull(values.addressLine1),
      addressLine2: blankToNull(values.addressLine2),
      city: values.city,
      state: values.state,
      postalCode: blankToNull(values.postalCode),
      phone: blankToNull(values.phone),
      email: blankToNull(values.email),
      headOffice: values.headOffice,
    }
    try {
      if (branch) {
        await updateBranch(branch.id, { ...input, version: branch.version })
      } else {
        await createBranch({ ...input, code: values.code })
      }
      await queryClient.invalidateQueries({ queryKey: branchKeys.all })
      navigate(LIST)
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  const textField = (name: 'name' | 'addressLine1' | 'addressLine2' | 'postalCode' | 'phone' | 'email', label: string) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input {...field} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )

  return (
    <FormCard>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" noValidate>
          <FormError message={formError} />

          {textField('name', 'Name')}

          <FormField
            control={form.control}
            name="code"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Code</FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    disabled={!!branch}
                    className="font-mono"
                    onChange={(event) => field.onChange(event.target.value.toUpperCase().replace(/\s+/g, '-'))}
                  />
                </FormControl>
                <FormDescription>
                  {branch ? 'The code cannot be changed.' : 'A short identifier such as LKO-HZG. It cannot be changed later.'}
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          {textField('addressLine1', 'Address line 1')}
          {textField('addressLine2', 'Address line 2')}

          <div className="grid gap-4 sm:grid-cols-3">
            <FormField
              control={form.control}
              name="city"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>City</FormLabel>
                  <FormControl>
                    <Input list="known-cities" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="state"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>State</FormLabel>
                  <FormControl>
                    <Input list="known-states" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {textField('postalCode', 'PIN code')}
          </div>
          <datalist id="known-cities">
            {knownCities.map((city) => (
              <option key={city} value={city} />
            ))}
          </datalist>
          <datalist id="known-states">
            {knownStates.map((state) => (
              <option key={state} value={state} />
            ))}
          </datalist>

          <div className="grid gap-4 sm:grid-cols-2">
            {textField('phone', 'Phone')}
            {textField('email', 'Email')}
          </div>

          <FormField
            control={form.control}
            name="headOffice"
            render={({ field }) => (
              <FormItem>
                <div className="flex items-center gap-2">
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
                  <FormLabel>This is the head office</FormLabel>
                </div>
                <FormDescription>
                  There is one head office. Ticking this moves the title from the current one.
                </FormDescription>
              </FormItem>
            )}
          />

          <FormActions
            submitLabel={branch ? 'Save changes' : 'Create branch'}
            submitting={form.formState.isSubmitting}
            cancelTo={LIST}
          />
        </form>
      </Form>
    </FormCard>
  )
}
