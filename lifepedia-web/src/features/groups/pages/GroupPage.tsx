import { ArrowLeftIcon, LogOutIcon, PackagePlusIcon, PencilIcon, Trash2Icon, UserPlusIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PageHeader, type PageAction } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsTrigger } from '@/components/ui/tabs'
import { useChatUnread } from '@/features/chat/api'
import { GroupChat } from '@/features/chat/components/GroupChat'
import { BringItemsDialog } from '@/features/finance/group/BringItemsDialog'
import { GroupOverview } from '@/features/finance/group/GroupOverview'
import { GroupRecurringTab } from '@/features/finance/group/GroupRecurringTab'
import { GroupTransactionsTab } from '@/features/finance/group/GroupTransactionsTab'
import { MonthPicker } from '@/features/finance/components/MonthPicker'
import { useMonthParam } from '@/features/finance/use-month-param'
import { PlanningTab } from '@/features/planning/components/PlanningTab'
import { AgendaBoard } from '@/features/tasks/agenda/AgendaBoard'
import { GroupTasksTab } from '@/features/tasks/components/GroupTasksTab'
import { ApiError } from '@/lib/api'
import { useDeleteGroup, useGroup, useLeaveGroup } from '../api'
import { GroupAvatar } from '../components/GroupAvatar'
import { GroupFormDialog } from '../components/GroupFormDialog'
import { InviteDialog } from '../components/InviteDialog'
import { MembersList } from '../components/MembersList'

const TABS = ['visao-geral', 'chat', 'lancamentos', 'fixos', 'planejamento', 'tarefas', 'agenda', 'membros'] as const
type Tab = (typeof TABS)[number]

