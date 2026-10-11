import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock,
  ExternalLink,
  Layers,
  ShoppingBag,
  Truck,
  XCircle,
} from 'lucide-react'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
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
  orderDetailQuery,
  orderKeys,
  type OrderStatus,
  updateItemDelivery,
} from '../api'
import { OrderStatusActionModal } from '../components/OrderStatusActionModal'
import { OrderStatusBadge } from '../components/OrderStatusBadge'

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()

  const canUpdate = useHasPermission('ORDER_UPDATE')
  const canCancel = useHasPermission('ORDER_CANCEL')

  const [actionStatus, setActionStatus] = useState<OrderStatus | null>(null)
  const [editingItemId, setEditingItemId] = useState<string | null>(null)
  const [deliveryQtyInput, setDeliveryQtyInput] = useState<string>('')

  const detailQuery = useQuery({
    ...orderDetailQuery(id!),
    enabled: Boolean(id),
  })

  const order = detailQuery.data

  const deliveryMutation = useMutation({
    mutationFn: ({ itemId, qty }: { itemId: string; qty: number }) =>
      updateItemDelivery(id!, itemId, qty),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: orderKeys.detail(id!) })
      void queryClient.invalidateQueries({ queryKey: orderKeys.lists() })
      setEditingItemId(null)
    },
    onError: (err: Error) => {
      alert(err.message || 'Failed to update delivery quantity')
    },
  })

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

  if (detailQuery.isError || !order) {
    return (
      <ErrorState
        title="Order not found"
        error={detailQuery.error}
      />
    )
  }

  const isTerminal = order.status === 'COMPLETED' || order.status === 'CANCELLED'

  return (
    <div className="flex flex-col gap-6">
      {/* Back link */}
      <div>
        <Link
          to="/orders"
          className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to Orders
        </Link>
      </div>

      {/* HEADER & ACTION BAR */}
      <div className="flex flex-col gap-4 rounded-lg border bg-card p-6 shadow-sm">
        <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
                <ShoppingBag className="h-6 w-6 text-primary" />
                {order.orderNumber}
              </h1>
              <OrderStatusBadge status={order.status} />
              {order.quotationNumber && (
                <Badge variant="secondary" className="gap-1 font-medium">
                  Preserved Quote: {order.quotationNumber}
                  {order.quotationVersionNumber && ` (v${order.quotationVersionNumber})`}
                </Badge>
              )}
            </div>
            <p className="mt-1 text-base font-semibold text-foreground">{order.title}</p>
            <div className="mt-2 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
              <span>
                Customer:{' '}
                <strong className="text-foreground">{order.customerName}</strong>
              </span>
              <span>
                Owner: <strong className="text-foreground">{order.ownerName}</strong>
              </span>
              {order.deliveryDate && (
                <span>
                  Delivery Target:{' '}
                  <strong>{new Date(order.deliveryDate).toLocaleDateString()}</strong>
                </span>
              )}
              <span>
                Created: <strong>{new Date(order.createdAt).toLocaleDateString()}</strong>
              </span>
            </div>
          </div>

          {/* Lifecycle Action Buttons */}
          <div className="flex flex-wrap items-center gap-2">
            {order.status === 'CONFIRMED' && canUpdate && (
              <Button
                size="sm"
                onClick={() => setActionStatus('IN_PROGRESS')}
                className="gap-1.5 bg-amber-600 hover:bg-amber-700 text-white"
              >
                <Clock className="h-4 w-4" />
                Start Fulfillment
              </Button>
            )}

            {order.status === 'IN_PROGRESS' && canUpdate && (
              <>
                <Button
                  size="sm"
                  onClick={() => setActionStatus('PARTIALLY_DELIVERED')}
                  className="gap-1.5 bg-purple-600 hover:bg-purple-700 text-white"
                >
                  <Truck className="h-4 w-4" />
                  Mark Partially Delivered
                </Button>
                <Button
                  size="sm"
                  onClick={() => setActionStatus('COMPLETED')}
                  className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
                >
                  <CheckCircle2 className="h-4 w-4" />
                  Complete & Deliver
                </Button>
              </>
            )}

            {order.status === 'PARTIALLY_DELIVERED' && canUpdate && (
              <Button
                size="sm"
                onClick={() => setActionStatus('COMPLETED')}
                className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
              >
                <CheckCircle2 className="h-4 w-4" />
                Complete Order
              </Button>
            )}

            {!isTerminal && canCancel && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setActionStatus('CANCELLED')}
                className="gap-1.5 text-rose-600 border-rose-300 hover:bg-rose-50 dark:hover:bg-rose-950/30"
              >
                <XCircle className="h-4 w-4" />
                Cancel Order
              </Button>
            )}
          </div>
        </div>

        {/* Cancellation Notice if Cancelled */}
        {order.status === 'CANCELLED' && (
          <div className="mt-2 rounded-md border border-rose-200 bg-rose-50/60 p-3 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/20 dark:text-rose-300">
            <div className="flex items-center gap-2 font-semibold">
              <AlertCircle className="h-4 w-4 text-rose-600" />
              Order Cancelled {order.cancelledAt ? `on ${new Date(order.cancelledAt).toLocaleString()}` : ''}
              {order.cancelledBy ? ` by ${order.cancelledBy}` : ''}
            </div>
            {order.cancellationReason && (
              <p className="mt-1 text-rose-700 dark:text-rose-400">
                Reason: {order.cancellationReason}
              </p>
            )}
          </div>
        )}

        {/* Completion Notice if Completed */}
        {order.status === 'COMPLETED' && (
          <div className="mt-2 rounded-md border border-emerald-200 bg-emerald-50/60 p-3 text-xs text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/20 dark:text-emerald-300">
            <div className="flex items-center gap-2 font-semibold">
              <CheckCircle2 className="h-4 w-4 text-emerald-600" />
              Order Fulfilled & Completed {order.completedAt ? `on ${new Date(order.completedAt).toLocaleString()}` : ''}
              {order.completedBy ? ` by ${order.completedBy}` : ''}
            </div>
          </div>
        )}
      </div>

      {/* Extension Point Bridge Information Banner */}
      <div className="flex items-center justify-between gap-4 rounded-lg border bg-blue-50/40 p-4 text-xs text-blue-900 dark:border-blue-900/30 dark:bg-blue-950/20 dark:text-blue-300">
        <div className="flex items-start gap-3">
          <Layers className="h-5 w-5 text-blue-600 dark:text-blue-400 shrink-0 mt-0.5" />
          <div>
            <span className="font-semibold text-blue-950 dark:text-blue-200">
              Project Management Extension SPI Ready
            </span>
            <p className="mt-0.5 text-blue-800/80 dark:text-blue-300/80">
              Order confirmed event and OrderProjectBridge hook have recorded this order context. Once the Project
              module is activated, projects and task milestones will be instantiated automatically from this order.
            </p>
          </div>
        </div>
      </div>

      {/* Main Grid: Order Items & Summary */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Left Column: Line Items (2 cols) */}
        <div className="space-y-6 lg:col-span-2">
          <Card className="shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between pb-3">
              <div>
                <CardTitle className="text-base font-bold">Order Items & Delivery</CardTitle>
                <p className="text-xs text-muted-foreground">
                  Preserved services, quantities, delivery progress, and line-item pricing
                </p>
              </div>
            </CardHeader>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-10">#</TableHead>
                      <TableHead>Service / Description</TableHead>
                      <TableHead className="text-right">Qty</TableHead>
                      <TableHead className="text-right">Delivered</TableHead>
                      <TableHead className="text-right">Unit Price</TableHead>
                      <TableHead className="text-right">Tax</TableHead>
                      <TableHead className="text-right">Total</TableHead>
                      {!isTerminal && canUpdate && <TableHead className="text-center w-24">Action</TableHead>}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {order.items.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell className="font-medium text-xs text-muted-foreground">
                          {item.itemOrder}
                        </TableCell>
                        <TableCell>
                          <div className="font-semibold text-sm text-foreground">
                            {item.serviceName}
                          </div>
                          {item.description && (
                            <p className="text-xs text-muted-foreground line-clamp-2">
                              {item.description}
                            </p>
                          )}
                        </TableCell>
                        <TableCell className="text-right font-medium text-xs">
                          {item.quantity}
                        </TableCell>
                        <TableCell className="text-right text-xs">
                          {editingItemId === item.id ? (
                            <div className="flex items-center justify-end gap-1">
                              <Input
                                type="number"
                                min="0"
                                max={item.quantity}
                                step="any"
                                value={deliveryQtyInput}
                                onChange={(e) => setDeliveryQtyInput(e.target.value)}
                                className="h-7 w-16 text-right text-xs"
                              />
                            </div>
                          ) : (
                            <span
                              className={`font-semibold ${
                                item.deliveredQuantity >= item.quantity
                                  ? 'text-emerald-600 dark:text-emerald-400'
                                  : item.deliveredQuantity > 0
                                  ? 'text-amber-600 dark:text-amber-400'
                                  : 'text-muted-foreground'
                              }`}
                            >
                              {item.deliveredQuantity} / {item.quantity}
                            </span>
                          )}
                        </TableCell>
                        <TableCell className="text-right text-xs">
                          {formatCurrency(item.unitPrice)}
                        </TableCell>
                        <TableCell className="text-right text-xs text-muted-foreground">
                          {item.taxPercentage ? `${item.taxPercentage}%` : '0%'}
                        </TableCell>
                        <TableCell className="text-right font-semibold text-xs">
                          {formatCurrency(item.totalAmount)}
                        </TableCell>
                        {!isTerminal && canUpdate && (
                          <TableCell className="text-center">
                            {editingItemId === item.id ? (
                              <div className="flex items-center justify-center gap-1">
                                <Button
                                  size="sm"
                                  variant="ghost"
                                  className="h-6 px-1.5 text-xs text-emerald-600"
                                  disabled={deliveryMutation.isPending}
                                  onClick={() =>
                                    deliveryMutation.mutate({
                                      itemId: item.id,
                                      qty: parseFloat(deliveryQtyInput) || 0,
                                    })
                                  }
                                >
                                  Save
                                </Button>
                                <Button
                                  size="sm"
                                  variant="ghost"
                                  className="h-6 px-1 text-xs text-muted-foreground"
                                  onClick={() => setEditingItemId(null)}
                                >
                                  Cancel
                                </Button>
                              </div>
                            ) : (
                              <Button
                                size="sm"
                                variant="ghost"
                                className="h-6 px-1.5 text-[11px] text-primary"
                                onClick={() => {
                                  setEditingItemId(item.id)
                                  setDeliveryQtyInput(item.deliveredQuantity.toString())
                                }}
                              >
                                Update Qty
                              </Button>
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>

          {/* Notes & Terms */}
          {(order.notes || order.terms) && (
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {order.notes && (
                <Card className="shadow-sm">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-xs font-semibold uppercase text-muted-foreground">
                      Fulfillment & Delivery Notes
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="text-xs whitespace-pre-wrap text-foreground">
                    {order.notes}
                  </CardContent>
                </Card>
              )}

              {order.terms && (
                <Card className="shadow-sm">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-xs font-semibold uppercase text-muted-foreground">
                      Preserved Commercial Terms
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="text-xs whitespace-pre-wrap text-muted-foreground">
                    {order.terms}
                  </CardContent>
                </Card>
              )}
            </div>
          )}
        </div>

        {/* Right Column: Financial Summary & History (1 col) */}
        <div className="space-y-6">
          {/* Financial Breakdown Card */}
          <Card className="shadow-sm">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-bold">Financial Summary</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2.5 text-xs">
              <div className="flex justify-between text-muted-foreground">
                <span>Subtotal:</span>
                <span className="font-medium text-foreground">{formatCurrency(order.subtotal)}</span>
              </div>
              {order.discountAmount > 0 && (
                <div className="flex justify-between text-muted-foreground">
                  <span>Total Discount:</span>
                  <span className="font-medium text-rose-600">-{formatCurrency(order.discountAmount)}</span>
                </div>
              )}
              <div className="flex justify-between text-muted-foreground">
                <span>Tax Amount (GST):</span>
                <span className="font-medium text-foreground">{formatCurrency(order.taxAmount)}</span>
              </div>
              <div className="border-t pt-2.5 flex justify-between text-sm font-bold">
                <span>Final Order Amount:</span>
                <span className="text-emerald-600 dark:text-emerald-400">
                  {formatCurrency(order.finalAmount)}
                </span>
              </div>

              {/* Quotation Link */}
              {order.quotationId && (
                <div className="mt-4 pt-3 border-t">
                  <Link
                    to={`/quotations/${order.quotationId}`}
                    className="inline-flex items-center gap-1.5 text-xs text-primary font-medium hover:underline"
                  >
                    <span>View Originating Quotation ({order.quotationNumber})</span>
                    <ExternalLink className="h-3.5 w-3.5" />
                  </Link>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Order History Timeline Card */}
          <Card className="shadow-sm">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-bold">Order History</CardTitle>
            </CardHeader>
            <CardContent>
              {order.history.length === 0 ? (
                <p className="text-xs text-muted-foreground">No history records logged.</p>
              ) : (
                <div className="relative space-y-4 border-l-2 border-muted pl-4">
                  {order.history.map((h) => (
                    <div key={h.id} className="relative">
                      <div className="absolute -left-[21px] top-1 h-2.5 w-2.5 rounded-full bg-primary" />
                      <div className="flex items-center justify-between text-[11px] text-muted-foreground">
                        <span className="font-semibold text-foreground">{h.action}</span>
                        <span>{new Date(h.performedAt).toLocaleDateString()}</span>
                      </div>
                      {h.fromStatus && h.toStatus && (
                        <div className="mt-0.5 text-[11px]">
                          <span className="text-muted-foreground">{h.fromStatus}</span>
                          <span className="mx-1 font-bold">→</span>
                          <span className="font-semibold text-foreground">{h.toStatus}</span>
                        </div>
                      )}
                      {h.notes && (
                        <p className="mt-1 text-xs text-muted-foreground">{h.notes}</p>
                      )}
                      <p className="mt-0.5 text-[10px] text-muted-foreground">By {h.performedBy}</p>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Status Action Modal */}
      {actionStatus && (
        <OrderStatusActionModal
          open={Boolean(actionStatus)}
          onClose={() => setActionStatus(null)}
          orderId={order.id}
          orderNumber={order.orderNumber}
          targetStatus={actionStatus}
        />
      )}
    </div>
  )
}
