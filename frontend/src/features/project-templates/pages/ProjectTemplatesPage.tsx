import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { StatusBadge } from '@/components/feedback/StatusBadge'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  days,
  deleteTemplate,
  setTemplateActive,
  templateKeys,
  templatesQuery,
  type TemplateSummary,
} from '@/features/project-templates/api'

/** The project template of each service (`/admin/project-templates`). */
export function ProjectTemplatesPage() {
  const queryClient = useQueryClient()
  const canConfigure = useHasPermission('PROJECT_CONFIGURE')
  const templates = useQuery(templatesQuery)

  const refresh = () => queryClient.invalidateQueries({ queryKey: templateKeys.all })
  const toggle = useMutation({
    mutationFn: (template: TemplateSummary) => setTemplateActive(template.id, !template.active),
    onSuccess: refresh,
  })
  const remove = useMutation({ mutationFn: (id: string) => deleteTemplate(id), onSuccess: refresh })

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Project templates"
        description="The stages a project goes through, defined per service. A new project of a service can be planned from its template."
        action={
          canConfigure ? (
            <Button asChild>
              <Link to="/admin/project-templates/new">
                <Plus aria-hidden="true" />
                New template
              </Link>
            </Button>
          ) : null
        }
      />
      {toggle.error || remove.error ? (
        <ErrorState title="The template could not be changed" error={toggle.error ?? remove.error} />
      ) : null}
      <Card>
        <CardContent>
          {templates.isPending ? (
            <Skeleton className="h-24 w-full" aria-busy="true" aria-label="Loading templates" />
          ) : templates.isError ? (
            <ErrorState title="Could not load the templates" error={templates.error} onRetry={() => void templates.refetch()} />
          ) : templates.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No service has a project template yet.{canConfigure ? ' Create one to define how its projects run.' : ''}
            </p>
          ) : (
            <Table aria-label="Project templates">
              <TableHeader>
                <TableRow>
                  <TableHead>Template</TableHead>
                  <TableHead>Service</TableHead>
                  <TableHead>Stages</TableHead>
                  <TableHead>Estimated duration</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {templates.data.map((template) => (
                  <TableRow key={template.id}>
                    <TableCell>
                      <Link
                        to={`/admin/project-templates/${template.id}`}
                        className="font-medium underline-offset-2 hover:underline"
                      >
                        {template.name}
                      </Link>
                    </TableCell>
                    <TableCell>
                      {template.service.name}
                      {template.service.verticalName ? (
                        <span className="text-muted-foreground"> · {template.service.verticalName}</span>
                      ) : null}
                    </TableCell>
                    <TableCell>{template.stageCount}</TableCell>
                    <TableCell>{days(template.totalDays)}</TableCell>
                    <TableCell>
                      <StatusBadge active={template.active} />
                    </TableCell>
                    <TableCell>
                      {canConfigure ? (
                        <div className="flex justify-end gap-2">
                          <Button asChild variant="outline" size="sm">
                            <Link to={`/admin/project-templates/${template.id}/edit`} aria-label={`Edit ${template.name}`}>
                              Edit
                            </Link>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={toggle.isPending}
                            aria-label={`${template.active ? 'Switch off' : 'Switch on'} ${template.name}`}
                            onClick={() => toggle.mutate(template)}
                          >
                            {template.active ? 'Switch off' : 'Switch on'}
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={remove.isPending}
                            aria-label={`Remove ${template.name}`}
                            onClick={() => remove.mutate(template.id)}
                          >
                            Remove
                          </Button>
                        </div>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
