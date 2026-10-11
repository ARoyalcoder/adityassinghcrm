import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { CheckCircle2, ShoppingBag, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { convertQuotationToOrder, orderKeys } from '../api'

interface ConvertQuotationToOrderModalProps {
  open: boolean
  onClose: () => void
  quotation: {
    id: string
    quotationNumber: string
    title: string
    customerName?: string | null
    finalAmount: number
    ownerId?: string | null
  }
}

export function ConvertQuotationToOrderModal({
  open,
  onClose,
  quotation,
}: ConvertQuotationToOrderModalProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [title, setTitle] = useState(
    `Order for ${quotation.quotationNumber}${quotation.customerName ? ` - ${quotation.customerName}` : ''}`
  )
  const [deliveryDate, setDeliveryDate] = useState('')
  const [notes, setNotes] = useState('')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const convertMut = useMutation({
    mutationFn: () =>
      convertQuotationToOrder({
        quotationId: quotation.id,
        title: title.trim(),
        deliveryDate: deliveryDate ? deliveryDate : undefined,
        notes: notes.trim() ? notes.trim() : undefined,
      }),
    onSuccess: (order) => {
      void queryClient.invalidateQueries({ queryKey: orderKeys.all })
      void queryClient.invalidateQueries({ queryKey: ['quotations'] })
      onClose()
      navigate(`/orders/${order.id}`)
    },
    onError: (err: Error) => {
      setErrorMsg(err.message || 'Failed to convert quotation to order')
    },
  })

  if (!open) return null

  function formatCurrency(val: number) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(val)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="w-full max-w-lg shadow-lg">
        <CardHeader className="flex flex-row items-center justify-between pb-4">
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-100 text-blue-600 dark:bg-blue-950 dark:text-blue-400">
              <ShoppingBag className="h-5 w-5" />
            </div>
            <div>
              <CardTitle className="text-lg font-bold">Convert to Confirmed Order</CardTitle>
              <p className="text-xs text-muted-foreground">
                Convert accepted quotation {quotation.quotationNumber} into a sales order
              </p>
            </div>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose} disabled={convertMut.isPending}>
            <X className="h-4 w-4" />
          </Button>
        </CardHeader>

        <CardContent className="space-y-4 pt-0">
          {errorMsg && (
            <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive font-medium">
              {errorMsg}
            </div>
          )}

          {/* Preserved Reference Info */}
          <div className="rounded-md border bg-muted/40 p-3 space-y-1.5 text-xs">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Quotation Reference:</span>
              <span className="font-semibold text-foreground">{quotation.quotationNumber}</span>
            </div>
            {quotation.customerName && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Customer:</span>
                <span className="font-semibold text-foreground">{quotation.customerName}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-muted-foreground">Preserved Order Value:</span>
              <span className="font-bold text-emerald-600 dark:text-emerald-400">
                {formatCurrency(quotation.finalAmount)}
              </span>
            </div>
            <div className="pt-1 text-[11px] text-muted-foreground border-t flex items-center gap-1.5">
              <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500 shrink-0" />
              <span>Line items, taxes, discounts, and terms are preserved automatically.</span>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="order-title">Order Title</Label>
            <Input
              id="order-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Order title"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="order-delivery-date">Expected Delivery / Completion Date</Label>
            <Input
              id="order-delivery-date"
              type="date"
              value={deliveryDate}
              onChange={(e) => setDeliveryDate(e.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="order-notes">Order Notes / Delivery Instructions</Label>
            <Textarea
              id="order-notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
              placeholder="Any special fulfillment or delivery instructions..."
            />
          </div>
        </CardContent>

        <div className="flex justify-end gap-2 border-t pt-4 px-5 pb-5">
          <Button variant="outline" onClick={onClose} disabled={convertMut.isPending}>
            Cancel
          </Button>
          <Button
            onClick={() => convertMut.mutate()}
            disabled={convertMut.isPending || !title.trim()}
            className="gap-1.5 bg-blue-600 hover:bg-blue-700 text-white"
          >
            <ShoppingBag className="h-4 w-4" />
            {convertMut.isPending ? 'Converting...' : 'Generate Order'}
          </Button>
        </div>
      </Card>
    </div>
  )
}
