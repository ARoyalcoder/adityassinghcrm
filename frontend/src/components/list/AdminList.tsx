import type { ReactNode } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import type { RowData } from '@tanstack/react-table'
import { DataTable, type DataTableColumn } from '@/components/data-table/DataTable'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Pagination } from '@/components/list/ListControls'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import type { Page } from '@/lib/api/page'

type AdminListProps<TData extends RowData> = {
  /** Search box and filters. */
  toolbar: ReactNode
  query: UseQueryResult<Page<TData>>
  columns: DataTableColumn<TData>[]
  /** What the rows are, plural and lower-case: "services". Used for the caption, counts and messages. */
  noun: string
  /** One of them: "service". */
  singular: string
  onPageChange: (page: number) => void
  /** A problem with a row action (activate, delete), shown above the table. */
  actionError?: unknown
}

/** The standard paged, filterable admin list: toolbar, loading and error states, table, pagination. */
export function AdminList<TData extends RowData>({
  toolbar,
  query,
  columns,
  noun,
  singular,
  onPageChange,
  actionError,
}: AdminListProps<TData>) {
  return (
    <Card>
      <CardContent className="grid gap-4">
        {toolbar}
        {actionError ? <ErrorState title={`That change to ${noun} could not be saved`} error={actionError} /> : null}
        {query.isPending ? (
          <div className="grid gap-2" aria-busy="true" aria-label={`Loading ${noun}`}>
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-9 w-full" />
            ))}
          </div>
        ) : query.isError ? (
          <ErrorState title={`Could not load ${noun}`} error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <div className={query.isPlaceholderData ? 'opacity-60 transition-opacity' : undefined}>
            <DataTable
              columns={columns}
              data={query.data.items}
              caption={noun.charAt(0).toUpperCase() + noun.slice(1)}
              emptyMessage={`No ${noun} match. Try changing the search or filters.`}
            />
            <Pagination
              page={query.data.page}
              totalPages={query.data.totalPages}
              totalItems={query.data.totalItems}
              noun={noun}
              singular={singular}
              onPageChange={onPageChange}
            />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
