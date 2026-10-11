import { useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { DataTable, dataTableColumns } from '@/components/data-table/DataTable'
import { ErrorState } from '@/components/feedback/ErrorState'
import { StatusBadge } from '@/components/feedback/StatusBadge'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useHasPermission } from '@/features/auth/auth-context'
import { catalogKeys, setVerticalActive, verticalsQuery, type Vertical } from '@/features/catalog/api'

const column = dataTableColumns<Vertical>()

/**
 * The six verticals. They can be renamed, reordered and switched off, but there is no way to add or
 * remove one: the set is fixed by the product.
 */
export function VerticalsPage() {
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission('CATALOG_UPDATE')
  const verticals = useQuery(verticalsQuery)

  const toggle = useMutation({
    mutationFn: (vertical: Vertical) => setVerticalActive(vertical.code, !vertical.active),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: catalogKeys.verticals }),
        // The overview and pickers elsewhere show active verticals only.
        queryClient.invalidateQueries({ queryKey: ['service-verticals'] }),
      ]),
  })
  const { mutate: toggleActive, isPending: toggling } = toggle

  const columns = useMemo(
    () =>
      column.columns([
        column.accessor('displayOrder', {
          header: '#',
          cell: ({ getValue }) => <span className="text-muted-foreground tabular-nums">{getValue()}</span>,
        }),
        column.accessor('name', {
          header: 'Vertical',
          cell: ({ getValue }) => <span className="font-medium">{getValue()}</span>,
        }),
        column.accessor('code', {
          header: 'Code',
          cell: ({ getValue }) => (
            <Badge variant="secondary" className="font-mono">
              {getValue()}
            </Badge>
          ),
        }),
        column.accessor('description', {
          header: 'Description',
          cell: ({ getValue }) => <span className="text-muted-foreground">{getValue() ?? '—'}</span>,
        }),
        column.accessor('active', { header: 'Status', cell: ({ getValue }) => <StatusBadge active={getValue()} /> }),
        column.display({
          id: 'actions',
          header: () => <span className="sr-only">Actions</span>,
          cell: ({ row }) =>
            canUpdate ? (
              <div className="flex justify-end gap-2">
                <Button asChild variant="outline" size="sm">
                  <Link to={`/admin/verticals/${row.original.code}/edit`} aria-label={`Edit ${row.original.name}`}>
                    Edit
                  </Link>
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={toggling}
                  aria-label={`${row.original.active ? 'Deactivate' : 'Activate'} ${row.original.name}`}
                  onClick={() => toggleActive(row.original)}
                >
                  {row.original.active ? 'Deactivate' : 'Activate'}
                </Button>
              </div>
            ) : null,
        }),
      ]),
    [canUpdate, toggleActive, toggling],
  )

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Verticals"
        description="The six lines of business. You can rename, reorder or switch them off; the set itself is fixed."
      />
      <Card>
        <CardContent className="grid gap-4">
          {toggle.error ? <ErrorState title="That change could not be saved" error={toggle.error} /> : null}
          {verticals.isPending ? (
            <div className="grid gap-2" aria-busy="true" aria-label="Loading verticals">
              {Array.from({ length: 6 }, (_, i) => (
                <Skeleton key={i} className="h-9 w-full" />
              ))}
            </div>
          ) : verticals.isError ? (
            <ErrorState
              title="Could not load verticals"
              error={verticals.error}
              onRetry={() => void verticals.refetch()}
            />
          ) : (
            <DataTable columns={columns} data={verticals.data} caption="Verticals" />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
