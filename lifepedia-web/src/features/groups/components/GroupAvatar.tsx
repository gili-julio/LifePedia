import { UsersIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

const SIZES = {
  sm: 'size-8 text-xs',
  md: 'size-10 text-sm',
  lg: 'size-14 text-base',
  xl: 'size-24 text-2xl',
} as const

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('')
}

type Props = {
  name: string
  imageUrl?: string | null
  size?: keyof typeof SIZES
  className?: string
}

/** Foto redonda do grupo; sem imagem, as iniciais (ou ícone). */
export function GroupAvatar({ name, imageUrl, size = 'md', className }: Props) {
  return (
    <span className={cn('relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-muted font-medium text-muted-foreground ring-1 ring-border', SIZES[size], className)}>
      {imageUrl ? (
        <img src={imageUrl} alt="" className="size-full object-cover" />
      ) : initials(name) ? (
        initials(name)
      ) : (
        <UsersIcon className="size-1/2" />
      )}
    </span>
  )
}
