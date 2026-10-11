import { useMemo } from 'react'
import {
  BarChart3,
  CheckCircle2,
  Clock,
  DollarSign,
  Percent,
  TrendingUp,
  XCircle,
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { PipelineAnalytics } from '../api'

interface PipelineAnalyticsSummaryProps {
  analytics: PipelineAnalytics
}

export function PipelineAnalyticsSummary({ analytics }: PipelineAnalyticsSummaryProps) {
  const formattedPipelineValue = useMemo(() => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(analytics.pipelineValue)
  }, [analytics.pipelineValue])

  const formattedWeightedValue = useMemo(() => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(analytics.weightedPipelineValue)
  }, [analytics.weightedPipelineValue])

  return (
    <div className="space-y-4">
      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Total Deals
            </CardTitle>
            <BarChart3 className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{analytics.totalOpportunities}</div>
            <p className="mt-1 text-xs text-muted-foreground">
              {analytics.openOpportunities} open deals
            </p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Pipeline Value
            </CardTitle>
            <DollarSign className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{formattedPipelineValue}</div>
            <p className="mt-1 text-xs text-muted-foreground">Open deal value</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Weighted Pipeline
            </CardTitle>
            <TrendingUp className="h-4 w-4 text-indigo-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{formattedWeightedValue}</div>
            <p className="mt-1 text-xs text-muted-foreground">Probability-adjusted</p>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Win Rate
            </CardTitle>
            <Percent className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {analytics.winRatePercentage != null ? `${analytics.winRatePercentage.toFixed(1)}%` : '0%'}
            </div>
            <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
              <span className="flex items-center text-emerald-600">
                <CheckCircle2 className="mr-0.5 h-3 w-3" />
                {analytics.wonOpportunities} won
              </span>
              <span>•</span>
              <span className="flex items-center text-rose-600">
                <XCircle className="mr-0.5 h-3 w-3" />
                {analytics.lostOpportunities} lost
              </span>
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Avg Velocity
            </CardTitle>
            <Clock className="h-4 w-4 text-slate-500" />
          </CardHeader>
          <CardContent>
            {analytics.averageTimePerStage.length > 0 ? (
              <div>
                <div className="text-2xl font-bold">
                  {(
                    analytics.averageTimePerStage.reduce((acc, s) => acc + s.averageDays, 0) /
                    analytics.averageTimePerStage.length
                  ).toFixed(1)}{' '}
                  <span className="text-sm font-normal text-muted-foreground">days/stage</span>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  Across {analytics.averageTimePerStage.length} active stages
                </p>
              </div>
            ) : (
              <div>
                <div className="text-2xl font-bold">—</div>
                <p className="mt-1 text-xs text-muted-foreground">Not enough stage data</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Stage Duration Breakdown */}
      {analytics.averageTimePerStage.length > 0 && (
        <Card className="shadow-sm">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold">Average Time Per Pipeline Stage</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-8">
              {analytics.averageTimePerStage.map((stage) => (
                <div
                  key={stage.stageId}
                  className="rounded-lg border bg-muted/30 p-2.5 text-center"
                >
                  <div className="truncate text-xs font-medium text-muted-foreground" title={stage.stageName}>
                    {stage.stageName}
                  </div>
                  <div className="mt-1 text-lg font-bold text-foreground">
                    {stage.averageDays >= 1
                      ? `${stage.averageDays.toFixed(1)}d`
                      : `${stage.averageHours.toFixed(1)}h`}
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
