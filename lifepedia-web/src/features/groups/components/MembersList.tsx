import { EllipsisVerticalIcon, ShieldIcon, ShieldOffIcon, UserMinusIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { UserAvatar } from '@/components/UserAvatar'
import { useAuth } from '@/features/auth/auth-context'
import { ApiError } from '@/lib/api'
import { useRemoveMember, useSetMemberRole } from '../api'
import type { GroupDetail, GroupMember } from '../types'

const joined = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' })

/** Membros do grupo; admins promovem, rebaixam e removem. */
export function MembersList({ group }: { group: GroupDetail }) {
  const { user } = useAuth()
  const setRole = useSetMemberRole()
  const remove = useRemoveMember()
  const [removing, setRemoving] = useState<GroupMember | null>(null)
  const isAdmin = group.role === 'ADMIN'

  async function run(action: Promise<unknown>, success: string) {
    try {
      await action
      toast.success(success)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível concluir.')
    }
  }

  return (
    <>
      <ul className="divide-y">
        {group.members.map((m) => {
          const me = m.user.id === user?.id
          return (
            <li key={m.user.id} className="flex items-center gap-3 py-3">
              <UserAvatar user={m.user} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{m.user.name}{me && <span className="text-muted-foreground"> (você)</span>}</p>
                <p className="truncate text-xs text-muted-foreground">@{m.user.username} · desde {joined.format(new Date(m.joinedAt))}</p>
              </div>
              {m.role === 'ADMIN' && <Badge variant="secondary" className="gap-1"><ShieldIcon className="size-3" /> Admin</Badge>}
              {isAdmin && !me && (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="icon-sm" aria-label={`Opções de ${m.user.name}`}><EllipsisVerticalIcon /></Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    {m.role === 'ADMIN' ? (
                      <DropdownMenuItem onClick={() => run(setRole.mutateAsync({ groupId: group.id, userId: m.user.id, role: 'MEMBER' }), 'Agora é membro.')}>
                        <ShieldOffIcon /> Tirar de admin
                      </DropdownMenuItem>
                    ) : (
                      <DropdownMenuItem onClick={() => run(setRole.mutateAsync({ groupId: group.id, userId: m.user.id, role: 'ADMIN' }), 'Agora é admin.')}>
                        <ShieldIcon /> Tornar admin
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuItem variant="destructive" onClick={() => setRemoving(m)}>
                      <UserMinusIcon /> Remover do grupo
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              )}
            </li>
          )
        })}
      </ul>
      <AlertDialog open={!!removing} onOpenChange={(open) => !open && setRemoving(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remover {removing?.user.name}?</AlertDialogTitle>
            <AlertDialogDescription>Os itens que essa pessoa levou saem do grupo (os dados pessoais dela não mudam).</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancelar</AlertDialogCancel>
            <AlertDialogAction variant="destructive"
              onClick={() => removing && run(remove.mutateAsync({ groupId: group.id, userId: removing.user.id }), 'Membro removido.')}>
              Remover
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
