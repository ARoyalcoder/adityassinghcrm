interface ProjectStatusBadgeProps {
  status: string
  color?: string
}

const DEFAULT_STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  PLANNING: { bg: 'bg-slate-100 dark:bg-slate-800', text: 'text-slate-800 dark:text-slate-200' },
  IN_PROGRESS: { bg: 'bg-blue-100 dark:bg-blue-900/40', text: 'text-blue-800 dark:text-blue-300' },
  ON_HOLD: { bg: 'bg-amber-100 dark:bg-amber-900/40', text: 'text-amber-800 dark:text-amber-300' },
  IN_REVIEW: { bg: 'bg-purple-100 dark:bg-purple-900/40', text: 'text-purple-800 dark:text-purple-300' },
  COMPLETED: { bg: 'bg-emerald-100 dark:bg-emerald-900/40', text: 'text-emerald-800 dark:text-emerald-300' },
  CANCELLED: { bg: 'bg-rose-100 dark:bg-rose-900/40', text: 'text-rose-800 dark:text-rose-300' },
}

export function ProjectStatusBadge({ status, color }: ProjectStatusBadgeProps) {
  const norm = status.toUpperCase().replace(/\s+/g, '_')
  const defaultStyle = DEFAULT_STATUS_COLORS[norm] ?? {
    bg: 'bg-muted',
    text: 'text-muted-foreground',
  }

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${defaultStyle.bg} ${defaultStyle.text}`}
      style={color ? { borderColor: color, color: color } : undefined}
    >
      {status.replace(/_/g, ' ')}
    </span>
  )
}

export function PriorityBadge({ priority }: { priority: string }) {
  const norm = priority.toUpperCase()
  let className = 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
  if (norm === 'HIGH' || norm === 'CRITICAL' || norm === 'URGENT') {
    className = 'bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-300'
  } else if (norm === 'MEDIUM') {
    className = 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300'
  } else if (norm === 'LOW') {
    className = 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300'
  }

  return (
    <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-medium ${className}`}>
      {norm}
    </span>
  )
}
