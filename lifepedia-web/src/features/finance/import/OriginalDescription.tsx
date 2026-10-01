import { FileTextIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { normalizeName } from '../categories'

/** Descrição como veio no documento (só leitura); some se igual à padronizada. */
export function OriginalDescription({ original, description, className }: {
  original: string | null
  description: string
  className?: string
}) {
  if (!original || normalizeName(original) === normalizeName(description)) return null
  return (
    <p className={cn('flex min-w-0 items-center gap-1 text-xs text-muted-foreground', className)} title={original}>
      <FileTextIcon className="size-3 shrink-0" aria-hidden />
      <span className="sr-only">No documento:</span>
      <span className="truncate">{original}</span>
    </p>
  )
}
