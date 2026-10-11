import { Check, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { PipelineDetail, PipelineStage } from '../api'

interface StageStepperProps {
  pipeline: PipelineDetail
  currentStage: PipelineStage
  status: 'OPEN' | 'WON' | 'LOST'
  lossReason?: string | null
  onSelectStage?: (stage: PipelineStage) => void
  disabled?: boolean
}

export function StageStepper({
  pipeline,
  currentStage,
  status,
  lossReason,
  onSelectStage,
  disabled = false,
}: StageStepperProps) {
  const stages = [...pipeline.stages].sort((a, b) => a.displayOrder - b.displayOrder)
  const currentIdx = stages.findIndex((s) => s.id === currentStage.id)

  return (
    <div className="space-y-3">
      {/* Visual Stepper */}
      <div className="flex w-full flex-wrap gap-1 sm:gap-2">
        {stages.map((stage, idx) => {
          const isSelected = stage.id === currentStage.id
          const isCompleted =
            status === 'WON'
              ? true
              : status === 'OPEN' && currentIdx !== -1 && idx < currentIdx

          const isLostStage = stage.isLost
          const isWonStage = stage.isWon

          let bgClasses = 'bg-muted/60 text-muted-foreground hover:bg-muted'
          let borderClasses = 'border-border'

          if (isSelected) {
            if (status === 'WON') {
              bgClasses = 'bg-emerald-600 text-white font-semibold shadow'
              borderClasses = 'border-emerald-600'
            } else if (status === 'LOST') {
              bgClasses = 'bg-rose-600 text-white font-semibold shadow'
              borderClasses = 'border-rose-600'
            } else {
              bgClasses = 'bg-primary text-primary-foreground font-semibold shadow'
              borderClasses = 'border-primary'
            }
          } else if (isCompleted) {
            bgClasses = 'bg-emerald-50 text-emerald-800 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300'
          } else if (isLostStage) {
            bgClasses = 'bg-rose-50/50 text-rose-700/80 hover:bg-rose-50 dark:bg-rose-950/20'
          } else if (isWonStage) {
            bgClasses = 'bg-emerald-50/50 text-emerald-700/80 hover:bg-emerald-50 dark:bg-emerald-950/20'
          }

          return (
            <button
              key={stage.id}
              type="button"
              disabled={disabled || isSelected}
              onClick={() => onSelectStage?.(stage)}
              className={cn(
                'relative flex flex-1 min-w-[120px] items-center justify-center gap-1.5 rounded-md border px-3 py-2 text-xs transition-all',
                bgClasses,
                borderClasses,
                disabled || isSelected ? 'cursor-default' : 'cursor-pointer'
              )}
            >
              {isCompleted && !isSelected && <Check className="h-3.5 w-3.5" />}
              {isSelected && status === 'WON' && <Check className="h-3.5 w-3.5" />}
              {isSelected && status === 'LOST' && <X className="h-3.5 w-3.5" />}
              <span className="truncate">{stage.name}</span>
            </button>
          )
        })}
      </div>

      {/* Lost Reason alert if lost */}
      {status === 'LOST' && (
        <div className="rounded-md border border-rose-200 bg-rose-50 p-2.5 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-300">
          <span className="font-semibold">Opportunity Closed as Lost: </span>
          {lossReason ? lossReason : 'No reason specified'}
        </div>
      )}
    </div>
  )
}
