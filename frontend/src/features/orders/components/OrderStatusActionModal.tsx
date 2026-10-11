import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, Clock, Truck, X, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { orderKeys, type OrderStatus, updateOrderStatus } from '../api'

interface OrderStatusActionModalProps {
  open: boolean
  onClose: () => void
  orderId: string
  orderNumber: string
  targetStatus: OrderStatus
}

export function OrderStatusActionModal({
  open,
  onClose,
  orderId,
  orderNumber,
  targetStatus,
}: OrderStatusActionModalProps) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState('')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const isCancel = targetStatus === 'CANCELLED'

  const mutation = useMutation({
    mutationFn: () =>
      updateOrderStatus(orderId, {
        status: targetStatus,
        reason: isCancel ? reason.trim() : undefined,
        notes: notes.trim() ? notes.trim() : (isCancel ? reason.trim() : undefined),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: orderKeys.all })
      onClose()
    },
    onError: (err: Error) => {
      setErrorMsg(err.message || 'Failed to update order status')
    },
  })

  if (!open) return null

  function getStatusMeta() {
    switch (targetStatus) {
      case 'IN_PROGRESS':
        return {
          title: 'Mark Order In Progress',
          description: 'Commence fulfillment, procurement, or execution for order ' + orderNumber,
          icon: Clock,
          color: 'text-amber-600 bg-amber-100 dark:bg-amber-950 dark:text-amber-400',
          btnClass: 'bg-amber-600 hover:bg-amber-700 text-white',
          btnText: 'Start Execution',
        }
      case 'PARTIALLY_DELIVERED':
        return {
          title: 'Mark Partially Delivered',
          description: 'Update order ' + orderNumber + ' to reflect partial delivery of items/services',
          icon: Truck,
          color: 'text-purple-600 bg-purple-100 dark:bg-purple-950 dark:text-purple-400',
          btnClass: 'bg-purple-600 hover:bg-purple-700 text-white',
          btnText: 'Confirm Partial Delivery',
        }
      case 'COMPLETED':
        return {
          title: 'Complete & Deliver Order',
          description: 'Finalize order ' + orderNumber + '. All services/items have been fulfilled',
          icon: CheckCircle2,
          color: 'text-emerald-600 bg-emerald-100 dark:bg-emerald-950 dark:text-emerald-400',
          btnClass: 'bg-emerald-600 hover:bg-emerald-700 text-white',
          btnText: 'Mark Completed',
        }
      case 'CANCELLED':
        return {
          title: 'Cancel Order',
          description: 'Cancelling order ' + orderNumber + ' will stop all fulfillment work',
          icon: XCircle,
          color: 'text-rose-600 bg-rose-100 dark:bg-rose-950 dark:text-rose-400',
          btnClass: 'bg-rose-600 hover:bg-rose-700 text-white',
          btnText: 'Confirm Cancellation',
        }
      default:
        return {
          title: 'Update Status',
          description: 'Change order status',
          icon: AlertCircle,
          color: 'text-slate-600 bg-slate-100',
          btnClass: 'bg-primary text-primary-foreground',
          btnText: 'Update',
        }
    }
  }

  const meta = getStatusMeta()
  const Icon = meta.icon

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="w-full max-w-md shadow-lg">
        <CardHeader className="flex flex-row items-center justify-between pb-3">
          <div className="flex items-center gap-2">
            <div className={`flex h-9 w-9 items-center justify-center rounded-lg ${meta.color}`}>
              <Icon className="h-5 w-5" />
            </div>
            <div>
              <CardTitle className="text-base font-bold">{meta.title}</CardTitle>
              <p className="text-xs text-muted-foreground">{meta.description}</p>
            </div>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose} disabled={mutation.isPending}>
            <X className="h-4 w-4" />
          </Button>
        </CardHeader>

        <CardContent className="space-y-4 pt-1">
          {errorMsg && (
            <div className="rounded-md bg-destructive/15 p-3 text-xs text-destructive font-medium">
              {errorMsg}
            </div>
          )}

          {isCancel && (
            <div className="space-y-2">
              <Label htmlFor="cancel-reason" className="text-xs font-semibold">
                Cancellation Reason <span className="text-destructive">*</span>
              </Label>
              <Textarea
                id="cancel-reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Reason why the order is being cancelled..."
                rows={3}
                required
              />
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="action-notes" className="text-xs font-semibold">
              {isCancel ? 'Additional Notes (optional)' : 'Notes / Status Update Comments'}
            </Label>
            <Textarea
              id="action-notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Add details regarding this status update..."
              rows={3}
            />
          </div>
        </CardContent>

        <div className="flex justify-end gap-2 border-t pt-4 px-5 pb-5">
          <Button variant="outline" size="sm" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button
            size="sm"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || (isCancel && !reason.trim())}
            className={meta.btnClass}
          >
            {mutation.isPending ? 'Updating...' : meta.btnText}
          </Button>
        </div>
      </Card>
    </div>
  )
}
