import { Checkbox } from '@/components/ui/checkbox'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import type { Transaction } from '../types'
import { Money } from './Money'

type Props = {
  items: Transaction[] | undefined
  loading: boolean
  excluded: Set<string>
  onToggle: (id: string, checked: boolean) => void
}

/** Ocasionais com o mesmo nome na vigência; os marcados são excluídos ao salvar o fixo. */
export function ReplacedOccasionals({ items, loading, excluded, onToggle }: Props) {
  const selected = items?.filter((t) => !excluded.has(t.id)).length ?? 0
  return (
    <div className="space-y-2">
      <div>
        <p className="text-sm font-medium">Remover dos ocasionais</p>
        <p className="text-xs text-muted-foreground">
          Mesmo nome e tipo dentro da vigência. Os marcados serão excluídos para não contar duas vezes.
        </p>
      </div>
      {loading ? (
        <Skeleton className="h-16" />
      ) : !items?.length ? (
        <p className="rounded-lg border border-dashed px-3 py-2 text-sm text-muted-foreground">
          Nenhum ocasional com esse nome no período.
        </p>
      ) : (
        <>
          <ul className="max-h-48 divide-y overflow-y-auto rounded-lg border text-sm">
            {items.map((t) => (
              <li key={t.id}>
                <label className="flex cursor-pointer items-center gap-3 px-3 py-1.5">
                  <Checkbox checked={!excluded.has(t.id)} onCheckedChange={(v) => onToggle(t.id, v === true)} />
                  <span className="min-w-0 flex-1 truncate">{t.description}</span>
                  <span className="hidden text-muted-foreground tabular-nums sm:inline">{formatDate(t.date)}</span>
                  <Money value={t.amount} type={t.type} />
                </label>
              </li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">{selected} de {items.length} marcado(s) para excluir.</p>
        </>
      )}
    </div>
  )
}
