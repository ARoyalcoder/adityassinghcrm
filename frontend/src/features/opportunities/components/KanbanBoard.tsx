import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Building2,
  Calendar,
  GripVertical,
  Plus,
  User,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type {
  KanbanBoard,
  PipelineDetail,
  PipelineStage,
} from '../api'

interface KanbanBoardProps {
  board: KanbanBoard
  pipeline: PipelineDetail
  onStageChange: (opportunityId: string, targetStage: PipelineStage) => void
  onNewOpportunity?: (stageId?: string) => void
  isUpdating?: boolean
}

export function KanbanBoardView({
  board,
  onStageChange,
  onNewOpportunity,
  isUpdating = false,
}: KanbanBoardProps) {
  const [draggedOpportunityId, setDraggedOpportunityId] = useState<string | null>(null)
  const [dragOverStageId, setDragOverStageId] = useState<string | null>(null)

  function handleDragStart(e: React.DragEvent, oppId: string) {
    e.dataTransfer.setData('text/plain', oppId)
    e.dataTransfer.effectAllowed = 'move'
    setDraggedOpportunityId(oppId)
  }

  function handleDragEnd() {
    setDraggedOpportunityId(null)
    setDragOverStageId(null)
  }

  function handleDragOver(e: React.DragEvent, stageId: string) {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
    if (dragOverStageId !== stageId) {
      setDragOverStageId(stageId)
    }
  }

  function handleDragLeave(e: React.DragEvent, stageId: string) {
    // Only reset if leaving the column element itself
    if (e.currentTarget.contains(e.relatedTarget as Node)) return
    if (dragOverStageId === stageId) {
      setDragOverStageId(null)
    }
  }

  function handleDrop(e: React.DragEvent, targetStage: PipelineStage) {
    e.preventDefault()
    const oppId = e.dataTransfer.getData('text/plain') || draggedOpportunityId
    setDraggedOpportunityId(null)
    setDragOverStageId(null)

    if (oppId) {
      onStageChange(oppId, targetStage)
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
    <div className="flex h-full w-full gap-4 overflow-x-auto pb-4 pt-1">
      {board.columns.map((column) => {
        const { stage, count, totalValue, opportunities } = column
        const isDragOver = dragOverStageId === stage.id

        return (
          <div
            key={stage.id}
            onDragOver={(e) => handleDragOver(e, stage.id)}
            onDragLeave={(e) => handleDragLeave(e, stage.id)}
            onDrop={(e) => handleDrop(e, stage)}
            className={cn(
              'flex w-80 shrink-0 flex-col rounded-lg border bg-muted/20 transition-colors',
              isDragOver ? 'border-primary/60 bg-primary/5 ring-2 ring-primary/20' : 'border-border'
            )}
          >
            {/* Stage Column Header */}
            <div className="flex items-center justify-between border-b bg-card/60 px-3 py-2.5 backdrop-blur-sm">
              <div className="flex items-center gap-2 overflow-hidden">
                <span
                  className={cn(
                    'h-2.5 w-2.5 rounded-full shrink-0',
                    stage.isWon
                      ? 'bg-emerald-500'
                      : stage.isLost
                      ? 'bg-rose-500'
                      : 'bg-primary'
                  )}
                />
                <span className="truncate text-xs font-semibold uppercase tracking-wider text-foreground">
                  {stage.name}
                </span>
                <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                  {count}
                </span>
              </div>
              <div className="text-xs font-semibold text-muted-foreground">
                {formatCurrency(totalValue)}
              </div>
            </div>

            {/* Stage Quick Add Button */}
            {onNewOpportunity && !stage.isWon && !stage.isLost && (
              <div className="p-2 pb-0">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => onNewOpportunity(stage.id)}
                  className="w-full justify-start text-xs text-muted-foreground hover:text-foreground"
                >
                  <Plus className="mr-1.5 h-3.5 w-3.5" />
                  Add Opportunity
                </Button>
              </div>
            )}

            {/* Cards Container */}
            <div className="flex flex-1 flex-col gap-2.5 overflow-y-auto p-2">
              {opportunities.length === 0 ? (
                <div className="flex flex-1 items-center justify-center rounded border border-dashed border-muted p-4 text-center text-xs text-muted-foreground">
                  No opportunities
                </div>
              ) : (
                opportunities.map((opp) => {
                  const isBeingDragged = draggedOpportunityId === opp.id

                  return (
                    <div
                      key={opp.id}
                      draggable={!isUpdating}
                      onDragStart={(e) => handleDragStart(e, opp.id)}
                      onDragEnd={handleDragEnd}
                      className={cn(
                        'group relative cursor-grab rounded-lg border bg-card p-3 shadow-sm transition-all hover:border-primary/40 hover:shadow',
                        isBeingDragged ? 'opacity-40' : 'opacity-100',
                        isUpdating ? 'cursor-not-allowed opacity-70' : ''
                      )}
                    >
                      <div className="flex items-start justify-between gap-1">
                        <Link
                          to={`/opportunities/${opp.id}`}
                          className="line-clamp-2 text-xs font-semibold text-foreground hover:text-primary hover:underline"
                        >
                          {opp.title}
                        </Link>
                        <GripVertical className="h-3.5 w-3.5 text-muted-foreground/40 opacity-0 transition-opacity group-hover:opacity-100 shrink-0" />
                      </div>

                      {/* Customer / Lead reference */}
                      {(opp.customerName || opp.leadName) && (
                        <div className="mt-1 flex items-center gap-1 text-[11px] text-muted-foreground">
                          <Building2 className="h-3 w-3 shrink-0" />
                          <span className="truncate">
                            {opp.customerName || opp.leadName}
                          </span>
                        </div>
                      )}

                      {/* Service */}
                      {opp.serviceName && (
                        <div className="mt-1 truncate text-[11px] font-medium text-slate-600 dark:text-slate-400">
                          {opp.serviceName}
                        </div>
                      )}

                      {/* Deal Value & Probability */}
                      <div className="mt-2.5 flex items-center justify-between border-t border-dashed pt-2">
                        <span className="text-xs font-bold text-foreground">
                          {formatCurrency(opp.value)}
                        </span>
                        <Badge
                          variant="secondary"
                          className={cn(
                            'text-[10px] font-medium px-1.5 py-0',
                            opp.probability >= 70
                              ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                              : opp.probability >= 30
                              ? 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300'
                              : 'bg-muted text-muted-foreground'
                          )}
                        >
                          {opp.probability}%
                        </Badge>
                      </div>

                      {/* Footer: Owner and Target Date */}
                      <div className="mt-2 flex items-center justify-between text-[10px] text-muted-foreground">
                        <span className="flex items-center gap-1 truncate max-w-[120px]">
                          <User className="h-3 w-3 shrink-0" />
                          <span className="truncate">{opp.ownerName || 'Unassigned'}</span>
                        </span>
                        {opp.expectedCloseDate && (
                          <span className="flex items-center gap-1 shrink-0">
                            <Calendar className="h-3 w-3" />
                            {new Date(opp.expectedCloseDate).toLocaleDateString(undefined, {
                              month: 'short',
                              day: 'numeric',
                            })}
                          </span>
                        )}
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
