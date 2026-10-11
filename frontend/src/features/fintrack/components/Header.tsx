import React, { useState } from 'react'
import { Search, Bell } from 'lucide-react'

interface HeaderProps {
  onSearch?: (query: string) => void
}

export function Header({ onSearch }: HeaderProps) {
  const [searchVal, setSearchVal] = useState('')
  const [hasNotification, setHasNotification] = useState(true)

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchVal(e.target.value)
    onSearch?.(e.target.value)
  }

  return (
    <header className="flex items-center justify-between pb-3 select-none">
      {/* Left greeting */}
      <div>
        <h1 className="text-[20px] sm:text-[22px] font-extrabold text-[#1a1721] tracking-tight flex items-center gap-1.5">
          Good morning! <span className="text-xl">👋</span>
        </h1>
        <p className="text-[11px] sm:text-[12px] font-medium text-[#7d7585] mt-0.5">
          Here's what's happening with your finances today.
        </p>
      </div>

      {/* Right controls */}
      <div className="flex items-center gap-2.5">
        {/* Search pill */}
        <div className="relative flex items-center">
          <Search className="w-3.5 h-3.5 text-[#9e96a8] absolute left-3 pointer-events-none" />
          <input
            type="text"
            value={searchVal}
            onChange={handleInputChange}
            placeholder="Search..."
            className="w-40 sm:w-48 pl-8 pr-3 py-1.5 bg-white text-[11px] rounded-full text-[#221f29] placeholder-[#9e96a8] border border-[#eee4db] shadow-[0_2px_8px_rgba(110,80,60,0.04)] focus:outline-none focus:ring-2 focus:ring-[#7752e2]/30 transition-all"
          />
        </div>

        {/* Notification Bell with red badge */}
        <button
          type="button"
          onClick={() => setHasNotification(!hasNotification)}
          aria-label="Notifications"
          className="relative w-8 h-8 rounded-full bg-white flex items-center justify-center text-[#554e60] hover:text-[#1a1721] border border-[#eee4db] shadow-[0_2px_8px_rgba(110,80,60,0.04)] hover:bg-[#faf7f4] active:scale-95 transition-all"
        >
          <Bell className="w-3.5 h-3.5" />
          {hasNotification && (
            <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-[#f43f5e] border-2 border-white ring-1 ring-white" />
          )}
        </button>

        {/* 3D User Avatar */}
        <div className="relative group cursor-pointer">
          <div className="w-8 h-8 rounded-full overflow-hidden border-2 border-white shadow-[0_2px_8px_rgba(110,80,60,0.1)] ring-1 ring-[#e8ddd3]">
            <img
              src="/images/fintrack/avatar-user.png"
              alt="User Profile"
              className="w-full h-full object-cover select-none"
            />
          </div>
        </div>
      </div>
    </header>
  )
}
