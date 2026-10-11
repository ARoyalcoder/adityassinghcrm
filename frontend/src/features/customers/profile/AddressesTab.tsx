import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ErrorState } from '@/components/feedback/ErrorState'
import { FormError } from '@/components/forms/FormParts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  ADDRESS_TYPES,
  addressesQuery,
  customerKeys,
  labelOf,
  removeAddress,
  saveAddress,
  type Address,
} from '@/features/customers/api'
import type { CustomerTabProps } from '@/features/customers/profile/tabs'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const text = (max: number) => z.string().trim().max(max, `At most ${max} characters`)

const schema = z.object({
  type: z.string().min(1),
  label: text(100),
  line1: text(200).min(1, 'Enter the address'),
  line2: text(200),
  city: text(100).min(1, 'Enter a city'),
  state: text(100).min(1, 'Enter a state'),
  postalCode: text(20),
  defaultAddress: z.boolean(),
})
type Values = z.infer<typeof schema>

type Editing = Address | 'new' | null

export function AddressesTab({ customer }: CustomerTabProps) {
  const queryClient = useQueryClient()
  const canEdit = useHasPermission('CUSTOMER_UPDATE')
  const addresses = useQuery(addressesQuery(customer.id))
  const [editing, setEditing] = useState<Editing>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: customerKeys.detail(customer.id) })
  const remove = useMutation({ mutationFn: (id: string) => removeAddress(customer.id, id), onSuccess: refresh })

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Addresses</CardTitle>
        {canEdit && editing === null ? (
          <Button size="sm" onClick={() => setEditing('new')}>
            <Plus aria-hidden="true" />
            Add address
          </Button>
        ) : null}
      </CardHeader>
      <CardContent className="grid gap-4">
        {editing !== null ? (
          <AddressForm
            key={editing === 'new' ? 'new' : editing.id}
            customerId={customer.id}
            address={editing === 'new' ? undefined : editing}
            onDone={() => {
              setEditing(null)
              void refresh()
            }}
            onCancel={() => setEditing(null)}
          />
        ) : null}
        {remove.error ? <ErrorState title="The address could not be removed" error={remove.error} /> : null}

        {addresses.isPending ? (
          <Skeleton className="h-16 w-full" aria-busy="true" aria-label="Loading addresses" />
        ) : addresses.isError ? (
          <ErrorState title="Could not load addresses" error={addresses.error} onRetry={() => void addresses.refetch()} />
        ) : addresses.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No addresses yet. Add a billing address for invoices and a service address for where the work is done.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2" aria-label="Addresses">
            {addresses.data.map((address) => (
              <li key={address.id} className="grid gap-2 rounded-md border p-3 text-sm">
                <p className="flex flex-wrap items-center gap-2 font-medium">
                  <Badge variant="outline">{labelOf(ADDRESS_TYPES, address.type)}</Badge>
                  {address.label}
                  {address.defaultAddress ? <Badge>Default</Badge> : null}
                </p>
                <address className="text-muted-foreground not-italic">
                  {address.line1}
                  {address.line2 ? <>, {address.line2}</> : null}
                  <br />
                  {address.city}, {address.state} {address.postalCode ?? ''}
                </address>
                {canEdit ? (
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      aria-label={`Edit ${labelOf(ADDRESS_TYPES, address.type)} address in ${address.city}`}
                      onClick={() => setEditing(address)}
                    >
                      Edit
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={remove.isPending}
                      aria-label={`Remove ${labelOf(ADDRESS_TYPES, address.type)} address in ${address.city}`}
                      onClick={() => remove.mutate(address.id)}
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
  )
}

type AddressFormProps = { customerId: string; address?: Address; onDone: () => void; onCancel: () => void }

function AddressForm({ customerId, address, onDone, onCancel }: AddressFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: address?.type ?? 'BILLING',
      label: address?.label ?? '',
      line1: address?.line1 ?? '',
      line2: address?.line2 ?? '',
      city: address?.city ?? '',
      state: address?.state ?? '',
      postalCode: address?.postalCode ?? '',
      defaultAddress: address?.defaultAddress ?? false,
    },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      await saveAddress(
        customerId,
        {
          type: values.type,
          label: blankToNull(values.label),
          line1: values.line1,
          line2: blankToNull(values.line2),
          city: values.city,
          state: values.state,
          postalCode: blankToNull(values.postalCode),
          defaultAddress: values.defaultAddress,
        },
        address,
      )
      onDone()
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  const field = (name: 'label' | 'line1' | 'line2' | 'city' | 'state' | 'postalCode', label: string) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field: input }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input {...input} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="grid gap-4 rounded-md border bg-muted/30 p-4"
        aria-label={address ? 'Edit address' : 'New address'}
        noValidate
      >
        <FormError message={formError} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="type"
            render={({ field: input }) => (
              <FormItem>
                <FormLabel>Used for</FormLabel>
                <FormControl>
                  <Select {...input}>
                    {ADDRESS_TYPES.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </Select>
                </FormControl>
              </FormItem>
            )}
          />
          {field('label', 'Label (optional)')}
        </div>
        {field('line1', 'Address line 1')}
        {field('line2', 'Address line 2')}
        <div className="grid gap-4 sm:grid-cols-3">
          {field('city', 'City')}
          {field('state', 'State')}
          {field('postalCode', 'PIN code')}
        </div>
        <FormField
          control={form.control}
          name="defaultAddress"
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
              <FormLabel>Default address of this type</FormLabel>
            </FormItem>
          )}
        />
        <div className="flex gap-2">
          <Button type="submit" size="sm" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? 'Saving…' : address ? 'Save address' : 'Add address'}
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Form>
  )
}
