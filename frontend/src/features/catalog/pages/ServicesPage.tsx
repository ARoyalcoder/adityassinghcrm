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
  BILLING_TYPES,
  billingTypeLabel,
  catalogKeys,
  categoriesQuery,
  servicesQuery,
  setServiceActive,
  verticalsQuery,
  type Service,
} from '@/features/catalog/api'
import { useListParams } from '@/lib/use-list-params'

const column = dataTableColumns<Service>()
const price = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 })

export function ServicesPage() {
  const params = useListParams()
  const queryClient = useQueryClient()
  const canCreate = useHasPermission('CATALOG_CREATE')
  const canUpdate = useHasPermission('CATALOG_UPDATE')

  const vertical = params.get('vertical')
  const services = useQuery(
    servicesQuery({
      search: params.get('search'),
      vertical,
      categoryId: params.get('categoryId'),
      active: params.get('active'),
      billingType: params.get('billingType'),
      page: params.page,
    }),
  )
  const verticals = useQuery(verticalsQuery)
  // Choices for the category filter: the categories of the chosen vertical, or all of them.
  const categories = useQuery(categoriesQuery({ vertical, size: 100 }))

  const toggle = useMutation({
    mutationFn: (service: Service) => setServiceActive(service.id, !service.active),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.services }),
  })
  const { mutate: toggleActive, isPending: toggling } = toggle

  const columns = useMemo(
    () =>
      column.columns([
        column.accessor('name', {
          header: 'Service',
          cell: ({ row }) => (
            <div className="grid">
              <span className="font-medium">{row.original.name}</span>
              <span className="font-mono text-xs text-muted-foreground">{row.original.code}</span>
            </div>
          ),
        }),
        column.accessor((service) => service.vertical.name, { id: 'vertical', header: 'Vertical' }),
        column.accessor((service) => service.category.name, { id: 'category', header: 'Category' }),
        column.accessor('billingType', {
          header: 'Billing',
          cell: ({ getValue }) => billingTypeLabel(getValue()),
        }),
        column.accessor('basePrice', {
          header: 'Base price',
          cell: ({ row }) => {
            const { basePrice, unitLabel } = row.original
            if (basePrice === null || basePrice === undefined) {
              return <span className="text-muted-foreground">On quote</span>
            }
            return (
              <span className="tabular-nums">
                {price.format(basePrice)}
                {unitLabel ? <span className="text-muted-foreground"> {unitLabel}</span> : null}
              </span>
            )
          },
        }),
        column.accessor('active', { header: 'Status', cell: ({ getValue }) => <StatusBadge active={getValue()} /> }),
        column.display({
          id: 'actions',
          header: () => <span className="sr-only">Actions</span>,
          cell: ({ row }) => (
            <div className="flex justify-end gap-2">
              <Button asChild variant="outline" size="sm">
                <Link to={`/admin/services/${row.original.id}/fields`} aria-label={`Lead fields of ${row.original.name}`}>
                  Lead fields
                </Link>
              </Button>
              {canUpdate ? (
                <>
                <Button asChild variant="outline" size="sm">
                  <Link to={`/admin/services/${row.original.id}/edit`} aria-label={`Edit ${row.original.name}`}>
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
                </>
              ) : null}
            </div>
          ),
        }),
      ]),
    [canUpdate, toggleActive, toggling],
  )

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Services"
        description="What the company sells. Each service belongs to a category in one of the six verticals."
        action={
          canCreate ? (
            <Button asChild>
              <Link to="/admin/services/new">
                <Plus aria-hidden="true" />
                New service
              </Link>
            </Button>
          ) : null
        }
      />
      <AdminList
        noun="services"
        singular="service"
        query={services}
        columns={columns}
        onPageChange={params.setPage}
        actionError={toggle.error}
        toolbar={
          <ListToolbar>
            <SearchInput
              label="Search services"
              value={params.get('search')}
              onChange={(value) => params.set('search', value)}
            />
            <FilterSelect
              label="Vertical"
              allLabel="All verticals"
              value={vertical}
              onChange={(value) => params.setMany({ vertical: value, categoryId: '' })}
              options={(verticals.data ?? []).map((item) => ({ value: item.code, label: item.name }))}
            />
            <FilterSelect
              label="Category"
              allLabel="All categories"
              value={params.get('categoryId')}
              onChange={(value) => params.set('categoryId', value)}
              options={(categories.data?.items ?? []).map((item) => ({
                value: item.id,
                label: vertical ? item.name : `${item.name} (${item.vertical.name})`,
              }))}
            />
            <FilterSelect
              label="Billing"
              allLabel="Any billing"
              value={params.get('billingType')}
              onChange={(value) => params.set('billingType', value)}
              options={BILLING_TYPES.map((type) => ({ value: type.value, label: type.label }))}
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
