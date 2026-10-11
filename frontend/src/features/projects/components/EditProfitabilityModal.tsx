import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { X, DollarSign, Calculator } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { projectsApi, type ProjectProfitability } from '../api'

interface EditProfitabilityModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  projectId: string
  profitability?: ProjectProfitability
}

export function EditProfitabilityModal({
  open,
  onOpenChange,
  projectId,
  profitability,
}: EditProfitabilityModalProps) {
  return (
    <EditProfitabilityModalForm
      key={JSON.stringify(profitability)}
      open={open}
      onOpenChange={onOpenChange}
      projectId={projectId}
      profitability={profitability}
    />
  )
}

function EditProfitabilityModalForm({
  open,
  onOpenChange,
  projectId,
  profitability,
}: EditProfitabilityModalProps) {
  const queryClient = useQueryClient()

  const [revenue, setRevenue] = useState(profitability?.revenue.toString() ?? '')
  const [materialCost, setMaterialCost] = useState(profitability?.materialCost.toString() ?? '')
  const [labourCost, setLabourCost] = useState(profitability?.labourCost.toString() ?? '')
  const [vendorCost, setVendorCost] = useState(profitability?.vendorCost.toString() ?? '')
  const [travelCost, setTravelCost] = useState(profitability?.travelCost.toString() ?? '')
  const [otherExpenses, setOtherExpenses] = useState(profitability?.otherExpenses.toString() ?? '')
  const [expectedCost, setExpectedCost] = useState(profitability?.expectedCost.toString() ?? '')

  const mutation = useMutation({
    mutationFn: (data: Parameters<typeof projectsApi.updateProfitability>[1]) =>
      projectsApi.updateProfitability(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
      onOpenChange(false)
    },
  })

  const numRev = parseFloat(revenue) || 0
  const numMat = parseFloat(materialCost) || 0
  const numLab = parseFloat(labourCost) || 0
  const numVen = parseFloat(vendorCost) || 0
  const numTrv = parseFloat(travelCost) || 0
  const numOth = parseFloat(otherExpenses) || 0
  const numActual = numMat + numLab + numVen + numTrv + numOth
  const numProfit = numRev - numActual
  const numMargin = numRev > 0 ? ((numProfit / numRev) * 100).toFixed(2) : '0.00'

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutation.mutate({
      revenue: parseFloat(revenue) || 0,
      materialCost: parseFloat(materialCost) || 0,
      labourCost: parseFloat(labourCost) || 0,
      vendorCost: parseFloat(vendorCost) || 0,
      travelCost: parseFloat(travelCost) || 0,
      otherExpenses: parseFloat(otherExpenses) || 0,
      expectedCost: parseFloat(expectedCost) || 0,
    })
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-w-lg w-full p-6 shadow-xl bg-card max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between pb-3 border-b mb-4">
          <div className="flex items-center gap-2">
            <DollarSign className="h-5 w-5 text-emerald-500" />
            <h3 className="font-semibold text-base">Update Project Profitability & Costs</h3>
          </div>
          <Button variant="ghost" size="sm" className="h-7 w-7 p-0" onClick={() => onOpenChange(false)}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium block mb-1">Total Revenue (₹)</label>
              <Input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={revenue}
                onChange={(e) => setRevenue(e.target.value)}
                required
              />
            </div>
            <div>
              <label className="text-xs font-medium block mb-1">Expected / Target Cost (₹)</label>
              <Input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={expectedCost}
                onChange={(e) => setExpectedCost(e.target.value)}
              />
            </div>
          </div>

          <div className="p-3 bg-muted/40 rounded-lg space-y-3 border">
            <h4 className="text-xs font-semibold text-foreground uppercase tracking-wider">
              Itemized Cost Breakdown
            </h4>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs font-medium block mb-1">Material Cost (₹)</label>
                <Input
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  value={materialCost}
                  onChange={(e) => setMaterialCost(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Labour Cost (₹)</label>
                <Input
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  value={labourCost}
                  onChange={(e) => setLabourCost(e.target.value)}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs font-medium block mb-1">Vendor / Subcontractor (₹)</label>
                <Input
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  value={vendorCost}
                  onChange={(e) => setVendorCost(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Travel & Logistics (₹)</label>
                <Input
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  value={travelCost}
                  onChange={(e) => setTravelCost(e.target.value)}
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-medium block mb-1">Other Direct Expenses (₹)</label>
              <Input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={otherExpenses}
                onChange={(e) => setOtherExpenses(e.target.value)}
              />
            </div>
          </div>

          {/* Real-time Calculation Preview */}
          <div className="p-3 bg-card border rounded-lg flex items-center justify-between text-xs">
            <div className="flex items-center gap-2 text-muted-foreground">
              <Calculator className="h-4 w-4 text-primary" />
              <span>Calculated Actual Cost:</span>
              <strong className="text-foreground">₹{numActual.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong>
            </div>
            <div>
              <span className="text-muted-foreground mr-1.5">Gross Margin:</span>
              <span className={`font-bold ${parseFloat(numMargin) >= 15 ? 'text-emerald-600' : 'text-amber-600'}`}>
                {numMargin}%
              </span>
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t">
            <Button type="button" variant="outline" size="sm" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={mutation.isPending}>
              {mutation.isPending ? 'Saving...' : 'Save Financials'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
