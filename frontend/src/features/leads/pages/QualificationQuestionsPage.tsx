import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { useFieldArray, useForm } from 'react-hook-form'
import { z } from 'zod'
import { ErrorState } from '@/components/feedback/ErrorState'
import { StatusBadge } from '@/components/feedback/StatusBadge'
import { FormError } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  adminQuestionsQuery,
  createQuestion,
  deleteQuestion,
  leadKeys,
  setQuestionActive,
  updateQuestion,
  type AdminQuestion,
} from '@/features/leads/api'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const optionSchema = z.object({
  /** Kept from the stored option, so rewording nothing keeps existing answers attached. */
  value: z.string(),
  label: z.string().trim().min(1, 'Enter the answer').max(100, 'At most 100 characters'),
  points: z
    .string()
    .trim()
    .refine((text) => /^-?\d+$/.test(text) && Math.abs(Number(text)) <= 100, 'A whole number from -100 to 100'),
  disqualifies: z.boolean(),
})

const schema = z.object({
  label: z.string().trim().min(1, 'Enter the question').max(200, 'At most 200 characters'),
  key: z
    .string()
    .trim()
    .min(1, 'Enter a key')
    .max(60, 'At most 60 characters')
    .regex(/^[a-z][a-z0-9_]*$/, 'Lower-case letters, digits and underscores, starting with a letter'),
  helpText: z.string().trim().max(300, 'At most 300 characters'),
  required: z.boolean(),
  options: z.array(optionSchema).min(2, 'Give at least 2 answers to choose from').max(20, 'At most 20 answers'),
})
type Values = z.infer<typeof schema>

function slug(label: string, upper: boolean, fallback: string) {
  const cleaned = label.replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '')
  const value = upper ? cleaned.toUpperCase() : cleaned.toLowerCase().replace(/^[^a-z]+/, '')
  return (value || fallback).slice(0, 60)
}

function signed(points: number) {
  return points > 0 ? `+${points}` : points < 0 ? `−${Math.abs(points)}` : '0'
}

/** `'new'` while adding, a question while editing it, `null` when just listing. */
type Editing = AdminQuestion | 'new' | null

/**
 * The questions asked to qualify a lead, and what each answer is worth
 * (`/admin/qualification-questions`). These are the configurable part of the score: the points set
 * here appear, answer by answer, in every lead's "Why this score?".
 */
