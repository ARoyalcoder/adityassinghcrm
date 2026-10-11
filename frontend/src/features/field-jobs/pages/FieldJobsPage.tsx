import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { FilterSelect, ListToolbar, Pagination, SearchInput } from '@/components/list/ListControls'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useHasPermission } from '@/features/auth/auth-context'
import { JOB_STATUSES, jobsQuery, techniciansQuery, type JobFilters } from '@/features/field-jobs/api'
import { JobCard } from '@/features/field-jobs/components/JobParts'
import { useListParams } from '@/lib/use-list-params'

/**
 * Every field job of the organization (`/field-jobs`), for those who schedule them. Shown as
 * cards rather than a table, so it reads the same on a phone as on a desk.
 */
export function FieldJobsPage() {
  const params = useListParams()
  const canManage = useHasPermission('FIELD_JOB_MANAGE')
  const filters: JobFilters = {
    search: params.get('search'),
    status: params.get('status'),
    technicianId: params.get('technicianId'),
    page: params.page,
  }
  const jobs = useQuery(jobsQuery(filters))
  const technicians = useQuery({ ...techniciansQuery, enabled: canManage })

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Field jobs"
        description="Jobs and site visits at customers' sites, and who is doing them."
        action={
          canManage ? (
            <Button asChild>
              <Link to="/field-jobs/new">
                <Plus aria-hidden="true" />
                New job
              </Link>
            </Button>
          ) : null
        }
      />
      <ListToolbar>
        <SearchInput label="Search jobs" value={params.get('search')} onChange={(value) => params.set('search', value)} />
        <FilterSelect
          label="Status"
          allLabel="Any status"
          value={params.get('status')}
          onChange={(value) => params.set('status', value)}
          options={[...JOB_STATUSES]}
        />
        {canManage ? (
          <FilterSelect
            label="Technician"
            allLabel="Anyone"
            value={params.get('technicianId')}
            onChange={(value) => params.set('technicianId', value)}
            options={(technicians.data ?? []).map((person) => ({ value: person.id, label: person.name }))}
          />
        ) : null}
      </ListToolbar>

      {jobs.isPending ? (
        <Skeleton className="h-40 w-full rounded-2xl" aria-busy="true" aria-label="Loading jobs" />
      ) : jobs.isError ? (
        <ErrorState title="Could not load the jobs" error={jobs.error} onRetry={() => void jobs.refetch()} />
      ) : jobs.data.items.length === 0 ? (
        <p className="rounded-2xl bg-card p-6 text-center text-sm text-muted-foreground shadow-soft">No jobs match.</p>
      ) : (
        <>
          <ul className="grid gap-3 md:grid-cols-2 xl:grid-cols-3" aria-label="Jobs">
            {jobs.data.items.map((job) => (
              <li key={job.id} className="grid gap-1">
                <JobCard job={job} />
                <span className="px-1 text-xs text-muted-foreground">
                  {job.technicianName ? `Assigned to ${job.technicianName}` : 'Not assigned yet'}
                </span>
              </li>
            ))}
          </ul>
          <Pagination
            page={jobs.data.page}
            totalPages={jobs.data.totalPages}
            totalItems={jobs.data.totalItems}
            noun="jobs"
            singular="job"
            onPageChange={params.setPage}
          />
        </>
      )}
    </div>
  )
}
