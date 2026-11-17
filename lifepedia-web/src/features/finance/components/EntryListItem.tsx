import { EllipsisVerticalIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Highlight } from '@/components/Highlight'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { cn } from '@/lib/utils'
import type { EntryType } from '../types'
import { TypeIcon } from './TypeIcon'

type Props = {
  type: EntryType
  title: string
  details: ReactNode
  amount: ReactNode
  onMore: () => void
  muted?: boolean
  /** Com `onSelect`, mostra a caixa de seleção */
  selected?: boolean
  onSelect?: (selected: boolean) => void
  /** Busca: destaca as palavras no título. */
  highlight?: string
}

/** Linha de lançamento no mobile, com ⋮ para as ações. */
export function EntryListItem({ type, title, details, amount, onMore, muted, selected, onSelect, highlight }: Props) {
  return (
    <li className={cn('flex items-center gap-3 py-3', muted && 'text-muted-foreground')}>
      {onSelect && <Checkbox aria-label={`Selecionar ${title}`} checked={selected} onCheckedChange={(v) => onSelect(v === true)} />}
      <TypeIcon type={type} />
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium">{highlight ? <Highlight text={title} query={highlight} /> : title}</p>
        <div className="flex min-w-0 items-center gap-1.5 truncate text-xs text-muted-foreground">{details}</div>
      </div>
      <div className="shrink-0 text-right text-sm font-medium">{amount}</div>
      <Button variant="ghost" size="icon-sm" className="-mr-2 shrink-0" aria-label={`Opções de ${title}`} onClick={onMore}>
        <EllipsisVerticalIcon />
      </Button>
    </li>
  )
}
