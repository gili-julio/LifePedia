import { CheckIcon, PlusIcon, ShieldIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { formatRelative } from '@/lib/format'
import { useGroups, useReceivedInvites, useRespondInvite } from '../api'
import { GroupAvatar } from '../components/GroupAvatar'
import { GroupFormDialog } from '../components/GroupFormDialog'

function participants(count: number) {
  return `${count} participante${count === 1 ? '' : 's'}`
}

function MyGroups({ onCreate }: { onCreate: () => void }) {
  const { data, isLoading } = useGroups()
  if (isLoading) return <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-20" />)}</div>
  if (!data?.length) {
    return (
      <div className="flex flex-col items-center gap-3 py-12 text-center">
        <p className="max-w-sm text-sm text-muted-foreground">
          Crie um grupo para dividir as contas com a família, o casal ou a república. Cada um leva os itens que quiser.
        </p>
        <Button onClick={onCreate}><PlusIcon /> Criar grupo</Button>
      </div>
    )
  }
  return (
    <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {data.map((g) => (
        <li key={g.id}>
          <Link to={`/grupos/${g.id}`}
            className="flex items-center gap-3 rounded-lg border bg-card p-3 transition-colors hover:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">
            <GroupAvatar name={g.name} imageUrl={g.imageUrl} size="lg" />
            <span className="min-w-0 flex-1">
              <span className="block truncate font-medium">{g.name}</span>
              <span className="block text-xs text-muted-foreground">{participants(g.memberCount)}</span>
            </span>
            {g.role === 'ADMIN' && <Badge variant="secondary" className="gap-1"><ShieldIcon className="size-3" /> Admin</Badge>}
          </Link>
        </li>
      ))}
    </ul>
  )
}

function Requests() {
  const navigate = useNavigate()
  const { data, isLoading } = useReceivedInvites()
  const respond = useRespondInvite()

  async function answer(inviteId: string, accept: boolean, groupId: string) {
    try {
      await respond.mutateAsync({ inviteId, accept })
      if (accept) {
        toast.success('Você entrou no grupo.')
        navigate(`/grupos/${groupId}`)
      } else toast.success('Convite recusado.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível responder.')
    }
  }

  if (isLoading) return <Skeleton className="h-20" />
  if (!data?.length) return <p className="py-12 text-center text-sm text-muted-foreground">Nenhuma solicitação pendente.</p>
  return (
    <ul className="space-y-3">
      {data.map((i) => (
        <li key={i.id} className="flex flex-col gap-3 rounded-lg border bg-card p-3 sm:flex-row sm:items-center">
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <GroupAvatar name={i.group.name} imageUrl={i.group.imageUrl} size="lg" />
            <div className="min-w-0 space-y-0.5">
              <p className="text-sm">
                <span className="font-medium">{i.invitedBy.name}</span> te chamou para participar do grupo{' '}
                <span className="font-medium">{i.group.name}</span>
              </p>
              <p className="text-xs text-muted-foreground">
                {participants(i.group.memberCount)} · @{i.invitedBy.username} · {formatRelative(i.createdAt)}
              </p>
            </div>
          </div>
          <div className="flex gap-2 sm:shrink-0">
            <Button variant="outline" size="sm" className="flex-1" disabled={respond.isPending} onClick={() => answer(i.id, false, i.group.id)}>
              <XIcon /> Recusar
            </Button>
            <Button size="sm" className="flex-1" disabled={respond.isPending} onClick={() => answer(i.id, true, i.group.id)}>
              <CheckIcon /> Aceitar
            </Button>
          </div>
        </li>
      ))}
    </ul>
  )
}

/** Grupos do usuário e convites recebidos (Solicitações). */
export function GroupsPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('aba') === 'solicitacoes' ? 'requests' : 'groups'
  const [creating, setCreating] = useState(false)
  const navigate = useNavigate()
  const { data: invites } = useReceivedInvites()
  const pending = invites?.length ?? 0

  return (
    <div className="space-y-6">
      <PageHeader
        title="Grupos"
        description="Divida despesas e ganhos com outras pessoas e acompanhe as métricas do grupo."
        actions={[{ label: 'Novo grupo', icon: PlusIcon, primary: true, onSelect: () => setCreating(true) }]}
      />
      <Card>
        <CardContent>
          <Tabs value={tab} onValueChange={(v) => setParams(v === 'requests' ? { aba: 'solicitacoes' } : {}, { replace: true })}>
            <TabsList>
              <TabsTrigger value="groups">Meus grupos</TabsTrigger>
              <TabsTrigger value="requests">
                Solicitações
                {pending > 0 && <span className="ml-1 rounded-full bg-destructive px-1.5 text-[10px] font-semibold text-white">{pending}</span>}
              </TabsTrigger>
            </TabsList>
            <TabsContent value="groups" className="pt-4"><MyGroups onCreate={() => setCreating(true)} /></TabsContent>
            <TabsContent value="requests" className="pt-4"><Requests /></TabsContent>
          </Tabs>
        </CardContent>
      </Card>
      <GroupFormDialog open={creating} onOpenChange={setCreating} onSaved={(g) => navigate(`/grupos/${g.id}`)} />
    </div>
  )
}
