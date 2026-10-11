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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  branchesQuery,
  branchKeys,
  branchLocationsQuery,
  setBranchActive,
  type Branch,
} from '@/features/organization/api'
import { useListParams } from '@/lib/use-list-params'

const column = dataTableColumns<Branch>()

export function BranchesPage() {
  const params = useListParams()
  const queryClient = useQueryClient()
  const canCreate = useHasPermission('BRANCH_CREATE')
  const canUpdate = useHasPermission('BRANCH_UPDATE')

  const state = params.get('state')
  const branches = useQuery(
    branchesQuery({
      search: params.get('search'),
      state,
      city: params.get('city'),
      active: params.get('active'),
      page: params.page,
    }),
  )
  const locations = useQuery(branchLocationsQuery)
  const states = locations.data ?? []
  // Cities of the chosen state, or of every state when none is chosen.
  const cities = [...new Set(states.filter((item) => !state || item.state === state).flatMap((item) => item.cities))]

  const toggle = useMutation({
    mutationFn: (branch: Branch) => setBranchActive(branch.id, !branch.active),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: branchKeys.all }),
  })
  const { mutate: toggleActive, isPending: toggling } = toggle

  const columns = useMemo(
    () =>
      column.columns([
        column.accessor('name', {
          header: 'Branch',
          cell: ({ row }) => (
            <div className="grid">
              <span className="flex items-center gap-2 font-medium">
                {row.original.name}
                {row.original.headOffice ? <Badge>Head office</Badge> : null}
              </span>
              <span className="font-mono text-xs text-muted-foreground">{row.original.code}</span>
            </div>
          ),
        }),
        column.accessor('city', { header: 'City' }),
        column.accessor('state', { header: 'State' }),
        column.accessor('phone', {
          header: 'Phone',
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
                  <Link to={`/admin/branches/${row.original.id}/edit`} aria-label={`Edit ${row.original.name}`}>
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
        title="Branches"
        description="The company's offices, by state and city."
        action={
          canCreate ? (
            <Button asChild>
              <Link to="/admin/branches/new">
                <Plus aria-hidden="true" />
                New branch
              </Link>
            </Button>
          ) : null
        }
      />
      <AdminList
        noun="branches"
        singular="branch"
        query={branches}
        columns={columns}
        onPageChange={params.setPage}
        actionError={toggle.error}
        toolbar={
          <ListToolbar>
            <SearchInput
              label="Search branches"
              value={params.get('search')}
              onChange={(value) => params.set('search', value)}
            />
            <FilterSelect
              label="State"
              allLabel="All states"
              value={state}
              onChange={(value) => params.setMany({ state: value, city: '' })}
              options={states.map((item) => ({ value: item.state, label: item.state }))}
            />
            <FilterSelect
              label="City"
              allLabel="All cities"
              value={params.get('city')}
              onChange={(value) => params.set('city', value)}
              options={cities.map((city) => ({ value: city, label: city }))}
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
