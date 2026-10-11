import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { Bell, Calendar, Repeat, X } from 'lucide-react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { FormError } from '@/components/forms/FormParts'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  ACTIVITY_TYPES,
  ENTITY_TYPES,
  INTERACTION_DIRECTIONS,
  RECURRENCE_PATTERNS,
  TASK_PRIORITIES,
  TASK_STATUSES,
  type ActivityDetail,
  type CreateActivityPayload,
} from '@/features/activities/api'
import { assigneesQuery } from '@/features/customers/api'
import { blankToNull } from '@/lib/forms'

const schema = z.object({
  type: z.string().min(1, 'Select an activity type'),
  title: z.string().trim().min(1, 'Title is required').max(250, 'At most 250 characters'),
  description: z.string().trim().max(4000),
  entityType: z.string().min(1, 'Select a related entity type'),
  entityName: z.string().trim().max(255),
  entityId: z.string(),
  ownerId: z.string(),
  priority: z.string(),
  status: z.string(),
  dueDate: z.string(),
  occurredAt: z.string(),
  direction: z.string(),
  reminderAt: z.string(),
  isRecurring: z.boolean(),
  recurrencePattern: z.string(),
  recurrenceInterval: z.coerce.number().min(1),
  recurrenceEndAt: z.string(),
})

type FormValues = z.infer<typeof schema>

type ActivityModalProps = {
  initial?: ActivityDetail | null
  defaultType?: string
  defaultEntityType?: string
  defaultEntityId?: string
  defaultEntityName?: string
  onClose: () => void
  onSubmit: (data: CreateActivityPayload) => Promise<void>
}

