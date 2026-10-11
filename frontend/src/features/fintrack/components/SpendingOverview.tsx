import { useState } from 'react'
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts'
import { ChevronDown } from 'lucide-react'
import type { SpendingCategory } from '../data'

interface SpendingOverviewProps {
  totalDisplay: string
  categories: SpendingCategory[]
}

export function SpendingOverview({ totalDisplay, categories }: SpendingOverviewProps) {
  const [selectedMonth] = useState('This Month')
  const [activeIndex, setActiveIndex] = useState<number | null>(null)

  return (
    <div className="bg-white rounded-[22px] p-4 border border-[#ede3d9]/70 shadow-[0_4px_16px_rgba(110,80,60,0.05)] flex flex-col justify-between h-full select-none">
      {/* Panel Header */}
      <div className="flex items-center justify-between pb-2">
        <h3 className="text-[13px] font-bold text-[#1b1824] tracking-tight">
          Spending Overview
        </h3>

        {/* Compact Period Dropdown */}
        <div className="relative">
          <button
            type="button"
            className="flex items-center gap-1 text-[10.5px] font-semibold text-[#5a5266] bg-[#f9f5f1] hover:bg-[#f1ebe3] px-2.5 py-1 rounded-full border border-[#e8ded3] transition-colors"
          >
            <span>{selectedMonth}</span>
            <ChevronDown className="w-3 h-3 text-[#797086]" />
          </button>
        </div>
      </div>

      {/* Main Content: Chart on left, Legend on right */}
      <div className="flex items-center justify-between gap-2 my-auto">
        {/* Donut Chart with Center Label */}
        <div className="relative w-36 h-36 shrink-0 flex items-center justify-center">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Tooltip
                formatter={(value, name) => [`₹${Number(value).toLocaleString('en-IN')}`, name]}
                contentStyle={{
                  backgroundColor: '#ffffff',
                  borderRadius: '10px',
                  border: '1px solid #ebe2d8',
                  fontSize: '11px',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.06)',
                }}
              />
              <Pie
                data={categories}
                cx="50%"
                cy="50%"
                innerRadius={44}
                outerRadius={65}
                paddingAngle={3}
                dataKey="amount"
                stroke="none"
                onMouseEnter={(_, index) => setActiveIndex(index)}
                onMouseLeave={() => setActiveIndex(null)}
              >
                {categories.map((entry, index) => (
                  <Cell
                    key={`cell-${entry.name}`}
                    fill={entry.color}
                    className="transition-all duration-200 cursor-pointer"
                    style={{
                      filter: activeIndex === index ? 'brightness(1.1) drop-shadow(0 2px 6px rgba(0,0,0,0.15))' : 'none',
                    }}
                  />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>

          {/* Donut Center text */}
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
            <span className="text-[9px] font-semibold text-[#8c8496] uppercase tracking-wider">
              Total
            </span>
            <span className="text-[13px] font-extrabold text-[#1a1721] tracking-tight">
              {totalDisplay}
            </span>
          </div>
        </div>

        {/* Categories Legend List */}
        <div className="flex-1 space-y-1 pl-2">
          {categories.map((cat, idx) => (
            <div
              key={cat.name}
              onMouseEnter={() => setActiveIndex(idx)}
              onMouseLeave={() => setActiveIndex(null)}
              className={`flex items-center justify-between text-[10.5px] py-0.5 px-1.5 rounded-lg transition-colors cursor-pointer ${
                activeIndex === idx ? 'bg-[#f8f3ee]' : 'hover:bg-[#faf6f2]'
              }`}
            >
              <div className="flex items-center gap-1.5 truncate">
                <span
                  className="w-2 h-2 rounded-full shrink-0 shadow-xs"
                  style={{ backgroundColor: cat.color }}
                />
                <span className="font-semibold text-[#484252] truncate">
                  {cat.name}
                </span>
              </div>

              <div className="flex items-center gap-2 shrink-0 font-medium">
                <span className="font-bold text-[#1f1b29]">
                  {cat.displayAmount}
                </span>
                <span className="text-[9.5px] text-[#8e8699] w-6 text-right">
                  {cat.share}%
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
