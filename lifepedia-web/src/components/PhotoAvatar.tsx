import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { cn } from '@/lib/utils'

const SIZES = {
  xs: 'size-5 text-[10px]',
  sm: 'size-8 text-xs',
  md: 'size-10 text-sm',
  lg: 'size-14 text-base',
  xl: 'size-24 text-2xl',
} as const

export type PhotoAvatarSize = keyof typeof SIZES

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('')
}

type Props = {
  name: string
  imageUrl?: string | null
  size?: PhotoAvatarSize
  /** Sem foto nem iniciais */
  icon: LucideIcon
  className?: string
}

/** Foto redonda; sem imagem (ou se ela falhar), as iniciais ou o ícone. */
export function PhotoAvatar({ name, imageUrl, size = 'md', icon: Icon, className }: Props) {
  const [failed, setFailed] = useState<string | null>(null)
  const showImage = !!imageUrl && failed !== imageUrl
  return (
    <span className={cn('relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-muted font-medium text-muted-foreground ring-1 ring-border', SIZES[size], className)}>
      {showImage ? (
        <img src={imageUrl} alt="" className="size-full object-cover" onError={() => setFailed(imageUrl)} />
      ) : initials(name) ? (
        initials(name)
      ) : (
        <Icon className="size-1/2" />
      )}
    </span>
  )
}
