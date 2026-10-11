import { useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { dataTableColumns } from '@/components/data-table/DataTable'
import { StatusBadge } from '@/components/feedback/StatusBadge'
import { PageHeader } from '@/components/layout/PageHeader'
import { AdminList } from '@/components/list/AdminList'
import { FilterSelect, ListToolbar, SearchInput } from '@/components/list/ListControls'
import { STATUS_OPTIONS } from '@/components/list/options'
import { Button } from '@/components/ui/button'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  catalogKeys,
  categoriesQuery,
  setCategoryActive,
  verticalsQuery,
  type Category,
} from '@/features/catalog/api'
import { useListParams } from '@/lib/use-list-params'

const column = dataTableColumns<Category>()

export function CategoriesPage() {
  const params = useListParams()
  const queryClient = useQueryClient()
  const canCreate = useHasPermission('CATALOG_CREATE')
  const canUpdate = useHasPermission('CATALOG_UPDATE')

  const categories = useQuery(
    categoriesQuery({
      search: params.get('search'),
      vertical: params.get('vertical'),
      active: params.get('active'),
      page: params.page,
    }),
  )
  const verticals = useQuery(verticalsQuery)

  const toggle = useMutation({
    mutationFn: (category: Category) => setCategoryActive(category.id, !category.active),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.categories }),
  })
  const { mutate: toggleActive, isPending: toggling } = toggle

  const columns = useMemo(
    () =>
      column.columns([
        column.accessor('name', {
          header: 'Category',
          cell: ({ row }) => (
            <div className="grid">
              <span className="font-medium">{row.original.name}</span>
              <span className="font-mono text-xs text-muted-foreground">{row.original.code}</span>
            </div>
          ),
        }),
        column.accessor((category) => category.vertical.name, { id: 'vertical', header: 'Vertical' }),
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
                  <Link to={`/admin/categories/${row.original.id}/edit`} aria-label={`Edit ${row.original.name}`}>
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
        title="Service categories"
        description="Groups of services inside a vertical."
        action={
          canCreate ? (
            <Button asChild>
              <Link to="/admin/categories/new">
                <Plus aria-hidden="true" />
                New category
              </Link>
            </Button>
          ) : null
        }
      />
      <AdminList
        noun="categories"
        singular="category"
        query={categories}
        columns={columns}
        onPageChange={params.setPage}
        actionError={toggle.error}
        toolbar={
          <ListToolbar>
            <SearchInput
              label="Search categories"
              value={params.get('search')}
              onChange={(value) => params.set('search', value)}
            />
            <FilterSelect
              label="Vertical"
              allLabel="All verticals"
              value={params.get('vertical')}
              onChange={(value) => params.set('vertical', value)}
              options={(verticals.data ?? []).map((item) => ({ value: item.code, label: item.name }))}
            />
            <FilterSelect
              label="Status"
              allLabel="Any status"
              value={params.get('active')}
              onChange={(value) => params.set('active', value)}
              options={STATUS_OPTIONS}
            />
          </ListToolbar>
        }
      />
    </div>
  )
}
