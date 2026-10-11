import { useEffect, useState, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'

/** The row of search box and filters above a list. */
export function ListToolbar({ children }: { children: ReactNode }) {
  return <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">{children}</div>
}

type SearchInputProps = {
  value: string
  onChange: (value: string) => void
  /** Also the accessible name, e.g. "Search services". */
  label: string
}

/** Search box that waits for a pause in typing before asking for new results. */
export function SearchInput({ value, onChange, label }: SearchInputProps) {
  const [text, setText] = useState(value)

  // Keep the box in step when the URL changes underneath it (back button, cleared filters).
  const [lastValue, setLastValue] = useState(value)
  if (lastValue !== value) {
    setLastValue(value)
    setText(value)
  }

  useEffect(() => {
    if (text.trim() === value) {
      return
    }
    const timer = setTimeout(() => onChange(text.trim()), 300)
    return () => clearTimeout(timer)
  }, [text, value, onChange])

  return (
    <div className="relative sm:w-64">
      <Search
        className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
        aria-hidden="true"
      />
      <Input
        type="search"
        aria-label={label}
        placeholder={label}
        value={text}
        onChange={(event) => setText(event.target.value)}
        className="pl-8"
      />
    </div>
  )
}

type FilterSelectProps = {
  /** Accessible name, e.g. "Vertical". */
  label: string
  value: string
  onChange: (value: string) => void
  /** Text of the "no filter" choice, e.g. "All verticals". */
  allLabel: string
  options: { value: string; label: string }[]
}

export function FilterSelect({ label, value, onChange, allLabel, options }: FilterSelectProps) {
  return (
    <Select aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} className="sm:w-48">
      <option value="">{allLabel}</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </Select>
  )
}

type PaginationProps = {
  page: number
  totalPages: number
  totalItems: number
  /** What is being counted, plural and singular: "services", "service". */
  noun: string
  singular: string
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalItems, noun, singular, onPageChange }: PaginationProps) {
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 pt-3 text-sm">
      <p className="text-muted-foreground" aria-live="polite">
        {totalItems} {totalItems === 1 ? singular : noun}
        {totalPages > 1 ? ` · page ${page + 1} of ${totalPages}` : ''}
      </p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft aria-hidden="true" />
          Previous
        </Button>
        <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
          Next
          <ChevronRight aria-hidden="true" />
        </Button>
      </div>
    </nav>
  )
}
