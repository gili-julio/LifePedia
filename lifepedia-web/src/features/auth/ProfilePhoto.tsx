import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { ImagePicker } from '@/components/image/ImagePicker'
import { UserAvatar } from '@/components/UserAvatar'
import { api, ApiError } from '@/lib/api'
import { useAuth } from './auth-context'
import type { User } from './types'

/** Foto de perfil: envia assim que o recorte é confirmado; remover apaga na hora. */
export function ProfilePhoto() {
  const { user, setUser } = useAuth()
  const queryClient = useQueryClient()
  const save = useMutation({
    mutationFn: (image: Blob | null) => {
      if (!image) return api<User>('/api/users/me/image', { method: 'DELETE' })
      const form = new FormData()
      form.append('file', image, 'perfil.jpg')
      return api<User>('/api/users/me/image', { method: 'PUT', body: form })
    },
    onSuccess: (updated, image) => {
      setUser(updated)
      // A foto aparece em membros, convites e autorias de vários módulos.
      void queryClient.invalidateQueries()
      toast.success(image ? 'Foto atualizada.' : 'Foto removida.')
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar a foto.'),
  })
  if (!user) return null

  return (
    <div className="relative">
      <ImagePicker
        preview={<UserAvatar user={user} size="xl" />}
        onChange={(image) => save.mutate(image)}
        onRemove={user.imageUrl ? () => save.mutate(null) : undefined}
      />
      {save.isPending && (
        <span className="absolute top-0 left-0 flex size-24 items-center justify-center rounded-full bg-background/60" aria-label="Salvando foto">
          <Loader2Icon className="size-5 animate-spin" />
        </span>
      )}
    </div>
  )
}
