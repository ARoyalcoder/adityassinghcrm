import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Plus, FolderKanban } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { projectsApi } from '@/features/projects/api'
import { ProjectStatusBadge, PriorityBadge } from '@/features/projects/components/ProjectStatusBadge'
import { CreateProjectModal } from '@/features/projects/components/CreateProjectModal'
import type { CustomerTabProps } from './tabs'

export function CustomerProjectsTab({ customer }: CustomerTabProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const { data: projects, isLoading } = useQuery(
    projectsApi.listQuery({ customerId: customer.id, size: 50 }),
  )

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FolderKanban className="h-5 w-5 text-muted-foreground" />
          <h3 className="text-base font-semibold">Projects ({projects?.items.length ?? 0})</h3>
        </div>
        <Button size="sm" onClick={() => setIsCreateOpen(true)}>
          <Plus className="h-4 w-4 mr-1.5" />
          New Project
        </Button>
      </div>

      {isLoading ? (
        <div className="p-8 text-center text-muted-foreground text-xs">Loading customer projects...</div>
      ) : projects?.items.length === 0 ? (
        <div className="text-center py-10 border border-dashed rounded-lg bg-muted/20 text-muted-foreground text-xs">
          No projects found for this customer. Create a new project or convert an accepted quotation order.
        </div>
      ) : (
        <div className="border rounded-xl bg-card overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead className="bg-muted/50 border-b font-medium text-muted-foreground">
                <tr>
                  <th className="p-3">Project #</th>
                  <th className="p-3">Title</th>
                  <th className="p-3">Status</th>
                  <th className="p-3">Priority</th>
                  <th className="p-3">Manager</th>
                  <th className="p-3">Progress</th>
                  <th className="p-3">Budget</th>
                  <th className="p-3">Due Date</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {projects?.items.map((p) => (
                  <tr key={p.id} className="hover:bg-muted/30 transition-colors">
                    <td className="p-3 font-mono font-medium">
                      <Link to={`/projects/${p.id}`} className="text-primary hover:underline">
                        {p.projectNumber}
                      </Link>
                    </td>
                    <td className="p-3">
                      <Link to={`/projects/${p.id}`} className="font-semibold hover:text-primary">
                        {p.title}
                      </Link>
                    </td>
                    <td className="p-3">
                      <ProjectStatusBadge status={p.status} />
                    </td>
                    <td className="p-3">
                      <PriorityBadge priority={p.priority} />
                    </td>
                    <td className="p-3 text-muted-foreground">{p.projectManagerName}</td>
                    <td className="p-3">
                      <div className="flex items-center gap-2">
                        <div className="w-14 bg-muted rounded-full h-1.5 overflow-hidden">
                          <div
                            className="bg-primary h-full rounded-full"
                            style={{ width: `${Math.min(100, Math.max(0, p.progressPercentage))}%` }}
                          />
                        </div>
                        <span className="text-[11px] font-medium">{p.progressPercentage}%</span>
                      </div>
                    </td>
                    <td className="p-3 font-medium">₹{p.budget.toLocaleString('en-IN')}</td>
                    <td className="p-3 text-muted-foreground">
                      {new Date(p.expectedCompletionDate).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <CreateProjectModal
        open={isCreateOpen}
        onOpenChange={setIsCreateOpen}
        initialCustomerId={customer.id}
        initialCustomerName={customer.displayName}
      />
    </div>
  )
}
