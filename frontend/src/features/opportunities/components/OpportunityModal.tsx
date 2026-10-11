import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { customersQuery } from '@/features/customers/api'
import { leadsQuery } from '@/features/leads/api'
import { servicesQuery } from '@/features/catalog/api'
import {
  createOpportunity,
  updateOpportunity,
  type CreateOpportunityPayload,
  type OpportunityDetail,
  type PipelineDetail,
} from '../api'

interface OpportunityModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  pipeline: PipelineDetail
  defaultStageId?: string | null
  opportunity?: OpportunityDetail | null
  onSuccess: (opportunity: OpportunityDetail) => void
}

export function OpportunityModal({
  open,
  onOpenChange,
  pipeline,
  defaultStageId,
  opportunity,
  onSuccess,
}: OpportunityModalProps) {
  const isEditing = Boolean(opportunity)

  const [title, setTitle] = useState(opportunity?.title ?? '')
  const [value, setValue] = useState(opportunity?.value?.toString() ?? '')
  const [probability, setProbability] = useState(
    opportunity?.probability?.toString() ??
      pipeline.stages.find((s) => s.id === defaultStageId)?.defaultProbability?.toString() ??
      '20'
  )
  const [expectedCloseDate, setExpectedCloseDate] = useState(
    opportunity?.expectedCloseDate ?? ''
  )
  const [stageId, setStageId] = useState(
    opportunity?.currentStage.id ?? defaultStageId ?? pipeline.stages[0]?.id ?? ''
  )

  // Associated relationships
  const [customerId, setCustomerId] = useState(opportunity?.customerId ?? '')
  const [customerName, setCustomerName] = useState(opportunity?.customerName ?? '')
  const [leadId, setLeadId] = useState(opportunity?.leadId ?? '')
  const [leadName, setLeadName] = useState(opportunity?.leadName ?? '')
  const [serviceId, setServiceId] = useState(opportunity?.serviceId ?? '')
  const [serviceName, setServiceName] = useState(opportunity?.serviceName ?? '')

  // Queries for select pickers
  const customersList = useQuery(customersQuery({}))
  const leadsList = useQuery(leadsQuery({}))
  const servicesList = useQuery(servicesQuery({}))

  const createMut = useMutation({
    mutationFn: (payload: CreateOpportunityPayload) => createOpportunity(payload),
    onSuccess: (created) => {
      onSuccess(created)
      onOpenChange(false)
    },
  })

  const updateMut = useMutation({
    mutationFn: (payload: { id: string; data: CreateOpportunityPayload }) =>
      updateOpportunity(payload.id, payload.data),
    onSuccess: (updated) => {
      onSuccess(updated)
      onOpenChange(false)
    },
  })

  const isPending = createMut.isPending || updateMut.isPending
  const error = createMut.error || updateMut.error

  if (!open) return null

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!title.trim()) return

    const parsedValue = value ? parseFloat(value) : 0
    const parsedProb = probability ? parseInt(probability, 10) : 20

    if (isEditing && opportunity) {
      updateMut.mutate({
        id: opportunity.id,
        data: {
          title: title.trim(),
          customerId: customerId || null,
          customerName: customerName || null,
          leadId: leadId || null,
          leadName: leadName || null,
          serviceId: serviceId || null,
          serviceName: serviceName || null,
          value: parsedValue,
          probability: parsedProb,
          expectedCloseDate: expectedCloseDate || null,
        },
      })
    } else {
      createMut.mutate({
        title: title.trim(),
        customerId: customerId || null,
        customerName: customerName || null,
        leadId: leadId || null,
        leadName: leadName || null,
        serviceId: serviceId || null,
        serviceName: serviceName || null,
        value: parsedValue,
        probability: parsedProb,
        expectedCloseDate: expectedCloseDate || null,
        pipelineId: pipeline.id,
        stageId: stageId || null,
      })
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-h-[90vh] w-full max-w-lg overflow-y-auto p-6 shadow-xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="flex items-center justify-between pb-3 border-b">
            <div>
              <h2 className="text-lg font-semibold">{isEditing ? 'Edit Opportunity' : 'New Opportunity'}</h2>
              <p className="text-xs text-muted-foreground">
                {isEditing
                  ? 'Update opportunity details and forecast parameters.'
                  : 'Track a new business deal in the sales pipeline.'}
              </p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={() => onOpenChange(false)}
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>

          {error && (
            <div className="rounded-md bg-destructive/10 p-3 text-xs text-destructive">
              {(error as Error).message || 'Failed to save opportunity'}
            </div>
          )}

          <div className="space-y-3">
            <div>
              <Label htmlFor="opp-title">Opportunity Title *</Label>
              <Input
                id="opp-title"
                required
                placeholder="e.g. 10kW Rooftop Solar - Commercial"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="opp-value">Value (₹) *</Label>
                <Input
                  id="opp-value"
                  type="number"
                  min="0"
                  step="any"
                  placeholder="0.00"
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                />
              </div>

              <div>
                <Label htmlFor="opp-prob">Win Probability (%)</Label>
                <Input
                  id="opp-prob"
                  type="number"
                  min="0"
                  max="100"
                  placeholder="20"
                  value={probability}
                  onChange={(e) => setProbability(e.target.value)}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="opp-close-date">Expected Close Date</Label>
                <Input
                  id="opp-close-date"
                  type="date"
                  value={expectedCloseDate}
                  onChange={(e) => setExpectedCloseDate(e.target.value)}
                />
              </div>

              {!isEditing && (
                <div>
                  <Label htmlFor="opp-stage">Initial Stage</Label>
                  <Select
                    id="opp-stage"
                    value={stageId}
                    onChange={(e) => {
                      const newStageId = e.target.value
                      setStageId(newStageId)
                      const st = pipeline.stages.find((s) => s.id === newStageId)
                      if (st) setProbability(st.defaultProbability.toString())
                    }}
                  >
                    {pipeline.stages.map((st) => (
                      <option key={st.id} value={st.id}>
                        {st.name} ({st.defaultProbability}%)
                      </option>
                    ))}
                  </Select>
                </div>
              )}
            </div>

            {/* Customer Picker */}
            <div>
              <Label htmlFor="opp-customer">Associated Customer</Label>
              <Select
                id="opp-customer"
                value={customerId}
                onChange={(e) => {
                  const val = e.target.value
                  setCustomerId(val)
                  const cust = customersList.data?.items.find((c) => c.id === val)
                  setCustomerName(cust ? cust.displayName : '')
                }}
              >
                <option value="">None / Not linked</option>
                {customersList.data?.items.map((cust) => (
                  <option key={cust.id} value={cust.id}>
                    {cust.displayName} ({cust.customerNumber})
                  </option>
                ))}
              </Select>
            </div>

            {/* Lead Picker */}
            <div>
              <Label htmlFor="opp-lead">Associated Lead</Label>
              <Select
                id="opp-lead"
                value={leadId}
                onChange={(e) => {
                  const val = e.target.value
                  setLeadId(val)
                  const lead = leadsList.data?.items.find((l) => l.id === val)
                  setLeadName(lead ? lead.contactName : '')
                }}
              >
                <option value="">None / Not linked</option>
                {leadsList.data?.items.map((lead) => (
                  <option key={lead.id} value={lead.id}>
                    {lead.contactName} ({lead.service.name})
                  </option>
                ))}
              </Select>
            </div>

            {/* Service Offering Picker */}
            <div>
              <Label htmlFor="opp-service">Service Offering</Label>
              <Select
                id="opp-service"
                value={serviceId}
                onChange={(e) => {
                  const val = e.target.value
                  setServiceId(val)
                  const s = servicesList.data?.items.find((item) => item.id === val)
                  setServiceName(s ? s.name : '')
                }}
              >
                <option value="">None / Custom service</option>
                {servicesList.data?.items.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t">
            <Button
              type="button"
              variant="outline"
              disabled={isPending}
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isPending || !title.trim()}>
              {isPending ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Opportunity'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
