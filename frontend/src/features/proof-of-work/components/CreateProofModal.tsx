import { useState } from 'react'
import { Plus, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Select } from '@/components/ui/select'
import { PROOF_CATEGORIES, type CreateProofItemInput } from '../api'

interface CreateProofModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (input: CreateProofItemInput) => Promise<void>
  entityType: 'PROJECT' | 'JOB'
  entityId: string
  isSubmitting?: boolean
}

export function CreateProofModal({
  isOpen,
  onClose,
  onSubmit,
  entityType,
  entityId,
  isSubmitting = false,
}: CreateProofModalProps) {
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState<string>('BEFORE_PHOTO')
  const [technicianNotes, setTechnicianNotes] = useState('')
  const [checklistItems, setChecklistItems] = useState<string[]>([])
  const [newChecklistTitle, setNewChecklistTitle] = useState('')

  if (!isOpen) return null

  const handleAddChecklistItem = () => {
    if (newChecklistTitle.trim()) {
      setChecklistItems([...checklistItems, newChecklistTitle.trim()])
      setNewChecklistTitle('')
    }
  }

  const handleRemoveChecklistItem = (index: number) => {
    setChecklistItems(checklistItems.filter((_, i) => i !== index))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return

    await onSubmit({
      entityType,
      projectId: entityType === 'PROJECT' ? entityId : undefined,
      jobId: entityType === 'JOB' ? entityId : undefined,
      title: title.trim(),
      category,
      technicianNotes: technicianNotes.trim() || undefined,
      checklistTitles: checklistItems.length > 0 ? checklistItems : undefined,
    })

    setTitle('')
    setCategory('BEFORE_PHOTO')
    setTechnicianNotes('')
    setChecklistItems([])
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-card w-full max-w-lg rounded-xl border p-6 shadow-xl space-y-4">
        <div className="flex items-center justify-between pb-3 border-b">
          <h2 className="text-lg font-semibold">Record Proof of Work</h2>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={isSubmitting}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Record Title *</label>
            <Input
              required
              placeholder="e.g. Inverter Installation & Safety Inspection"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Proof Category *</label>
            <Select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              disabled={isSubmitting}
            >
              {PROOF_CATEGORIES.map((cat) => (
                <option key={cat.value} value={cat.value}>
                  {cat.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Technician Notes & Observations</label>
            <Textarea
              rows={3}
              placeholder="Detail site conditions, technical measurements, safety precautions taken..."
              value={technicianNotes}
              onChange={(e) => setTechnicianNotes(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          <div className="space-y-2 pt-2 border-t">
            <label className="text-xs font-medium flex items-center justify-between">
              <span>Checklist Items for Verification</span>
              <span className="text-muted-foreground font-normal">{checklistItems.length} items</span>
            </label>

            <div className="flex gap-2">
              <Input
                placeholder="Add checklist item (e.g. Grounding resistance checked)"
                value={newChecklistTitle}
                onChange={(e) => setNewChecklistTitle(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    handleAddChecklistItem()
                  }
                }}
                disabled={isSubmitting}
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleAddChecklistItem}
                disabled={isSubmitting || !newChecklistTitle.trim()}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>

            {checklistItems.length > 0 && (
              <ul className="space-y-1.5 max-h-36 overflow-y-auto">
                {checklistItems.map((item, index) => (
                  <li
                    key={index}
                    className="flex items-center justify-between text-xs bg-muted/50 p-2 rounded-md"
                  >
                    <span>{item}</span>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="h-6 w-6 p-0 text-muted-foreground hover:text-destructive"
                      onClick={() => handleRemoveChecklistItem(index)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t">
            <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting || !title.trim()}>
              {isSubmitting ? 'Creating...' : 'Create Record'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
