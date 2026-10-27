import { ArrowLeftIcon, UserMinusIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { UserAvatar } from '@/components/UserAvatar'
import { ApiError } from '@/lib/api'
import { formatInstantDate } from '@/lib/format'
import { useFriendProfile, useRemoveFriend } from '../api'
import { AchievementGrid } from '../components/AchievementGrid'

/** Perfil de um amigo: foto, desde quando são amigos e as conquistas dele. */
export function FriendProfilePage() {
  const { friendId = '' } = useParams()
  const navigate = useNavigate()
  const { data, isLoading, error } = useFriendProfile(friendId)
  const remove = useRemoveFriend()
  const [confirming, setConfirming] = useState(false)

  if (error) {
    return (
      <div className="flex flex-col items-center gap-3 py-12 text-center">
        <p className="text-sm text-muted-foreground">
          {error instanceof ApiError && error.status === 404 ? 'Esta pessoa não está na sua lista de amigos.' : 'Não foi possível carregar o perfil.'}
        </p>
        <Button variant="outline" asChild><Link to="/amigos"><ArrowLeftIcon /> Voltar para Amigos</Link></Button>
      </div>
    )
  }
  if (isLoading || !data) return <Skeleton className="h-64 w-full" />

  async function unfriend() {
    try {
      await remove.mutateAsync(friendId)
      toast.success('Amizade desfeita.')
      navigate('/amigos', { replace: true })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível desfazer a amizade.')
    }
  }

  const unlocked = data.achievements.filter((a) => a.level > 0).length
  return (
    <div className="space-y-6">
      <PageHeader
        title={data.user.name}
        actions={[{ label: 'Desfazer amizade', icon: UserMinusIcon, secondary: true, destructive: true, onSelect: () => setConfirming(true) }]}
      />
      <div className="flex items-center gap-4">
        <UserAvatar user={data.user} size="xl" />
        <div className="min-w-0 space-y-0.5">
          <p className="truncate text-sm text-muted-foreground">@{data.user.username}</p>
          <p className="text-sm text-muted-foreground">Amigos desde {formatInstantDate(data.since)}</p>
          <p className="text-sm font-medium">{unlocked} de {data.achievements.length} conquistas desbloqueadas</p>
        </div>
      </div>
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Conquistas</h2>
        <AchievementGrid achievements={data.achievements} />
      </section>
      <ConfirmDelete open={confirming} onOpenChange={setConfirming} confirmLabel="Desfazer"
        title={`Desfazer amizade com ${data.user.name}?`}
        description="Vocês deixam de ver as conquistas um do outro. Dá para mandar um pedido de novo depois."
        onConfirm={unfriend} />
    </div>
  )
}
