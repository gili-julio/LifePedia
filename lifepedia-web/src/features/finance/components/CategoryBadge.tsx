import { cn } from '@/lib/utils'
import { FALLBACK_COLOR } from '../categories'
import { useCategoryLookup } from '../use-category-lookup'

export function ColorDot({ color, className }: { color: string; className?: string }) {
  return <span aria-hidden className={cn('inline-block size-2.5 shrink-0 rounded-full', className)} style={{ background: color }} />
}

/** Nome da categoria com a bolinha de cor. */
export function CategoryBadge({ categoryId }: { categoryId: string }) {
  const { byId } = useCategoryLookup()
  const category = byId.get(categoryId)
  return (
    <span className="inline-flex min-w-0 items-center gap-2">
      <ColorDot color={category?.color ?? FALLBACK_COLOR} />
      <span className="truncate">{category?.name ?? '—'}</span>
    </span>
  )
}
