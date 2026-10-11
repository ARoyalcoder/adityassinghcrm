import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useHasPermission } from '@/features/auth/auth-context'
import { answerQuestions, leadKeys, type Qualification } from '@/features/leads/api'

type Props = { leadId: string; qualification: Qualification | undefined }

/**
 * The organization's qualification questions for one lead. Choosing an answer saves it at once,
 * and the score beside it is worked out again. The questions come from the server; none is named here.
 */
export function QualificationQuestions({ leadId, qualification }: Props) {
  const queryClient = useQueryClient()
  const canAnswer = useHasPermission('LEAD_UPDATE')
  const canConfigure = useHasPermission('LEAD_CONFIGURE')

  const answer = useMutation({
    mutationFn: (change: Record<string, string | null>) => answerQuestions(leadId, change),
    onSuccess: (updated) => {
      // The response is the new assessment: show it straight away, then refresh the lead itself.
      queryClient.setQueryData([...leadKeys.detail(leadId), 'qualification'], updated)
      return queryClient.invalidateQueries({ queryKey: leadKeys.all })
    },
  })

  // While an answer is on its way to the server, show it as chosen: the click must be seen to register.
  const pending = answer.isPending ? answer.variables : undefined
  const shown = (question: Qualification['questions'][number]) =>
    pending && question.key in pending ? pending[question.key] : (question.answer ?? null)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Qualification</CardTitle>
        <CardDescription>
          {qualification?.qualificationReason ?? 'What we know about how good a fit this lead is.'}
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-5">
        {answer.error ? <ErrorState title="The answer could not be saved" error={answer.error} /> : null}
        {!qualification ? (
          <Skeleton className="h-24 w-full" aria-busy="true" aria-label="Loading questions" />
        ) : qualification.questions.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No qualification questions have been set up.{' '}
            {canConfigure ? (
              <Link className="underline underline-offset-2" to="/admin/qualification-questions">
                Set them up
              </Link>
            ) : null}
          </p>
        ) : (
          qualification.questions.map((question) => (
            <fieldset key={question.id} className="grid gap-2" disabled={!canAnswer || answer.isPending}>
              <legend className="text-sm font-medium">
                {question.label}
                {question.required ? <span className="font-normal text-muted-foreground"> (required)</span> : null}
              </legend>
              {question.helpText ? <p className="text-sm text-muted-foreground">{question.helpText}</p> : null}
              <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
                {question.options.map((option) => (
                  <label key={option.value} className="flex items-center gap-2 text-sm">
                    <input
                      type="radio"
                      className="size-4 accent-primary"
                      name={`question-${question.key}`}
                      checked={shown(question) === option.value}
                      onChange={() => answer.mutate({ [question.key]: option.value })}
                    />
                    {option.label}
                  </label>
                ))}
                {canAnswer && question.answer ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    aria-label={`Clear the answer to ${question.label}`}
                    onClick={() => answer.mutate({ [question.key]: null })}
                  >
                    Clear
                  </Button>
                ) : null}
              </div>
            </fieldset>
          ))
        )}
      </CardContent>
    </Card>
  )
}
