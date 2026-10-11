import { useQuery } from '@tanstack/react-query'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { businessVerticalsQuery, systemInfoQuery } from '@/features/system/api'
import { VerticalsTable } from '@/features/system/components/VerticalsTable'

export function OverviewPage() {
  const verticals = useQuery(businessVerticalsQuery)
  const info = useQuery(systemInfoQuery)
  const { user } = useAuth()

  return (
    <div className="grid gap-6">
      <header className="grid gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Overview</h1>
        <p className="text-sm text-muted-foreground">
          Platform foundation is running. Business modules will appear here as they are built.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Business verticals</CardTitle>
          <CardDescription>The service lines this system is built for.</CardDescription>
        </CardHeader>
        <CardContent>
          {verticals.isPending ? (
            <div className="grid gap-2" aria-busy="true" aria-label="Loading business verticals">
              {Array.from({ length: 6 }, (_, i) => (
                <Skeleton key={i} className="h-9 w-full" />
              ))}
            </div>
          ) : verticals.isError ? (
            <ErrorState
              title="Could not load business verticals"
              error={verticals.error}
              onRetry={() => void verticals.refetch()}
            />
          ) : (
            <VerticalsTable verticals={verticals.data} />
          )}
        </CardContent>
      </Card>

      {user ? (
        <Card>
          <CardHeader>
            <CardTitle>Your access</CardTitle>
            <CardDescription>
              Signed in as {user.fullName}. What you can do is decided by your roles.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 text-sm">
            <div className="flex flex-wrap items-center gap-1.5" aria-label="Your roles">
              <span className="mr-1 text-muted-foreground">Roles</span>
              {user.roles.length > 0 ? (
                user.roles.map((role) => <Badge key={role}>{role}</Badge>)
              ) : (
                <span className="text-muted-foreground">None</span>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-1.5" aria-label="Your permissions">
              <span className="mr-1 text-muted-foreground">Permissions</span>
              {user.permissions.length > 0 ? (
                user.permissions.map((permission) => (
                  <Badge key={permission} variant="secondary" className="font-mono">
                    {permission}
                  </Badge>
                ))
              ) : (
                <span className="text-muted-foreground">None</span>
              )}
            </div>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>System</CardTitle>
          <CardDescription>What this browser is connected to.</CardDescription>
        </CardHeader>
        <CardContent>
          {info.isPending ? (
            <Skeleton className="h-10 w-64" aria-busy="true" aria-label="Loading system information" />
          ) : info.isError ? (
            <ErrorState title="Could not reach the API" error={info.error} onRetry={() => void info.refetch()} />
          ) : (
            <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1.5 text-sm">
              <dt className="text-muted-foreground">Product</dt>
              <dd>{info.data.name}</dd>
              <dt className="text-muted-foreground">Version</dt>
              <dd className="font-mono">{info.data.version}</dd>
              <dt className="text-muted-foreground">API</dt>
              <dd className="font-mono">{info.data.apiVersion}</dd>
            </dl>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