export function ActivityModal({
  initial,
  defaultType = 'TASK',
  defaultEntityType = 'CUSTOMER',
  defaultEntityId,
  defaultEntityName,
  onClose,
  onSubmit,
}: ActivityModalProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const assignees = useQuery(assigneesQuery)

  const defaultValues: FormValues = {
    type: initial?.type ?? defaultType,
    title: initial?.title ?? '',
    description: initial?.description ?? '',
    entityType: initial?.entityType ?? defaultEntityType,
    entityId: initial?.entityId ?? defaultEntityId ?? '',
    entityName: initial?.entityName ?? defaultEntityName ?? '',
    ownerId: initial?.ownerId ?? '',
    priority: initial?.priority ?? 'MEDIUM',
    status: initial?.status ?? 'PENDING',
    dueDate: initial?.dueDate ? initial.dueDate.slice(0, 16) : '',
    occurredAt: initial?.occurredAt ? initial.occurredAt.slice(0, 16) : '',
    direction: initial?.direction ?? '',
    reminderAt: initial?.reminderAt ? initial.reminderAt.slice(0, 16) : '',
    isRecurring: initial?.isRecurring ?? false,
    recurrencePattern: initial?.recurrencePattern ?? 'WEEKLY',
    recurrenceInterval: initial?.recurrenceInterval ?? 1,
    recurrenceEndAt: initial?.recurrenceEndAt ? initial.recurrenceEndAt.slice(0, 16) : '',
  }

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues,
  })

  const isRecurring = useWatch({ control: form.control, name: 'isRecurring' })
  const activityType = useWatch({ control: form.control, name: 'type' })
  const isCommunication = ['CALL', 'EMAIL', 'WHATSAPP'].includes(activityType)

  async function handleFormSubmit(values: FormValues) {
    setFormError(null)
    try {
      const entityId = values.entityId && values.entityId.trim() !== ''
        ? values.entityId.trim()
        : crypto.randomUUID()

      await onSubmit({
        type: values.type,
        title: values.title,
        description: blankToNull(values.description),
        entityType: values.entityType,
        entityId,
        entityName: blankToNull(values.entityName),
        ownerId: blankToNull(values.ownerId),
        priority: values.priority,
        status: values.status,
        dueDate: values.dueDate ? new Date(values.dueDate).toISOString() : null,
        occurredAt: values.occurredAt ? new Date(values.occurredAt).toISOString() : null,
        direction: isCommunication ? blankToNull(values.direction) : null,
        reminderAt: values.reminderAt ? new Date(values.reminderAt).toISOString() : null,
        isRecurring: values.isRecurring,
        recurrencePattern: values.isRecurring ? values.recurrencePattern : null,
        recurrenceInterval: values.isRecurring ? values.recurrenceInterval : null,
        recurrenceEndAt: values.isRecurring && values.recurrenceEndAt ? new Date(values.recurrenceEndAt).toISOString() : null,
      })
      onClose()
    } catch (err: unknown) {
      if (err instanceof Error) {
        setFormError(err.message)
      } else {
        setFormError('Failed to save activity')
      }
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-h-[90vh] w-full max-w-2xl overflow-y-auto p-6 shadow-xl">
        <div className="flex items-center justify-between pb-4 border-b">
          <div>
            <h2 className="text-lg font-semibold">{initial ? 'Edit Activity / Task' : 'New Activity or Task'}</h2>
            <p className="text-xs text-muted-foreground">
              Log client touchpoints, assign tasks, schedule follow-ups, and set reminders
            </p>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close">
            <X className="h-4 w-4" />
          </Button>
        </div>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleFormSubmit)} className="space-y-4 pt-4" noValidate>
            <FormError message={formError} />

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="type"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Activity Type *</FormLabel>
                    <FormControl>
                      <Select {...field}>
                        {ACTIVITY_TYPES.map((t) => (
                          <option key={t.value} value={t.value}>
                            {t.label}
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
                name="priority"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Priority</FormLabel>
                    <FormControl>
                      <Select {...field}>
                        {TASK_PRIORITIES.map((p) => (
                          <option key={p.value} value={p.value}>
                            {p.label}
                          </option>
                        ))}
                      </Select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="title"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Title / Subject *</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. Call regarding solar rooftop proposal" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description / Details</FormLabel>
                  <FormControl>
                    <Textarea placeholder="Additional context or agenda..." rows={3} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="entityType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Related Entity *</FormLabel>
                    <FormControl>
                      <Select {...field}>
                        {ENTITY_TYPES.map((e) => (
                          <option key={e.value} value={e.value}>
                            {e.label}
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
                name="entityName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Entity Name / Label</FormLabel>
                    <FormControl>
                      <Input placeholder="e.g. Aditya Singh or Project Solar Phase 1" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="ownerId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Assigned Owner</FormLabel>
                    <FormControl>
                      <Select {...field}>
                        <option value="">Current user (Default)</option>
                        {assignees.data?.map((u) => (
                          <option key={u.id} value={u.id}>
                            {u.name}
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
                name="status"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Status</FormLabel>
                    <FormControl>
                      <Select {...field}>
                        {TASK_STATUSES.map((s) => (
                          <option key={s.value} value={s.value}>
                            {s.label}
                          </option>
                        ))}
                      </Select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="dueDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="flex items-center gap-1.5">
                      <Calendar className="h-3.5 w-3.5 text-muted-foreground" />
                      Due Date & Time
                    </FormLabel>
                    <FormControl>
                      <Input type="datetime-local" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="reminderAt"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="flex items-center gap-1.5">
                      <Bell className="h-3.5 w-3.5 text-muted-foreground" />
                      Reminder Date & Time
                    </FormLabel>
                    <FormControl>
                      <Input type="datetime-local" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            {isCommunication ? (
              <FormField
                control={form.control}
                name="direction"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Direction</FormLabel>
                    <FormControl>
                      <Select {...field}>
                        <option value="">Not specified</option>
                        {INTERACTION_DIRECTIONS.map((d) => (
                          <option key={d.value} value={d.value}>
                            {d.label}
                          </option>
                        ))}
                      </Select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {/* Recurring Task Configuration */}
            <div className="rounded-lg border bg-muted/40 p-4 space-y-3">
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="isRecurring"
                  className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
                  {...form.register('isRecurring')}
                />
                <label htmlFor="isRecurring" className="text-sm font-medium flex items-center gap-1.5 cursor-pointer">
                  <Repeat className="h-4 w-4 text-primary" />
                  Make this a recurring task
                </label>
              </div>

              {isRecurring ? (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 pt-2">
                  <FormField
                    control={form.control}
                    name="recurrencePattern"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Frequency</FormLabel>
                        <FormControl>
                          <Select {...field}>
                            {RECURRENCE_PATTERNS.map((p) => (
                              <option key={p.value} value={p.value}>
                                {p.label}
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
                    name="recurrenceInterval"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Interval (Every N)</FormLabel>
                        <FormControl>
                          <Input type="number" min="1" max="100" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="recurrenceEndAt"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>End Date (Optional)</FormLabel>
                        <FormControl>
                          <Input type="datetime-local" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              ) : null}
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t">
              <Button type="button" variant="outline" onClick={onClose}>
                Cancel
              </Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Saving…' : initial ? 'Update Activity' : 'Create Activity'}
              </Button>
            </div>
          </form>
        </Form>
      </Card>
    </div>
  )
}
