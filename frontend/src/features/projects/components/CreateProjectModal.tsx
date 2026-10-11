import { useState } from 'react'
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { projectsApi } from '../api'

interface CreateProjectModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialCustomerId?: string
  initialCustomerName?: string
  initialOrderId?: string
  initialOrderNumber?: string
}

export function CreateProjectModal({
  open,
  onOpenChange,
  initialCustomerId,
  initialCustomerName,
  initialOrderId,
  initialOrderNumber,
}: CreateProjectModalProps) {
  const queryClient = useQueryClient()
  const { data: statuses } = useQuery(projectsApi.statusesQuery())

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [customerId, setCustomerId] = useState(initialCustomerId || '')
  const [customerName, setCustomerName] = useState(initialCustomerName || '')
  const [orderId, setOrderId] = useState(initialOrderId || '')
  const [orderNumber, setOrderNumber] = useState(initialOrderNumber || '')
  const [serviceName, setServiceName] = useState('')
  const [startDate, setStartDate] = useState('')
  const [expectedCompletionDate, setExpectedCompletionDate] = useState('')
  const [priority, setPriority] = useState('MEDIUM')
  const [budget, setBudget] = useState('')
  const [revenue, setRevenue] = useState('')
  const [status, setStatus] = useState('')
  const [notes, setNotes] = useState('')

  const createMutation = useMutation({
    mutationFn: (data: Parameters<typeof projectsApi.createProject>[0]) =>
      projectsApi.createProject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      onOpenChange(false)
      resetForm()
    },
  })

  const resetForm = () => {
    setTitle('')
    setDescription('')
    if (!initialCustomerId) {
      setCustomerId('')
      setCustomerName('')
    }
    if (!initialOrderId) {
      setOrderId('')
      setOrderNumber('')
    }
    setServiceName('')
    setStartDate('')
    setExpectedCompletionDate('')
    setPriority('MEDIUM')
    setBudget('')
    setRevenue('')
    setStatus('')
    setNotes('')
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !customerId.trim() || !customerName.trim()) return

    createMutation.mutate({
      title: title.trim(),
      description: description.trim() || undefined,
      customerId: customerId.trim(),
      customerName: customerName.trim(),
      orderId: orderId.trim() || undefined,
      orderNumber: orderNumber.trim() || undefined,
      serviceName: serviceName.trim() || undefined,
      startDate: startDate || undefined,
      expectedCompletionDate: expectedCompletionDate || undefined,
      status: status || undefined,
      priority,
      budget: budget ? parseFloat(budget) : 0,
      revenue: revenue ? parseFloat(revenue) : 0,
      notes: notes.trim() || undefined,
    })
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-w-xl w-full p-6 shadow-xl bg-card max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between pb-3 border-b mb-4">
          <h3 className="font-semibold text-base">Create New Project</h3>
          <Button variant="ghost" size="sm" className="h-7 w-7 p-0" onClick={() => onOpenChange(false)}>
            <X className="h-4 w-4" />
          </Button>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="text-xs font-medium block mb-1">Project Title *</label>
            <Input
              placeholder="e.g. 10kW Commercial Solar Installation"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>

          <div>
            <label className="text-xs font-medium block mb-1">Description</label>
            <Input
              placeholder="Brief summary of project scope"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium block mb-1">Customer Name *</label>
              <Input
                placeholder="Client Name"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                required
              />
            </div>
            <div>
              <label className="text-xs font-medium block mb-1">Customer ID (UUID) *</label>
              <Input
                placeholder="UUID"
                value={customerId}
                onChange={(e) => setCustomerId(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium block mb-1">Order Number (Optional)</label>
              <Input
                placeholder="ORD-..."
                value={orderNumber}
                onChange={(e) => setOrderNumber(e.target.value)}
              />
            </div>
            <div>
              <label className="text-xs font-medium block mb-1">Order ID (UUID)</label>
              <Input
                placeholder="UUID"
                value={orderId}
                onChange={(e) => setOrderId(e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium block mb-1">Service Offering</label>
              <Input
                placeholder="e.g. Solar Rooftop Setup"
                value={serviceName}
                onChange={(e) => setServiceName(e.target.value)}
              />
            </div>
            <div>
              <label className="text-xs font-medium block mb-1">Initial Status</label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className="w-full h-9 text-xs bg-background border rounded px-2"
              >
                <option value="">Default (Planning)</option>
                {statuses?.map((s) => (
                  <option key={s.code} value={s.code}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="text-xs font-medium block mb-1">Priority</label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                className="w-full h-9 text-xs bg-background border rounded px-2"
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="CRITICAL">CRITICAL</option>
              </select>
            </div>
            <div>
              <label className="text-xs font-medium block mb-1">Start Date</label>
              <Input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="h-9 text-xs"
              />
            </div>
            <div>
              <label className="text-xs font-medium block mb-1">Expected Completion</label>
              <Input
                type="date"
                value={expectedCompletionDate}
                onChange={(e) => setExpectedCompletionDate(e.target.value)}
                className="h-9 text-xs"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium block mb-1">Budget (₹)</label>
              <Input
                type="number"
                placeholder="0.00"
                value={budget}
                onChange={(e) => setBudget(e.target.value)}
                className="h-9 text-xs"
              />
            </div>
            <div>
              <label className="text-xs font-medium block mb-1">Revenue (₹)</label>
              <Input
                type="number"
                placeholder="0.00"
                value={revenue}
                onChange={(e) => setRevenue(e.target.value)}
                className="h-9 text-xs"
              />
            </div>
          </div>

          <div>
            <label className="text-xs font-medium block mb-1">Internal Notes</label>
            <Input
              placeholder="Any operational notes or instructions"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating...' : 'Create Project'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
