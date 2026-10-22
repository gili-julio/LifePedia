import { UserRoundIcon } from 'lucide-react'
import { PhotoAvatar, type PhotoAvatarSize } from '@/components/PhotoAvatar'

type Props = {
  /** Nulo = conta excluída */
  user: { name: string; imageUrl?: string | null } | null
  size?: PhotoAvatarSize
  className?: string
}

/** Foto de perfil; sem foto, as iniciais. */
export function UserAvatar({ user, size, className }: Props) {
  return <PhotoAvatar icon={UserRoundIcon} name={user?.name ?? ''} imageUrl={user?.imageUrl} size={size} className={className} />
}