export function QualificationQuestionsPage() {
  const queryClient = useQueryClient()
  const questions = useQuery(adminQuestionsQuery)
  const [editing, setEditing] = useState<Editing>(null)

  // Changing a question rescored the open leads on the server, so anything showing a lead is stale too.
  const refresh = () => queryClient.invalidateQueries({ queryKey: leadKeys.all })
  const toggle = useMutation({
    mutationFn: (question: AdminQuestion) => setQuestionActive(question.id, !question.active),
    onSuccess: refresh,
  })
  const remove = useMutation({ mutationFn: (id: string) => deleteQuestion(id), onSuccess: refresh })

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Qualification questions"
        description="What to find out about every lead, and how much each answer adds to or takes from its score."
        action={
          editing === null ? (
            <Button onClick={() => setEditing('new')}>
              <Plus aria-hidden="true" />
              Add question
            </Button>
          ) : null
        }
      />

      {editing !== null ? (
        <QuestionEditor
          key={editing === 'new' ? 'new' : editing.id}
          existing={editing === 'new' ? undefined : editing}
          onDone={() => {
            setEditing(null)
            void refresh()
          }}
          onCancel={() => setEditing(null)}
        />
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Questions</CardTitle>
          <CardDescription>
            In the order they are asked. Changing a question rescores every open lead straight away.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          {toggle.error || remove.error ? (
            <ErrorState title="The question could not be changed" error={toggle.error ?? remove.error} />
          ) : null}
          {questions.isPending ? (
            <Skeleton className="h-16 w-full" aria-busy="true" aria-label="Loading questions" />
          ) : questions.isError ? (
            <ErrorState title="Could not load the questions" error={questions.error} onRetry={() => void questions.refetch()} />
          ) : questions.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No questions yet. Until there are some, a lead's score comes only from its value, its contact details and
              how recently it was worked.
            </p>
          ) : (
            <ul className="grid gap-3" aria-label="Questions">
              {questions.data.map((question) => (
                <li key={question.id} className="flex flex-wrap items-start justify-between gap-3 rounded-md border p-3 text-sm">
                  <div className="grid gap-1.5">
                    <p className="flex flex-wrap items-center gap-2 font-medium">
                      {question.label}
                      {question.required ? <Badge variant="outline">Required</Badge> : null}
                      <StatusBadge active={question.active} />
                    </p>
                    <p className="font-mono text-xs text-muted-foreground">{question.key}</p>
                    <ul className="flex flex-wrap gap-x-4 gap-y-1" aria-label={`Answers to ${question.label}`}>
                      {question.options.map((option) => (
                        <li key={option.value}>
                          {option.label} <span className="font-mono tabular-nums text-muted-foreground">{signed(option.points)}</span>
                          {option.disqualifies ? <span className="text-red-700"> · rules the lead out</span> : null}
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" aria-label={`Edit ${question.label}`} onClick={() => setEditing(question)}>
                      Edit
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={toggle.isPending}
                      aria-label={`${question.active ? 'Switch off' : 'Switch on'} ${question.label}`}
                      onClick={() => toggle.mutate(question)}
                    >
                      {question.active ? 'Switch off' : 'Switch on'}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={remove.isPending}
                      aria-label={`Remove ${question.label}`}
                      onClick={() => remove.mutate(question.id)}
                    >
                      Remove
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

const YES_NO = [
  { value: 'YES', label: 'Yes', points: '10', disqualifies: false },
  { value: 'NO', label: 'No', points: '0', disqualifies: false },
]

type EditorProps = { existing?: AdminQuestion; onDone: () => void; onCancel: () => void }

function QuestionEditor({ existing, onDone, onCancel }: EditorProps) {
  const [formError, setFormError] = useState<string | null>(null)
  // Until the key has been typed by hand, it follows the question.
  const [keyTyped, setKeyTyped] = useState(false)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      label: existing?.label ?? '',
      key: existing?.key ?? '',
      helpText: existing?.helpText ?? '',
      required: existing?.required ?? false,
      options: existing
        ? existing.options.map((option) => ({ ...option, points: String(option.points) }))
        : YES_NO,
    },
  })
  const options = useFieldArray({ control: form.control, name: 'options' })

  async function onSubmit(values: Values) {
    setFormError(null)
    const input = {
      label: values.label,
      helpText: blankToNull(values.helpText),
      required: values.required,
      displayOrder: existing?.displayOrder ?? null,
      options: values.options.map((option, index) => ({
        value: option.value || slug(option.label, true, `OPTION_${index + 1}`),
        label: option.label,
        points: Number(option.points),
        disqualifies: option.disqualifies,
      })),
    }
    try {
      if (existing) {
        await updateQuestion(existing.id, { ...input, version: existing.version })
      } else {
        await createQuestion({ ...input, key: values.key })
      }
      onDone()
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  const optionsError = form.formState.errors.options?.root?.message ?? form.formState.errors.options?.message

  return (
    <Card>
      <CardHeader>
        <CardTitle>{existing ? `Edit ${existing.label}` : 'New question'}</CardTitle>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-5" aria-label={existing ? `Edit ${existing.label}` : 'New question'} noValidate>
            <FormError message={formError} />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="label"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Question</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        onChange={(event) => {
                          field.onChange(event)
                          if (!existing && !keyTyped) {
                            form.setValue('key', slug(event.target.value, false, ''))
                          }
                        }}
                      />
                    </FormControl>
                    <FormDescription>For example: Decision maker identified?</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="key"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Key</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        disabled={!!existing}
                        className="font-mono"
                        onChange={(event) => {
                          setKeyTyped(true)
                          field.onChange(event)
                        }}
                      />
                    </FormControl>
                    <FormDescription>{existing ? 'The key cannot be changed.' : 'It cannot be changed later.'}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <FormField
              control={form.control}
              name="helpText"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Help text</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>Optional. Shown under the question.</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <fieldset className="grid gap-3">
              <legend className="mb-2 text-sm font-medium">Answers</legend>
              <p className="text-sm text-muted-foreground">
                Points are added to the score of a lead that gives the answer; a negative number takes points away.
              </p>
              {options.fields.map((row, index) => (
                <div key={row.id} className="flex flex-wrap items-start gap-3" role="group" aria-label={`Answer ${index + 1}`}>
                  <FormField
                    control={form.control}
                    name={`options.${index}.label`}
                    render={({ field }) => (
                      <FormItem className="min-w-40 flex-1">
                        <FormLabel className="sr-only">Answer</FormLabel>
                        <FormControl>
                          <Input placeholder="Answer" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name={`options.${index}.points`}
                    render={({ field }) => (
                      <FormItem className="w-24">
                        <FormLabel className="sr-only">Points</FormLabel>
                        <FormControl>
                          <Input placeholder="Points" inputMode="numeric" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name={`options.${index}.disqualifies`}
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
                        <FormLabel>Rules the lead out</FormLabel>
                      </FormItem>
                    )}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Remove answer ${index + 1}`}
                    onClick={() => options.remove(index)}
                  >
                    <Trash2 aria-hidden="true" />
                  </Button>
                </div>
              ))}
              {optionsError ? (
                <p role="alert" className="text-sm text-destructive">
                  {optionsError}
                </p>
              ) : null}
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="w-fit"
                onClick={() => options.append({ value: '', label: '', points: '0', disqualifies: false })}
              >
                <Plus aria-hidden="true" />
                Add answer
              </Button>
            </fieldset>

            <FormField
              control={form.control}
              name="required"
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
                  <FormLabel>Required before a lead counts as qualified</FormLabel>
                </FormItem>
              )}
            />
            <div className="flex gap-2">
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Saving…' : existing ? 'Save question' : 'Add question'}
              </Button>
              <Button type="button" variant="outline" onClick={onCancel}>
                Cancel
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}
