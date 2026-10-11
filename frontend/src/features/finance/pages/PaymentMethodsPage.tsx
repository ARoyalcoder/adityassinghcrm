import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { useHasPermission } from '@/features/auth/auth-context'
import { deleteMethod, financeKeys, paymentMethodsQuery, saveMethod, type MethodInput, type PaymentMethod } from '@/features/finance/api'
import { Section } from '@/features/finance/components/FinanceParts'
import { ApiError } from '@/lib/api/errors'

/** The ways the company takes and returns money (`/admin/payment-methods`). */
export function PaymentMethodsPage() {
  const queryClient = useQueryClient()
  const canConfigure = useHasPermission('FINANCE_CONFIGURE')
  const methods = useQuery(paymentMethodsQuery(true))
  /** The method being edited, "new" while one is being added, or nothing. */
  const [editing, setEditing] = useState<string | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: [...financeKeys.all, 'methods'] })

  const save = useMutation({
    mutationFn: ({ input, existing }: { input: MethodInput; existing?: PaymentMethod }) => saveMethod(input, existing),
    onSuccess: () => {
      setEditing(null)
      return refresh()
    },
  })
  const remove = useMutation({ mutationFn: (id: string) => deleteMethod(id), onSuccess: refresh })
  const startEditing = (target: string | null) => {
    save.reset()
    remove.reset()
    setEditing(target)
  }

  return (
    <div className="grid max-w-3xl gap-6">
      <PageHeader
        title="Payment methods"
        description="How money arrives and is returned. Offered when a payment or a refund is recorded."
        action={
          canConfigure && editing !== 'new' ? (
            <Button onClick={() => startEditing('new')}>
              <Plus aria-hidden="true" />
              New method
            </Button>
          ) : null
        }
      />
      {remove.error ? <ErrorState title="That method could not be removed" error={remove.error} /> : null}
      {editing === 'new' ? (
        <Section title="New payment method">
          <MethodForm
            busy={save.isPending}
            error={save.error}
            nextOrder={(methods.data ?? []).reduce((highest, method) => Math.max(highest, method.displayOrder), 0) + 1}
            onCancel={() => startEditing(null)}
            onSave={(input) => save.mutate({ input })}
          />
        </Section>
      ) : null}

      {methods.isPending ? (
        <Skeleton className="h-48 w-full rounded-2xl" aria-busy="true" aria-label="Loading payment methods" />
      ) : methods.isError ? (
        <ErrorState title="Could not load the payment methods" error={methods.error} onRetry={() => void methods.refetch()} />
      ) : methods.data.length === 0 ? (
        <p className="rounded-2xl bg-card p-6 text-sm text-muted-foreground shadow-soft">
          There are no payment methods. Until one is added, payments and refunds cannot be recorded.
        </p>
      ) : (
        <ul className="grid gap-3" aria-label="Payment methods">
          {methods.data.map((method) => (
            <li key={method.id} className="grid gap-3 rounded-2xl bg-card p-4 shadow-soft">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="grid gap-0.5">
                  <span className="font-medium">{method.name}</span>
                  <span className="text-xs text-muted-foreground">
                    <span className="font-mono">{method.code}</span>
                    {method.requiresReference ? ' · needs a reference' : ''}
                    {method.active ? '' : ' · switched off'}
                  </span>
                </div>
                {canConfigure && editing !== method.id ? (
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => startEditing(method.id)} aria-label={`Edit ${method.name}`}>
                      Edit
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={remove.isPending}
                      aria-label={`Remove ${method.name}`}
                      onClick={() => {
                        if (window.confirm(`Remove ${method.name}? Payments already taken by it keep its name.`)) {
                          remove.mutate(method.id)
                        }
                      }}
                    >
                      Remove
                    </Button>
                  </div>
                ) : null}
              </div>
              {editing === method.id ? (
                <MethodForm
                  existing={method}
                  busy={save.isPending}
                  error={save.error}
                  nextOrder={method.displayOrder}
                  onCancel={() => startEditing(null)}
                  onSave={(input) => save.mutate({ input, existing: method })}
                />
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

type MethodFormProps = {
  existing?: PaymentMethod
  busy: boolean
  error: unknown
  nextOrder: number
  onCancel: () => void
  onSave: (input: MethodInput) => void
}

function MethodForm({ existing, busy, error, nextOrder, onCancel, onSave }: MethodFormProps) {
  const [name, setName] = useState(existing?.name ?? '')
  const [code, setCode] = useState(existing?.code ?? '')
  const [requiresReference, setRequiresReference] = useState(existing?.requiresReference ?? false)
  const [active, setActive] = useState(existing?.active ?? true)
  const [problem, setProblem] = useState<string | null>(null)
  const serverProblem = error instanceof ApiError ? (error.fieldErrors[0]?.message ?? error.message) : error ? 'That could not be saved.' : null

  function submit(event: FormEvent) {
    event.preventDefault()
    if (name.trim() === '') {
      setProblem('Give the method a name.')
      return
    }
    if (!/^[A-Z][A-Z0-9_]{1,29}$/.test(code)) {
      setProblem('The code is 2 to 30 capital letters, digits or underscores, starting with a letter: BANK_TRANSFER.')
      return
    }
    setProblem(null)
    onSave({ code, name: name.trim(), requiresReference, active, displayOrder: nextOrder })
  }

  return (
    <form onSubmit={submit} className="grid gap-3" aria-label={existing ? `Edit ${existing.name}` : 'New payment method'}>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="grid gap-1.5">
          <Label htmlFor="method-name">Name</Label>
          <Input id="method-name" maxLength={100} value={name} onChange={(event) => setName(event.target.value)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="method-code">Code</Label>
          <Input
            id="method-code"
            maxLength={30}
            value={code}
            disabled={existing !== undefined}
            onChange={(event) => setCode(event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))}
            aria-describedby="method-code-help"
          />
          <span id="method-code-help" className="text-xs text-muted-foreground">
            {existing ? 'The code identifies the method and cannot be changed.' : 'Capitals, digits and underscores. Cannot be changed later.'}
          </span>
        </div>
      </div>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="size-4 accent-primary" checked={requiresReference} onChange={(event) => setRequiresReference(event.target.checked)} />
        A reference (cheque number, UTR, transaction id) must be given with every payment
      </label>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="size-4 accent-primary" checked={active} onChange={(event) => setActive(event.target.checked)} />
        Offered when recording a payment
      </label>
      {(problem ?? serverProblem) ? (
        <p role="alert" className="text-sm text-destructive">
          {problem ?? serverProblem}
        </p>
      ) : null}
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : existing ? 'Save method' : 'Add method'}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
