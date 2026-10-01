import { EllipsisVerticalIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
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
}

/** Linha de lançamento no mobile, com ⋮ para as ações. */
export function EntryListItem({ type, title, details, amount, onMore, muted }: Props) {
  return (
    <li className={cn('flex items-center gap-3 py-3', muted && 'text-muted-foreground')}>
      <TypeIcon type={type} />
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium">{title}</p>
        <div className="flex min-w-0 items-center gap-1.5 truncate text-xs text-muted-foreground">{details}</div>
      </div>
      <div className="shrink-0 text-right text-sm font-medium">{amount}</div>
      <Button variant="ghost" size="icon-sm" className="-mr-2 shrink-0" aria-label={`Opções de ${title}`} onClick={onMore}>
        <EllipsisVerticalIcon />
      </Button>
    </li>
  )
}