/** Página do grupo: métricas, itens levados pelos membros e participantes. */
export function GroupPage() {
  const { groupId = '' } = useParams()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [month, setMonth] = useMonthParam()
  const { data: group, isLoading, error } = useGroup(groupId)
  const unread = useChatUnread().data?.[groupId] ?? 0
  const leave = useLeaveGroup()
  const remove = useDeleteGroup()
  const [dialog, setDialog] = useState<'bring' | 'invite' | 'edit' | 'leave' | 'delete' | null>(null)

  const rawTab = params.get('aba') as Tab | null
  const tab: Tab = rawTab && TABS.includes(rawTab) ? rawTab : 'visao-geral'
  const setTab = (next: string) =>
    setParams((prev) => {
      const updated = new URLSearchParams(prev)
      // Filtros de outras abas (ex.: vista das tarefas) não passam adiante.
      updated.delete('vista')
      updated.delete('tarefa')
      updated.delete('dia')
      if (next === 'visao-geral') updated.delete('aba')
      else updated.set('aba', next)
      return updated
    }, { replace: true })

  if (isLoading) return <Skeleton className="h-64" />
  if (!group) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="text-sm text-muted-foreground">
          {error instanceof ApiError && error.status === 404 ? 'Grupo não encontrado ou você não participa dele.' : 'Não foi possível carregar o grupo.'}
        </p>
        <Button variant="outline" asChild><Link to="/grupos"><ArrowLeftIcon /> Voltar para grupos</Link></Button>
      </div>
    )
  }

  const isAdmin = group.role === 'ADMIN'
  const actions: PageAction[] = [
    { label: 'Levar meus itens', icon: PackagePlusIcon, primary: true, onSelect: () => setDialog('bring') },
    ...(isAdmin ? [
      { label: 'Convidar', icon: UserPlusIcon, onSelect: () => setDialog('invite') },
      { label: 'Editar grupo', icon: PencilIcon, secondary: true, onSelect: () => setDialog('edit') },
    ] : []),
    { label: 'Sair do grupo', icon: LogOutIcon, secondary: true, onSelect: () => setDialog('leave') },
    ...(isAdmin ? [{ label: 'Excluir grupo', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDialog('delete') }] : []),
  ]
  const bring = () => setDialog('bring')

  async function confirm(action: 'leave' | 'delete') {
    try {
      await (action === 'leave' ? leave : remove).mutateAsync(group!.id)
      toast.success(action === 'leave' ? 'Você saiu do grupo.' : 'Grupo excluído.')
      navigate('/grupos', { replace: true })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível concluir.')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start gap-4">
        <GroupAvatar name={group.name} imageUrl={group.imageUrl} size="lg" className="mt-0.5" />
        <div className="min-w-0 flex-1">
          <PageHeader
            title={group.name}
            description={`${group.members.length} participante${group.members.length === 1 ? '' : 's'}`}
            toolbar={tab === 'visao-geral' || tab === 'lancamentos' ? <MonthPicker value={month} onChange={setMonth} /> : undefined}
            actions={actions}
          />
        </div>
      </div>

      <Tabs value={tab} onValueChange={setTab}>
        <ScrollTabsList value={tab}>
          <TabsTrigger value="visao-geral">Visão geral</TabsTrigger>
          <TabsTrigger value="chat">
            Chat
            {unread > 0 && tab !== 'chat' && (
              <span className="ml-1 rounded-full bg-destructive px-1.5 text-[10px] font-semibold text-white">{unread > 99 ? '99+' : unread}</span>
            )}
          </TabsTrigger>
          <TabsTrigger value="lancamentos">Lançamentos</TabsTrigger>
          <TabsTrigger value="fixos">Fixos</TabsTrigger>
          <TabsTrigger value="planejamento">Planejamento</TabsTrigger>
          <TabsTrigger value="tarefas">Tarefas</TabsTrigger>
          <TabsTrigger value="agenda">Agenda</TabsTrigger>
          <TabsTrigger value="membros">Membros ({group.members.length})</TabsTrigger>
        </ScrollTabsList>
        <TabsContent value="visao-geral" className="pt-4">
          <GroupOverview groupId={group.id} month={month} onBring={bring} />
        </TabsContent>
        <TabsContent value="chat" className="pt-4">
          {tab === 'chat' && <GroupChat groupId={group.id} />}
        </TabsContent>
        <TabsContent value="lancamentos" className="pt-4">
          <Card><CardContent><GroupTransactionsTab groupId={group.id} month={month} isAdmin={isAdmin} onBring={bring} /></CardContent></Card>
        </TabsContent>
        <TabsContent value="fixos" className="pt-4">
          <Card><CardContent><GroupRecurringTab groupId={group.id} isAdmin={isAdmin} onBring={bring} /></CardContent></Card>
        </TabsContent>
        <TabsContent value="planejamento" className="pt-4">
          <PlanningTab groupId={group.id} />
        </TabsContent>
        <TabsContent value="tarefas" className="pt-4">
          <GroupTasksTab group={group} />
        </TabsContent>
        <TabsContent value="agenda" className="pt-4">
          <AgendaBoard group={group} />
        </TabsContent>
        <TabsContent value="membros" className="pt-4">
          <Card>
            <CardContent className="space-y-3">
              {isAdmin && (
                <Button variant="outline" size="sm" onClick={() => setDialog('invite')}><UserPlusIcon /> Convidar pessoas</Button>
              )}
              <MembersList group={group} />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <BringItemsDialog open={dialog === 'bring'} onOpenChange={(open) => !open && setDialog(null)}
        groupId={group.id} groupName={group.name} month={month} />
      {isAdmin && (
        <>
          <InviteDialog open={dialog === 'invite'} onOpenChange={(open) => !open && setDialog(null)} groupId={group.id} groupName={group.name} />
          <GroupFormDialog open={dialog === 'edit'} onOpenChange={(open) => !open && setDialog(null)} group={group} />
        </>
      )}
      <AlertDialog open={dialog === 'leave' || dialog === 'delete'} onOpenChange={(open) => !open && setDialog(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{dialog === 'delete' ? `Excluir "${group.name}"?` : `Sair de "${group.name}"?`}</AlertDialogTitle>
            <AlertDialogDescription>
              {dialog === 'delete'
                ? 'O grupo e tudo o que foi levado para ele serão apagados para todos. Os dados pessoais de cada um não mudam.'
                : 'Os itens que você levou saem do grupo; seus dados pessoais não mudam. Se você for o último admin, outro membro assume.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancelar</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => confirm(dialog === 'delete' ? 'delete' : 'leave')}>
              {dialog === 'delete' ? 'Excluir grupo' : 'Sair'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
