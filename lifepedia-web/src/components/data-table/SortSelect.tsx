import { ArrowDownIcon, ArrowUpIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { ListState, SortDirection } from './list-state'

export type SortOption = { field: string; label: string; firstDirection?: SortDirection }

/** Ordenação no mobile (sem cabeçalho de tabela). */
export function SortSelect({ list, options }: { list: ListState; options: SortOption[] }) {
  const asc = list.sort.direction === 'asc'
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm text-muted-foreground">Ordenar</span>
      <Select
        value={list.sort.field}
        onValueChange={(field) => {
          const option = options.find((o) => o.field === field)
          list.setSort({ field, direction: option?.firstDirection ?? 'asc' })
        }}
      >
        <SelectTrigger size="sm" className="min-w-0 flex-1"><SelectValue /></SelectTrigger>
        <SelectContent>
          {options.map((o) => <SelectItem key={o.field} value={o.field}>{o.label}</SelectItem>)}
        </SelectContent>
      </Select>
      <Button type="button" variant="outline" size="icon-sm" aria-label={asc ? 'Crescente' : 'Decrescente'}
        onClick={() => list.setSort({ field: list.sort.field, direction: asc ? 'desc' : 'asc' })}>
        {asc ? <ArrowUpIcon /> : <ArrowDownIcon />}
      </Button>
    </div>
  )
}
