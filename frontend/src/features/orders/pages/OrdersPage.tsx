import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  CheckCircle2,
  Clock,
  ExternalLink,
  Layers,
  Search,
  ShoppingBag,
  Truck,
  XCircle,
} from 'lucide-react'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  ORDER_STATUSES,
  orderDashboardQuery,
  ordersQuery,
  type OrderStatus,
} from '../api'
import { OrderStatusBadge } from '../components/OrderStatusBadge'

export function OrdersPage() {
  const [statusFilter, setStatusFilter] = useState<OrderStatus | 'ALL'>('ALL')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const dashboardQuery = useQuery(orderDashboardQuery())

  const ordersListQuery = useQuery(
    ordersQuery({
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      search: search || undefined,
      page,
      size: 20,
    })
  )

  const dashboard = dashboardQuery.data
  const orders = ordersListQuery.data?.items ?? []
  const totalPages = ordersListQuery.data?.totalPages ?? 1
  const totalItems = ordersListQuery.data?.totalItems ?? 0

  function formatCurrency(val?: number | null) {
    if (val == null) return '₹0'
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val)
  }

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSearch(searchInput.trim())
    setPage(0)
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Top Header */}
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <ShoppingBag className="h-6 w-6 text-primary" />
            Orders
          </h1>
          <p className="text-sm text-muted-foreground">
            Manage confirmed customer orders, fulfillment progress, and service delivery
          </p>
        </div>
      </div>

      {/* KPI Metrics Dashboard Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <Card className="border shadow-none">
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
              <span>Total Orders</span>
              <Layers className="h-4 w-4 text-blue-500" />
            </div>
            <p className="mt-2 text-2xl font-bold text-foreground">
              {dashboard?.totalOrders ?? 0}
            </p>
            <p className="text-[11px] font-semibold text-muted-foreground mt-0.5">
              {formatCurrency(dashboard?.totalValue)}
            </p>
          </CardContent>
        </Card>

        <Card className="border shadow-none">
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
              <span>Confirmed</span>
              <ShoppingBag className="h-4 w-4 text-blue-500" />
            </div>
            <p className="mt-2 text-2xl font-bold text-blue-600 dark:text-blue-400">
              {dashboard?.confirmedCount ?? 0}
            </p>
            <p className="text-[11px] text-muted-foreground mt-0.5">Ready for work</p>
          </CardContent>
        </Card>

        <Card className="border shadow-none">
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
              <span>In Progress</span>
              <Clock className="h-4 w-4 text-amber-500" />
            </div>
            <p className="mt-2 text-2xl font-bold text-amber-600 dark:text-amber-400">
              {dashboard?.inProgressCount ?? 0}
            </p>
            <p className="text-[11px] text-muted-foreground mt-0.5">Under execution</p>
          </CardContent>
        </Card>

        <Card className="border shadow-none">
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
              <span>Partially Deliv.</span>
              <Truck className="h-4 w-4 text-purple-500" />
            </div>
            <p className="mt-2 text-2xl font-bold text-purple-600 dark:text-purple-400">
              {dashboard?.partiallyDeliveredCount ?? 0}
            </p>
            <p className="text-[11px] text-muted-foreground mt-0.5">Ongoing delivery</p>
          </CardContent>
        </Card>

        <Card className="border shadow-none">
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
              <span>Completed</span>
              <CheckCircle2 className="h-4 w-4 text-emerald-500" />
            </div>
            <p className="mt-2 text-2xl font-bold text-emerald-600 dark:text-emerald-400">
              {dashboard?.completedCount ?? 0}
            </p>
            <p className="text-[11px] text-muted-foreground mt-0.5">Fully fulfilled</p>
          </CardContent>
        </Card>

        <Card className="border shadow-none">
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
              <span>Cancelled</span>
              <XCircle className="h-4 w-4 text-rose-500" />
            </div>
            <p className="mt-2 text-2xl font-bold text-rose-600 dark:text-rose-400">
              {dashboard?.cancelledCount ?? 0}
            </p>
            <p className="text-[11px] text-muted-foreground mt-0.5">Closed/Cancelled</p>
          </CardContent>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col gap-3 rounded-lg border bg-card p-4 shadow-sm md:flex-row md:items-center md:justify-between">
        <form onSubmit={handleSearchSubmit} className="flex flex-1 items-center gap-2 max-w-md">
          <div className="relative flex-1">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search by order #, title, customer, quotation..."
              className="pl-8"
            />
          </div>
          <Button type="submit" variant="secondary" size="sm">
            Search
          </Button>
          {search && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => {
                setSearchInput('')
                setSearch('')
                setPage(0)
              }}
            >
              Clear
            </Button>
          )}
        </form>

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground font-medium">Status:</span>
            <Select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as OrderStatus | 'ALL')
                setPage(0)
              }}
              className="w-[180px] h-9 text-xs"
            >
              <option value="ALL">All Statuses</option>
              {ORDER_STATUSES.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
          </div>
        </div>
      </div>

      {/* Orders Table */}
      <Card className="shadow-sm">
        <CardContent className="p-0">
          {ordersListQuery.isLoading ? (
            <div className="space-y-3 p-6">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : ordersListQuery.isError ? (
            <div className="p-6">
              <ErrorState
                title="Could not load orders"
                error={ordersListQuery.error}
                onRetry={() => ordersListQuery.refetch()}
              />
            </div>
          ) : orders.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-muted text-muted-foreground mb-3">
                <ShoppingBag className="h-6 w-6" />
              </div>
              <h3 className="text-base font-semibold text-foreground">No orders found</h3>
              <p className="mt-1 text-xs text-muted-foreground max-w-sm">
                {search || statusFilter !== 'ALL'
                  ? 'No orders match your filter criteria.'
                  : 'Orders are created by converting accepted quotations or creating direct sales orders.'}
              </p>
              <div className="mt-4">
                <Button variant="outline" size="sm" asChild>
                  <Link to="/quotations">View Accepted Quotations</Link>
                </Button>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Order #</TableHead>
                    <TableHead>Title & Customer</TableHead>
                    <TableHead>Quotation Ref</TableHead>
                    <TableHead>Delivery Date</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Total Amount</TableHead>
                    <TableHead className="text-right">Created</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {orders.map((o) => (
                    <TableRow key={o.id} className="hover:bg-muted/50">
                      <TableCell>
                        <Link
                          to={`/orders/${o.id}`}
                          className="font-semibold text-primary hover:underline"
                        >
                          {o.orderNumber}
                        </Link>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-col">
                          <Link
                            to={`/orders/${o.id}`}
                            className="font-medium text-foreground hover:underline line-clamp-1"
                          >
                            {o.title}
                          </Link>
                          <span className="text-xs text-muted-foreground">{o.customerName}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        {o.quotationNumber ? (
                          <div className="flex items-center gap-1 text-xs text-muted-foreground">
                            {o.quotationId ? (
                              <Link
                                to={`/quotations/${o.quotationId}`}
                                className="inline-flex items-center gap-1 text-primary hover:underline"
                              >
                                {o.quotationNumber}
                                <ExternalLink className="h-3 w-3" />
                              </Link>
                            ) : (
                              <span>{o.quotationNumber}</span>
                            )}
                            {o.quotationVersionNumber && (
                              <span className="text-[10px] text-muted-foreground">
                                (v{o.quotationVersionNumber})
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-xs text-muted-foreground italic">Direct Order</span>
                        )}
                      </TableCell>
                      <TableCell className="text-xs">
                        {o.deliveryDate ? (
                          <span>{new Date(o.deliveryDate).toLocaleDateString()}</span>
                        ) : (
                          <span className="text-muted-foreground italic">Not set</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <OrderStatusBadge status={o.status} />
                      </TableCell>
                      <TableCell className="text-right font-semibold text-foreground">
                        {formatCurrency(o.finalAmount)}
                      </TableCell>
                      <TableCell className="text-right text-xs text-muted-foreground">
                        {new Date(o.createdAt).toLocaleDateString()}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t px-4 py-3 text-xs text-muted-foreground">
              <span>
                Showing page {page + 1} of {totalPages} ({totalItems} orders)
              </span>
              <div className="flex items-center gap-1">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
