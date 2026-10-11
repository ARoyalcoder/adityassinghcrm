import type { ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth, useHasPermission } from '@/features/auth/auth-context'
import {
  analyticsKeys,
  moveProjectStage,
  pipelineProjectsQuery,
  projectAnalyticsQuery,
  type PipelineProject,
  type ProjectAnalytics,
} from '@/features/project-analytics/api'
import { cn } from '@/lib/utils'

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const dateFormat = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' })

/** "12.5 days", "1 day"; a figure the server could not work out is said in words, never shown as 0. */
function days(value: number | null | undefined, unknown: string) {
  return value === null || value === undefined ? unknown : `${value} ${value === 1 ? 'day' : 'days'}`
}

function greeting() {
  const hour = new Date().getHours()
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
}

/** How projects are doing: the cards, the pipeline, delivery performance and productivity. */
export function ProjectAnalyticsPage() {
  const { user } = useAuth()
  const analytics = useQuery(projectAnalyticsQuery)
  const firstName = user?.fullName.split(' ')[0]

  return (
    <div className="grid gap-6">
      <header className="grid gap-1">
        <h1 className="text-3xl font-semibold tracking-tight">
          {greeting()}
          {firstName ? `, ${firstName}` : ''}! <span aria-hidden="true">👋</span>
        </h1>
        <p className="text-muted-foreground">Here is how your projects are doing today.</p>
      </header>

      {analytics.isPending ? (
        <Skeleton className="h-96 w-full rounded-2xl" aria-busy="true" aria-label="Loading analytics" />
      ) : analytics.isError ? (
        <ErrorState title="Could not load the analytics" error={analytics.error} onRetry={() => void analytics.refetch()} />
      ) : (
        <Dashboard data={analytics.data} />
      )}
    </div>
  )
}

function Dashboard({ data }: { data: ProjectAnalytics }) {
  const { cards } = data
  return (
    <>
      <section aria-label="Overview" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5">
        <StatCard label="Active Projects" value={String(cards.activeProjects)} caption="In progress right now" emoji="📁" tone="violet" />
        <StatCard label="Starting Today" value={String(cards.startingToday)} caption="Kick-offs scheduled for today" emoji="🚀" tone="sky" />
        <StatCard label="Due Today" value={String(cards.dueToday)} caption="Expected to be delivered today" emoji="⏰" tone="amber" />
        <StatCard
          label="Delayed Projects"
          value={String(cards.delayedProjects)}
          caption={cards.delayedProjects === 0 ? 'Nothing is past its date' : 'Past their expected date'}
          emoji="⚠️"
          tone="rose"
        />
        <StatCard label="Completed This Month" value={String(cards.completedThisMonth)} caption="Delivered since the 1st" emoji="✅" tone="emerald" />
        <StatCard label="Revenue in Progress" value={rupees.format(cards.revenueInProgress)} caption="Across active projects" emoji="💰" tone="emerald" />
        <StatCard
          label="Completed Revenue"
          value={rupees.format(cards.completedRevenue)}
          caption={`${rupees.format(cards.completedRevenueThisMonth)} this month`}
          emoji="🏆"
          tone="amber"
        />
        <StatCard
          label="Average Project Duration"
          value={days(cards.averageProjectDurationDays, 'Not known yet')}
          caption="Start to delivery, completed projects"
          emoji="📅"
          tone="sky"
        />
        <StatCard
          label="Average Delivery Delay"
          value={days(cards.averageDeliveryDelayDays, 'No late deliveries')}
          caption="Among projects delivered late"
          emoji="🐢"
          tone="rose"
        />
        <StatCard
          label="Client Satisfaction"
          value={cards.clientSatisfaction === null || cards.clientSatisfaction === undefined ? 'No ratings yet' : `${cards.clientSatisfaction} / 5`}
          caption={cards.ratedProjects === 1 ? 'From 1 rated project' : `From ${cards.ratedProjects} rated projects`}
          emoji="😊"
          tone="violet"
        />
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <PipelineCard data={data} />
        <PipelineProjectsCard />
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <DeliveryCard delivery={data.delivery} />
        <ProductivityCard productivity={data.productivity} />
      </div>
      <Insight data={data} />
    </>
  )
}

