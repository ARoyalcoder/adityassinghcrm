import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { servicesQuery } from '@/features/catalog/api'
import { useQuery } from '@tanstack/react-query'
import type { ItemInput } from '../api'

interface QuotationItemsEditorProps {
  items: ItemInput[]
  onChange: (items: ItemInput[]) => void
  disabled?: boolean
}

export function QuotationItemsEditor({ items, onChange, disabled = false }: QuotationItemsEditorProps) {
  const servicesList = useQuery(servicesQuery({}))

  function addItem() {
    onChange([
      ...items,
      {
        serviceId: null,
        serviceName: '',
        description: '',
        quantity: 1,
        unitPrice: 0,
        discountPercentage: 0,
        taxPercentage: 18,
      },
    ])
  }

  function removeItem(index: number) {
    const updated = items.filter((_, i) => i !== index)
    onChange(updated)
  }

  function updateItem(index: number, patch: Partial<ItemInput>) {
    const updated = items.map((itm, i) => (i === index ? { ...itm, ...patch } : itm))
    onChange(updated)
  }

  function calculateItemRow(item: ItemInput) {
    const sub = (item.quantity || 0) * (item.unitPrice || 0)
    const disc = sub * ((item.discountPercentage || 0) / 100)
    const afterDisc = Math.max(0, sub - disc)
    const tax = afterDisc * ((item.taxPercentage || 0) / 100)
    return {
      subtotal: sub,
      discount: disc,
      tax,
      total: afterDisc + tax,
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <Label className="text-sm font-semibold">Quotation Line Items</Label>
        {!disabled && (
          <Button type="button" variant="outline" size="sm" onClick={addItem} className="h-7 text-xs">
            <Plus className="mr-1 h-3.5 w-3.5" />
            Add Item
          </Button>
        )}
      </div>

      <div className="space-y-2">
        {items.map((item, idx) => {
          const rowCalc = calculateItemRow(item)

          return (
            <div
              key={idx}
              className="rounded-lg border bg-card p-3 shadow-sm space-y-2 text-xs"
            >
              <div className="flex items-start justify-between gap-2">
                <span className="font-semibold text-muted-foreground">Item #{idx + 1}</span>
                {!disabled && items.length > 1 && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => removeItem(idx)}
                    className="h-6 w-6 p-0 text-destructive hover:bg-destructive/10"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                )}
              </div>

              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                <div>
                  <Label className="text-[11px]">Service Offering</Label>
                  <Select
                    value={item.serviceId ?? ''}
                    disabled={disabled}
                    onChange={(e) => {
                      const sid = e.target.value
                      const s = servicesList.data?.items.find((serv) => serv.id === sid)
                      updateItem(idx, {
                        serviceId: sid || null,
                        serviceName: s ? s.name : item.serviceName,
                      })
                    }}
                    className="h-8 text-xs"
                  >
                    <option value="">Custom Service Item</option>
                    {servicesList.data?.items.map((serv) => (
                      <option key={serv.id} value={serv.id}>
                        {serv.name}
                      </option>
                    ))}
                  </Select>
                </div>

                <div>
                  <Label className="text-[11px]">Item Name / Title *</Label>
                  <Input
                    required
                    disabled={disabled}
                    placeholder="e.g. 5kW Inverter System"
                    value={item.serviceName}
                    onChange={(e) => updateItem(idx, { serviceName: e.target.value })}
                    className="h-8 text-xs"
                  />
                </div>
              </div>

              <div>
                <Label className="text-[11px]">Specifications / Description</Label>
                <Input
                  disabled={disabled}
                  placeholder="Optional line notes or model numbers..."
                  value={item.description ?? ''}
                  onChange={(e) => updateItem(idx, { description: e.target.value })}
                  className="h-8 text-xs"
                />
              </div>

              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <div>
                  <Label className="text-[11px]">Qty</Label>
                  <Input
                    type="number"
                    min="0.01"
                    step="any"
                    disabled={disabled}
                    value={item.quantity}
                    onChange={(e) => updateItem(idx, { quantity: parseFloat(e.target.value) || 0 })}
                    className="h-8 text-xs"
                  />
                </div>

                <div>
                  <Label className="text-[11px]">Unit Price (₹)</Label>
                  <Input
                    type="number"
                    min="0"
                    step="any"
                    disabled={disabled}
                    value={item.unitPrice}
                    onChange={(e) => updateItem(idx, { unitPrice: parseFloat(e.target.value) || 0 })}
                    className="h-8 text-xs"
                  />
                </div>

                <div>
                  <Label className="text-[11px]">Discount (%)</Label>
                  <Input
                    type="number"
                    min="0"
                    max="100"
                    disabled={disabled}
                    value={item.discountPercentage ?? 0}
                    onChange={(e) =>
                      updateItem(idx, { discountPercentage: parseFloat(e.target.value) || 0 })
                    }
                    className="h-8 text-xs"
                  />
                </div>

                <div>
                  <Label className="text-[11px]">GST / Tax (%)</Label>
                  <Input
                    type="number"
                    min="0"
                    max="100"
                    disabled={disabled}
                    value={item.taxPercentage ?? 18}
                    onChange={(e) =>
                      updateItem(idx, { taxPercentage: parseFloat(e.target.value) || 0 })
                    }
                    className="h-8 text-xs"
                  />
                </div>
              </div>

              {/* Row Total Summary */}
              <div className="flex justify-end pt-1 text-[11px] text-muted-foreground border-t border-dashed">
                <span>
                  Subtotal: ₹{rowCalc.subtotal.toFixed(2)} | Tax: ₹{rowCalc.tax.toFixed(2)} |{' '}
                  <strong className="text-foreground">Line Total: ₹{rowCalc.total.toFixed(2)}</strong>
                </span>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
