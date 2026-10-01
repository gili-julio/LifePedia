import { ArrowDownIcon, ArrowUpDownIcon, ArrowUpIcon } from 'lucide-react'
import type { ComponentProps } from 'react'
import { TableHead } from '@/components/ui/table'
import { cn } from '@/lib/utils'
import type { ListState, SortDirection } from './list-state'

type Props = ComponentProps<typeof TableHead> & {
  field: string
  list: ListState
  /** Direção ao clicar pela primeira vez */
  firstDirection?: SortDirection
  align?: 'left' | 'right'
}

/** Cabeçalho que ordena pela coluna (na API). */
export function SortableHead({ field, list, firstDirection = 'asc', align = 'left', className, children, ...props }: Props) {
  const active = list.sort.field === field
  const Icon = !active ? ArrowUpDownIcon : list.sort.direction === 'asc' ? ArrowUpIcon : ArrowDownIcon
  return (
    <TableHead
      aria-sort={active ? (list.sort.direction === 'asc' ? 'ascending' : 'descending') : 'none'}
      className={cn(align === 'right' && 'text-right', className)}
      {...props}
    >
      <button
        type="button"
        onClick={() => list.toggleSort(field, firstDirection)}
        className={cn(
          '-mx-1 inline-flex items-center gap-1 rounded px-1 hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none',
          align === 'right' && 'flex-row-reverse',
          active && 'text-foreground',
        )}
      >
        {children}
        <Icon className={cn('size-3.5', !active && 'opacity-40')} />
      </button>
    </TableHead>
  )
}
