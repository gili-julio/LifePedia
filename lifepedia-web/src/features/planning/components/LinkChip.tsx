import { ExternalLinkIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { hostOf, isSafeLink } from '../link'

type Props = {
  url: string
  site: string | null
  className?: string
}

/** Atalho para a página do produto (outra aba, sem expor a origem). */
export function LinkChip({ url, site, className }: Props) {
  if (!isSafeLink(url)) return null
  return (
    <a href={url} target="_blank" rel="noopener noreferrer nofollow"
      className={cn('inline-flex max-w-full items-center gap-1 truncate text-xs text-muted-foreground underline-offset-2 hover:text-foreground hover:underline', className)}>
      <ExternalLinkIcon className="size-3 shrink-0" />
      <span className="truncate">{site || hostOf(url)}</span>
    </a>
  )
}
