import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
  AlertTriangleIcon,
  ArrowDownIcon,
  ArrowUpIcon,
  CheckCircle2Icon,
  EllipsisVerticalIcon,
  GripVerticalIcon,
  type LucideIcon,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Switch } from '@/components/ui/switch'
import { UserAvatar } from '@/components/UserAvatar'
import { formatCurrency, formatMonth } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ItemResult } from '../types'
import { ItemThumb } from './ItemThumb'
import { LinkChip } from './LinkChip'

export type RowAction = { label: string; icon: LucideIcon; onSelect: () => void; destructive?: boolean }

type Props = {
  result: ItemResult
  index: number
  count: number
  /** Liga/desliga na simulação (ou no cenário que está sendo visto) */
  onToggle: (considered: boolean) => void
  onMove: (direction: -1 | 1) => void
  actions: RowAction[]
  /** Mobile: abre a folha de ações */
  onMore: () => void
  busy?: boolean
}

/** Situação do item no prazo: cabe, falta quanto e quando daria. */
function Status({ result }: { result: ItemResult }) {
  const { item, considered, shortfall, earliestMonth, monthlySaving } = result
  const earlier = earliestMonth && earliestMonth < item.targetMonth ? ` · dá já em ${formatMonth(earliestMonth, 'short')}` : ''
  const later = earliestMonth ? `cabe a partir de ${formatMonth(earliestMonth, 'short')}` : 'sem previsão de caber'

  if (!considered) {
    return (
      <p className="text-xs text-muted-foreground">
        Fora da simulação · {shortfall === 0 ? 'se entrar, cabe' : `se entrar, faltam ${formatCurrency(shortfall)} (${later})`}
      </p>
    )
  }
  if (shortfall === 0) {
    return (
      <p className="flex items-start gap-1 text-xs">
        <CheckCircle2Icon className="mt-px size-3.5 shrink-0 text-income" aria-hidden />
        <span>
          Cabe no prazo{monthlySaving ? ` · guardar ${formatCurrency(monthlySaving)}/mês` : ''}{earlier}
        </span>
      </p>
    )
  }
  return (
    <p className="flex items-start gap-1 text-xs text-destructive">
      <AlertTriangleIcon className="mt-px size-3.5 shrink-0" aria-hidden />
      <span>Faltam {formatCurrency(shortfall)} em {formatMonth(item.targetMonth, 'short')} · {later}</span>
    </p>
  )
}

/** Item do planejamento: prioridade (arrastar pela alça ou setas), foto, valores, situação e ações. */
export function PlanItemRow({ result, index, count, onToggle, onMove, actions, onMore, busy }: Props) {
  const { item, considered, installmentAmount, overdue } = result
  const kind = item.kind === 'INVESTMENT' ? 'Investimento' : 'Compra'
  const payment = item.installments > 1 && installmentAmount ? ` · ${item.installments}× de ${formatCurrency(installmentAmount)}` : ''
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } =
    useSortable({ id: item.id, disabled: busy || count < 2 })

  return (
    <li ref={setNodeRef} style={{ transform: CSS.Translate.toString(transform), transition }}
      className={cn('relative flex gap-3 bg-card py-4', isDragging && 'z-10 rounded-lg shadow-lg ring-1 ring-border',
        !considered && !isDragging && 'opacity-70')}>
      <button ref={setActivatorNodeRef} type="button" {...attributes} {...listeners} disabled={busy || count < 2}
        aria-label={`Arrastar ${item.name} para mudar a prioridade`}
        className="-my-4 -ml-2 -mr-2 flex w-6 shrink-0 cursor-grab touch-none items-center justify-center rounded-md text-muted-foreground outline-none hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 active:cursor-grabbing disabled:pointer-events-none disabled:opacity-40">
        <GripVerticalIcon className="size-4" />
      </button>
      <div className="flex w-7 shrink-0 flex-col items-center gap-0.5">
        <Button variant="ghost" size="icon-xs" aria-label={`Subir prioridade de ${item.name}`} disabled={index === 0 || busy}
          onClick={() => onMove(-1)}>
          <ArrowUpIcon />
        </Button>
        <span className="text-xs font-medium tabular-nums text-muted-foreground" title="Prioridade">{index + 1}º</span>
        <Button variant="ghost" size="icon-xs" aria-label={`Descer prioridade de ${item.name}`} disabled={index === count - 1 || busy}
          onClick={() => onMove(1)}>
          <ArrowDownIcon />
        </Button>
      </div>
      <ItemThumb src={item.imageUrl} kind={item.kind} className="max-sm:size-12" />
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-col gap-0.5 sm:flex-row sm:items-start sm:justify-between sm:gap-2">
          <p className="line-clamp-2 font-medium leading-snug">{item.name}</p>
          <p className="font-semibold tabular-nums sm:shrink-0">{formatCurrency(item.amount)}</p>
        </div>
        <p className="flex flex-wrap items-center gap-x-1 text-xs text-muted-foreground">
          <span>{kind} · até {formatMonth(item.targetMonth, 'short')}{payment} ·</span>
          <span className="inline-flex items-center gap-1">
            por <UserAvatar user={item.createdBy} size="xs" /> {item.createdBy.name.split(' ')[0]}
          </span>
          {overdue && <Badge variant="outline" className="h-4 px-1.5 text-[10px]">Prazo passou</Badge>}
        </p>
        <Status result={result} />
        {(item.url || item.referenceUrl) && (
          <div className="flex flex-wrap gap-x-3">
            {item.url && <LinkChip url={item.url} site={item.linkSite} />}
            {item.referenceSite && item.referenceUrl && (
              <LinkChip url={item.referenceUrl} site={`Preço/foto de referência: ${item.referenceSite}`} className="italic" />
            )}
          </div>
        )}
      </div>
      <div className="flex shrink-0 flex-col items-end gap-2">
        <Switch checked={considered} disabled={busy} onCheckedChange={onToggle}
          aria-label={considered ? `Tirar ${item.name} da simulação` : `Incluir ${item.name} na simulação`} />
        {actions.length > 0 && (
          <>
            <Button variant="ghost" size="icon-sm" className="-mr-2 md:hidden" aria-label={`Opções de ${item.name}`} onClick={onMore}>
              <EllipsisVerticalIcon />
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon-sm" className="-mr-2 hidden md:inline-flex" aria-label={`Opções de ${item.name}`}>
                  <EllipsisVerticalIcon />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {actions.map(({ label, icon: Icon, onSelect, destructive }) => (
                  <DropdownMenuItem key={label} onSelect={onSelect} variant={destructive ? 'destructive' : 'default'}>
                    <Icon /> {label}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </>
        )}
      </div>
    </li>
  )
}
