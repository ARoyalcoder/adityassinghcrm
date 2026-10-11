import type { GoalItem } from '../data'

interface GoalsProgressProps {
  goals: GoalItem[]
  onViewAll?: () => void
}

export function GoalsProgress({ goals, onViewAll }: GoalsProgressProps) {
  return (
    <div className="bg-white rounded-[22px] p-3.5 border border-[#ede3d9]/70 shadow-[0_4px_16px_rgba(110,80,60,0.05)] select-none">
      {/* Header */}
      <div className="flex items-center justify-between pb-2">
        <h3 className="text-[13px] font-bold text-[#1b1824] tracking-tight">
          Goals Progress
        </h3>

        <button
          type="button"
          onClick={onViewAll}
          className="text-[10px] font-bold text-[#7752e2] hover:text-[#5a38c2] transition-colors"
        >
          View All Goals
        </button>
      </div>

      {/* Side-by-side Goals cards */}
      <div className="grid grid-cols-2 gap-3 mt-1">
        {goals.map((goal) => (
          <div
            key={goal.id}
            className="bg-[#faf6f2] rounded-[16px] p-2.5 border border-[#ede3d9]/80 flex items-center gap-2.5 hover:bg-[#f6efe7] transition-all"
          >
            {/* 3D Illustration */}
            <div className="w-10 h-10 rounded-xl bg-white border border-[#eae0d5] flex items-center justify-center shrink-0 shadow-xs overflow-hidden p-1">
              <img
                src={goal.illustration}
                alt={goal.illustrationAlt}
                className="w-full h-full object-contain pointer-events-none drop-shadow-xs"
              />
            </div>

            {/* Title, Progress bar & Percentage */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-1">
                <h4 className="text-[11px] font-bold text-[#1a1721] whitespace-nowrap">
                  {goal.title}
                </h4>
              </div>

              {/* Progress track */}
              <div className="w-full bg-[#e8ded3] rounded-full h-1.5 mt-1.5 overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-500"
                  style={{
                    width: `${goal.percentage}%`,
                    backgroundColor: goal.progressColor || '#818cf8',
                  }}
                />
              </div>

              {/* Value and percent */}
              <div className="flex items-center justify-between text-[9px] mt-1 font-semibold text-[#7c7587]">
                <span>{goal.displayProgress}</span>
                <span className="font-bold text-[#1a1721]">{goal.percentage}%</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
