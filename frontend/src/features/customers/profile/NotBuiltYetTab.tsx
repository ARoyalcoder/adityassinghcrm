import { Hourglass } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'

type NotBuiltYetTabProps = {
  /** The section's name, e.g. "Sales history". */
  title: string
  /** What will be shown here, e.g. "Leads, quotations and orders". */
  contents: string
}

/**
 * Shown for a profile section whose module has not been built. It says so plainly: an empty table
 * or a row of zeros would read as "this customer has none", which is not known.
 */
export function NotBuiltYetTab({ title, contents }: NotBuiltYetTabProps) {
  return (
    <Card>
      <CardContent className="flex flex-col items-start gap-2 py-6">
        <Hourglass className="size-5 text-muted-foreground" aria-hidden="true" />
        <h2 className="text-base font-semibold">{title} is not available yet</h2>
        <p className="max-w-prose text-sm text-muted-foreground">
          {contents} for this customer will appear here once that part of the system is built. Its events will
          then show on the customer timeline as well.
        </p>
      </CardContent>
    </Card>
  )
}
