import { useState } from 'react'
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query'
import { Plus, Trash2, Settings, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { projectsApi } from '../api'

interface ProjectStatusSettingsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ProjectStatusSettingsModal({ open, onOpenChange }: ProjectStatusSettingsModalProps) {
  const queryClient = useQueryClient()
  const { data: statuses } = useQuery(projectsApi.statusesQuery())

  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [color, setColor] = useState('#3b82f6')
  const [category, setCategory] = useState('IN_PROGRESS')
  const [isDefault, setIsDefault] = useState(false)
  const [isCompleted, setIsCompleted] = useState(false)
  const [isCancelled, setIsCancelled] = useState(false)

  const createMutation = useMutation({
    mutationFn: (data: any) => projectsApi.createStatusConfig(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', 'statuses'] })
      queryClient.invalidateQueries({ queryKey: ['projects', 'kanban'] })
      setCode('')
      setName('')
      setColor('#3b82f6')
      setIsDefault(false)
      setIsCompleted(false)
      setIsCancelled(false)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => projectsApi.deleteStatusConfig(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', 'statuses'] })
      queryClient.invalidateQueries({ queryKey: ['projects', 'kanban'] })
    },
  })

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault()
    if (!code.trim() || !name.trim()) return

    createMutation.mutate({
      code: code.trim().toUpperCase().replace(/\s+/g, '_'),
      name: name.trim(),
      displayOrder: (statuses?.length || 0) + 1,
      color,
      category,
      isDefault,
      isCompleted,
      isCancelled,
    })
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-w-lg w-full p-6 shadow-xl bg-card max-h-[85vh] overflow-y-auto">
        <div className="flex items-center justify-between pb-3 border-b mb-4">
          <div className="flex items-center gap-2">
            <Settings className="h-5 w-5" />
            <h3 className="font-semibold text-base">Configurable Project Statuses</h3>
          </div>
          <Button variant="ghost" size="sm" className="h-7 w-7 p-0" onClick={() => onOpenChange(false)}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        <div className="space-y-4">
          <p className="text-xs text-muted-foreground">
            Configure custom workflow statuses. These appear as columns across your Project Kanban board.
          </p>

          {/* List of current statuses */}
          <div className="space-y-2 border rounded-lg p-2 bg-muted/20">
            {statuses?.map((s) => (
              <div
                key={s.id}
                className="flex items-center justify-between p-2 rounded bg-card border text-xs"
              >
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full" style={{ backgroundColor: s.color }} />
                  <span className="font-semibold">{s.name}</span>
                  <span className="text-muted-foreground font-mono text-[10px]">({s.code})</span>
                  {s.isDefault && (
                    <span className="text-[10px] bg-muted px-1.5 py-0.2 rounded font-medium">Default</span>
                  )}
                  {s.isCompleted && (
                    <span className="text-[10px] bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 px-1.5 py-0.2 rounded font-medium">
                      Completed
                    </span>
                  )}
                  {s.isCancelled && (
                    <span className="text-[10px] bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300 px-1.5 py-0.2 rounded font-medium">
                      Cancelled
                    </span>
                  )}
                </div>

                <Button
                  variant="ghost"
                  size="sm"
                  className="text-destructive h-6 w-6 p-0 hover:bg-destructive/10"
                  onClick={() => {
                    if (confirm(`Delete status ${s.name}?`)) {
                      deleteMutation.mutate(s.id)
                    }
                  }}
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            ))}
          </div>

          {/* Add status form */}
          <form onSubmit={handleAdd} className="space-y-3 pt-3 border-t">
            <h4 className="text-xs font-semibold">Add New Status</h4>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-[11px] font-medium block mb-1">Status Code *</label>
                <Input
                  placeholder="e.g. TESTING"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  className="h-8 text-xs font-mono"
                  required
                />
              </div>
              <div>
                <label className="text-[11px] font-medium block mb-1">Display Name *</label>
                <Input
                  placeholder="e.g. Quality Testing"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="h-8 text-xs"
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-[11px] font-medium block mb-1">Color (Hex/CSS)</label>
                <div className="flex items-center gap-2">
                  <input
                    type="color"
                    value={color}
                    onChange={(e) => setColor(e.target.value)}
                    className="h-8 w-10 cursor-pointer rounded border"
                  />
                  <Input
                    value={color}
                    onChange={(e) => setColor(e.target.value)}
                    className="h-8 text-xs font-mono"
                  />
                </div>
              </div>
              <div>
                <label className="text-[11px] font-medium block mb-1">Category</label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="w-full h-8 text-xs bg-background border rounded px-2"
                >
                  <option value="PLANNING">PLANNING</option>
                  <option value="IN_PROGRESS">IN_PROGRESS</option>
                  <option value="REVIEW">REVIEW</option>
                  <option value="DONE">DONE</option>
                  <option value="CANCELLED">CANCELLED</option>
                </select>
              </div>
            </div>

            <div className="flex flex-wrap gap-4 text-xs pt-1">
              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={isDefault}
                  onChange={(e) => setIsDefault(e.target.checked)}
                  className="rounded text-primary h-3.5 w-3.5"
                />
                <span>Default Initial Status</span>
              </label>
              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={isCompleted}
                  onChange={(e) => setIsCompleted(e.target.checked)}
                  className="rounded text-primary h-3.5 w-3.5"
                />
                <span>Marks Project Completed</span>
              </label>
              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={isCancelled}
                  onChange={(e) => setIsCancelled(e.target.checked)}
                  className="rounded text-primary h-3.5 w-3.5"
                />
                <span>Marks Project Cancelled</span>
              </label>
            </div>

            <div className="flex justify-end pt-2">
              <Button size="sm" type="submit" disabled={createMutation.isPending}>
                <Plus className="h-4 w-4 mr-1" />
                Add Status
              </Button>
            </div>
          </form>
        </div>
      </Card>
    </div>
  )
}
