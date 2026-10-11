import type { ReactNode } from 'react'
import { ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'

type PageHeaderProps = {
  title: string
  description?: string
  /** Button(s) shown on the right, e.g. "New service". */
  action?: ReactNode
  /** If set, shows a "back" link above the title. */
  back?: { to: string; label: string }
}

export function PageHeader({ title, description, action, back }: PageHeaderProps) {
  return (
    <header className="grid gap-2">
      {back ? (
        <Link
          to={back.to}
          className="flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          {back.label}
        </Link>
      ) : null}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
        </div>
        {action}
      </div>
    </header>
  )
}
