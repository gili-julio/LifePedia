import { CheckIcon, CopyIcon, Link2Icon, Link2OffIcon, Loader2Icon, RefreshCwIcon, SendIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { UserAvatar } from '@/components/UserAvatar'
import { ApiError } from '@/lib/api'
import { formatRelative } from '@/lib/format'
import {
  inviteUrl,
  useCancelInvite,
  useInviteLink,
  useInviteUsers,
  useRenewInviteLink,
  useRevokeInviteLink,
  useSentInvites,
} from '../api'
import type { UserSummary } from '../types'
import { UserPicker } from './UserPicker'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupId: string
  groupName: string
}

const expiry = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

/** Convites do grupo (admins): usuários direto, link e pendentes. */
export function InviteDialog({ open, onOpenChange, groupId, groupName }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Convidar para {groupName}</DialogTitle>
          <DialogDescription>A pessoa recebe uma notificação e aceita ou recusa em Grupos → Solicitações.</DialogDescription>
        </DialogHeader>
        <InviteBody groupId={groupId} />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Fechar</Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}

/** Conteúdo; montado só com o dialog aberto (a seleção recomeça a cada abertura). */
function InviteBody({ groupId }: { groupId: string }) {
  const [selected, setSelected] = useState<UserSummary[]>([])
  const [copied, setCopied] = useState(false)
  const invite = useInviteUsers()
  const link = useInviteLink(groupId, true)
  const renew = useRenewInviteLink()
  const revoke = useRevokeInviteLink()
  const pending = useSentInvites(groupId, true)
  const cancel = useCancelInvite()

  async function send() {
    try {
      const result = await invite.mutateAsync({ groupId, userIds: selected.map((u) => u.id) })
      toast.success(result.invited === 1 ? 'Convite enviado.' : `${result.invited} convites enviados.`)
      setSelected([])
    } catch (error) {
      toast.error(errorMessage(error, 'Não foi possível enviar os convites.'))
    }
  }

  async function copy(token: string) {
    try {
      await navigator.clipboard.writeText(inviteUrl(token))
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error('Não consegui copiar. Selecione o link e copie manualmente.')
    }
  }

  return (
    <DialogBody className="space-y-5">
      <section className="space-y-2">
        <h3 className="text-sm font-medium">Pessoas</h3>
        <UserPicker groupId={groupId} selected={selected} onChange={setSelected} />
        <Button size="sm" onClick={send} disabled={!selected.length || invite.isPending}>
          {invite.isPending ? <Loader2Icon className="animate-spin" /> : <SendIcon />}
          Enviar convite{selected.length > 1 ? `s (${selected.length})` : ''}
        </Button>
      </section>

      <Separator />

      <section className="space-y-2">
        <h3 className="text-sm font-medium">Link de convite</h3>
        <p className="text-xs text-muted-foreground">Quem tiver o link e estiver logado entra direto. Vale por 7 dias.</p>
        {link.isLoading ? (
          <Loader2Icon className="size-4 animate-spin text-muted-foreground" />
        ) : link.data ? (
          <div className="space-y-2">
            <div className="flex gap-2">
              <Input readOnly value={inviteUrl(link.data.token)} onFocus={(e) => e.currentTarget.select()}
                aria-label="Link de convite" className="font-mono text-xs" />
              <Button variant="outline" size="icon" aria-label="Copiar link" onClick={() => copy(link.data!.token)}>
                {copied ? <CheckIcon /> : <CopyIcon />}
              </Button>
            </div>
            <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <span>Válido até {expiry.format(new Date(link.data.expiresAt))}</span>
              <Button variant="ghost" size="sm" disabled={renew.isPending}
                onClick={() => renew.mutateAsync(groupId).catch((e) => toast.error(errorMessage(e, 'Falhou.')))}>
                <RefreshCwIcon /> Gerar novo
              </Button>
              <Button variant="ghost" size="sm" disabled={revoke.isPending}
                onClick={() => revoke.mutateAsync(groupId).then(() => toast.success('Link desativado.'))
                  .catch((e) => toast.error(errorMessage(e, 'Falhou.')))}>
                <Link2OffIcon /> Desativar
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="outline" size="sm" disabled={renew.isPending}
            onClick={() => renew.mutateAsync(groupId).catch((e) => toast.error(errorMessage(e, 'Falhou.')))}>
            {renew.isPending ? <Loader2Icon className="animate-spin" /> : <Link2Icon />} Gerar link
          </Button>
        )}
      </section>

      {!!pending.data?.length && (
        <>
          <Separator />
          <section className="space-y-2">
            <h3 className="text-sm font-medium">Aguardando resposta ({pending.data.length})</h3>
            <ul className="divide-y rounded-lg border">
              {pending.data.map((i) => (
                <li key={i.id} className="flex items-center gap-3 px-3 py-2">
                  <UserAvatar user={i.user} size="sm" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{i.user.name}</span>
                    <span className="block truncate text-xs text-muted-foreground">
                      @{i.user.username} · {formatRelative(i.createdAt)}
                    </span>
                  </span>
                  <Button variant="ghost" size="sm" disabled={cancel.isPending}
                    onClick={() => cancel.mutateAsync({ groupId, inviteId: i.id })
                      .then(() => toast.success('Convite cancelado.'))
                      .catch((e) => toast.error(errorMessage(e, 'Falhou.')))}>
                    <XIcon /> Cancelar
                  </Button>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </DialogBody>
  )
}
