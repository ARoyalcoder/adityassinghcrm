import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import {
  Calendar,
  CheckCircle2,
  Clock,
  DollarSign,
  FileSpreadsheet,
  FileX2,
  Send,
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { QuotationDashboard } from '../api'

interface QuotationDashboardOverviewProps {
  dashboard: QuotationDashboard
}

export function QuotationDashboardOverview({ dashboard }: QuotationDashboardOverviewProps) {
  const formattedTotalValue = useMemo(() => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(dashboard.totalValue)
  }, [dashboard.totalValue])

  return (
    <div className="space-y-4">
      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Total Quotations
            </CardTitle>
            <FileSpreadsheet className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{dashboard.totalQuotations}</div>
            <p className="mt-1 text-xs text-muted-foreground">All versions</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Total Value
            </CardTitle>
            <DollarSign className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{formattedTotalValue}</div>
            <p className="mt-1 text-xs text-muted-foreground">Active pipeline sum</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Draft
            </CardTitle>
            <Clock className="h-4 w-4 text-slate-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{dashboard.draftCount}</div>
            <p className="mt-1 text-xs text-muted-foreground">In preparation</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Sent
            </CardTitle>
            <Send className="h-4 w-4 text-indigo-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{dashboard.sentCount}</div>
            <p className="mt-1 text-xs text-muted-foreground">Awaiting decision</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Accepted
            </CardTitle>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">{dashboard.acceptedCount}</div>
            <p className="mt-1 text-xs text-muted-foreground">Won contracts</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Rejected
            </CardTitle>
            <FileX2 className="h-4 w-4 text-rose-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-rose-600">{dashboard.rejectedCount}</div>
            <p className="mt-1 text-xs text-muted-foreground">Declined by client</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm border-amber-200 bg-amber-50/30 dark:bg-amber-950/10">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-amber-800 dark:text-amber-300">
              Today&apos;s Follow-ups
            </CardTitle>
            <Calendar className="h-4 w-4 text-amber-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-amber-700 dark:text-amber-400">
              {dashboard.todaysFollowUpsCount}
            </div>
            <p className="mt-1 text-xs text-amber-600/80">Scheduled today</p>
          </CardContent>
        </Card>
      </div>

      {/* Today's Follow-up List Alert Box */}
      {dashboard.todaysFollowUps.length > 0 && (
        <Card className="border-amber-200 shadow-sm">
          <CardHeader className="py-3 bg-amber-50/50 dark:bg-amber-950/20 border-b border-amber-100">
            <div className="flex items-center gap-2">
              <Calendar className="h-4 w-4 text-amber-600" />
              <CardTitle className="text-sm font-semibold text-amber-900 dark:text-amber-200">
                Action Required: Follow Up on {dashboard.todaysFollowUps.length} Quotations Today
              </CardTitle>
            </div>
          </CardHeader>
          <CardContent className="p-3">
            <div className="divide-y text-xs">
              {dashboard.todaysFollowUps.map((q) => (
                <div key={q.id} className="flex items-center justify-between py-2.5">
                  <div className="flex items-center gap-3">
                    <Link
                      to={`/quotations/${q.id}`}
                      className="font-bold text-primary hover:underline"
                    >
                      {q.quotationNumber}
                    </Link>
                    <span className="font-medium text-foreground">{q.title}</span>
                    <span className="text-muted-foreground">
                      {q.customerName || q.leadName || 'Direct'}
                    </span>
                  </div>
                  <div className="flex items-center gap-4">
                    <span className="font-semibold text-foreground">
                      {new Intl.NumberFormat('en-IN', {
                        style: 'currency',
                        currency: 'INR',
                        maximumFractionDigits: 0,
                      }).format(q.finalAmount)}
                    </span>
                    <span className="text-muted-foreground">Owner: {q.ownerName}</span>
                    <Link
                      to={`/quotations/${q.id}`}
                      className="rounded bg-primary/10 px-2 py-1 font-medium text-primary hover:bg-primary/20"
                    >
                      View →
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
