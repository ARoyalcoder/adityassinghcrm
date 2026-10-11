import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, Camera, MapPin, Pencil, Phone, UserRound } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { formatDateTime } from '@/features/audit/api'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  addMaterial,
  addNote,
  advanceJob,
  assignJob,
  cancelJob,
  formatSchedule,
  jobKeys,
  jobQuery,
  JOB_TYPES,
  labelOf,
  photoPath,
  removeMaterial,
  removePhoto,
  STEP_ACTIONS,
  techniciansQuery,
  tickItem,
  uploadPhoto,
  type AdvanceInput,
  type JobDetail,
} from '@/features/field-jobs/api'
import { AuthedImage, DelayedBadge, JobStatusBadge, SignaturePad } from '@/features/field-jobs/components/JobParts'
import { JobProofSection } from '@/features/field-jobs/components/JobProofSection'

/** One job, as the technician works through it on site (`/field-jobs/:id`). One column, large controls. */
export function FieldJobPage() {
  const { id = '' } = useParams()
  const canWork = useHasPermission('FIELD_JOB_WORK')
  const job = useQuery(jobQuery(id))
  const back = canWork ? { to: '/my-jobs', label: 'My jobs' } : { to: '/field-jobs', label: 'Field jobs' }

  if (!job.data) {
    return (
      <div className="mx-auto grid w-full max-w-2xl gap-6">
        <PageHeader title="Job" back={back} />
        <RecordLoadState noun="job" isPending={job.isPending} error={job.error} onRetry={() => void job.refetch()} />
      </div>
    )
  }
  return <JobView detail={job.data} back={back} />
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card role="region" aria-label={title}>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3">{children}</CardContent>
    </Card>
  )
}

