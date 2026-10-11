import {
  LayoutDashboard,
  Wallet,
  ArrowLeftRight,
  PieChart,
  Target,
  TrendingUp,
  FileBarChart,
  Settings,
  HelpCircle,
} from 'lucide-react'

interface NavItem {
  id: string
  label: string
  icon: React.ComponentType<{ className?: string }>
  badge?: string
}

const NAV_ITEMS: NavItem[] = [
  { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { id: 'accounts', label: 'Accounts', icon: Wallet },
  { id: 'transactions', label: 'Transactions', icon: ArrowLeftRight },
  { id: 'budget', label: 'Budget', icon: PieChart },
  { id: 'goals', label: 'Goals', icon: Target },
  { id: 'investments', label: 'Investments', icon: TrendingUp },
  { id: 'reports', label: 'Reports', icon: FileBarChart },
  { id: 'settings', label: 'Settings', icon: Settings },
  { id: 'help', label: 'Help & Support', icon: HelpCircle },
]

interface SidebarProps {
  activeTab: string
  onSelectTab: (id: string) => void
  onCreateGoal?: () => void
}

export function Sidebar({ activeTab, onSelectTab, onCreateGoal }: SidebarProps) {
  return (
    <aside className="w-[142px] shrink-0 bg-white rounded-[26px] p-3 flex flex-col justify-between shadow-[0_10px_30px_rgba(110,80,60,0.06)] border border-[#ede5dd]/60 select-none">
      {/* Brand & Nav */}
      <div className="space-y-4">
        {/* Brand Logo */}
        <div className="flex items-center gap-2 px-1 pt-1">
          <div className="w-8 h-8 rounded-xl bg-[#6744d3] flex items-center justify-center text-white shadow-sm shadow-[#6744d3]/30">
            {/* Custom pulse/graph icon */}
            <svg
              className="w-4 h-4 text-white stroke-[2.5]"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M4 14l4-5 4 4 8-9"
              />
            </svg>
          </div>
          <span className="font-extrabold text-[15px] tracking-tight text-[#1e1b26]">
            FinTrack
          </span>
        </div>

        {/* Navigation List */}
        <nav className="space-y-1">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon
            const isActive = activeTab === item.id

            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onSelectTab(item.id)}
                className={`w-full flex items-center gap-2.5 px-2.5 py-2 rounded-xl text-[12px] font-semibold transition-all duration-150 text-left ${
                  isActive
                    ? 'bg-[#6744d3] text-white shadow-sm shadow-[#6744d3]/35'
                    : 'text-[#5f596b] hover:text-[#1e1b26] hover:bg-[#f8f3ef]'
                }`}
              >
                <Icon
                  className={`w-4 h-4 shrink-0 ${
                    isActive ? 'text-white' : 'text-[#6b6477]'
                  }`}
                />
                <span className="truncate">{item.label}</span>
              </button>
            )
          })}
        </nav>
      </div>

      {/* Bottom Promo Card with 3D Piggy Bank */}
      <div className="mt-4 relative overflow-hidden rounded-[20px] bg-gradient-to-br from-[#7752e2] to-[#603bc9] p-3 text-white shadow-md shadow-[#6744d3]/25">
        {/* Top text */}
        <div className="relative z-10 pr-6">
          <h4 className="text-[12px] font-bold leading-tight">
            Save more,
            <br />
            Achieve more!
          </h4>
          <p className="text-[9px] text-[#e0d6ff] mt-1 leading-snug">
            Start your financial journey today.
          </p>
        </div>

        {/* 3D Piggy bank & coin */}
        <div className="flex justify-end -mt-1 -mb-1 relative z-10">
          <img
            src="/images/fintrack/piggy-bank-tr.png"
            alt="Piggy bank with golden coin"
            className="w-14 h-14 object-contain drop-shadow-md select-none pointer-events-none"
          />
        </div>

        {/* Action Button */}
        <div className="mt-2 relative z-10">
          <button
            type="button"
            onClick={onCreateGoal}
            className="w-full bg-white hover:bg-[#f6f2fd] text-[#5e38cb] active:scale-95 text-[11px] font-bold py-1.5 px-2.5 rounded-xl transition-transform shadow-sm flex items-center justify-center gap-1"
          >
            <span>Create Goal</span>
          </button>
        </div>

        {/* Decorative soft glow */}
        <div className="absolute -bottom-6 -right-6 w-20 h-20 bg-white/10 rounded-full blur-xl pointer-events-none" />
      </div>
    </aside>
  )
}
