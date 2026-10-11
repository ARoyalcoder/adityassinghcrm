import { Coffee, Car, ArrowDownLeft, ShoppingBag } from 'lucide-react'
import type { TransactionItem } from '../data'

interface RecentTransactionsProps {
  transactions: TransactionItem[]
  onViewAll?: () => void
}

function getFallbackIcon(type: TransactionItem['iconType']) {
  switch (type) {
    case 'coffee':
      return <Coffee className="w-3.5 h-3.5 text-[#d97706]" />
    case 'car':
      return <Car className="w-3.5 h-3.5 text-[#2563eb]" />
    case 'income':
      return <ArrowDownLeft className="w-3.5 h-3.5 text-[#16a34a]" />
    case 'shopping':
    default:
      return <ShoppingBag className="w-3.5 h-3.5 text-[#db2777]" />
  }
}

export function RecentTransactions({ transactions, onViewAll }: RecentTransactionsProps) {
  return (
    <div className="bg-white rounded-[22px] p-4 border border-[#ede3d9]/70 shadow-[0_4px_16px_rgba(110,80,60,0.05)] flex flex-col justify-between h-full select-none">
      {/* Header */}
      <div className="flex items-center justify-between pb-2">
        <h3 className="text-[13px] font-bold text-[#1b1824] tracking-tight">
          Recent Transactions
        </h3>

        <button
          type="button"
          onClick={onViewAll}
          className="text-[10.5px] font-bold text-[#7752e2] hover:text-[#5a38c2] transition-colors"
        >
          View All
        </button>
      </div>

      {/* Transaction List */}
      <div className="flex-1 flex flex-col justify-around py-0.5">
        {transactions.map((tx) => (
          <div
            key={tx.id}
            className="flex items-center justify-between py-1 px-1 rounded-xl hover:bg-[#faf6f2] transition-colors"
          >
            {/* Left: icon + title + category */}
            <div className="flex items-center gap-2.5 truncate">
              {/* Icon container */}
              <div className="w-7 h-7 rounded-xl bg-[#f7f2ed] border border-[#ede5dd] flex items-center justify-center shrink-0 shadow-xs overflow-hidden">
                {tx.iconUrl ? (
                  <img
                    src={tx.iconUrl}
                    alt={tx.title}
                    className="w-5 h-5 object-contain"
                    onError={(e) => {
                      // fallback to SVG if image error
                      e.currentTarget.style.display = 'none'
                    }}
                  />
                ) : (
                  getFallbackIcon(tx.iconType)
                )}
              </div>

              <div className="truncate">
                <h4 className="text-[11px] font-bold text-[#1a1721] truncate">
                  {tx.title}
                </h4>
                <p className="text-[9.5px] font-medium text-[#8a8294] truncate">
                  {tx.category}
                </p>
              </div>
            </div>

            {/* Right: Amount & Date */}
            <div className="text-right shrink-0">
              <span
                className={`text-[11px] font-extrabold block tracking-tight ${
                  tx.isPositive ? 'text-[#16a34a]' : 'text-[#1a1721]'
                }`}
              >
                {tx.amount}
              </span>
              <span className="text-[9px] font-medium text-[#9a93a4] block">
                {tx.date}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
