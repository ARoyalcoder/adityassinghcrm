import { createColumnHelper, tableFeatures, useTable, type ColumnDef, type RowData } from '@tanstack/react-table'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

/**
 * The app-wide table. TanStack Table owns the row/column model; this component owns the markup.
 * Sorting, filtering and pagination are added here once (as table features) when the first list
 * screen needs them, so every table in the product behaves the same way.
 */
export const dataTableFeatures = tableFeatures({})
type Features = typeof dataTableFeatures

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- cell value types differ per column
export type DataTableColumn<TData extends RowData> = ColumnDef<Features, TData, any>

/** Typed column builder: `const col = dataTableColumns<Lead>(); col.accessor('name', {...})`. */
export function dataTableColumns<TData extends RowData>() {
  return createColumnHelper<Features, TData>()
}

type DataTableProps<TData extends RowData> = {
  /** Define at module scope (or memoise): a new array on every render rebuilds the table model. */
  columns: DataTableColumn<TData>[]
  data: TData[]
  /** Read by screen readers; describe what the rows are. */
  caption: string
  emptyMessage?: string
}

export function DataTable<TData extends RowData>({
  columns,
  data,
  caption,
  emptyMessage = 'Nothing to show yet.',
}: DataTableProps<TData>) {
  const table = useTable({ features: dataTableFeatures, columns, data })
  const rows = table.getRowModel().rows

  return (
    <Table>
      <caption className="sr-only">{caption}</caption>
      <TableHeader>
        {table.getHeaderGroups().map((group) => (
          <TableRow key={group.id} className="hover:bg-transparent">
            {group.headers.map((header) => (
              <TableHead key={header.id} scope="col">
                {header.isPlaceholder ? null : <table.FlexRender header={header} />}
              </TableHead>
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {rows.length === 0 ? (
          <TableRow className="hover:bg-transparent">
            <TableCell colSpan={columns.length} className="h-24 text-center text-muted-foreground">
              {emptyMessage}
            </TableCell>
          </TableRow>
        ) : (
          rows.map((row) => (
            <TableRow key={row.id}>
              {row.getAllCells().map((cell) => (
                <TableCell key={cell.id}>
                  <table.FlexRender cell={cell} />
                </TableCell>
              ))}
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  )
}
