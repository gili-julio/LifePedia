import { EllipsisVerticalIcon, type LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'

export type RowAction = { label: string; icon: LucideIcon; onSelect: () => void; destructive?: boolean }

/** Menu ⋮ de uma linha (some quando não há ação permitida). */
export function RowMenu({ label, actions }: { label: string; actions: RowAction[] }) {
  if (!actions.length) return null
  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon-sm" aria-label={`Ações de ${label}`} className="relative after:absolute after:-inset-2">
          <EllipsisVerticalIcon />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {actions.map((a) => (
          <DropdownMenuItem key={a.label} variant={a.destructive ? 'destructive' : 'default'} onSelect={a.onSelect}>
            <a.icon /> {a.label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
