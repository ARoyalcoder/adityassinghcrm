import { Badge } from '@/components/ui/badge'

/** Active / Inactive, said in words as well as colour. */
export function StatusBadge({ active }: { active: boolean }) {
  return active ? <Badge variant="outline">Active</Badge> : <Badge variant="secondary">Inactive</Badge>
}
