import { UsersIcon } from 'lucide-react'
import { PhotoAvatar, type PhotoAvatarSize } from '@/components/PhotoAvatar'

type Props = {
  name: string
  imageUrl?: string | null
  size?: PhotoAvatarSize
  className?: string
}

/** Foto redonda do grupo; sem imagem, as iniciais (ou ícone). */
export function GroupAvatar(props: Props) {
  return <PhotoAvatar icon={UsersIcon} {...props} />
}
