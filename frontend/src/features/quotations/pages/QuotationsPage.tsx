import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  Calendar,
  FileSpreadsheet,
  Plus,
  Search,
} from 'lucide-react'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
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
import { useHasPermission } from '@/features/auth/auth-context'
import {
  quotationDashboardQuery,
  quotationKeys,
  quotationsQuery,
  type QuotationFilters,
} from '../api'
import { QuotationDashboardOverview } from '../components/QuotationDashboardOverview'
import { QuotationModal } from '../components/QuotationModal'
import { QuotationStatusBadge } from '../components/QuotationStatusBadge'

export function QuotationsPage() {
  const queryClient = useQueryClient()
  const canCreate = useHasPermission('QUOTATION_CREATE')

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [sort] = useState('createdAt,desc')
  const [page, setPage] = useState(0)
  const pageSize = 20

  const [createModalOpen, setCreateModalOpen] = useState(false)

  const dashboardQuery = useQuery(quotationDashboardQuery())

  const filters: QuotationFilters = {
    status: statusFilter || null,
    search: search.trim() || null,
  }

  const listQuery = useQuery(quotationsQuery({ filters, page, size: pageSize, sort }))

  function invalidateAll() {
    void queryClient.invalidateQueries({ queryKey: quotationKeys.all })
  }

  function formatCurrency(val: number) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Quotations"
        description="Create formal client proposals, manage versions, configure items, track approvals, and generate PDFs."
        action={
          canCreate && (
            <Button onClick={() => setCreateModalOpen(true)}>
              <Plus className="mr-1.5 h-4 w-4" />
              New Quotation
            </Button>
          )
        }
      />

      {/* DASHBOARD OVERVIEW */}
      {dashboardQuery.isLoading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-7">
          {[1, 2, 3, 4, 5, 6, 7].map((i) => (
            <Skeleton key={i} className="h-24 rounded-lg" />
          ))}
        </div>
      ) : dashboardQuery.data ? (
        <QuotationDashboardOverview dashboard={dashboardQuery.data} />
      ) : null}

      {/* LIST SECTION */}
      <Card className="shadow-sm">
        <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between border-b">
          <div className="flex items-center gap-2">
            <FileSpreadsheet className="h-4 w-4 text-muted-foreground" />
            <h2 className="text-sm font-semibold">All Quotations</h2>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-[200px]">
              <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                placeholder="Search number, title, customer..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setPage(0)
                }}
                className="pl-8 text-xs h-8"
              />
            </div>

            <Select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value)
                setPage(0)
              }}
              className="w-36 text-xs h-8"
            >
              <option value="">All Statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="PENDING_APPROVAL">Pending Approval</option>
              <option value="APPROVED">Approved</option>
              <option value="SENT">Sent</option>
              <option value="VIEWED">Viewed</option>
              <option value="ACCEPTED">Accepted</option>
              <option value="REJECTED">Rejected</option>
              <option value="EXPIRED">Expired</option>
              <option value="CANCELLED">Cancelled</option>
            </Select>
          </div>
        </div>

        <CardContent className="p-0">
          {listQuery.isLoading ? (
            <div className="space-y-2 p-6">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : listQuery.isError ? (
            <div className="p-6">
              <ErrorState
                title="Failed to load quotations"
                error={listQuery.error}
              />
            </div>
          ) : (
            <div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Quotation #</TableHead>
                    <TableHead>Title</TableHead>
                    <TableHead>Customer / Lead</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Version</TableHead>
                    <TableHead>Amount (₹)</TableHead>
                    <TableHead>Follow-up Date</TableHead>
                    <TableHead>Owner</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {listQuery.data?.items.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} className="h-32 text-center text-sm text-muted-foreground">
                        No quotations found.
                      </TableCell>
                    </TableRow>
                  ) : (
                    listQuery.data?.items.map((q) => (
                      <TableRow key={q.id}>
                        <TableCell>
                          <Link
                            to={`/quotations/${q.id}`}
                            className="font-bold text-primary hover:underline"
                          >
                            {q.quotationNumber}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <Link
                            to={`/quotations/${q.id}`}
                            className="font-medium text-foreground hover:underline"
                          >
                            {q.title}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <div className="text-sm">
                            {q.customerName || q.leadName || 'Direct'}
                          </div>
                          <div className="text-xs text-muted-foreground">
                            {q.customerName ? 'Customer' : q.leadName ? 'Lead' : ''}
                          </div>
                        </TableCell>
                        <TableCell>
                          <QuotationStatusBadge status={q.status} />
                        </TableCell>
                        <TableCell>
                          <span className="rounded bg-muted px-2 py-0.5 text-xs font-semibold">
                            v{q.currentVersionNumber}
                          </span>
                        </TableCell>
                        <TableCell className="font-semibold text-foreground">
                          {formatCurrency(q.finalAmount)}
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground">
                          {q.nextFollowUpDate ? (
                            <span className="flex items-center gap-1">
                              <Calendar className="h-3 w-3" />
                              {new Date(q.nextFollowUpDate).toLocaleDateString()}
                            </span>
                          ) : (
                            '—'
                          )}
                        </TableCell>
                        <TableCell className="text-xs">{q.ownerName}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>

              {/* Pagination */}
              {listQuery.data && listQuery.data.totalPages > 1 && (
                <div className="flex items-center justify-between border-t px-4 py-3 text-xs text-muted-foreground">
                  <div>
                    Page {listQuery.data.page + 1} of {listQuery.data.totalPages} (
                    {listQuery.data.totalItems} total)
                  </div>
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={listQuery.data.page === 0}
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                    >
                      Previous
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={listQuery.data.page >= listQuery.data.totalPages - 1}
                      onClick={() => setPage((p) => p + 1)}
                    >
                      Next
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* CREATE MODAL */}
      {createModalOpen && (
        <QuotationModal
          open={createModalOpen}
          onOpenChange={setCreateModalOpen}
          onSuccess={() => {
            invalidateAll()
          }}
        />
      )}
    </div>
  )
}
