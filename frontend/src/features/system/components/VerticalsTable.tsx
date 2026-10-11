import { Cctv, DraftingCompass, Headset, Megaphone, Sofa, Sun, type LucideIcon } from 'lucide-react'
import { DataTable, dataTableColumns } from '@/components/data-table/DataTable'
import { Badge } from '@/components/ui/badge'
import type { BusinessVertical, BusinessVerticalCode } from '@/features/system/api'

const VERTICAL_ICONS: Record<BusinessVerticalCode, LucideIcon> = {
  CCTV_SECURITY: Cctv,
  DIGITAL_MARKETING: Megaphone,
  INTERIOR_DESIGN: Sofa,
  ARCHITECTURE_TECH: DraftingCompass,
  SOLAR: Sun,
  IT_SUPPORT: Headset,
}

const column = dataTableColumns<BusinessVertical>()

const columns = column.columns([
  column.accessor('displayOrder', {
    header: '#',
    cell: ({ getValue }) => <span className="text-muted-foreground tabular-nums">{getValue()}</span>,
  }),
  column.accessor('name', {
    header: 'Vertical',
    cell: ({ row, getValue }) => {
      const Icon = VERTICAL_ICONS[row.original.code]
      return (
        <span className="flex items-center gap-2.5 font-medium">
          <Icon className="size-4 text-muted-foreground" aria-hidden="true" />
          {getValue()}
        </span>
      )
    },
  }),
  column.accessor('code', {
    header: 'Code',
    cell: ({ getValue }) => (
      <Badge variant="secondary" className="font-mono">
        {getValue()}
      </Badge>
    ),
  }),
])

export function VerticalsTable({ verticals }: { verticals: BusinessVertical[] }) {
  return <DataTable columns={columns} data={verticals} caption="Business verticals" />
}
