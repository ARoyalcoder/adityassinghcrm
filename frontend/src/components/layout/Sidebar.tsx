import { LogOut } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { navigation } from '@/config/navigation'
import { useAuth } from '@/features/auth/auth-context'
import { cn } from '@/lib/utils'

/** Side rail on desktop, top bar on small screens. Shows only what the user's permissions allow. */
export function Sidebar() {
  const { user, signOut } = useAuth()
  const sections = navigation
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => !item.permission || user?.permissions.includes(item.permission)),
    }))
    .filter((section) => section.items.length > 0)

  return (
    <aside className="flex shrink-0 items-center gap-4 bg-sidebar px-4 py-3 shadow-soft md:sticky md:top-4 md:m-4 md:mr-0 md:h-[calc(100svh-2rem)] md:w-60 md:flex-col md:items-stretch md:gap-6 md:overflow-y-auto md:rounded-2xl md:py-6">
      <div className="flex items-center gap-2.5 md:px-2">
        <div
          aria-hidden="true"
          className="flex size-9 items-center justify-center rounded-xl bg-primary text-xs font-bold text-primary-foreground shadow-soft"
        >
          PP
        </div>
        <div className="leading-tight">
          <p className="text-sm font-semibold">Pawan Putra</p>
          <p className="text-xs text-muted-foreground">Business OS</p>
        </div>
      </div>

      <nav aria-label="Main" className="flex min-w-0 flex-1 gap-1 overflow-x-auto md:flex-col md:gap-4 md:overflow-visible">
        {sections.map((section) => (
          <div key={section.title ?? 'main'} className="flex gap-1 md:flex-col">
            {section.title ? (
              <p className="hidden px-2.5 pb-1 text-xs font-medium text-muted-foreground md:block">{section.title}</p>
            ) : null}
            {section.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-medium whitespace-nowrap text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground',
                    isActive && 'bg-primary text-primary-foreground shadow-soft hover:bg-primary hover:text-primary-foreground',
                  )
                }
              >
                <item.icon className="size-4" aria-hidden="true" />
                {item.label}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>

      {user ? (
        <div className="flex items-center gap-2 md:border-t md:px-2 md:pt-4">
          <div className="hidden min-w-0 flex-1 leading-tight md:block">
            <p className="truncate text-sm font-medium">{user.fullName}</p>
            <p className="truncate text-xs text-muted-foreground">{user.email}</p>
          </div>
          <Button variant="ghost" size="icon" aria-label="Sign out" title="Sign out" onClick={() => void signOut()}>
            <LogOut aria-hidden="true" />
          </Button>
        </div>
      ) : null}
    </aside>
  )
}
