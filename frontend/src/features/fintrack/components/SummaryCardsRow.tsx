import { MoreVertical } from 'lucide-react'
import type { SummaryCardItem } from '../data'

interface SummaryCardsRowProps {
  cards: SummaryCardItem[]
}

export function SummaryCardsRow({ cards }: SummaryCardsRowProps) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
      {cards.map((card) => (
        <div
          key={card.id}
          className="relative bg-white rounded-[20px] p-3.5 border border-[#ede3d9]/70 shadow-[0_4px_16px_rgba(110,80,60,0.05)] flex items-center justify-between overflow-hidden hover:shadow-[0_8px_24px_rgba(110,80,60,0.08)] hover:-translate-y-0.5 transition-all duration-200 select-none group"
        >
          {/* Left Text content */}
          <div className="flex flex-col justify-between h-full z-10 pr-2">
            <div>
              <p className="text-[11px] font-semibold text-[#7c7587] tracking-tight">
                {card.title}
              </p>
              <h3 className="text-[19px] sm:text-[21px] font-extrabold text-[#1a1721] tracking-tight mt-0.5">
                {card.amount}
              </h3>
            </div>

            <div className="flex items-center gap-1.5 mt-2.5">
              <span className="text-[10px] font-bold text-[#16a34a] flex items-center">
                {card.trend}
              </span>
              <span className="text-[9.5px] font-medium text-[#9a93a4]">
                {card.comparison}
              </span>
            </div>
          </div>

          {/* Right 3D Illustration & Top-Right Menu dots */}
          <div className="relative shrink-0 flex flex-col items-end justify-between self-stretch">
            {/* 3-dots action button */}
            <button
              type="button"
              aria-label="Options"
              className="text-[#aba2b5] hover:text-[#2a2636] p-0.5 -mr-1 -mt-0.5 rounded transition-colors"
            >
              <MoreVertical className="w-3.5 h-3.5" />
            </button>

            {/* 3D Illustration thumbnail */}
            <div className="w-13 h-13 sm:w-14 sm:h-14 flex items-center justify-center -mr-1 -mb-1">
              <img
                src={card.illustration}
                alt={card.illustrationAlt}
                className="w-full h-full object-contain drop-shadow-[0_4px_10px_rgba(60,40,30,0.08)] pointer-events-none select-none transition-transform group-hover:scale-105 duration-200"
              />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
