import { CopyIcon, RefreshCwIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { ColorDot } from '../components/CategoryBadge'
import type { CategoryRef } from './types'

/** Selo do modo: sincronizado (segue o pessoal) ou cópia. */
export function ShareBadge({ synced }: { synced: boolean }) {
  return synced ? (
    <Badge variant="outline" className="gap-1 font-normal" title="Acompanha o lançamento pessoal">
      <RefreshCwIcon className="size-3" /> Sincronizado
    </Badge>
  ) : (
    <Badge variant="outline" className="gap-1 font-normal" title="Cópia independente">
      <CopyIcon className="size-3" /> Cópia
    </Badge>
  )
}

export function CategoryLabel({ category }: { category: CategoryRef }) {
  return (
    <span className="inline-flex min-w-0 items-center gap-1.5">
      <ColorDot color={category.color} />
      <span className="truncate">{category.name}</span>
    </span>
  )
}
