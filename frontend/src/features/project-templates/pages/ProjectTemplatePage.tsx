import { useQuery } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { StatusBadge } from '@/components/feedback/StatusBadge'
import { RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useHasPermission } from '@/features/auth/auth-context'
import { days, roleLabel, templateQuery, type ScheduledStage, type TemplateDetail } from '@/features/project-templates/api'

/** One template as it will be followed: its stages placed in time, and what happens in each. */
export function ProjectTemplatePage() {
  const { id = '' } = useParams()
  const template = useQuery(templateQuery(id))

  if (!template.data) {
    return (
      <div className="grid gap-6">
        <PageHeader title="Project template" back={{ to: '/admin/project-templates', label: 'Project templates' }} />
        <RecordLoadState
          noun="template"
          isPending={template.isPending}
          error={template.error}
          onRetry={() => void template.refetch()}
        />
      </div>
    )
  }
  return <TemplateView template={template.data} />
}

function TemplateView({ template }: { template: TemplateDetail }) {
  const canConfigure = useHasPermission('PROJECT_CONFIGURE')
  const stages = template.plan.stages
  const nameOf = (key: string) => stages.find((scheduled) => scheduled.stage.key === key)?.stage.name ?? key

  return (
    <div className="grid gap-6">
      <PageHeader
        title={template.name}
        description={[template.service.name, template.service.verticalName, template.description].filter(Boolean).join(' · ')}
        back={{ to: '/admin/project-templates', label: 'Project templates' }}
        action={
          <div className="flex items-center gap-3">
            <StatusBadge active={template.active} />
            {canConfigure ? (
              <Button asChild variant="outline">
                <Link to={`/admin/project-templates/${template.id}/edit`}>
                  <Pencil aria-hidden="true" />
                  Edit
                </Link>
              </Button>
            ) : null}
          </div>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>Timeline</CardTitle>
          <CardDescription>
            {stages.length} {stages.length === 1 ? 'stage' : 'stages'}, estimated {days(template.plan.totalDays)} from the
            start of a project. A stage starts when the stages it waits for have finished.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table aria-label="Timeline">
            <TableHeader>
              <TableRow>
                <TableHead>Stage</TableHead>
                <TableHead>Days</TableHead>
                <TableHead>Duration</TableHead>
                <TableHead>Responsible</TableHead>
                <TableHead>Waits for</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {stages.map((scheduled, index) => (
                <TableRow key={scheduled.stage.key}>
                  <TableCell className="font-medium">
                    {index + 1}. {scheduled.stage.name}
                  </TableCell>
                  <TableCell className="tabular-nums">
                    Day {scheduled.startDay} to {scheduled.endDay}
                  </TableCell>
                  <TableCell>{days(scheduled.stage.durationDays)}</TableCell>
                  <TableCell>{roleLabel(scheduled.stage.responsibleRole)}</TableCell>
                  <TableCell>
                    {scheduled.stage.dependsOn.length > 0
                      ? scheduled.stage.dependsOn.map(nameOf).join(', ')
                      : 'Nothing: starts with the project'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <ol className="grid gap-4" aria-label="Stages">
        {stages.map((scheduled, index) => (
          <li key={scheduled.stage.key}>
            <StageCard scheduled={scheduled} position={index + 1} />
          </li>
        ))}
      </ol>
    </div>
  )
}

function StageCard({ scheduled, position }: { scheduled: ScheduledStage; position: number }) {
  const stage = scheduled.stage
  const empty = stage.milestones.length === 0 && stage.tasks.length === 0 && stage.documents.length === 0
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          {position}. {stage.name}
        </CardTitle>
        <CardDescription>
          {days(stage.durationDays)} · {roleLabel(stage.responsibleRole)}
          {stage.description ? ` · ${stage.description}` : ''}
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 text-sm sm:grid-cols-3">
        {empty ? <p className="text-muted-foreground sm:col-span-3">Nothing is defined inside this stage.</p> : null}
        {stage.tasks.length > 0 ? (
          <section className="grid content-start gap-2" aria-label={`Tasks of ${stage.name}`}>
            <h3 className="font-medium">Tasks</h3>
            <ul className="grid gap-2">
              {stage.tasks.map((task) => (
                <li key={task.title} className="grid gap-1">
                  <span>
                    {task.title}
                    {task.responsibleRole ? <span className="text-muted-foreground"> · {roleLabel(task.responsibleRole)}</span> : null}
                    {task.estimatedHours ? <span className="text-muted-foreground"> · {task.estimatedHours} h</span> : null}
                  </span>
                  {task.description ? <span className="text-muted-foreground">{task.description}</span> : null}
                  {task.checklist.length > 0 ? (
                    <ul className="ml-4 list-disc text-muted-foreground" aria-label={`Checklist of ${task.title}`}>
                      {task.checklist.map((item) => (
                        <li key={item}>{item}</li>
                      ))}
                    </ul>
                  ) : null}
                </li>
              ))}
            </ul>
          </section>
        ) : null}
        {stage.milestones.length > 0 ? (
          <section className="grid content-start gap-2" aria-label={`Milestones of ${stage.name}`}>
            <h3 className="font-medium">Milestones</h3>
            <ul className="grid gap-1">
              {stage.milestones.map((milestone) => (
                <li key={milestone}>{milestone}</li>
              ))}
            </ul>
          </section>
        ) : null}
        {stage.documents.length > 0 ? (
          <section className="grid content-start gap-2" aria-label={`Documents of ${stage.name}`}>
            <h3 className="font-medium">Required documents</h3>
            <ul className="grid gap-1">
              {stage.documents.map((document) => (
                <li key={document.name} className="flex flex-wrap items-center gap-2">
                  {document.name}
                  <Badge variant={document.required ? 'outline' : 'secondary'}>{document.required ? 'Required' : 'Optional'}</Badge>
                  {document.description ? <span className="text-muted-foreground">{document.description}</span> : null}
                </li>
              ))}
            </ul>
          </section>
        ) : null}
      </CardContent>
    </Card>
  )
}
