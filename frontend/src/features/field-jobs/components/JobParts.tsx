import { useEffect, useMemo, useRef, type PointerEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CalendarClock, MapPin, UserRound } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatSchedule, JOB_STATUSES, JOB_TYPES, labelOf, type JobSummary } from '@/features/field-jobs/api'
import { apiBlob } from '@/lib/api/client'
import { cn } from '@/lib/utils'

const GOOD = 'border-emerald-200 bg-emerald-50 text-emerald-800'
const WARN = 'border-amber-200 bg-amber-50 text-amber-900'
const BAD = 'border-red-200 bg-red-50 text-red-800'
const NEUTRAL = 'bg-muted text-muted-foreground'
const ACTIVE = 'border-violet-200 bg-violet-50 text-violet-800'

const TONES: Record<string, string> = {
  UNASSIGNED: WARN,
  ASSIGNED: NEUTRAL,
  ACCEPTED: ACTIVE,
  EN_ROUTE: ACTIVE,
  ARRIVED: ACTIVE,
  IN_PROGRESS: ACTIVE,
  COMPLETED: GOOD,
  CUSTOMER_CONFIRMED: GOOD,
  CANCELLED: BAD,
}

const BADGE = 'inline-flex w-fit items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap'

/** The job's status in words. */
export function JobStatusBadge({ status }: { status: string }) {
  return (
    <span className={cn(BADGE, TONES[status] ?? NEUTRAL)}>
      <span className="sr-only">Status: </span>
      {labelOf(JOB_STATUSES, status)}
    </span>
  )
}

export function DelayedBadge() {
  return <span className={cn(BADGE, BAD)}>Delayed</span>
}

/** One job as a large, tappable card: what, when, where. Made for a phone held in one hand. */
export function JobCard({ job }: { job: JobSummary }) {
  return (
    <Link
      to={`/field-jobs/${job.id}`}
      className="grid gap-2 rounded-2xl bg-card p-4 shadow-soft outline-none transition-shadow hover:shadow-md focus-visible:ring-[3px] focus-visible:ring-ring/50"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="grid min-w-0">
          <span className="text-base font-semibold">{job.title}</span>
          <span className="text-xs text-muted-foreground">
            {job.jobNumber} · {labelOf(JOB_TYPES, job.jobType)}
          </span>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {job.delayed ? <DelayedBadge /> : null}
          <JobStatusBadge status={job.status} />
        </div>
      </div>
      <p className="flex items-center gap-2 text-sm">
        <CalendarClock className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        {formatSchedule(job.scheduledStart, job.scheduledEnd)}
      </p>
      <p className="flex items-center gap-2 text-sm">
        <UserRound className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        {job.customerName}
      </p>
      <p className="flex items-start gap-2 text-sm text-muted-foreground">
        <MapPin className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        {job.siteAddress}
      </p>
    </Link>
  )
}

/**
 * A picture that lives behind the API. An ordinary <img src> cannot send the access token, so the
 * picture is fetched like any other request and shown from memory.
 */
export function AuthedImage({ path, alt, className }: { path: string; alt: string; className?: string }) {
  const blob = useQuery({ queryKey: ['blob', path], queryFn: ({ signal }) => apiBlob(path, signal), staleTime: Infinity })
  const url = useMemo(() => (blob.data ? URL.createObjectURL(blob.data) : null), [blob.data])
  useEffect(() => {
    return () => {
      if (url) {
        URL.revokeObjectURL(url)
      }
    }
  }, [url])

  if (url) {
    return <img src={url} alt={alt} className={className} />
  }
  if (blob.isError) {
    return <div className={cn('flex items-center justify-center bg-muted text-xs text-muted-foreground', className)}>Could not load</div>
  }
  return <Skeleton className={className} aria-busy="true" aria-label={`Loading ${alt}`} />
}

type SignaturePadProps = {
  /** Called with a PNG data URL after every stroke, and with null when the pad is cleared. */
  onChange: (signature: string | null) => void
}

/** A box the customer signs in with a finger, a stylus or a mouse. */
export function SignaturePad({ onChange }: SignaturePadProps) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)

  function point(event: PointerEvent<HTMLCanvasElement>) {
    const box = event.currentTarget.getBoundingClientRect()
    // The canvas is drawn at a fixed size and stretched to the screen: translate between the two.
    return {
      x: ((event.clientX - box.left) / box.width) * event.currentTarget.width,
      y: ((event.clientY - box.top) / box.height) * event.currentTarget.height,
    }
  }

  function start(event: PointerEvent<HTMLCanvasElement>) {
    const context = event.currentTarget.getContext('2d')
    if (!context) {
      return
    }
    event.currentTarget.setPointerCapture(event.pointerId)
    drawing.current = true
    const { x, y } = point(event)
    context.lineWidth = 2.5
    context.lineCap = 'round'
    context.lineJoin = 'round'
    context.strokeStyle = '#1f1b3a'
    context.beginPath()
    context.moveTo(x, y)
  }

  function move(event: PointerEvent<HTMLCanvasElement>) {
    if (!drawing.current) {
      return
    }
    const context = event.currentTarget.getContext('2d')
    const { x, y } = point(event)
    context?.lineTo(x, y)
    context?.stroke()
  }

  function end(event: PointerEvent<HTMLCanvasElement>) {
    if (!drawing.current) {
      return
    }
    drawing.current = false
    onChange(event.currentTarget.toDataURL('image/png'))
  }

  function clear() {
    const element = canvas.current
    element?.getContext('2d')?.clearRect(0, 0, element.width, element.height)
    onChange(null)
  }

  return (
    <div className="grid gap-2">
      <canvas
        ref={canvas}
        width={600}
        height={200}
        role="img"
        aria-label="Signature"
        // touch-none: a finger on the pad draws instead of scrolling the page.
        className="h-40 w-full touch-none rounded-xl border-2 border-dashed border-input bg-card"
        onPointerDown={start}
        onPointerMove={move}
        onPointerUp={end}
        onPointerCancel={end}
      />
      <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={clear}>
        Clear signature
      </Button>
    </div>
  )
}
