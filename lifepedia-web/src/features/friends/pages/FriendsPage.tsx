import { CheckIcon, UserPlusIcon, XIcon } from 'lucide-react'
import { type ReactNode, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PageHeader } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsTrigger } from '@/components/ui/tabs'
import { UserAvatar } from '@/components/UserAvatar'
import { ApiError } from '@/lib/api'
import { formatInstantDate, formatRelative } from '@/lib/format'
import { useAcceptFriendRequest, useDismissFriendRequest, useFriendRequests, useFriends, useMyAchievements } from '../api'
import { AchievementGrid } from '../components/AchievementGrid'
import { AddFriendDialog } from '../components/AddFriendDialog'
import type { FriendRequestItem } from '../types'

const TABS = ['amigos', 'pedidos', 'conquistas'] as const
type Tab = (typeof TABS)[number]

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

function FriendList({ onAdd }: { onAdd: () => void }) {
  const { data, isLoading } = useFriends()
  if (isLoading) {
    return <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-16" />)}</div>
  }
  if (!data?.length) {
    return (
      <div className="flex flex-col items-center gap-3 py-12 text-center">
        <p className="max-w-sm text-sm text-muted-foreground">
          Adicione amigos para acompanhar as conquistas uns dos outros e manter a motivação em dia.
        </p>
        <Button onClick={onAdd}><UserPlusIcon /> Adicionar amigo</Button>
      </div>
    )
  }
  return (
    <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {data.map((f) => (
        <li key={f.user.id}>
          <Link to={`/amigos/${f.user.id}`}
            className="flex items-center gap-3 rounded-lg border bg-card p-3 transition-colors hover:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none motion-reduce:transition-none">
            <UserAvatar user={f.user} size="md" />
            <span className="min-w-0 flex-1">
              <span className="block truncate font-medium">{f.user.name}</span>
              <span className="block truncate text-xs text-muted-foreground">@{f.user.username} · desde {formatInstantDate(f.since)}</span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

function RequestRow({ request, children }: { request: FriendRequestItem; children: ReactNode }) {
  return (
    <li className="flex flex-col gap-3 rounded-lg border bg-card p-3 sm:flex-row sm:items-center">
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <UserAvatar user={request.user} size="md" />
        <span className="min-w-0">
          <span className="block truncate text-sm font-medium">{request.user.name}</span>
          <span className="block truncate text-xs text-muted-foreground">@{request.user.username} · {formatRelative(request.createdAt)}</span>
        </span>
      </div>
      <div className="flex gap-2 sm:shrink-0">{children}</div>
    </li>
  )
}

function Requests() {
  const { data, isLoading } = useFriendRequests()
  const accept = useAcceptFriendRequest()
  const dismiss = useDismissFriendRequest()
  const busy = accept.isPending || dismiss.isPending

  if (isLoading) return <Skeleton className="h-20" />
  if (!data?.received.length && !data?.sent.length) {
    return <p className="py-12 text-center text-sm text-muted-foreground">Nenhum pedido pendente.</p>
  }
  return (
    <div className="space-y-6">
      {!!data.received.length && (
        <section className="space-y-2">
          <h2 className="text-sm font-medium">Recebidos</h2>
          <ul className="space-y-3">
            {data.received.map((r) => (
              <RequestRow key={r.id} request={r}>
                <Button variant="outline" size="sm" className="flex-1" disabled={busy}
                  onClick={() => dismiss.mutateAsync(r.id).then(() => toast.success('Pedido recusado.'))
                    .catch((e) => toast.error(errorMessage(e, 'Não foi possível recusar.')))}>
                  <XIcon /> Recusar
                </Button>
                <Button size="sm" className="flex-1" disabled={busy}
                  onClick={() => accept.mutateAsync(r.id).then(() => toast.success(`Agora você e ${r.user.name} são amigos.`))
                    .catch((e) => toast.error(errorMessage(e, 'Não foi possível aceitar.')))}>
                  <CheckIcon /> Aceitar
                </Button>
              </RequestRow>
            ))}
          </ul>
        </section>
      )}
      {!!data.sent.length && (
        <section className="space-y-2">
          <h2 className="text-sm font-medium">Enviados</h2>
          <ul className="space-y-3">
            {data.sent.map((r) => (
              <RequestRow key={r.id} request={r}>
                <Button variant="ghost" size="sm" className="flex-1" disabled={busy}
                  onClick={() => dismiss.mutateAsync(r.id).then(() => toast.success('Pedido cancelado.'))
                    .catch((e) => toast.error(errorMessage(e, 'Não foi possível cancelar.')))}>
                  <XIcon /> Cancelar
                </Button>
              </RequestRow>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

function MyAchievements() {
  const { data, isLoading, isError } = useMyAchievements()
  if (isError) return <p className="text-sm text-destructive">Não foi possível carregar as conquistas.</p>
  if (isLoading || !data) return <Skeleton className="h-40" />
  const unlocked = data.filter((a) => a.level > 0).length
  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        {unlocked === 0
          ? 'Conclua tarefas, mantenha hábitos e chame amigos para desbloquear as primeiras conquistas.'
          : `${unlocked} de ${data.length} conquistas desbloqueadas. Seus amigos veem o seu progresso no seu perfil.`}
      </p>
      <AchievementGrid achievements={data} />
    </div>
  )
}

/** Amigos, pedidos de amizade e as próprias conquistas. */
export function FriendsPage() {
  const [params, setParams] = useSearchParams()
  const aba = params.get('aba')
  const tab: Tab = TABS.includes(aba as Tab) ? (aba as Tab) : 'amigos'
  const [adding, setAdding] = useState(false)
  const { data: requests } = useFriendRequests()
  const received = requests?.received.length ?? 0

  return (
    <div className="space-y-6">
      <PageHeader
        title="Amigos"
        description="Acompanhe as conquistas dos seus amigos e motivem-se juntos."
        actions={[{ label: 'Adicionar amigo', icon: UserPlusIcon, primary: true, onSelect: () => setAdding(true) }]}
      />
      <Card>
        <CardContent>
          <Tabs value={tab} onValueChange={(v) => setParams(v === 'amigos' ? {} : { aba: v }, { replace: true })}>
            <ScrollTabsList value={tab}>
              <TabsTrigger value="amigos">Meus amigos</TabsTrigger>
              <TabsTrigger value="pedidos">
                Pedidos
                {received > 0 && <span className="ml-1 rounded-full bg-destructive px-1.5 text-[10px] font-semibold text-white">{received}</span>}
              </TabsTrigger>
              <TabsTrigger value="conquistas">Conquistas</TabsTrigger>
            </ScrollTabsList>
            <TabsContent value="amigos" className="pt-4"><FriendList onAdd={() => setAdding(true)} /></TabsContent>
            <TabsContent value="pedidos" className="pt-4"><Requests /></TabsContent>
            <TabsContent value="conquistas" className="pt-4"><MyAchievements /></TabsContent>
          </Tabs>
        </CardContent>
      </Card>
      <AddFriendDialog open={adding} onOpenChange={setAdding} />
    </div>
  )
}
