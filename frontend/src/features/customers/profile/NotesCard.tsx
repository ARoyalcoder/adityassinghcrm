import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { formatDateTime } from '@/features/audit/api'
import { useHasPermission } from '@/features/auth/auth-context'
import { addNote, customerKeys, notesQuery, removeNote } from '@/features/customers/api'

/** Free-text notes about the customer. Adding or removing one refreshes the timeline and summary too. */
export function NotesCard({ customerId }: { customerId: string }) {
  const queryClient = useQueryClient()
  const canEdit = useHasPermission('CUSTOMER_UPDATE')
  const notes = useQuery(notesQuery(customerId))
  const [draft, setDraft] = useState('')

  const refresh = () => queryClient.invalidateQueries({ queryKey: customerKeys.detail(customerId) })
  const add = useMutation({
    mutationFn: () => addNote(customerId, draft.trim()),
    onSuccess: () => {
      setDraft('')
      return refresh()
    },
  })
  const remove = useMutation({ mutationFn: (noteId: string) => removeNote(customerId, noteId), onSuccess: refresh })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Notes</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4">
        {canEdit ? (
          <form
            className="grid gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              if (draft.trim() !== '') {
                add.mutate()
              }
            }}
          >
            <Textarea
              aria-label="New note"
              placeholder="Write a note about this customer…"
              maxLength={4000}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
            />
            <Button type="submit" size="sm" className="w-fit" disabled={add.isPending || draft.trim() === ''}>
              {add.isPending ? 'Saving…' : 'Add note'}
            </Button>
          </form>
        ) : null}
        {add.error || remove.error ? (
          <ErrorState title="The note could not be saved" error={add.error ?? remove.error} />
        ) : null}

        {notes.isPending ? (
          <Skeleton className="h-16 w-full" aria-busy="true" aria-label="Loading notes" />
        ) : notes.isError ? (
          <ErrorState title="Could not load notes" error={notes.error} onRetry={() => void notes.refetch()} />
        ) : notes.data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No notes yet.</p>
        ) : (
          <ul className="grid gap-3" aria-label="Notes">
            {notes.data.items.map((note) => (
              <li key={note.id} className="grid gap-1 rounded-md border p-3 text-sm">
                <p className="whitespace-pre-wrap">{note.body}</p>
                <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
                  <span>
                    {note.authorName} · {formatDateTime(note.createdAt)}
                  </span>
                  {canEdit ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={remove.isPending}
                      aria-label={`Remove note: ${note.body.slice(0, 40)}`}
                      onClick={() => remove.mutate(note.id)}
                    >
                      Remove
                    </Button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
