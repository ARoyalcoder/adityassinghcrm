import { useState } from 'react'
import {
  AlertTriangle,
  CheckCircle2,
  AlertCircle,
  TrendingUp,
  DollarSign,
  Calendar,
  CheckSquare,
  Users,
  ShieldAlert,
  FileCheck2,
  Edit,
  ArrowUpRight,
  TrendingDown,
} from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { ProjectHealth } from '../api'
import { EditProfitabilityModal } from './EditProfitabilityModal'

interface ProjectHealthTabProps {
  projectId: string
  health?: ProjectHealth
}

function getScoreColor(score: number): { text: string; bg: string; border: string; bar: string } {
  if (score >= 80) {
    return {
      text: 'text-emerald-600 dark:text-emerald-400',
      bg: 'bg-emerald-50 dark:bg-emerald-950/40',
      border: 'border-emerald-200 dark:border-emerald-800',
      bar: 'bg-emerald-500',
    }
  }
  if (score >= 60) {
    return {
      text: 'text-amber-600 dark:text-amber-400',
      bg: 'bg-amber-50 dark:bg-amber-950/40',
      border: 'border-amber-200 dark:border-amber-800',
      bar: 'bg-amber-500',
    }
  }
  return {
    text: 'text-rose-600 dark:text-rose-400',
    bg: 'bg-rose-50 dark:bg-rose-950/40',
    border: 'border-rose-200 dark:border-rose-800',
    bar: 'bg-rose-500',
  }
}

function getSeverityBadge(severity: string) {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL':
      return <Badge variant="destructive" className="text-[10px] uppercase font-semibold">Critical</Badge>
    case 'WARNING':
      return <Badge className="bg-amber-500 text-white hover:bg-amber-600 text-[10px] uppercase font-semibold">Warning</Badge>
    case 'INFO':
    default:
      return <Badge variant="secondary" className="text-[10px] uppercase font-semibold">Info</Badge>
  }
}

