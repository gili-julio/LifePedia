import { BanIcon, LockOpenIcon, LogOutIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { UserAvatar } from '@/components/UserAvatar'
import { formatInstantDate, formatRelative } from '@/lib/format'
import { useAdminUser, useRevokeSessions, useSetBlocked } from '../api'
import { COUNT_LABEL, errorMessage } from '../labels'
import type { AdminUser, AdminUserDetail, UserCountKey } from '../types'
import { AdminSwitch } from './AdminSwitch'
import type { PendingAction } from './ConfirmAction'

type Props = {
  id: string | null
  onClose: () => void
  onAsk: (action: PendingAction) => void
  onAskAdmin: (user: AdminUser, admin: boolean) => void
}

/** Cadastro e contagens de uma conta (nunca o conteúdo), com bloquear e encerrar sessões. */
export function UserDetailSheet({ id, onClose, onAsk, onAskAdmin }: Props) {
  const { data, isLoading } = useAdminUser(id)
  return (
    <Sheet open={id !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full gap-0 sm:max-w-md">
        <SheetHeader className="border-b">
          <SheetTitle>Detalhes da conta</SheetTitle>
          <SheetDescription>Só dados de cadastro e quantidades. O conteúdo de cada pessoa não aparece aqui.</SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          {isLoading || !data ? (
            <div className="space-y-3">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-12" />)}</div>
          ) : <Detail detail={data} onAsk={onAsk} onAskAdmin={onAskAdmin} />}
        </div>
      </SheetContent>
    </Sheet>
  )
}

function Detail({ detail, onAsk, onAskAdmin }: { detail: AdminUserDetail } & Pick<Props, 'onAsk' | 'onAskAdmin'>) {
  const { user } = detail
  const setBlocked = useSetBlocked()
  const revoke = useRevokeSessions()
  const canBlock = !user.self && !user.fixedAdmin

  function askBlock(blocked: boolean) {
    onAsk({
      title: blocked ? `Bloquear a conta de ${user.name}?` : `Desbloquear a conta de ${user.name}?`,
      description: blocked
        ? 'A pessoa sai de todos os aparelhos na hora e não consegue entrar até ser desbloqueada. Os dados dela continuam guardados.'
        : 'A pessoa volta a conseguir entrar normalmente.',
      confirmLabel: blocked ? 'Bloquear' : 'Desbloquear',
      destructive: blocked,
      run: () => setBlocked.mutate({ id: user.id, blocked }, {
        onSuccess: () => toast.success(blocked ? 'Conta bloqueada.' : 'Conta desbloqueada.'),
        onError: (e) => toast.error(errorMessage(e)),
      }),
    })
  }

  function askRevoke() {
    onAsk({
      title: `Encerrar as sessões de ${user.name}?`,
      description: 'A pessoa sai de todos os aparelhos e precisa entrar de novo.',
      confirmLabel: 'Encerrar sessões',
      destructive: true,
      run: () => revoke.mutate(user.id, {
        onSuccess: () => toast.success('Sessões encerradas.'),
        onError: (e) => toast.error(errorMessage(e)),
      }),
    })
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <UserAvatar user={user} size="lg" />
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold">{user.name}</p>
          <p className="truncate text-sm text-muted-foreground">{user.username ? `@${user.username}` : 'Sem @usuário'}</p>
          <p className="truncate text-sm text-muted-foreground">{user.email}</p>
        </div>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {user.self && <Badge variant="outline">Você</Badge>}
        {user.admin && <Badge variant="secondary">Admin{user.fixedAdmin ? ' do servidor' : ''}</Badge>}
        {user.blocked && <Badge variant="destructive">Bloqueado desde {formatInstantDate(detail.blockedAt!)}</Badge>}
        {detail.googleLinked && <Badge variant="outline">Google vinculado</Badge>}
        {!detail.hasPassword && <Badge variant="outline">Entra só pelo Google</Badge>}
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
        <Info label="Cadastro">{formatInstantDate(user.createdAt)}</Info>
        <Info label="Último acesso">{user.lastSeenAt ? formatRelative(user.lastSeenAt) : 'Sem registro'}</Info>
        <Info label="Sessões ativas">{detail.activeSessions}</Info>
        <Info label="Aparelhos com push">{detail.pushDevices}</Info>
        <Info label="Termos aceitos">
          {detail.termsVersion ? `${detail.termsVersion}${detail.termsCurrent ? '' : ' (antiga)'}` : 'Nunca'}
        </Info>
      </dl>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">Uso</h3>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          {(Object.keys(COUNT_LABEL) as UserCountKey[]).map((key) => (
            <Info key={key} label={COUNT_LABEL[key]}>{(detail.counts[key] ?? 0).toLocaleString('pt-BR')}</Info>
          ))}
        </dl>
      </section>

      <section className="space-y-3 border-t pt-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-sm font-medium">Administrador</p>
            <p className="text-xs text-muted-foreground">Acesso a este painel</p>
          </div>
          <AdminSwitch user={user} busy={false} onChange={onAskAdmin} />
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          {user.blocked ? (
            <Button variant="outline" disabled={setBlocked.isPending} onClick={() => askBlock(false)}><LockOpenIcon /> Desbloquear</Button>
          ) : (
            <Button variant="outline" className="text-destructive" disabled={!canBlock || setBlocked.isPending}
              title={canBlock ? undefined : 'Esta conta não pode ser bloqueada.'} onClick={() => askBlock(true)}>
              <BanIcon /> Bloquear conta
            </Button>
          )}
          <Button variant="outline" disabled={user.self || detail.activeSessions === 0 || revoke.isPending} onClick={askRevoke}>
            <LogOutIcon /> Encerrar sessões
          </Button>
        </div>
      </section>
    </div>
  )
}

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="truncate font-medium tabular-nums">{children}</dd>
    </div>
  )
}
