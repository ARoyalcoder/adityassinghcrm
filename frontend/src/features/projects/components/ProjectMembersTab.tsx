import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, Shield, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { projectsApi, type ProjectMember } from '../api'

interface ProjectMembersTabProps {
  projectId: string
  members: ProjectMember[]
  projectManagerId: string
}

export function ProjectMembersTab({ projectId, members, projectManagerId }: ProjectMembersTabProps) {
  const queryClient = useQueryClient()
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState('CONTRIBUTOR')

  const addMutation = useMutation({
    mutationFn: (data: { userId: string; role: string }) =>
      projectsApi.addMember(projectId, data.userId, data.role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
      setIsAddOpen(false)
      setUserId('')
      setRole('CONTRIBUTOR')
    },
  })

  const removeMutation = useMutation({
    mutationFn: (memberUserId: string) =>
      projectsApi.removeMember(projectId, memberUserId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!userId.trim()) return
    addMutation.mutate({ userId: userId.trim(), role })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold">Project Team ({members.length})</h3>
        <Button size="sm" onClick={() => setIsAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1.5" />
          Add Member
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {members.map((member) => {
          const isPM = member.userId === projectManagerId
          return (
            <div
              key={member.id}
              className="p-3.5 rounded-xl border bg-card flex items-center justify-between"
            >
              <div className="flex items-center gap-3">
                <div className="h-9 w-9 rounded-full bg-primary/10 text-primary flex items-center justify-center font-semibold text-sm">
                  {member.name.charAt(0)}
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h4 className="text-sm font-semibold">{member.name}</h4>
                    {isPM && (
                      <span className="text-[10px] bg-primary/10 text-primary px-1.5 py-0.5 rounded font-medium flex items-center gap-1">
                        <Shield className="h-2.5 w-2.5" /> PM
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-muted-foreground">
                    {member.role} {member.email && `• ${member.email}`}
                  </div>
                </div>
              </div>

              {!isPM && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-destructive hover:bg-destructive/10 h-7 w-7 p-0"
                  onClick={() => {
                    if (confirm(`Remove ${member.name} from project?`)) {
                      removeMutation.mutate(member.userId)
                    }
                  }}
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              )}
            </div>
          )
        })}
      </div>

      {/* Add Member Dialog */}
      {isAddOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="max-w-md w-full p-6 shadow-xl bg-card">
            <div className="flex items-center justify-between pb-3 border-b mb-4">
              <h3 className="font-semibold text-base">Add Team Member</h3>
              <Button variant="ghost" size="sm" className="h-7 w-7 p-0" onClick={() => setIsAddOpen(false)}>
                <X className="h-4 w-4" />
              </Button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div>
                <label className="text-xs font-medium block mb-1">User ID / UUID *</label>
                <Input
                  placeholder="Enter user UUID"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="text-xs font-medium block mb-1">Role *</label>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  className="w-full h-9 text-xs bg-background border rounded px-2"
                >
                  <option value="CONTRIBUTOR">CONTRIBUTOR</option>
                  <option value="PROJECT_MANAGER">PROJECT_MANAGER</option>
                  <option value="LEAD_ENGINEER">LEAD_ENGINEER</option>
                  <option value="QUALITY_AUDITOR">QUALITY_AUDITOR</option>
                  <option value="OBSERVER">OBSERVER</option>
                </select>
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t">
                <Button type="button" variant="outline" onClick={() => setIsAddOpen(false)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={addMutation.isPending}>
                  {addMutation.isPending ? 'Saving...' : 'Add Member'}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
    </div>
  )
}