export function ProjectHealthTab({ projectId, health }: ProjectHealthTabProps) {
  const [isEditOpen, setIsEditOpen] = useState(false)

  if (!health) {
    return (
      <div className="p-8 text-center text-muted-foreground text-sm">
        Health metrics are calculating...
      </div>
    )
  }

  const {
    overallScore,
    scheduleHealth,
    budgetHealth,
    taskHealth,
    clientApprovalHealth,
    teamCapacityHealth,
    profitability,
    riskAlerts,
  } = health

  const overallColors = getScoreColor(overallScore)

  const components = [
    {
      title: 'Schedule Health',
      score: scheduleHealth,
      icon: Calendar,
      description: 'Timeline adherence against expected completion date',
    },
    {
      title: 'Budget Health',
      score: budgetHealth,
      icon: DollarSign,
      description: 'Actual cost vs allocated budget consumption',
    },
    {
      title: 'Task Health',
      score: taskHealth,
      icon: CheckSquare,
      description: 'Completion rate, blocked items, and overdue tasks',
    },
    {
      title: 'Client Approval Health',
      score: clientApprovalHealth,
      icon: FileCheck2,
      description: 'Accepted vs pending deliverables sign-off status',
    },
    {
      title: 'Team Capacity Health',
      score: teamCapacityHealth,
      icon: Users,
      description: 'Active task distribution per assigned team member',
    },
  ]

  return (
    <div className="space-y-6">
      {/* Overview & Score Card Header */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Overall Score Meter */}
        <Card className={`p-5 rounded-xl border ${overallColors.border} ${overallColors.bg} flex flex-col justify-between`}>
          <div>
            <div className="flex items-center justify-between">
              <span className="text-xs uppercase tracking-wider font-semibold text-muted-foreground">
                Overall Project Health
              </span>
              <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${overallColors.text} bg-background/80`}>
                {overallScore >= 80 ? 'Healthy' : overallScore >= 60 ? 'Moderate Risk' : 'High Risk'}
              </span>
            </div>
            <div className="mt-4 flex items-baseline gap-3">
              <span className={`text-5xl font-black tracking-tight ${overallColors.text}`}>
                {overallScore}%
              </span>
              <span className="text-xs text-muted-foreground font-medium">Composite Score</span>
            </div>
          </div>
          <div className="mt-4">
            <div className="w-full bg-background/60 rounded-full h-2.5 overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${overallColors.bar}`}
                style={{ width: `${overallScore}%` }}
              />
            </div>
            <p className="text-[11px] text-muted-foreground mt-2">
              Weighted: Schedule (25%), Budget (25%), Tasks (20%), Approval (15%), Capacity (15%)
            </p>
          </div>
        </Card>

        {/* Profitability Summary Card */}
        <Card className="p-5 rounded-xl border col-span-1 md:col-span-2 flex flex-col justify-between">
          <div className="flex items-center justify-between pb-3 border-b">
            <div>
              <h3 className="font-semibold text-sm flex items-center gap-1.5">
                <TrendingUp className="h-4 w-4 text-emerald-500" />
                Project Profitability Overview
              </h3>
              <p className="text-xs text-muted-foreground">Live revenue, actual costs, and gross margins</p>
            </div>
            <Button
              variant="outline"
              size="sm"
              className="h-7 text-xs gap-1"
              onClick={() => setIsEditOpen(true)}
            >
              <Edit className="h-3.5 w-3.5" />
              Update Financials
            </Button>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-3">
            <div className="space-y-0.5">
              <span className="text-[11px] text-muted-foreground">Contract Revenue</span>
              <p className="text-base font-bold text-foreground">
                ₹{profitability.revenue.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="space-y-0.5">
              <span className="text-[11px] text-muted-foreground">Actual Cost</span>
              <p className="text-base font-bold text-foreground">
                ₹{profitability.actualCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="space-y-0.5">
              <span className="text-[11px] text-muted-foreground">Gross Profit</span>
              <p className={`text-base font-bold flex items-center gap-1 ${profitability.grossProfit >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                {profitability.grossProfit >= 0 ? (
                  <ArrowUpRight className="h-4 w-4" />
                ) : (
                  <TrendingDown className="h-4 w-4" />
                )}
                ₹{profitability.grossProfit.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="space-y-0.5">
              <span className="text-[11px] text-muted-foreground">Gross Margin</span>
              <p className={`text-base font-bold ${profitability.grossMargin >= 15 ? 'text-emerald-600' : 'text-amber-600'}`}>
                {profitability.grossMargin.toFixed(2)}%
              </p>
            </div>
          </div>

          <div className="mt-3 pt-3 border-t flex flex-wrap items-center justify-between text-xs text-muted-foreground">
            <span>
              Target Expected Cost: <strong className="text-foreground">₹{profitability.expectedCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong>
            </span>
            <span>
              Projected Final Cost: <strong className="text-foreground">₹{profitability.projectedFinalCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong>
            </span>
          </div>
        </Card>
      </div>

      {/* 5 Component Health Intelligence Indicators */}
      <div className="space-y-3">
        <h3 className="font-semibold text-sm">Health Component Breakdown</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
          {components.map((comp) => {
            const colors = getScoreColor(comp.score)
            const Icon = comp.icon
            return (
              <Card key={comp.title} className="p-4 rounded-xl border shadow-sm flex flex-col justify-between">
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="p-1.5 rounded-lg bg-muted text-foreground">
                      <Icon className="h-4 w-4 text-primary" />
                    </div>
                    <span className={`text-lg font-bold ${colors.text}`}>{comp.score}%</span>
                  </div>
                  <div>
                    <h4 className="font-semibold text-xs text-foreground">{comp.title}</h4>
                    <p className="text-[11px] text-muted-foreground line-clamp-2 mt-0.5">
                      {comp.description}
                    </p>
                  </div>
                </div>

                <div className="mt-3">
                  <div className="w-full bg-muted rounded-full h-1.5 overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-300 ${colors.bar}`}
                      style={{ width: `${comp.score}%` }}
                    />
                  </div>
                </div>
              </Card>
            )
          })}
        </div>
      </div>

      {/* Risk Alerts Panel */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-sm flex items-center gap-1.5">
            <ShieldAlert className="h-4 w-4 text-amber-500" />
            Active Risk Alerts
            <span className="text-xs font-normal text-muted-foreground">
              ({riskAlerts.length} detected)
            </span>
          </h3>
        </div>

        {riskAlerts.length === 0 ? (
          <Card className="p-6 text-center border-dashed">
            <div className="flex flex-col items-center justify-center space-y-2">
              <CheckCircle2 className="h-8 w-8 text-emerald-500" />
              <p className="text-sm font-semibold text-foreground">No Active Risk Alerts</p>
              <p className="text-xs text-muted-foreground">
                All schedule, budget, margin, task execution, and approval thresholds are currently within expected ranges.
              </p>
            </div>
          </Card>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {riskAlerts.map((alert, idx) => (
              <Card
                key={`${alert.code}-${idx}`}
                className={`p-4 rounded-xl border ${
                  alert.severity === 'CRITICAL'
                    ? 'border-rose-300 bg-rose-50/50 dark:bg-rose-950/20'
                    : alert.severity === 'WARNING'
                    ? 'border-amber-300 bg-amber-50/50 dark:bg-amber-950/20'
                    : 'border-blue-300 bg-blue-50/50 dark:bg-blue-950/20'
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className="pt-0.5">
                    {alert.severity === 'CRITICAL' ? (
                      <AlertCircle className="h-5 w-5 text-rose-600" />
                    ) : alert.severity === 'WARNING' ? (
                      <AlertTriangle className="h-5 w-5 text-amber-600" />
                    ) : (
                      <CheckCircle2 className="h-5 w-5 text-blue-600" />
                    )}
                  </div>
                  <div className="flex-1 space-y-1">
                    <div className="flex items-center justify-between">
                      <h4 className="text-xs font-bold text-foreground">{alert.title}</h4>
                      {getSeverityBadge(alert.severity)}
                    </div>
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      {alert.description}
                    </p>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* Itemized Cost Breakdown Table */}
      <Card className="rounded-xl border">
        <CardContent className="p-5 space-y-4">
          <div className="flex items-center justify-between pb-3 border-b">
            <div>
              <h3 className="font-semibold text-sm">Itemized Project Cost Breakdown</h3>
              <p className="text-xs text-muted-foreground">Comprehensive direct expenditure tracking</p>
            </div>
            <Button
              variant="outline"
              size="sm"
              className="h-7 text-xs"
              onClick={() => setIsEditOpen(true)}
            >
              Edit Breakdown
            </Button>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
            <div className="p-3 bg-muted/40 rounded-lg space-y-1">
              <span className="text-[11px] text-muted-foreground font-medium">Material Cost</span>
              <p className="text-sm font-bold">
                ₹{profitability.materialCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="p-3 bg-muted/40 rounded-lg space-y-1">
              <span className="text-[11px] text-muted-foreground font-medium">Labour Cost</span>
              <p className="text-sm font-bold">
                ₹{profitability.labourCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="p-3 bg-muted/40 rounded-lg space-y-1">
              <span className="text-[11px] text-muted-foreground font-medium">Vendor / Subcontract</span>
              <p className="text-sm font-bold">
                ₹{profitability.vendorCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="p-3 bg-muted/40 rounded-lg space-y-1">
              <span className="text-[11px] text-muted-foreground font-medium">Travel & Logistics</span>
              <p className="text-sm font-bold">
                ₹{profitability.travelCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="p-3 bg-muted/40 rounded-lg space-y-1">
              <span className="text-[11px] text-muted-foreground font-medium">Other Direct Expenses</span>
              <p className="text-sm font-bold">
                ₹{profitability.otherExpenses.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
          </div>

          <div className="p-4 bg-muted/20 border rounded-lg flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-xs">
            <div>
              <span className="text-muted-foreground">Total Actual Cost Incurred: </span>
              <strong className="text-foreground text-sm">
                ₹{profitability.actualCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </strong>
            </div>
            <div>
              <span className="text-muted-foreground">Target Expected Cost: </span>
              <strong className="text-foreground text-sm">
                ₹{profitability.expectedCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </strong>
            </div>
            <div>
              <span className="text-muted-foreground">Projected Final Cost: </span>
              <strong className="text-foreground text-sm">
                ₹{profitability.projectedFinalCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </strong>
            </div>
          </div>
        </CardContent>
      </Card>

      <EditProfitabilityModal
        open={isEditOpen}
        onOpenChange={setIsEditOpen}
        projectId={projectId}
        profitability={profitability}
      />
    </div>
  )
}
