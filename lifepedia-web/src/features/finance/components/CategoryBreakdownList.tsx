import { ChevronRightIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { type YearMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { useCategoryItems } from '../api'
import type { CategoryBreakdown, CategoryItem } from '../types'
import { ColorDot } from './CategoryBadge'

type Props = {
  items: CategoryBreakdown[]
  month: YearMonth
  /** Total do tipo no mês, da API */
  total: number
  /** Conteúdo ao expandir (padrão: itens pessoais da categoria) */
  renderItems?: (categoryId: string) => ReactNode
}

/** Itens pessoais da categoria no mês, agrupados pelo nome. */
function CategoryItems({ month, categoryId }: { month: YearMonth; categoryId: string }) {
  const { data, isLoading } = useCategoryItems(month, categoryId, true)
  return <CategoryItemList items={data} loading={isLoading} />
}

/** Lista de itens de uma categoria (fixo = badge; ocasionais repetidos = N×). */
export function CategoryItemList({ items: data, loading }: { items?: CategoryItem[]; loading: boolean }) {
  const { formatCurrency } = useMoney()
  if (loading) return <Skeleton className="h-16" />
  if (!data?.length) return <p className="text-xs text-muted-foreground">Nada nesta categoria.</p>
  return (
    <ul className="divide-y rounded-lg border text-sm">
      {data.map((item) => (
        <li key={`${item.fixed}-${item.description}`} className="flex items-center gap-2 px-3 py-1.5">
          <span className="min-w-0 flex-1 truncate">{item.description}</span>
          {item.fixed ? (
            <Badge variant="outline">Fixo</Badge>
          ) : item.count > 1 && (
            <span className="text-xs text-muted-foreground tabular-nums">{item.count}×</span>
          )}
          <span className="font-medium tabular-nums">{formatCurrency(item.total)}</span>
        </li>
      ))}
    </ul>
  )
}

const percent = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 1 })

/** Composição do mês por categoria: fatias no total e fixo x ocasional por categoria. */
export function CategoryBreakdownList({ items, month, total: grandTotal, renderItems }: Props) {
  const { formatCurrency } = useMoney()
  const [expanded, setExpanded] = useState<string | null>(null)
  if (!items.length) {
    return <p className="py-6 text-center text-sm text-muted-foreground">Nada lançado neste mês.</p>
  }

  const max = Math.max(...items.map((i) => i.total))

  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <div className="flex h-3 w-full gap-0.5" role="img" aria-label="Distribuição por categoria">
          {items.map((item) => (
            <span
              key={item.categoryId}
              className="h-full rounded-[3px] first:rounded-l-full last:rounded-r-full"
              style={{ flexGrow: item.total, background: item.color }}
              title={`${item.name}: ${formatCurrency(item.total)} (${percent.format(item.total / grandTotal)})`}
            />
          ))}
        </div>
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5"><span className="h-2 w-4 rounded-[2px] bg-foreground/70" />Fixo</span>
          <span className="flex items-center gap-1.5"><span className="h-2 w-4 rounded-[2px] bg-foreground/25" />Ocasional</span>
        </div>
      </div>

      <ul className="space-y-3">
        {items.map((item) => (
          <li key={item.categoryId} className="space-y-2">
            <button
              type="button"
              aria-expanded={expanded === item.categoryId}
              onClick={() => setExpanded((current) => (current === item.categoryId ? null : item.categoryId))}
              className="w-full space-y-1.5 rounded-md text-left focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
              title={`Fixo ${formatCurrency(item.fixed)} · Ocasional ${formatCurrency(item.occasional)}`}
            >
              <span className="flex items-baseline justify-between gap-2 text-sm">
                <span className="flex items-center gap-2">
                  <ChevronRightIcon className={cn('size-3.5 text-muted-foreground transition-transform motion-reduce:transition-none',
                    expanded === item.categoryId && 'rotate-90')} />
                  <ColorDot color={item.color} />
                  {item.name}
                </span>
                <span className="flex items-baseline gap-2">
                  <span className="text-xs text-muted-foreground tabular-nums">{percent.format(item.total / grandTotal)}</span>
                  <span className="font-medium tabular-nums">{formatCurrency(item.total)}</span>
                </span>
              </span>
              <span className="flex h-2 gap-0.5" style={{ width: `${(item.total / max) * 100}%` }}>
                {item.fixed > 0 && (
                  <span className="h-full rounded-[3px]" style={{ flexGrow: item.fixed, background: item.color }} />
                )}
                {item.occasional > 0 && (
                  <span
                    className="h-full rounded-[3px] opacity-40"
                    style={{ flexGrow: item.occasional, background: item.color }}
                  />
                )}
              </span>
            </button>
            {expanded === item.categoryId && (renderItems
              ? renderItems(item.categoryId)
              : <CategoryItems month={month} categoryId={item.categoryId} />)}
          </li>
        ))}
      </ul>
    </div>
  )
}
