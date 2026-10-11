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
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { formatDateTime } from '@/features/audit/api'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  customerKeys,
  INTERACTION_DIRECTIONS,
  INTERACTION_TYPES,
  interactionsQuery,
  labelOf,
  logInteraction,
  removeInteraction,
} from '@/features/customers/api'
import type { CustomerTabProps } from '@/features/customers/profile/tabs'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const schema = z.object({
  type: z.string().min(1, 'Choose what kind of activity this was'),
  direction: z.string(),
  occurredAt: z
    .string()
    .refine((value) => value === '' || !Number.isNaN(new Date(value).getTime()), 'Enter a date and time')
    // Activities record what has happened. (A minute of grace for clocks that disagree.)
    .refine((value) => value === '' || new Date(value).getTime() <= Date.now() + 60_000, 'This cannot be in the future'),
  subject: z.string().trim().min(1, 'Say briefly what it was about').max(200, 'At most 200 characters'),
  details: z.string().trim().max(4000, 'At most 4000 characters'),
  outcome: z.string().trim().max(500, 'At most 500 characters'),
})
type Values = z.infer<typeof schema>

/** Calls, meetings, emails and visits with the customer: log one, and see those already logged. */
export function ActivitiesTab({ customer }: CustomerTabProps) {
  const queryClient = useQueryClient()
  const canEdit = useHasPermission('CUSTOMER_UPDATE')
  const interactions = useQuery(interactionsQuery(customer.id))
  const [logging, setLogging] = useState(false)

  // Everything about the customer is refreshed: the timeline, last contact and health all depend on this.
  const refresh = () => queryClient.invalidateQueries({ queryKey: customerKeys.detail(customer.id) })
  const remove = useMutation({ mutationFn: (id: string) => removeInteraction(customer.id, id), onSuccess: refresh })

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-3">
        <div className="grid gap-1">
          <CardTitle>Activities</CardTitle>
          <CardDescription>Calls, meetings, emails and visits with this customer, newest first.</CardDescription>
        </div>
        {canEdit && !logging ? (
          <Button size="sm" onClick={() => setLogging(true)}>
            <Plus aria-hidden="true" />
            Log activity
          </Button>
        ) : null}
      </CardHeader>
      <CardContent className="grid gap-4">
        {logging ? (
          <InteractionForm
            customerId={customer.id}
            onDone={() => {
              setLogging(false)
              void refresh()
            }}
            onCancel={() => setLogging(false)}
          />
        ) : null}
        {remove.error ? <ErrorState title="The activity could not be removed" error={remove.error} /> : null}

        {interactions.isPending ? (
          <Skeleton className="h-16 w-full" aria-busy="true" aria-label="Loading activities" />
        ) : interactions.isError ? (
          <ErrorState
            title="Could not load activities"
            error={interactions.error}
            onRetry={() => void interactions.refetch()}
          />
        ) : interactions.data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No activities logged yet. Log each call, meeting and visit so the whole team can see where things stand.
          </p>
        ) : (
          <ul className="grid gap-3" aria-label="Activities">
            {interactions.data.items.map((interaction) => (
              <li key={interaction.id} className="flex flex-wrap items-start justify-between gap-3 rounded-md border p-3 text-sm">
                <div className="grid min-w-0 gap-1">
                  <p className="flex flex-wrap items-center gap-2 font-medium">
                    <Badge variant="secondary">{labelOf(INTERACTION_TYPES, interaction.type)}</Badge>
                    {interaction.direction ? (
                      <span className="text-xs font-normal text-muted-foreground">
                        {labelOf(INTERACTION_DIRECTIONS, interaction.direction)}
                      </span>
                    ) : null}
                    {interaction.subject}
                  </p>
                  {interaction.details ? <p className="whitespace-pre-wrap">{interaction.details}</p> : null}
                  {interaction.outcome ? (
                    <p>
                      <span className="text-muted-foreground">Outcome: </span>
                      {interaction.outcome}
                    </p>
                  ) : null}
                  <p className="text-xs text-muted-foreground">
                    {formatDateTime(interaction.occurredAt)} · logged by {interaction.loggedByName}
                  </p>
                </div>
                {canEdit ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={remove.isPending}
                    aria-label={`Remove ${interaction.subject}`}
                    onClick={() => remove.mutate(interaction.id)}
                  >
                    Remove
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

type InteractionFormProps = { customerId: string; onDone: () => void; onCancel: () => void }

function InteractionForm({ customerId, onDone, onCancel }: InteractionFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { type: 'CALL', direction: '', occurredAt: '', subject: '', details: '', outcome: '' },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      await logInteraction(customerId, {
        type: values.type,
        direction: blankToNull(values.direction),
        // The picker gives local time; the server is sent the instant. Left empty, the server uses "now".
        occurredAt: values.occurredAt === '' ? null : new Date(values.occurredAt).toISOString(),
        subject: values.subject,
        details: blankToNull(values.details),
        outcome: blankToNull(values.outcome),
      })
      onDone()
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="grid gap-4 rounded-md border bg-muted/30 p-4"
        aria-label="Log activity"
        noValidate
      >
        <FormError message={formError} />
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField
            control={form.control}
            name="type"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Type</FormLabel>
                <FormControl>
                  <Select {...field}>
                    {INTERACTION_TYPES.map((option) => (
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
          <FormField
            control={form.control}
            name="direction"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Direction</FormLabel>
                <FormControl>
                  <Select {...field}>
                    <option value="">Not applicable</option>
                    {INTERACTION_DIRECTIONS.map((option) => (
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
          <FormField
            control={form.control}
            name="occurredAt"
            render={({ field }) => (
              <FormItem>
                <FormLabel>When (leave empty for now)</FormLabel>
                <FormControl>
                  <Input type="datetime-local" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
        <FormField
          control={form.control}
          name="subject"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Subject</FormLabel>
              <FormControl>
                <Input placeholder="What was it about?" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="details"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Details</FormLabel>
              <FormControl>
                <Textarea {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="outcome"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Outcome</FormLabel>
              <FormControl>
                <Input placeholder="What was agreed or decided?" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <div className="flex gap-2">
          <Button type="submit" size="sm" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? 'Saving…' : 'Save activity'}
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Form>
  )
}
