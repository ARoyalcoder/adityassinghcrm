import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { fieldTypeLabel, type FieldDefinition } from '@/components/dynamic-fields/model'
import { ErrorState } from '@/components/feedback/ErrorState'
import { RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/features/audit/api'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  ACTIVITY_TYPES,
  labelOf,
  leadActivitiesQuery,
  leadKeys,
  leadQuery,
  logLeadActivity,
  qualificationQuery,
  type LeadDetail,
} from '@/features/leads/api'
import {
  HealthBadge,
  LeadStatusBadge,
  PriorityBadge,
  QualificationBadge,
  ScoreBadge,
} from '@/features/leads/components/LeadBadges'
import { QualificationQuestions } from '@/features/leads/components/QualificationQuestions'
import { ScorePanel } from '@/features/leads/components/ScorePanel'

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 })

/** One lead: what is known about it, its score and why, the qualification questions, and what has been done. */
export function LeadDetailPage() {
  const { id = '' } = useParams()
  const lead = useQuery(leadQuery(id))

  if (!lead.data) {
    return (
      <div className="grid gap-6">
        <PageHeader title="Lead" back={{ to: '/leads', label: 'Leads' }} />
        <RecordLoadState noun="lead" isPending={lead.isPending} error={lead.error} onRetry={() => void lead.refetch()} />
      </div>
    )
  }
  return <LeadView lead={lead.data} />
}

function LeadView({ lead }: { lead: LeadDetail }) {
  const canEdit = useHasPermission('LEAD_UPDATE')
  const qualification = useQuery(qualificationQuery(lead.id))
  // The explanation is worked out as of now; the lead row holds what was last stored. Where they
  // differ (time has passed since the last rescoring), the explanation is the truth.
  const current = qualification.data

  return (
    <div className="grid gap-6">
      <PageHeader
        title={lead.contactName}
        description={[lead.leadNumber, lead.companyName, lead.service.name].filter(Boolean).join(' · ')}
        back={{ to: '/leads', label: 'Leads' }}
        action={
          canEdit ? (
            <Button asChild variant="outline">
              <Link to={`/leads/${lead.id}/edit`}>
                <Pencil aria-hidden="true" />
                Edit
              </Link>
            </Button>
          ) : null
        }
      />
      <div className="flex flex-wrap gap-2" aria-label="Assessment" role="group">
        <ScoreBadge score={current?.score ?? lead.score} />
        <PriorityBadge priority={current?.priority ?? lead.priority} />
        <HealthBadge health={current?.health ?? lead.health} />
        <QualificationBadge status={current?.qualificationStatus ?? lead.qualificationStatus} />
        <LeadStatusBadge status={lead.status} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <ScorePanel
          qualification={qualification.data}
          isPending={qualification.isPending}
          error={qualification.error}
          onRetry={() => void qualification.refetch()}
        />
        <QualificationQuestions leadId={lead.id} qualification={qualification.data} />
        <DetailsCard lead={lead} />
        <ActivityCard leadId={lead.id} />
      </div>
    </div>
  )
}

/** Shows a stored answer the way its field's type calls for, using only the field's definition. */
function formatAnswer(field: FieldDefinition, value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const optionLabel = (stored: unknown) => field.options?.find((option) => option.value === stored)?.label ?? String(stored)
  switch (field.type) {
    case 'BOOLEAN':
      return value ? 'Yes' : 'No'
    case 'CURRENCY':
      return rupees.format(Number(value))
    case 'NUMBER':
      return field.unit ? `${String(value)} ${field.unit}` : String(value)
    case 'SELECT':
      return optionLabel(value)
    case 'MULTI_SELECT':
      return Array.isArray(value) ? value.map(optionLabel).join(', ') : String(value)
    case 'FILE':
      return typeof value === 'object' && 'fileName' in value ? String(value.fileName) : fieldTypeLabel(field.type)
    default:
      return String(value)
  }
}

