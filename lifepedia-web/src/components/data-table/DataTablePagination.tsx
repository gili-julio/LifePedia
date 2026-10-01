import { ChevronLeftIcon, ChevronRightIcon, ChevronsLeftIcon, ChevronsRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { PAGE_SIZES, type ListState, type Page } from './list-state'

/** Rodapé da listagem: intervalo, total, itens por página e navegação. */
export function DataTablePagination({ data, list }: { data?: Page<unknown>; list: ListState }) {
  const total = data?.totalItems ?? 0
  const pages = Math.max(data?.totalPages ?? 0, 1)
  const page = Math.min(list.page, pages - 1)
  const from = total === 0 ? 0 : page * list.size + 1
  const to = Math.min((page + 1) * list.size, total)
  const go = (next: number) => list.setPage(Math.min(Math.max(next, 0), pages - 1))

  return (
    <div className="flex flex-col gap-3 border-t pt-4 text-sm sm:flex-row sm:items-center sm:justify-between">
      <p className="text-muted-foreground tabular-nums">
        {total === 0 ? 'Nenhum item' : <>{from}–{to} de <span className="font-medium text-foreground">{total}</span> {total === 1 ? 'item' : 'itens'}</>}
      </p>
      <div className="flex items-center justify-between gap-4 sm:justify-end">
        <div className="flex items-center gap-2">
          <span className="text-muted-foreground">Por página</span>
          <Select value={String(list.size)} onValueChange={(v) => list.setSize(Number(v))}>
            <SelectTrigger size="sm" className="w-18" aria-label="Itens por página"><SelectValue /></SelectTrigger>
            <SelectContent>
              {PAGE_SIZES.map((s) => <SelectItem key={s} value={String(s)}>{s}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center gap-1">
          <span className="mr-2 whitespace-nowrap tabular-nums">{page + 1} / {pages}</span>
          <Button variant="outline" size="icon-sm" className="hidden sm:inline-flex" aria-label="Primeira página"
            disabled={page === 0} onClick={() => go(0)}>
            <ChevronsLeftIcon />
          </Button>
          <Button variant="outline" size="icon-sm" aria-label="Página anterior" disabled={page === 0} onClick={() => go(page - 1)}>
            <ChevronLeftIcon />
          </Button>
          <Button variant="outline" size="icon-sm" aria-label="Próxima página" disabled={page >= pages - 1} onClick={() => go(page + 1)}>
            <ChevronRightIcon />
          </Button>
          <Button variant="outline" size="icon-sm" className="hidden sm:inline-flex" aria-label="Última página"
            disabled={page >= pages - 1} onClick={() => go(pages - 1)}>
            <ChevronsRightIcon />
          </Button>
        </div>
      </div>
    </div>
  )
}