const TONES = {
  violet: 'from-violet-200 to-violet-100',
  sky: 'from-sky-200 to-sky-100',
  amber: 'from-amber-200 to-amber-100',
  rose: 'from-rose-200 to-rose-100',
  emerald: 'from-emerald-200 to-emerald-100',
} as const

type StatCardProps = { label: string; value: string; caption: string; emoji: string; tone: keyof typeof TONES }

/** One headline figure. The picture is decoration; the label, value and caption carry everything. */
function StatCard({ label, value, caption, emoji, tone }: StatCardProps) {
  return (
    <Card className="gap-0 py-4" role="group" aria-label={label}>
      <CardContent className="flex items-start justify-between gap-3 px-4">
        <div className="grid min-w-0 gap-1.5">
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <p className="text-2xl leading-tight font-semibold tracking-tight break-words">{value}</p>
          <p className="text-xs text-muted-foreground">{caption}</p>
        </div>
        <span
          aria-hidden="true"
          className={cn('flex size-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br text-2xl shadow-soft', TONES[tone])}
        >
          {emoji}
        </span>
      </CardContent>
    </Card>
  )
}

/** Projects by stage: one bar per stage, one hue, each labelled with its count. */
function PipelineCard({ data }: { data: ProjectAnalytics }) {
  const most = Math.max(1, ...data.pipeline.map((stage) => stage.projects))
  return (
    <Card>
      <CardHeader>
        <CardTitle>Pipeline</CardTitle>
        <CardDescription>
          Projects by the stage they are in. The stages are the ones defined in your{' '}
          <Link className="underline underline-offset-2" to="/admin/project-templates">
            project templates
          </Link>
          .
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {data.pipeline.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No project has been placed in a stage yet. Plan a project from its template and it enters the first stage.
          </p>
        ) : (
          <ul className="grid gap-3" aria-label="Projects by stage">
            {data.pipeline.map((stage) => (
              <li
                key={stage.name}
                className="grid grid-cols-[minmax(7rem,11rem)_1fr_auto] items-center gap-3 text-sm"
                title={`${stage.name}: ${stage.projects} ${stage.projects === 1 ? 'project' : 'projects'}, ${rupees.format(stage.revenue)}`}
              >
                <span className="truncate">{stage.name}</span>
                <span className="h-3 rounded-full bg-muted" aria-hidden="true">
                  <span
                    className="block h-3 min-w-3 rounded-full bg-primary"
                    style={{ width: `${(stage.projects / most) * 100}%` }}
                  />
                </span>
                <span className="w-8 text-right font-medium tabular-nums">{stage.projects}</span>
              </li>
            ))}
          </ul>
        )}
        {data.unstagedProjects > 0 ? (
          <p className="text-xs text-muted-foreground">
            {data.unstagedProjects} active {data.unstagedProjects === 1 ? 'project is' : 'projects are'} not in any stage yet.
          </p>
        ) : null}
      </CardContent>
    </Card>
  )
}

