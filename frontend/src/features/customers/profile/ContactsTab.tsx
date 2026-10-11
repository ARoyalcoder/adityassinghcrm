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
import { Skeleton } from '@/components/ui/skeleton'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  contactsQuery,
  customerKeys,
  removeContact,
  saveContact,
  type Contact,
} from '@/features/customers/api'
import type { CustomerTabProps } from '@/features/customers/profile/tabs'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(200, 'At most 200 characters'),
  designation: z.string().trim().max(100, 'At most 100 characters'),
  phone: z
    .string()
    .trim()
    .refine((value) => value === '' || /^[+0-9][0-9 ()-]{5,28}$/.test(value), 'Enter a phone number'),
  email: z
    .string()
    .trim()
    .max(254)
    .refine((value) => value === '' || z.string().email().safeParse(value).success, 'Enter a valid email address'),
  primaryContact: z.boolean(),
})
type Values = z.infer<typeof schema>

/** `'new'` while adding, a contact while editing it, `null` when just listing. */
type Editing = Contact | 'new' | null

export function ContactsTab({ customer }: CustomerTabProps) {
  const queryClient = useQueryClient()
  const canEdit = useHasPermission('CUSTOMER_UPDATE')
  const contacts = useQuery(contactsQuery(customer.id))
  const [editing, setEditing] = useState<Editing>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: customerKeys.detail(customer.id) })
  const remove = useMutation({ mutationFn: (id: string) => removeContact(customer.id, id), onSuccess: refresh })

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Contacts</CardTitle>
        {canEdit && editing === null ? (
          <Button size="sm" onClick={() => setEditing('new')}>
            <Plus aria-hidden="true" />
            Add contact
          </Button>
        ) : null}
      </CardHeader>
      <CardContent className="grid gap-4">
        {editing !== null ? (
          <ContactForm
            key={editing === 'new' ? 'new' : editing.id}
            customerId={customer.id}
            contact={editing === 'new' ? undefined : editing}
            onDone={() => {
              setEditing(null)
              void refresh()
            }}
            onCancel={() => setEditing(null)}
          />
        ) : null}
        {remove.error ? <ErrorState title="The contact could not be removed" error={remove.error} /> : null}

        {contacts.isPending ? (
          <Skeleton className="h-16 w-full" aria-busy="true" aria-label="Loading contacts" />
        ) : contacts.isError ? (
          <ErrorState title="Could not load contacts" error={contacts.error} onRetry={() => void contacts.refetch()} />
        ) : contacts.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No contacts yet. Add the people you deal with at this customer.
          </p>
        ) : (
          <ul className="grid gap-3" aria-label="Contacts">
            {contacts.data.map((contact) => (
              <li key={contact.id} className="flex flex-wrap items-start justify-between gap-3 rounded-md border p-3 text-sm">
                <div className="grid gap-0.5">
                  <p className="flex items-center gap-2 font-medium">
                    {contact.name}
                    {contact.primaryContact ? <Badge>Primary</Badge> : null}
                  </p>
                  {contact.designation ? <p className="text-muted-foreground">{contact.designation}</p> : null}
                  <p className="text-muted-foreground">
                    {[contact.phone, contact.email].filter(Boolean).join(' · ') || 'No phone or email'}
                  </p>
                </div>
                {canEdit ? (
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" aria-label={`Edit ${contact.name}`} onClick={() => setEditing(contact)}>
                      Edit
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={remove.isPending}
                      aria-label={`Remove ${contact.name}`}
                      onClick={() => remove.mutate(contact.id)}
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

type ContactFormProps = { customerId: string; contact?: Contact; onDone: () => void; onCancel: () => void }

function ContactForm({ customerId, contact, onDone, onCancel }: ContactFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: contact?.name ?? '',
      designation: contact?.designation ?? '',
      phone: contact?.phone ?? '',
      email: contact?.email ?? '',
      primaryContact: contact?.primaryContact ?? false,
    },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      await saveContact(
        customerId,
        {
          name: values.name,
          designation: blankToNull(values.designation),
          phone: blankToNull(values.phone),
          email: blankToNull(values.email),
          primaryContact: values.primaryContact,
        },
        contact,
      )
      onDone()
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  const field = (name: 'name' | 'designation' | 'phone' | 'email', label: string) => (
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
        aria-label={contact ? `Edit ${contact.name}` : 'New contact'}
        noValidate
      >
        <FormError message={formError} />
        <div className="grid gap-4 sm:grid-cols-2">
          {field('name', 'Name')}
          {field('designation', 'Designation')}
          {field('phone', 'Phone')}
          {field('email', 'Email')}
        </div>
        <FormField
          control={form.control}
          name="primaryContact"
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
              <FormLabel>Primary contact</FormLabel>
            </FormItem>
          )}
        />
        <div className="flex gap-2">
          <Button type="submit" size="sm" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? 'Saving…' : contact ? 'Save contact' : 'Add contact'}
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Form>
  )
}
