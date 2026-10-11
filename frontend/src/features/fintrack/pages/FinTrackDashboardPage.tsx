import { useState } from 'react'
import { Sidebar } from '../components/Sidebar'
import { Header } from '../components/Header'
import { SummaryCardsRow } from '../components/SummaryCardsRow'
import { SpendingOverview } from '../components/SpendingOverview'
import { RecentTransactions } from '../components/RecentTransactions'
import { GoalsProgress } from '../components/GoalsProgress'
import { SmartTip } from '../components/SmartTip'
import { CharacterDeskScene } from '../components/CharacterDeskScene'
import { FINTRACK_DATA } from '../data'

export function FinTrackDashboardPage() {
  const [activeTab, setActiveTab] = useState('dashboard')

  return (
    <div className="w-screen h-screen bg-[#f7f0eb] flex items-center justify-center p-3 sm:p-5 font-sans antialiased text-[#1f1b26] overflow-hidden select-none">
      {/* 
        Reference desktop canvas: exact proportions matching reference image
      */}
      <div className="w-full max-w-[1220px] h-[750px] bg-[#f7f0eb] flex gap-3.5 relative overflow-hidden">
        {/* 1. Left Sidebar Navigation */}
        <Sidebar
          activeTab={activeTab}
          onSelectTab={setActiveTab}
          onCreateGoal={() => setActiveTab('goals')}
        />

        {/* 2. Main Dashboard Area */}
        <div className="flex-1 flex flex-col justify-between h-full min-w-0 pr-0.5 relative">
          {/* Header */}
          <Header />

          {/* Row 1: 4 Financial Summary Cards */}
          <div className="w-full">
            <SummaryCardsRow cards={FINTRACK_DATA.summaryCards} />
          </div>

          {/* Row 2 & 3: Content area with right side decor and character */}
          <div className="flex-1 grid grid-cols-12 gap-3 mt-2 min-h-0 relative items-stretch">
            {/* Column 1: Spending Overview (top) & Goals Progress (bottom) */}
            <div className="col-span-5 flex flex-col justify-between gap-2.5 h-full">
              <div className="flex-1 min-h-0">
                <SpendingOverview
                  totalDisplay={FINTRACK_DATA.spendingOverview.totalDisplay}
                  categories={FINTRACK_DATA.spendingOverview.categories}
                />
              </div>

              <div className="shrink-0">
                <GoalsProgress
                  goals={FINTRACK_DATA.goals}
                  onViewAll={() => setActiveTab('goals')}
                />
              </div>
            </div>

            {/* Column 2: Recent Transactions (top) & Smart Tip (bottom) */}
            <div className="col-span-4 flex flex-col justify-between gap-2.5 h-full">
              <div className="flex-1 min-h-0">
                <RecentTransactions
                  transactions={FINTRACK_DATA.recentTransactions}
                  onViewAll={() => setActiveTab('transactions')}
                />
              </div>

              <div className="shrink-0">
                <SmartTip
                  title={FINTRACK_DATA.smartTip.title}
                  message={FINTRACK_DATA.smartTip.message}
                />
              </div>
            </div>

            {/* Column 3: Wall Decor (Shelf + Frame) and Character at Desk */}
            <div className="col-span-3 relative h-full flex flex-col justify-between items-end overflow-visible pointer-events-none select-none">
              <CharacterDeskScene />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