function DetailsCard({ lead }: { lead: LeadDetail }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Details</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
          <dt className="text-muted-foreground">Contact</dt>
          <dd>{lead.contactName}</dd>
          <dt className="text-muted-foreground">Company</dt>
          <dd>{lead.companyName ?? '—'}</dd>
          <dt className="text-muted-foreground">Phone</dt>
          <dd>{lead.phone ?? '—'}</dd>
          <dt className="text-muted-foreground">Email</dt>
          <dd className="break-all">{lead.email ?? '—'}</dd>
          <dt className="text-muted-foreground">Service</dt>
          <dd>
            {lead.service.name}
            {lead.service.verticalName ? <span className="text-muted-foreground"> · {lead.service.verticalName}</span> : null}
          </dd>
          <dt className="text-muted-foreground">Estimated value</dt>
          <dd>
            {lead.estimatedValue === null || lead.estimatedValue === undefined ? 'Not estimated' : rupees.format(lead.estimatedValue)}
          </dd>
          {/* The service's own questions: whatever its administrator defined, in their order. */}
          {lead.customFields.map((answer) => (
            <div key={answer.field.id} className="contents">
              <dt className="text-muted-foreground">{answer.field.label}</dt>
              <dd>{formatAnswer(answer.field, answer.value)}</dd>
            </div>
          ))}
          <dt className="text-muted-foreground">Received</dt>
          <dd>{formatDateTime(lead.createdAt)}</dd>
          {lead.closedAt ? (
            <>
              <dt className="text-muted-foreground">Closed</dt>
              <dd>{formatDateTime(lead.closedAt)}</dd>
            </>
          ) : null}
        </dl>
      </CardContent>
    </Card>
  )
}

function ActivityCard({ leadId }: { leadId: string }) {
  const queryClient = useQueryClient()
  const canEdit = useHasPermission('LEAD_UPDATE')
  const activities = useQuery(leadActivitiesQuery(leadId))
  const [type, setType] = useState('CALL')
  const [subject, setSubject] = useState('')

  const log = useMutation({
    mutationFn: () => logLeadActivity(leadId, { type, subject: subject.trim() }),
    onSuccess: () => {
      setSubject('')
      // Activity changes the score, the health and the lists that show them.
      return queryClient.invalidateQueries({ queryKey: leadKeys.all })
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Activity</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4">
        {canEdit ? (
          <form
            className="flex flex-wrap items-end gap-2"
            aria-label="Log activity"
            onSubmit={(event) => {
              event.preventDefault()
              if (subject.trim() !== '') {
                log.mutate()
              }
            }}
          >
            <Select aria-label="Type" className="sm:w-32" value={type} onChange={(event) => setType(event.target.value)}>
              {ACTIVITY_TYPES.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
            <Input
              aria-label="What happened"
              placeholder="What happened?"
              className="min-w-40 flex-1"
              maxLength={200}
              value={subject}
              onChange={(event) => setSubject(event.target.value)}
            />
            <Button type="submit" size="sm" disabled={log.isPending || subject.trim() === ''}>
              {log.isPending ? 'Saving…' : 'Log activity'}
            </Button>
          </form>
        ) : null}
        {log.error ? <ErrorState title="The activity could not be saved" error={log.error} /> : null}

        {activities.isPending ? (
          <Skeleton className="h-16 w-full" aria-busy="true" aria-label="Loading activity" />
        ) : activities.isError ? (
          <ErrorState title="Could not load activity" error={activities.error} onRetry={() => void activities.refetch()} />
        ) : activities.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing has been done about this lead yet.</p>
        ) : (
          <ul className="grid gap-3" aria-label="Activity">
            {activities.data.map((activity) => (
              <li key={activity.id} className="grid gap-0.5 border-l-2 pl-3 text-sm">
                <p>
                  <span className="font-medium">{labelOf(ACTIVITY_TYPES, activity.type)}</span>: {activity.subject}
                </p>
                <p className="text-xs text-muted-foreground">
                  {activity.loggedByName} · {formatDateTime(activity.occurredAt)}
                </p>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