function JobView({ detail, back }: { detail: JobDetail; back: { to: string; label: string } }) {
  const queryClient = useQueryClient()
  const canManage = useHasPermission('FIELD_JOB_MANAGE')
  const { job } = detail
  const id = job.id
  // Work is recorded from accepting the job until the customer confirms.
  const working = detail.canAct && ['ACCEPTED', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED'].includes(job.status)
  const closed = job.status === 'CUSTOMER_CONFIRMED' || job.status === 'CANCELLED'

  // A tick shows at once, before the server has answered: on a slow connection on site, a tap that
  // seems to do nothing gets tapped again.
  const [ticking, setTicking] = useState<Record<string, boolean>>({})

  /** Every action answers with the whole job, so the page is simply replaced by the answer. */
  const act = useMutation({
    mutationFn: (action: () => Promise<JobDetail>) => action(),
    onSuccess: (updated) => {
      queryClient.setQueryData(jobKeys.detail(id), updated)
      return queryClient.invalidateQueries({ queryKey: jobKeys.all, refetchType: 'inactive' })
    },
    onSettled: () => setTicking({}),
  })
  const run = (action: () => Promise<JobDetail>) => act.mutate(action)

  return (
    <div className="mx-auto grid w-full max-w-2xl gap-5 pb-28">
      <PageHeader
        title={job.title}
        description={`${job.jobNumber} · ${labelOf(JOB_TYPES, job.jobType)}`}
        back={back}
        action={
          <div className="flex flex-wrap items-center gap-2">
            {job.delayed ? <DelayedBadge /> : null}
            <JobStatusBadge status={job.status} />
            {canManage && !closed ? (
              <Button asChild variant="outline" size="sm">
                <Link to={`/field-jobs/${id}/edit`}>
                  <Pencil aria-hidden="true" />
                  Edit
                </Link>
              </Button>
            ) : null}
          </div>
        }
      />
      {act.error ? <ErrorState title="That could not be done" error={act.error} /> : null}

      <Section title="Customer and site">
        <p className="flex items-center gap-2 text-sm">
          <CalendarClock className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          {formatSchedule(job.scheduledStart, job.scheduledEnd)}
        </p>
        <p className="flex items-center gap-2 text-sm">
          <UserRound className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span>
            {job.customerName}
            {detail.siteContactName ? <span className="text-muted-foreground"> · ask for {detail.siteContactName}</span> : null}
          </span>
        </p>
        <p className="flex items-start gap-2 text-sm">
          <MapPin className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          {job.siteAddress}
        </p>
        <div className="flex flex-wrap gap-2">
          {detail.siteContactPhone ? (
            <Button asChild variant="outline" className="h-11">
              <a href={`tel:${detail.siteContactPhone.replace(/[^+0-9]/g, '')}`}>
                <Phone aria-hidden="true" />
                Call {detail.siteContactPhone}
              </a>
            </Button>
          ) : null}
          <Button asChild variant="outline" className="h-11">
            <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(job.siteAddress)}`} target="_blank" rel="noreferrer">
              <MapPin aria-hidden="true" />
              Open in Maps
            </a>
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          {job.technicianName ? `Assigned to ${job.technicianName}` : 'Not assigned to anyone yet'}
        </p>
      </Section>

      {canManage && ['UNASSIGNED', 'ASSIGNED', 'ACCEPTED'].includes(job.status) ? (
        <AssignSection current={job.technicianId ?? ''} onAssign={(technicianId) => run(() => assignJob(id, technicianId))} busy={act.isPending} />
      ) : null}

      <Section title="Instructions">
        {detail.instructions ? (
          <p className="text-sm whitespace-pre-wrap">{detail.instructions}</p>
        ) : (
          <p className="text-sm text-muted-foreground">No instructions were given for this job.</p>
        )}
      </Section>

      <Section title={`Checklist (${detail.checklist.filter((item) => item.done).length} of ${detail.checklist.length})`}>
        {detail.checklist.length === 0 ? (
          <p className="text-sm text-muted-foreground">This job has no checklist.</p>
        ) : (
          <ul className="grid gap-1" aria-label="Checklist">
            {detail.checklist.map((item) => (
              <li key={item.id}>
                <label className="flex min-h-11 items-center gap-3 rounded-xl px-2 text-sm hover:bg-muted/60">
                  <input
                    type="checkbox"
                    className="size-5 accent-primary"
                    checked={ticking[item.id] ?? item.done}
                    disabled={!working || act.isPending}
                    onChange={(event) => {
                      const done = event.target.checked
                      setTicking({ [item.id]: done })
                      run(() => tickItem(id, item.id, done))
                    }}
                  />
                  <span className={(ticking[item.id] ?? item.done) ? 'text-muted-foreground line-through' : undefined}>
                    {item.label}
                  </span>
                </label>
              </li>
            ))}
          </ul>
        )}
        {detail.canAct && job.status === 'ASSIGNED' ? (
          <p className="text-xs text-muted-foreground">Accept the job to start ticking things off.</p>
        ) : null}
      </Section>

      <PhotosSection detail={detail} working={working} busy={act.isPending} run={run} />
      <MaterialsSection detail={detail} working={working} busy={act.isPending} run={run} />
      <NotesSection detail={detail} canAdd={detail.canAct && !closed} busy={act.isPending} run={run} />

      <Section title="Proof of Work Timeline">
        <JobProofSection jobId={id} />
      </Section>

      {detail.completionNotes ? (
        <Section title="Completion notes">
          <p className="text-sm whitespace-pre-wrap">{detail.completionNotes}</p>
        </Section>
      ) : null}

      {detail.signOff ? (
        <Section title="Customer sign-off">
          <p className="text-sm">
            Confirmed by <span className="font-medium">{detail.signOff.signedByName}</span>
            {detail.signOff.confirmedAt ? ` on ${formatDateTime(detail.signOff.confirmedAt)}` : ''}
          </p>
          {detail.signOff.remarks ? <p className="text-sm text-muted-foreground">“{detail.signOff.remarks}”</p> : null}
          {detail.signOff.signature ? (
            <img src={detail.signOff.signature} alt={`Signature of ${detail.signOff.signedByName}`} className="h-28 w-fit rounded-xl border bg-card" />
          ) : (
            <p className="text-xs text-muted-foreground">No signature was taken.</p>
          )}
        </Section>
      ) : null}

      {canManage && !closed ? (
        <Button variant="ghost" className="w-fit text-red-700" disabled={act.isPending} onClick={() => run(() => cancelJob(id))}>
          Cancel this job
        </Button>
      ) : null}

      {detail.canAct && detail.nextStatus ? (
        // Keyed by the step: each step starts with its own form closed and empty.
        <NextStep key={detail.nextStatus} detail={detail} busy={act.isPending} onAdvance={(input) => run(() => advanceJob(id, input))} />
      ) : null}
    </div>
  )
}

function AssignSection({ current, onAssign, busy }: { current: string; onAssign: (technicianId: string) => void; busy: boolean }) {
  const technicians = useQuery(techniciansQuery)
  const [chosen, setChosen] = useState(current)
  return (
    <Section title="Assignment">
      <div className="flex flex-wrap gap-2">
        <Select aria-label="Technician" className="h-11 min-w-48 flex-1" value={chosen} onChange={(event) => setChosen(event.target.value)}>
          <option value="">Choose a technician…</option>
          {(technicians.data ?? []).map((person) => (
            <option key={person.id} value={person.id}>
              {person.name}
            </option>
          ))}
        </Select>
        <Button className="h-11" disabled={busy || chosen === '' || chosen === current} onClick={() => onAssign(chosen)}>
          {current ? 'Reassign' : 'Assign'}
        </Button>
      </div>
    </Section>
  )
}

type SectionProps = { detail: JobDetail; working: boolean; busy: boolean; run: (action: () => Promise<JobDetail>) => void }

function PhotosSection({ detail, working, busy, run }: SectionProps) {
  const id = detail.job.id
  const [caption, setCaption] = useState('')
  const [problem, setProblem] = useState<string | null>(null)

  function choose(file: File | undefined) {
    if (!file) {
      return
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setProblem('Choose a JPEG, PNG or WebP picture.')
    } else if (file.size > 10 * 1024 * 1024) {
      setProblem('The picture must be at most 10 MB.')
    } else {
      setProblem(null)
      const text = caption.trim()
      run(() => uploadPhoto(id, file, text === '' ? null : text))
      setCaption('')
    }
  }

  return (
    <Section title={`Photos (${detail.photos.length})`}>
      {detail.photos.length === 0 ? <p className="text-sm text-muted-foreground">No photos yet.</p> : null}
      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3" aria-label="Photos">
        {detail.photos.map((photo) => (
          <li key={photo.id} className="grid gap-1">
            <AuthedImage path={photoPath(id, photo.id)} alt={photo.caption ?? photo.fileName} className="aspect-square w-full rounded-xl object-cover" />
            <span className="truncate text-xs text-muted-foreground">{photo.caption ?? photo.fileName}</span>
            {working ? (
              <Button variant="ghost" size="sm" disabled={busy} aria-label={`Remove photo ${photo.caption ?? photo.fileName}`} onClick={() => run(() => removePhoto(id, photo.id))}>
                Remove
              </Button>
            ) : null}
          </li>
        ))}
      </ul>
      {working ? (
        <div className="grid gap-2">
          <Input aria-label="Photo caption" placeholder="Caption (optional), e.g. Before" maxLength={300} value={caption} onChange={(event) => setCaption(event.target.value)} />
          {/* On a phone, capture="environment" opens the back camera; elsewhere it is a file picker. */}
          <label className="flex h-12 cursor-pointer items-center justify-center gap-2 rounded-xl bg-secondary text-sm font-medium text-secondary-foreground">
            <Camera className="size-4" aria-hidden="true" />
            {busy ? 'Working…' : 'Take or choose a photo'}
            <input
              type="file"
              className="sr-only"
              aria-label="Add photo"
              accept="image/jpeg,image/png,image/webp"
              capture="environment"
              disabled={busy}
              onChange={(event) => {
                choose(event.target.files?.[0])
                event.target.value = ''
              }}
            />
          </label>
          {problem ? (
            <p role="alert" className="text-sm text-destructive">
              {problem}
            </p>
          ) : null}
        </div>
      ) : null}
    </Section>
  )
}

function MaterialsSection({ detail, working, busy, run }: SectionProps) {
  const id = detail.job.id
  const [name, setName] = useState('')
  const [quantity, setQuantity] = useState('')
  const [unit, setUnit] = useState('')
  const amount = Number(quantity)
  const valid = name.trim() !== '' && quantity.trim() !== '' && Number.isFinite(amount) && amount > 0

  return (
    <Section title="Materials used">
      {detail.materials.length === 0 ? (
        <p className="text-sm text-muted-foreground">No materials recorded.</p>
      ) : (
        <ul className="grid gap-1" aria-label="Materials used">
          {detail.materials.map((material) => (
            <li key={material.id} className="flex items-center justify-between gap-3 rounded-xl bg-muted/60 px-3 py-2 text-sm">
              <span>
                {material.name}{' '}
                <span className="font-medium tabular-nums">
                  × {material.quantity}
                  {material.unit ? ` ${material.unit}` : ''}
                </span>
              </span>
              {working ? (
                <Button variant="ghost" size="sm" disabled={busy} aria-label={`Remove ${material.name}`} onClick={() => run(() => removeMaterial(id, material.id))}>
                  Remove
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {working ? (
        <form
          className="grid grid-cols-[1fr_5rem_5rem] gap-2"
          aria-label="Add material"
          onSubmit={(event) => {
            event.preventDefault()
            if (valid) {
              run(() => addMaterial(id, { name: name.trim(), quantity: amount, unit: unit.trim() === '' ? null : unit.trim() }))
              setName('')
              setQuantity('')
              setUnit('')
            }
          }}
        >
          <Input aria-label="Material" placeholder="Material" className="h-11" maxLength={200} value={name} onChange={(event) => setName(event.target.value)} />
          <Input aria-label="Quantity" placeholder="Qty" className="h-11" inputMode="decimal" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
          <Input aria-label="Unit" placeholder="Unit" className="h-11" maxLength={20} value={unit} onChange={(event) => setUnit(event.target.value)} />
          <Button type="submit" variant="secondary" className="col-span-3 h-11" disabled={busy || !valid}>
            Add material
          </Button>
        </form>
      ) : null}
    </Section>
  )
}

function NotesSection({ detail, canAdd, busy, run }: { detail: JobDetail; canAdd: boolean; busy: boolean; run: SectionProps['run'] }) {
  const [draft, setDraft] = useState('')
  return (
    <Section title="Notes">
      {canAdd ? (
        <form
          className="grid gap-2"
          aria-label="Add note"
          onSubmit={(event) => {
            event.preventDefault()
            if (draft.trim() !== '') {
              run(() => addNote(detail.job.id, draft.trim()))
              setDraft('')
            }
          }}
        >
          <Textarea aria-label="New note" placeholder="Anything worth writing down…" maxLength={2000} value={draft} onChange={(event) => setDraft(event.target.value)} />
          <Button type="submit" variant="secondary" className="h-11" disabled={busy || draft.trim() === ''}>
            Add note
          </Button>
        </form>
      ) : null}
      {detail.notes.length === 0 ? (
        <p className="text-sm text-muted-foreground">No notes yet.</p>
      ) : (
        <ul className="grid gap-2" aria-label="Notes">
          {detail.notes.map((note) => (
            <li key={note.id} className="grid gap-0.5 rounded-xl bg-muted/60 px-3 py-2 text-sm">
              <p className="whitespace-pre-wrap">{note.body}</p>
              <p className="text-xs text-muted-foreground">
                {note.authorName} · {formatDateTime(note.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </Section>
  )
}

/**
 * The one thing to do next, fixed to the bottom of the screen where a thumb reaches it. Completing
 * asks what was done; customer confirmation asks who confirms and takes their signature.
 */
function NextStep({ detail, busy, onAdvance }: { detail: JobDetail; busy: boolean; onAdvance: (input: AdvanceInput) => void }) {
  const next = detail.nextStatus ?? ''
  const [open, setOpen] = useState(false)
  const [completionNotes, setCompletionNotes] = useState('')
  const [signedByName, setSignedByName] = useState('')
  const [remarks, setRemarks] = useState('')
  const [signature, setSignature] = useState<string | null>(null)
  const remaining = detail.checklist.filter((item) => !item.done).length
  const needsForm = next === 'COMPLETED' || next === 'CUSTOMER_CONFIRMED'

  function confirm() {
    if (next === 'COMPLETED') {
      onAdvance({ to: next, completionNotes: completionNotes.trim() === '' ? null : completionNotes.trim() })
    } else {
      onAdvance({ to: next, signedByName: signedByName.trim(), remarks: remarks.trim() === '' ? null : remarks.trim(), signature })
    }
  }

  return (
    <div className="fixed inset-x-0 bottom-0 z-10 border-t bg-card/95 p-3 backdrop-blur md:left-64">
      <div className="mx-auto grid w-full max-w-2xl gap-3">
        {open && next === 'COMPLETED' ? (
          <div className="grid gap-2" role="group" aria-label="Complete job">
            {remaining > 0 ? (
              <p role="alert" className="text-sm text-destructive">
                {remaining === 1 ? '1 checklist item is still open.' : `${remaining} checklist items are still open.`} Finish the checklist first.
              </p>
            ) : null}
            <Textarea aria-label="What was done" placeholder="What was done (optional)" maxLength={4000} value={completionNotes} onChange={(event) => setCompletionNotes(event.target.value)} />
          </div>
        ) : null}
        {open && next === 'CUSTOMER_CONFIRMED' ? (
          <div className="grid gap-2" role="group" aria-label="Customer confirmation">
            <p className="text-sm text-muted-foreground">Hand the phone to the customer to confirm the work.</p>
            <Input aria-label="Name of the person confirming" placeholder="Name of the person confirming" className="h-11" maxLength={200} value={signedByName} onChange={(event) => setSignedByName(event.target.value)} />
            <Input aria-label="Remarks" placeholder="Remarks (optional)" className="h-11" maxLength={1000} value={remarks} onChange={(event) => setRemarks(event.target.value)} />
            <SignaturePad onChange={setSignature} />
          </div>
        ) : null}
        <div className="flex gap-2">
          {open ? (
            <Button variant="outline" className="h-12" onClick={() => setOpen(false)}>
              Back
            </Button>
          ) : null}
          <Button
            className="h-12 flex-1 text-base"
            disabled={
              busy ||
              (open && next === 'COMPLETED' && remaining > 0) ||
              (open && next === 'CUSTOMER_CONFIRMED' && signedByName.trim() === '')
            }
            onClick={() => (needsForm && !open ? setOpen(true) : needsForm ? confirm() : onAdvance({ to: next }))}
          >
            {busy ? 'Working…' : open && next === 'CUSTOMER_CONFIRMED' ? 'Confirm work' : (STEP_ACTIONS[next] ?? next)}
          </Button>
        </div>
      </div>
    </div>
  )
}
