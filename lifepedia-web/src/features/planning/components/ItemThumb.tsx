import { ShoppingBagIcon, TrendingUpIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { PlanKind } from '../types'

type Props = {
  /** URL da API ou data URL da prévia */
  src: string | null
  kind: PlanKind
  className?: string
}

/** Foto do item (fundo branco, sem cortar o produto) ou ícone do tipo. */
export function ItemThumb({ src, kind, className }: Props) {
  const Icon = kind === 'INVESTMENT' ? TrendingUpIcon : ShoppingBagIcon
  return src ? (
    <img src={src} alt="" loading="lazy" className={cn('size-14 shrink-0 rounded-lg border bg-white object-contain', className)} />
  ) : (
    <div className={cn('flex size-14 shrink-0 items-center justify-center rounded-lg border bg-muted text-muted-foreground', className)}>
      <Icon className="size-6" />
    </div>
  )
}
