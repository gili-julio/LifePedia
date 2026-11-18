import { ShoppingBagIcon, TargetIcon, UsersIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Highlight } from '@/components/Highlight'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import type { WatchSummary } from '../types'
import { ChangeBadge } from './ChangeBadge'

/** Item na lista: foto, menor preço e loja, variação em 30 dias e alvo. */
export function WatchCard({ watch: w, query = '' }: { watch: WatchSummary; query?: string }) {
  const { formatCurrency } = useMoney()
  return (
    <Link to={`/precos/${w.id}`}
      className={cn('flex gap-3 rounded-xl border bg-card p-3 transition-colors hover:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none',
        !w.active && 'opacity-70')}>
      <span className="flex size-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-muted">
        {w.imageUrl ? <img src={w.imageUrl} alt="" className="size-full object-contain" />
          : <ShoppingBagIcon className="size-6 text-muted-foreground" aria-hidden />}
      </span>
      <span className="min-w-0 flex-1 space-y-1">
        <span className="line-clamp-2 text-sm font-medium leading-snug"><Highlight text={w.name} query={query} /></span>
        {w.bestPrice !== null ? (
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-semibold tabular-nums">{formatCurrency(w.bestPrice)}</span>
            {w.bestStore && <span className="text-xs text-muted-foreground">em {w.bestStore}</span>}
            <ChangeBadge value={w.change30} period="em 30 dias" />
          </span>
        ) : <span className="block text-xs text-muted-foreground">{w.lastError ?? 'Buscando preços...'}</span>}
        <span className="flex flex-wrap items-center gap-x-3 text-xs text-muted-foreground">
          {w.targetPrice !== null && (
            <span className={cn('inline-flex items-center gap-1', w.atTarget && 'font-medium text-income')}>
              <TargetIcon className="size-3" aria-hidden /> {w.atTarget ? 'No alvo' : 'Alvo'} {formatCurrency(w.targetPrice)}
            </span>
          )}
          {w.groupName && <span className="inline-flex items-center gap-1"><UsersIcon className="size-3" aria-hidden /> {w.groupName}</span>}
          {!w.active && <span>Parado</span>}
        </span>
      </span>
    </Link>
  )
}
