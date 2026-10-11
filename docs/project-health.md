# Project Health Intelligence & Project Profitability

## Overview
Project Health Intelligence provides deterministic, rule-based analytics to evaluate execution risk and profitability across all client projects without AI dependencies.

---

## 1. Project Health Intelligence Metrics

### Health Components & Calculation Logic

| Metric | Target | Calculation Logic |
| :--- | :--- | :--- |
| **Schedule Health (%)** | 100% | Evaluates elapsed days against total expected duration versus `progressPercentage`. Returns `100%` if completed or ahead. Imposes progressive penalties if behind or overdue past completion date. |
| **Budget Health (%)** | 100% | Compares actual cost incurred against total project budget. If cost ratio $\le 1.0$, health is $80 + 20 \times (1 - \text{costRatio})$. Overruns reduce score progressively down to minimum floor. |
| **Task Health (%)** | 100% | Evaluates completed task ratio with deductions for overdue ($40\%$ weight) and blocked ($30\%$ weight) tasks. |
| **Client Approval Health (%)** | 100% | Ratio of accepted deliverables with heavy deductions for rejected items and mild deductions for pending reviews. |
| **Team Capacity Health (%)** | 100% | Ratio of active uncompleted tasks per assigned project member. Balanced ($\le 4$ tasks/member) yields $95\%$, moderate ($\le 8$) yields $80\%$, heavy ($\le 12$) yields $65\%$, and overloaded ($> 12$) yields $45\%$. |

### Overall Project Health Score Formula
A deterministic weighted composite score:
$$\text{Overall Score} = \text{round}(0.25 \times \text{Schedule} + 0.25 \times \text{Budget} + 0.20 \times \text{Tasks} + 0.15 \times \text{Approval} + 0.15 \times \text{Capacity})$$
Bounded strictly within $[0, 100]$.

*Example:*
- Schedule Health: $74\%$
- Budget Health: $91\%$
- Task Health: $88\%$
- Client Approval Health: $62\%$
- Team Capacity Health: $93\%$
- **Overall Project Health Score:** $82\%$

---

## 2. Project Profitability Metrics

### Itemized Cost Breakdown
- **Material Cost**: Direct equipment, hardware, panels, structures, and consumables.
- **Labour Cost**: Direct engineering, technician, installation, and field contractor payroll.
- **Vendor Cost**: Third-party specialized subcontractors and fabrication fees.
- **Travel Cost**: Field logistics, lodging, vehicle transport, and site commute.
- **Other Expenses**: Permits, fees, testing apparatus, and miscellaneous direct charges.

### Derived Financial Metrics
- **Actual Cost**: $\sum (\text{Material} + \text{Labour} + \text{Vendor} + \text{Travel} + \text{Other Expenses})$
- **Expected Cost**: Pre-contract baseline target budget.
- **Gross Profit**: $\text{Revenue} - \text{Actual Cost}$
- **Gross Margin (%)**: $\frac{\text{Gross Profit}}{\text{Revenue}} \times 100$
- **Projected Final Cost**: Progress-extrapolated final cost:
  $$\text{Projected Final Cost} = \frac{\text{Actual Cost}}{\max(1, \text{Progress})} \times 100$$
  Bounded below by the expected cost floor when actual expenditure is within budget.

---

## 3. Risk Alerts Engine

The system evaluates active risk indicators:
1. `BUDGET_OVERRUN` (CRITICAL): Actual expenditure exceeds allocated budget.
2. `SCHEDULE_DELAY` (CRITICAL): Current date has exceeded target completion date while project is uncompleted.
3. `MARGIN_DECLINE` (WARNING): Gross profit margin drops below target threshold ($15\%$).
4. `TASK_BOTTLENECK` (WARNING): Blocked tasks exist or multiple overdue tasks obstruct progression.
5. `APPROVAL_PENDING` (INFO): Deliverables submitted and awaiting client sign-off.

---

## 4. API Endpoints

- `GET /api/v1/projects/{id}/health`: Retrieve composite health score, component metrics, risk alerts, and profitability breakdown.
- `PUT /api/v1/projects/{id}/profitability`: Update contract revenue and itemized cost components.
