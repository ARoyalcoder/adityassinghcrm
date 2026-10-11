import {
  Calendar,
  CheckSquare,
  Clock,
  FileText,
  Mail,
  MapPin,
  MessageSquare,
  Phone,
  type LucideIcon,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { labelOf, ACTIVITY_TYPES } from '@/features/activities/api'

const TYPE_CONFIG: Record<string, { icon: LucideIcon; className: string }> = {
  CALL: { icon: Phone, className: 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300 border-blue-200' },
  MEETING: { icon: Calendar, className: 'bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-300 border-purple-200' },
  WHATSAPP: { icon: MessageSquare, className: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300 border-emerald-200' },
  EMAIL: { icon: Mail, className: 'bg-sky-100 text-sky-800 dark:bg-sky-900/40 dark:text-sky-300 border-sky-200' },
  SITE_VISIT: { icon: MapPin, className: 'bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300 border-orange-200' },
  FOLLOW_UP: { icon: Clock, className: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300 border-amber-200' },
  NOTE: { icon: FileText, className: 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-300 border-slate-200' },
  TASK: { icon: CheckSquare, className: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/40 dark:text-indigo-300 border-indigo-200' },
}

export function ActivityTypeBadge({ type }: { type: string }) {
  const config = TYPE_CONFIG[type] ?? { icon: FileText, className: 'bg-muted text-foreground' }
  const Icon = config.icon

  return (
    <Badge variant="outline" className={`gap-1.5 font-medium ${config.className}`}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      <span>{labelOf(ACTIVITY_TYPES, type)}</span>
    </Badge>
  )
}