/** The active projects, most urgent first, each with the stages of its own template to move it through. */
function PipelineProjectsCard() {
  const queryClient = useQueryClient()
  const canMove = useHasPermission('PROJECT_UPDATE')
  const projects = useQuery(pipelineProjectsQuery)
  const move = useMutation({
    mutationFn: ({ project, stageKey }: { project: PipelineProject; stageKey: string }) => moveProjectStage(project.id, stageKey),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: analyticsKeys.all }),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Active projects</CardTitle>
        <CardDescription>Most urgent first. Change a project's stage as it moves along.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {move.error ? <ErrorState title="The project could not be moved" error={move.error} /> : null}
        {projects.isPending ? (
          <Skeleton className="h-24 w-full" aria-busy="true" aria-label="Loading projects" />
        ) : projects.isError ? (
          <ErrorState title="Could not load the projects" error={projects.error} onRetry={() => void projects.refetch()} />
        ) : projects.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">There are no active projects.</p>
        ) : (
          <ul className="grid max-h-80 gap-2 overflow-y-auto pr-1" aria-label="Active projects">
            {projects.data.map((project) => (
              <li key={project.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-muted/60 px-3 py-2 text-sm">
                <div className="grid min-w-0">
                  <Link to={`/projects/${project.id}`} className="truncate font-medium underline-offset-2 hover:underline">
                    {project.title}
                  </Link>
                  <span className="text-xs text-muted-foreground">
                    {project.projectNumber} · {project.customerName}
                    {project.expectedCompletionDate ? ` · due ${dateFormat.format(new Date(project.expectedCompletionDate))}` : ''}
                    {project.delayed ? <span className="font-medium text-red-700"> · Delayed</span> : null}
                  </span>
                </div>
                {project.stages.length === 0 ? (
                  <span className="text-xs text-muted-foreground">No template for its service</span>
                ) : canMove ? (
                  <Select
                    aria-label={`Stage of ${project.title}`}
                    className="w-44"
                    disabled={move.isPending}
                    value={project.stageKey ?? ''}
                    onChange={(event) => move.mutate({ project, stageKey: event.target.value })}
                  >
                    {/* A project keeps the name of a stage it entered even if the template no longer has it. */}
                    {project.stages.some((stage) => stage.key === project.stageKey) ? null : (
                      <option value={project.stageKey ?? ''} disabled>
                        {project.stageName ?? 'Not in a stage'}
                      </option>
                    )}
                    {project.stages.map((stage) => (
                      <option key={stage.key} value={stage.key}>
                        {stage.name}
                      </option>
                    ))}
                  </Select>
                ) : (
                  <span className="rounded-lg bg-card px-2 py-1 text-xs font-medium">{project.stageName ?? 'Not in a stage'}</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function Fact({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5 rounded-xl bg-muted/60 px-3 py-2">
      <dt className="text-xs text-muted-foreground">{term}</dt>
      <dd className="text-sm font-medium">{children}</dd>
    </div>
  )
}

function DeliveryCard({ delivery }: { delivery: ProjectAnalytics['delivery'] }) {
  const delivered = delivery.deliveredOnTime + delivery.deliveredLate
  const extreme = (project: ProjectAnalytics['delivery']['fastestDelivery']) =>
    project ? (
      <>
        {days(project.days, '')} <span className="font-normal text-muted-foreground">· {project.title}</span>
      </>
    ) : (
      'Nothing delivered yet'
    )
  return (
    <Card>
      <CardHeader>
        <CardTitle>Delivery Performance</CardTitle>
        <CardDescription>Completed projects, by whether they met their expected date.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {delivered === 0 ? (
          <p className="text-sm text-muted-foreground">No project has been delivered yet.</p>
        ) : (
          <div className="grid gap-2">
            <p className="flex items-baseline gap-2">
              <span className="text-3xl font-semibold tabular-nums">{delivery.onTimePercentage}%</span>
              <span className="text-sm text-muted-foreground">delivered on time</span>
            </p>
            {/* One ratio against the whole: a meter, the on-time share filled in. */}
            <div
              role="meter"
              aria-label="Delivered on time"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={delivery.onTimePercentage ?? 0}
              className="h-3 rounded-full bg-muted"
            >
              <div className="h-3 rounded-full bg-primary" style={{ width: `${delivery.onTimePercentage ?? 0}%` }} />
            </div>
          </div>
        )}
        <dl className="grid gap-2 sm:grid-cols-2" aria-label="Delivery figures">
          <Fact term="Delivered On Time">
            <span aria-hidden="true">✓ </span>
            {delivery.deliveredOnTime}
          </Fact>
          <Fact term="Delivered Late">
            <span aria-hidden="true">⚠ </span>
            {delivery.deliveredLate}
          </Fact>
          <Fact term="Average Delay">{days(delivery.averageDelayDays, 'No late deliveries')}</Fact>
          <Fact term="Average Completion Time">{days(delivery.averageCompletionDays, 'Not known yet')}</Fact>
          <Fact term="Fastest Delivery">{extreme(delivery.fastestDelivery)}</Fact>
          <Fact term="Longest Delivery">{extreme(delivery.longestDelivery)}</Fact>
        </dl>
      </CardContent>
    </Card>
  )
}

function ProductivityCard({ productivity }: { productivity: ProjectAnalytics['productivity'] }) {
  const average = (value: number | null | undefined, unit: string, unknown: string) =>
    value === null || value === undefined ? unknown : `${value} ${unit}`
  return (
    <Card>
      <CardHeader>
        <CardTitle>Productivity</CardTitle>
        <CardDescription>What completed projects took, and who did the work.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <dl className="grid gap-2 sm:grid-cols-2" aria-label="Productivity figures">
          <Fact term="Projects Completed">
            {productivity.projectsCompleted}{' '}
            <span className="font-normal text-muted-foreground">· {productivity.projectsCompletedThisMonth} this month</span>
          </Fact>
          <Fact term="Average Hours">{average(productivity.averageHours, 'hours per project', 'No hours logged yet')}</Fact>
          <Fact term="Average Deliverables">{average(productivity.averageDeliverables, 'per project', 'Not known yet')}</Fact>
          <Fact term="Average Drawings">{average(productivity.averageDrawings, 'per project', 'Not tracked yet')}</Fact>
        </dl>
        {productivity.employees.length === 0 ? (
          <p className="text-sm text-muted-foreground">No tasks have been assigned to anyone yet.</p>
        ) : (
          <Table aria-label="Employee productivity">
            <TableHeader>
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead className="text-right">Tasks done</TableHead>
                <TableHead className="text-right">Open</TableHead>
                <TableHead className="text-right">Hours</TableHead>
                <TableHead className="text-right">Projects</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {productivity.employees.map((employee) => (
                <TableRow key={employee.userId}>
                  <TableCell className="font-medium">{employee.name ?? 'Unknown'}</TableCell>
                  <TableCell className="text-right tabular-nums">{employee.tasksCompleted}</TableCell>
                  <TableCell className="text-right tabular-nums">{employee.openTasks}</TableCell>
                  <TableCell className="text-right tabular-nums">{employee.hoursLogged}</TableCell>
                  <TableCell className="text-right tabular-nums">{employee.projects}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

/** One plain sentence drawn from the figures above: what most deserves a look today. */
function Insight({ data }: { data: ProjectAnalytics }) {
  const { cards } = data
  const message =
    cards.delayedProjects > 0
      ? `${cards.delayedProjects} ${cards.delayedProjects === 1 ? 'project is' : 'projects are'} past the expected date. They are at the top of the active list.`
      : cards.dueToday > 0
        ? `${cards.dueToday} ${cards.dueToday === 1 ? 'project is' : 'projects are'} due today, and nothing is overdue.`
        : cards.activeProjects > 0
          ? 'Nothing is overdue. Keep it up!'
          : 'No projects are running at the moment.'
  return (
    <Card className="bg-gradient-to-r from-amber-50 to-emerald-50 py-4" role="note" aria-label="Today">
      <CardContent className="flex items-center gap-3 px-4">
        <span aria-hidden="true" className="text-3xl">
          💡
        </span>
        <div className="grid gap-0.5">
          <p className="text-sm font-semibold">Today</p>
          <p className="text-sm text-muted-foreground">{message}</p>
        </div>
      </CardContent>
    </Card>
  )
}
