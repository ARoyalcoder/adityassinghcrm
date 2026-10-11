import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  CheckCircle2,
  Clock,
  Download,
  Edit3,
  FileX2,
  History,
  Layers,
  Send,
  ShoppingBag,
  Trash2,
  XCircle,
} from 'lucide-react'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
import { ConvertQuotationToOrderModal } from '@/features/orders/components/ConvertQuotationToOrderModal'
import {
  approveQuotation,
  cancelQuotation,
  deleteQuotation,
  generateQuotationPdf,
  markQuotationAccepted,
  markQuotationRejected,
  quotationDetailQuery,
  quotationKeys,
  quotationVersionQuery,
  rejectQuotationApproval,
  requestQuotationApproval,
  sendQuotationToClient,
} from '../api'
import { CreateVersionModal } from '../components/CreateVersionModal'
import {
  QuotationActionModal,
  type QuotationActionType,
} from '../components/QuotationActionModal'
import { QuotationModal } from '../components/QuotationModal'
import { QuotationStatusBadge } from '../components/QuotationStatusBadge'

export function QuotationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const canUpdate = useHasPermission('QUOTATION_UPDATE')
  const canApprove = useHasPermission('QUOTATION_APPROVE')
  const canDelete = useHasPermission('QUOTATION_DELETE')
  const canCreateOrder = useHasPermission('ORDER_CREATE')

  const [selectedVersionNum, setSelectedVersionNum] = useState<number | null>(null)

  // Modals
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [versionModalOpen, setVersionModalOpen] = useState(false)
  const [actionModal, setActionModal] = useState<QuotationActionType | null>(null)
  const [convertOrderModalOpen, setConvertOrderModalOpen] = useState(false)
  const [pdfGenerating, setPdfGenerating] = useState(false)

  const detailQuery = useQuery({
    ...quotationDetailQuery(id!),
    enabled: Boolean(id),
  })

  const q = detailQuery.data
  const activeVerNum = selectedVersionNum ?? q?.currentVersionNumber ?? 1

  const versionQuery = useQuery({
    ...quotationVersionQuery(id!, activeVerNum),
    enabled: Boolean(id && selectedVersionNum != null && selectedVersionNum !== q?.currentVersionNumber),
  })

  // Displayed version (either current from detail query, or fetched historic version)
  const displayedVersion =
    selectedVersionNum == null || selectedVersionNum === q?.currentVersionNumber
      ? q?.currentVersion
      : versionQuery.data

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: quotationKeys.all })
  }

  // Mutations
  const actionMut = useMutation({
    mutationFn: async ({
      action,
      data,
    }: {
      action: QuotationActionType
      data: { notes?: string; reason?: string; nextFollowUpDate?: string }
    }) => {
      if (!id) return
      switch (action) {
        case 'request_approval':
          return requestQuotationApproval(id, data.notes)
        case 'approve':
          return approveQuotation(id, data.notes)
        case 'reject_approval':
          return rejectQuotationApproval(id, data.reason)
        case 'send':
          return sendQuotationToClient(id, data.nextFollowUpDate, data.notes)
        case 'accept':
          return markQuotationAccepted(id, data.notes)
        case 'reject':
          return markQuotationRejected(id, data.reason || 'Client declined')
        case 'cancel':
          return cancelQuotation(id, data.reason)
      }
    },
    onSuccess: () => {
      invalidate()
      setActionModal(null)
    },
  })

  const deleteMut = useMutation({
    mutationFn: () => deleteQuotation(id!),
    onSuccess: () => {
      navigate('/quotations')
    },
  })

  async function handleDownloadPdf() {
    if (!id) return
    try {
      setPdfGenerating(true)
      const res = await generateQuotationPdf(id, displayedVersion?.versionNumber)
      window.open(res.url, '_blank')
    } catch (err) {
      alert((err as Error).message || 'Failed to generate PDF')
    } finally {
      setPdfGenerating(false)
    }
  }

  function formatCurrency(val?: number | null) {
    if (val == null) return '₹0'
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(val)
  }

  if (detailQuery.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }

  if (detailQuery.isError || !q) {
    return (
      <ErrorState
        title="Quotation not found"
        error={detailQuery.error}
      />
    )
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Back button */}
      <div>
        <Link
          to="/quotations"
          className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to Quotations
        </Link>
      </div>

      {/* HEADER & LIFECYCLE ACTION BAR */}
      <div className="flex flex-col gap-4 rounded-lg border bg-card p-6 shadow-sm">
        <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                {q.quotationNumber}
              </h1>
              <QuotationStatusBadge status={q.status} />
              <Badge variant="secondary" className="font-semibold">
                Version {q.currentVersionNumber}
              </Badge>
            </div>
            <p className="mt-1 text-sm font-medium text-foreground">{q.title}</p>
            <div className="mt-2 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
              {q.customerName && <span>Customer: <strong className="text-foreground">{q.customerName}</strong></span>}
              {q.leadName && <span>Lead: <strong className="text-foreground">{q.leadName}</strong></span>}
              {q.opportunityTitle && <span>Deal: <strong className="text-foreground">{q.opportunityTitle}</strong></span>}
              <span>Owner: <strong className="text-foreground">{q.ownerName}</strong></span>
              {q.validUntil && <span>Valid Until: <strong>{new Date(q.validUntil).toLocaleDateString()}</strong></span>}
            </div>
          </div>

          {/* Action buttons based on current status */}
          <div className="flex flex-wrap items-center gap-2">
            {/* PDF Generation */}
            <Button
              variant="outline"
              size="sm"
              disabled={pdfGenerating}
              onClick={handleDownloadPdf}
              className="gap-1.5"
            >
              <Download className="h-4 w-4" />
              {pdfGenerating ? 'Generating...' : 'Download PDF'}
            </Button>

            {/* Edit Draft */}
            {q.status === 'DRAFT' && canUpdate && (
              <Button variant="outline" size="sm" onClick={() => setEditModalOpen(true)} className="gap-1.5">
                <Edit3 className="h-4 w-4" />
                Edit Draft
              </Button>
            )}

            {/* Create New Version / Revision */}
            {canUpdate && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setVersionModalOpen(true)}
                className="gap-1.5"
              >
                <Layers className="h-4 w-4" />
                New Version (v{q.currentVersionNumber + 1})
              </Button>
            )}

            {/* Workflow transitions */}
            {q.status === 'DRAFT' && canUpdate && (
              <Button
                size="sm"
                onClick={() => setActionModal('request_approval')}
                className="gap-1.5"
              >
                <Clock className="h-4 w-4" />
                Submit for Approval
              </Button>
            )}

            {q.status === 'PENDING_APPROVAL' && canApprove && (
              <>
                <Button
                  size="sm"
                  onClick={() => setActionModal('approve')}
                  className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
                >
                  <CheckCircle2 className="h-4 w-4" />
                  Approve
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => setActionModal('reject_approval')}
                  className="gap-1.5"
                >
                  <XCircle className="h-4 w-4" />
                  Reject
                </Button>
              </>
            )}

            {(q.status === 'APPROVED' || q.status === 'DRAFT') && canUpdate && (
              <Button
                size="sm"
                onClick={() => setActionModal('send')}
                className="gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white"
              >
                <Send className="h-4 w-4" />
                Send to Client
              </Button>
            )}

            {(q.status === 'SENT' || q.status === 'VIEWED') && canUpdate && (
              <>
                <Button
                  size="sm"
                  onClick={() => setActionModal('accept')}
                  className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
                >
                  <CheckCircle2 className="h-4 w-4" />
                  Client Accepted
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setActionModal('reject')}
                  className="gap-1.5 text-rose-600 border-rose-300 hover:bg-rose-50"
                >
                  <FileX2 className="h-4 w-4" />
                  Client Rejected
                </Button>
              </>
            )}

            {q.status === 'ACCEPTED' && canCreateOrder && (
              <Button
                size="sm"
                onClick={() => setConvertOrderModalOpen(true)}
                className="gap-1.5 bg-blue-600 hover:bg-blue-700 text-white"
              >
                <ShoppingBag className="h-4 w-4" />
                Convert to Order
              </Button>
            )}

            {canDelete && q.status !== 'CANCELLED' && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setActionModal('cancel')}
                className="text-destructive hover:bg-destructive/10"
              >
                Cancel Quotation
              </Button>
            )}

            {canDelete && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  if (confirm('Are you sure you want to delete this quotation?')) {
                    deleteMut.mutate()
                  }
                }}
                className="text-destructive hover:bg-destructive/10"
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            )}
          </div>
        </div>

        {/* Approval info alert if rejected or approved */}
        {q.rejectionReason && q.status === 'DRAFT' && (
          <div className="rounded-md border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800">
            <strong>Internal Approval Rejection: </strong> {q.rejectionReason} (by {q.rejectedBy})
          </div>
        )}

        {q.clientRejectionReason && q.status === 'REJECTED' && (
          <div className="rounded-md border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800">
            <strong>Client Decline Reason: </strong> {q.clientRejectionReason}
          </div>
        )}
      </div>

      {/* VERSION SELECTOR BAR */}
      <div className="flex items-center justify-between rounded-lg border bg-muted/40 p-3 text-xs">
        <div className="flex items-center gap-2">
          <History className="h-4 w-4 text-muted-foreground" />
          <span className="font-semibold text-foreground">Quotation Versions:</span>
          <div className="flex flex-wrap gap-1">
            {q.allVersions.map((ver) => {
              const isSelected = displayedVersion?.versionNumber === ver.versionNumber
              return (
                <button
                  key={ver.id}
                  type="button"
                  onClick={() => setSelectedVersionNum(ver.versionNumber)}
                  className={`rounded px-2.5 py-1 font-medium transition-colors ${
                    isSelected
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'bg-card text-muted-foreground hover:bg-muted hover:text-foreground border'
                  }`}
                >
                  v{ver.versionNumber} {ver.versionNumber === q.currentVersionNumber ? '(Active)' : ''}
                </button>
              )
            })}
          </div>
        </div>

        {displayedVersion?.changeSummary && (
          <span className="text-muted-foreground italic">
            &quot;{displayedVersion.changeSummary}&quot;
          </span>
        )}
      </div>

      {/* DISPLAYED VERSION ITEMS TABLE & FINANCIALS */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Line Items Table */}
        <div className="lg:col-span-2 space-y-4">
          <Card className="shadow-sm">
            <CardHeader className="py-3.5 border-b">
              <CardTitle className="text-sm font-semibold">
                Line Items (Version {displayedVersion?.versionNumber})
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-12">#</TableHead>
                    <TableHead>Service / Description</TableHead>
                    <TableHead className="text-right">Qty</TableHead>
                    <TableHead className="text-right">Unit Price</TableHead>
                    <TableHead className="text-right">Tax</TableHead>
                    <TableHead className="text-right">Total</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {displayedVersion?.items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-mono text-xs">{item.itemOrder}</TableCell>
                      <TableCell>
                        <div className="font-medium text-foreground">{item.serviceName}</div>
                        {item.description && (
                          <div className="text-xs text-muted-foreground">{item.description}</div>
                        )}
                      </TableCell>
                      <TableCell className="text-right">{item.quantity}</TableCell>
                      <TableCell className="text-right">{formatCurrency(item.unitPrice)}</TableCell>
                      <TableCell className="text-right text-xs text-muted-foreground">
                        {item.taxPercentage}% ({formatCurrency(item.taxAmount)})
                      </TableCell>
                      <TableCell className="text-right font-semibold">
                        {formatCurrency(item.totalAmount)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          {/* Terms & Notes */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Card className="shadow-sm">
              <CardHeader className="py-3 border-b">
                <CardTitle className="text-xs font-semibold uppercase text-muted-foreground">
                  Commercial Terms
                </CardTitle>
              </CardHeader>
              <CardContent className="p-3 text-xs text-foreground whitespace-pre-wrap">
                {displayedVersion?.terms || 'No specific terms recorded.'}
              </CardContent>
            </Card>

            <Card className="shadow-sm">
              <CardHeader className="py-3 border-b">
                <CardTitle className="text-xs font-semibold uppercase text-muted-foreground">
                  Internal Notes
                </CardTitle>
              </CardHeader>
              <CardContent className="p-3 text-xs text-muted-foreground whitespace-pre-wrap">
                {displayedVersion?.notes || 'No internal notes.'}
              </CardContent>
            </Card>
          </div>
        </div>

        {/* Financial Calculation Summary Card & Timeline */}
        <div className="space-y-4">
          <Card className="shadow-sm border-primary/20 bg-card">
            <CardHeader className="py-3.5 border-b">
              <CardTitle className="text-sm font-semibold">Pricing Summary</CardTitle>
            </CardHeader>
            <CardContent className="p-4 space-y-3 text-sm">
              <div className="flex justify-between text-muted-foreground">
                <span>Subtotal</span>
                <span>{formatCurrency(displayedVersion?.subtotal)}</span>
              </div>

              {(displayedVersion?.discountAmount ?? 0) > 0 && (
                <div className="flex justify-between text-rose-600">
                  <span>
                    Discount ({displayedVersion?.discountPercentage}%)
                  </span>
                  <span>- {formatCurrency(displayedVersion?.discountAmount)}</span>
                </div>
              )}

              {(displayedVersion?.taxAmount ?? 0) > 0 && (
                <div className="flex justify-between text-muted-foreground">
                  <span>
                    Tax / GST ({displayedVersion?.taxPercentage}%)
                  </span>
                  <span>+ {formatCurrency(displayedVersion?.taxAmount)}</span>
                </div>
              )}

              <div className="flex justify-between border-t pt-3 text-base font-bold text-foreground">
                <span>Final Quotation Value</span>
                <span className="text-primary">{formatCurrency(displayedVersion?.finalAmount)}</span>
              </div>
            </CardContent>
          </Card>

          {/* Audit Trail */}
          <Card className="shadow-sm">
            <CardHeader className="py-3 border-b">
              <CardTitle className="text-xs font-semibold uppercase text-muted-foreground">
                Audit Trail & History
              </CardTitle>
            </CardHeader>
            <CardContent className="p-3">
              <div className="space-y-3 text-xs">
                {q.auditLogs.map((log) => (
                  <div key={log.id} className="border-l-2 border-primary/40 pl-2.5">
                    <div className="flex items-center justify-between font-semibold text-foreground">
                      <span>{log.action}</span>
                      <span className="text-[10px] text-muted-foreground">
                        {new Date(log.performedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>
                    {log.notes && <div className="text-muted-foreground mt-0.5">{log.notes}</div>}
                    <div className="text-[10px] text-muted-foreground mt-1">
                      By {log.performedBy} on {new Date(log.performedAt).toLocaleDateString()}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* MODALS */}
      {editModalOpen && (
        <QuotationModal
          open={editModalOpen}
          onOpenChange={setEditModalOpen}
          quotation={q}
          onSuccess={() => invalidate()}
        />
      )}

      {versionModalOpen && (
        <CreateVersionModal
          open={versionModalOpen}
          onOpenChange={setVersionModalOpen}
          quotation={q}
          onSuccess={() => invalidate()}
        />
      )}

      {actionModal && (
        <QuotationActionModal
          open={Boolean(actionModal)}
          onOpenChange={(open) => !open && setActionModal(null)}
          actionType={actionModal}
          quotationNumber={q.quotationNumber}
          isPending={actionMut.isPending}
          onConfirm={(data) => {
            actionMut.mutate({ action: actionModal, data })
          }}
        />
      )}

      {convertOrderModalOpen && (
        <ConvertQuotationToOrderModal
          open={convertOrderModalOpen}
          onClose={() => setConvertOrderModalOpen(false)}
          quotation={q}
        />
      )}
    </div>
  )
}
