import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  BarChart3,
  Kanban,
  List,
  Plus,
  Search,
} from 'lucide-react'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  analyticsQuery,
  changeOpportunityStage,
  kanbanQuery,
  opportunitiesQuery,
  opportunityKeys,
  pipelinesQuery,
  type OpportunityFilters,
  type PipelineDetail,
  type PipelineStage,
} from '../api'
import { ChangeStageModal } from '../components/ChangeStageModal'
import { KanbanBoardView } from '../components/KanbanBoard'
import { OpportunityModal } from '../components/OpportunityModal'
import { PipelineAnalyticsSummary } from '../components/PipelineAnalyticsSummary'

type ViewMode = 'kanban' | 'list' | 'analytics'

export function OpportunitiesPage() {
  const queryClient = useQueryClient()
  const canCreate = useHasPermission('OPPORTUNITY_CREATE')

  const [view, setView] = useState<ViewMode>('kanban')
  const [selectedPipelineId, setSelectedPipelineId] = useState<string>('')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [stageFilter, setStageFilter] = useState('')
  const [sort] = useState('createdAt,desc')
  const [page, setPage] = useState(0)
  const pageSize = 20

  // Modals state
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [defaultStageForCreate, setDefaultStageForCreate] = useState<string | null>(null)
  const [stageChangeOpp, setStageChangeOpp] = useState<{
    id: string
    title: string
    targetStage: PipelineStage
  } | null>(null)

  // Pipelines
  const pipelinesList = useQuery(pipelinesQuery())
  const activePipelines = pipelinesList.data ?? []
  const activePipeline: PipelineDetail | undefined =
    activePipelines.find((p) => (selectedPipelineId ? p.id === selectedPipelineId : p.isDefault)) ??
    activePipelines[0]

  const currentPipelineId = activePipeline?.id ?? null

  // Queries
  const kanbanData = useQuery({
    ...kanbanQuery(currentPipelineId),
    enabled: Boolean(currentPipelineId) && view === 'kanban',
  })

  const analyticsData = useQuery({
    ...analyticsQuery(currentPipelineId),
    enabled: Boolean(currentPipelineId),
  })

  const filters: OpportunityFilters = {
    pipelineId: currentPipelineId,
    stageId: stageFilter || null,
    status: statusFilter || null,
    search: search.trim() || null,
  }

  const listData = useQuery({
    ...opportunitiesQuery({ filters, page, size: pageSize, sort }),
    enabled: view === 'list',
  })

  function invalidateAll() {
    void queryClient.invalidateQueries({ queryKey: opportunityKeys.all })
  }

  const changeStageMut = useMutation({
    mutationFn: ({
      id,
      stageId,
      notes,
      lossReason,
    }: {
      id: string
      stageId: string
      notes?: string
      lossReason?: string
    }) => changeOpportunityStage(id, { stageId, notes, lossReason }),
    onSuccess: () => {
      invalidateAll()
      setStageChangeOpp(null)
    },
  })

  function handleQuickStageChange(oppId: string, targetStage: PipelineStage) {
    if (targetStage.isLost) {
      // Must prompt for loss reason
      const opp = kanbanData.data?.columns
        .flatMap((c) => c.opportunities)
        .find((o) => o.id === oppId)
      setStageChangeOpp({
        id: oppId,
        title: opp?.title ?? 'Opportunity',
        targetStage,
      })
    } else {
      changeStageMut.mutate({
        id: oppId,
        stageId: targetStage.id,
      })
    }
  }

  function formatCurrency(val: number) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Opportunities"
        description="Manage your deal pipeline, stages, win probabilities, and sales performance."
        action={
          canCreate && (
            <Button
              onClick={() => {
                setDefaultStageForCreate(null)
                setCreateModalOpen(true)
              }}
            >
              <Plus className="mr-1.5 h-4 w-4" />
              New Opportunity
            </Button>
          )
        }
      />

      {/* Top Filter Bar & View Selector */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-2">
          {/* Pipeline Switcher */}
          {activePipelines.length > 1 && (
            <Select
              value={activePipeline?.id ?? ''}
              onChange={(e) => setSelectedPipelineId(e.target.value)}
              className="w-48"
            >
              {activePipelines.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} {p.isDefault ? '(Default)' : ''}
                </option>
              ))}
            </Select>
          )}

          {/* View Mode Buttons */}
          <div className="flex items-center rounded-lg border bg-muted/40 p-0.5">
            <Button
              variant={view === 'kanban' ? 'secondary' : 'ghost'}
              size="sm"
              onClick={() => setView('kanban')}
              className="gap-1.5 text-xs h-8"
            >
              <Kanban className="h-3.5 w-3.5" />
              Kanban
            </Button>
            <Button
              variant={view === 'list' ? 'secondary' : 'ghost'}
              size="sm"
              onClick={() => setView('list')}
              className="gap-1.5 text-xs h-8"
            >
              <List className="h-3.5 w-3.5" />
              List
            </Button>
            <Button
              variant={view === 'analytics' ? 'secondary' : 'ghost'}
              size="sm"
              onClick={() => setView('analytics')}
              className="gap-1.5 text-xs h-8"
            >
              <BarChart3 className="h-3.5 w-3.5" />
              Analytics
            </Button>
          </div>
        </div>

        {/* Search & Status Filters for List View */}
        {view === 'list' && (
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-[200px]">
              <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                placeholder="Search deals..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setPage(0)
                }}
                className="pl-8 text-xs h-8"
              />
            </div>
            <Select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value)
                setPage(0)
              }}
              className="w-28 text-xs h-8"
            >
              <option value="">All Statuses</option>
              <option value="OPEN">Open</option>
              <option value="WON">Won</option>
              <option value="LOST">Lost</option>
            </Select>
            {activePipeline && (
              <Select
                value={stageFilter}
                onChange={(e) => {
                  setStageFilter(e.target.value)
                  setPage(0)
                }}
                className="w-36 text-xs h-8"
              >
                <option value="">All Stages</option>
                {activePipeline.stages.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            )}
          </div>
        )}
      </div>

      {/* Analytics Summary Banner */}
      {view !== 'analytics' && analyticsData.data && (
        <div className="rounded-lg border bg-card p-3 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-4 text-xs">
            <div className="flex items-center gap-6">
              <div>
                <span className="text-muted-foreground">Open Deals: </span>
                <span className="font-semibold text-foreground">
                  {analyticsData.data.openOpportunities}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground">Pipeline Value: </span>
                <span className="font-semibold text-foreground">
                  {formatCurrency(analyticsData.data.pipelineValue)}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground">Weighted: </span>
                <span className="font-semibold text-indigo-600 dark:text-indigo-400">
                  {formatCurrency(analyticsData.data.weightedPipelineValue)}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground">Win Rate: </span>
                <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                  {analyticsData.data.winRatePercentage != null
                    ? `${analyticsData.data.winRatePercentage.toFixed(1)}%`
                    : '0%'}
                </span>
              </div>
            </div>
            <Button
              variant="link"
              size="sm"
              onClick={() => setView('analytics')}
              className="h-auto p-0 text-xs text-primary"
            >
              View Full Analytics →
            </Button>
          </div>
        </div>
      )}

      {/* MAIN VIEW CONTENT */}
      {view === 'kanban' && (
        <div>
          {kanbanData.isLoading ? (
            <div className="flex gap-4 overflow-x-auto pb-4">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-96 w-80 shrink-0 rounded-lg" />
              ))}
            </div>
          ) : kanbanData.isError ? (
            <ErrorState
              title="Failed to load pipeline Kanban board"
              error={kanbanData.error}
            />
          ) : kanbanData.data && activePipeline ? (
            <KanbanBoardView
              board={kanbanData.data}
              pipeline={activePipeline}
              onStageChange={handleQuickStageChange}
              onNewOpportunity={
                canCreate
                  ? (stageId) => {
                      setDefaultStageForCreate(stageId ?? null)
                      setCreateModalOpen(true)
                    }
                  : undefined
              }
              isUpdating={changeStageMut.isPending}
            />
          ) : null}
        </div>
      )}

      {view === 'list' && (
        <Card className="shadow-sm">
          <CardContent className="p-0">
            {listData.isLoading ? (
              <div className="space-y-2 p-6">
                {[1, 2, 3, 4, 5].map((i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : listData.isError ? (
              <div className="p-6">
                <ErrorState
                  title="Failed to load opportunities"
                  error={listData.error}
                />
              </div>
            ) : (
              <div>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Opportunity</TableHead>
                      <TableHead>Customer / Lead</TableHead>
                      <TableHead>Stage</TableHead>
                      <TableHead>Value</TableHead>
                      <TableHead>Probability</TableHead>
                      <TableHead>Close Date</TableHead>
                      <TableHead>Owner</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {listData.data?.items.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7} className="h-32 text-center text-sm text-muted-foreground">
                          No opportunities match your criteria.
                        </TableCell>
                      </TableRow>
                    ) : (
                      listData.data?.items.map((opp) => (
                        <TableRow key={opp.id}>
                          <TableCell>
                            <Link
                              to={`/opportunities/${opp.id}`}
                              className="font-medium text-foreground hover:text-primary hover:underline"
                            >
                              {opp.title}
                            </Link>
                            {opp.serviceName && (
                              <div className="text-xs text-muted-foreground">
                                {opp.serviceName}
                              </div>
                            )}
                          </TableCell>
                          <TableCell>
                            <div className="text-sm">
                              {opp.customerName || opp.leadName || '—'}
                            </div>
                            <div className="text-xs text-muted-foreground">
                              {opp.customerName ? 'Customer' : opp.leadName ? 'Lead' : ''}
                            </div>
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant="outline"
                              className={
                                opp.status === 'WON'
                                  ? 'border-emerald-500 bg-emerald-50 text-emerald-800'
                                  : opp.status === 'LOST'
                                  ? 'border-rose-500 bg-rose-50 text-rose-800'
                                  : ''
                              }
                            >
                              {opp.stageName}
                            </Badge>
                          </TableCell>
                          <TableCell className="font-semibold">
                            {formatCurrency(opp.value)}
                          </TableCell>
                          <TableCell>{opp.probability}%</TableCell>
                          <TableCell className="text-xs text-muted-foreground">
                            {opp.expectedCloseDate
                              ? new Date(opp.expectedCloseDate).toLocaleDateString()
                              : '—'}
                          </TableCell>
                          <TableCell className="text-xs">
                            {opp.ownerName || 'Unassigned'}
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>

                {/* Pagination */}
                {listData.data && listData.data.totalPages > 1 && (
                  <div className="flex items-center justify-between border-t px-4 py-3 text-xs text-muted-foreground">
                    <div>
                      Page {listData.data.page + 1} of {listData.data.totalPages} (
                      {listData.data.totalItems} total)
                    </div>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={listData.data.page === 0}
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                      >
                        Previous
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={listData.data.page >= listData.data.totalPages - 1}
                        onClick={() => setPage((p) => p + 1)}
                      >
                        Next
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {view === 'analytics' && (
        <div>
          {analyticsData.isLoading ? (
            <div className="space-y-4">
              <Skeleton className="h-28 w-full rounded-lg" />
              <Skeleton className="h-64 w-full rounded-lg" />
            </div>
          ) : analyticsData.isError ? (
            <ErrorState
              title="Failed to load analytics"
              error={analyticsData.error}
            />
          ) : analyticsData.data ? (
            <PipelineAnalyticsSummary analytics={analyticsData.data} />
          ) : null}
        </div>
      )}

      {/* MODALS */}
      {activePipeline && createModalOpen && (
        <OpportunityModal
          open={createModalOpen}
          onOpenChange={setCreateModalOpen}
          pipeline={activePipeline}
          defaultStageId={defaultStageForCreate}
          onSuccess={() => {
            invalidateAll()
          }}
        />
      )}

      {stageChangeOpp && (
        <ChangeStageModal
          open={Boolean(stageChangeOpp)}
          onOpenChange={(open) => !open && setStageChangeOpp(null)}
          targetStage={stageChangeOpp.targetStage}
          opportunityTitle={stageChangeOpp.title}
          isPending={changeStageMut.isPending}
          onConfirm={(notes, lossReason) => {
            changeStageMut.mutate({
              id: stageChangeOpp.id,
              stageId: stageChangeOpp.targetStage.id,
              notes,
              lossReason,
            })
          }}
        />
      )}
    </div>
  )
}
