import { AlertTriangleIcon, EllipsisVerticalIcon, FlameIcon, HistoryIcon, KeyRoundIcon, PencilIcon, RefreshCwIcon, UnplugIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ColorPicker } from '@/components/ColorPicker'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate, formatRelative, todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useDisconnect, useGoogleStart, useLabelAccount, useSyncAccount, useSyncHistory } from '../api'
import type { IntegrationAccount } from '../types'

type Props = {
  account: IntegrationAccount
  /** Abre o diálogo para trocar o token (GitLab/GitHub) */
  onReconnect: (account: IntegrationAccount) => void
}

/** Uma conta ligada: situação, última sincronização, onde é usada e as ações. */
export function AccountRow({ account, onReconnect }: Props) {
  const sync = useSyncAccount()
  const disconnect = useDisconnect()
  const googleStart = useGoogleStart()
  const [editing, setEditing] = useState(false)
  const [history, setHistory] = useState(false)
  const [removing, setRemoving] = useState(false)
  const broken = account.status === 'ERROR'
  const expiresSoon = account.tokenExpiresAt && daysUntil(account.tokenExpiresAt) <= 14

  async function syncNow() {
    try {
      const fresh = await sync.mutateAsync(account.id)
      if (fresh.lastError) toast.error(fresh.lastError)
      else toast.success('Sincronizado.')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível sincronizar.')
    }
  }

  async function reconnect() {
    if (account.provider !== 'GOOGLE') return onReconnect(account)
    try {
      window.location.assign((await googleStart.mutateAsync()).url)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível abrir o Google.')
    }
  }

  async function remove() {
    try {
      await disconnect.mutateAsync(account.id)
      toast.success('Conta desligada.')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível desligar.')
    }
  }

  return (
    <li className="flex items-start gap-3 py-3">
      <span className="mt-1.5 size-2.5 shrink-0 rounded-full" style={{ background: account.color }} aria-hidden />
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <p className="truncate text-sm font-medium">{account.nickname}</p>
          {broken ? <Badge variant="destructive">Precisa reconectar</Badge>
            : <Badge variant="secondary">Ativa</Badge>}
          {account.provider !== 'DUOLINGO' && account.provider !== 'GOOGLE' && !account.canWrite && (
            <Badge variant="outline" title="Sem permissão de escrita: as raias não mudam sozinhas">Só leitura</Badge>
          )}
        </div>
        <p className="truncate text-xs text-muted-foreground">
          {account.login}{account.baseUrl && account.baseUrl !== 'https://gitlab.com' ? ` · ${account.baseUrl.replace('https://', '')}` : ''}
        </p>
        {account.duolingo && (
          <p className="flex items-center gap-1.5 text-xs">
            <FlameIcon className={cn('size-3.5', account.duolingo.doneToday ? 'text-orange-500' : 'text-muted-foreground')} aria-hidden />
            Ofensiva de {account.duolingo.streak} {account.duolingo.streak === 1 ? 'dia' : 'dias'}
            <span className="text-muted-foreground">· {account.duolingo.totalXp.toLocaleString('pt-BR')} XP</span>
          </p>
        )}
        <p className="text-xs text-muted-foreground">
          {account.lastSyncAt ? `Sincronizada ${formatRelative(account.lastSyncAt)}` : 'Ainda não sincronizou'}
          {account.projects.length > 0 && ` · Usada em ${account.projects.join(', ')}`}
        </p>
        {account.lastError && (
          <p className="flex items-start gap-1.5 text-xs text-destructive">
            <AlertTriangleIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />{account.lastError}
          </p>
        )}
        {expiresSoon && account.tokenExpiresAt && (
          <p className="flex items-start gap-1.5 text-xs text-amber-600 dark:text-amber-400">
            <AlertTriangleIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            Token vence em {formatDate(account.tokenExpiresAt)}. Gere outro e use "Trocar token".
          </p>
        )}
      </div>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" aria-label={`Ações de ${account.nickname}`}><EllipsisVerticalIcon /></Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem disabled={sync.isPending} onSelect={() => void syncNow()}><RefreshCwIcon /> Sincronizar agora</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => setEditing(true)}><PencilIcon /> Apelido e cor</DropdownMenuItem>
          {account.provider !== 'DUOLINGO' && (
            <DropdownMenuItem onSelect={() => void reconnect()}>
              <KeyRoundIcon /> {account.provider === 'GOOGLE' ? 'Reconectar' : 'Trocar token'}
            </DropdownMenuItem>
          )}
          <DropdownMenuItem onSelect={() => setHistory(true)}><HistoryIcon /> Histórico</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem variant="destructive" onSelect={() => setRemoving(true)}><UnplugIcon /> Desligar</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <LabelDialog account={account} open={editing} onOpenChange={setEditing} />
      <HistoryDialog account={history ? account : null} onOpenChange={setHistory} />
      <ConfirmDelete open={removing} onOpenChange={setRemoving} confirmLabel="Desligar"
        title={`Desligar ${account.nickname}?`}
        description={account.projects.length > 0
          ? `O que veio desta conta sai do LifePedia, inclusive os repositórios ligados em ${account.projects.join(', ')}. As tarefas criadas continuam.`
          : 'O que veio desta conta sai do LifePedia. As tarefas criadas continuam.'}
        onConfirm={() => void remove()} />
    </li>
  )
}

function LabelDialog({ account, open, onOpenChange }: { account: IntegrationAccount; open: boolean; onOpenChange: (open: boolean) => void }) {
  const save = useLabelAccount()
  const [nickname, setNickname] = useState(account.nickname)
  const [color, setColor] = useState(account.color)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!nickname.trim()) return
    try {
      await save.mutateAsync({ id: account.id, nickname: nickname.trim(), color })
      onOpenChange(false)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível salvar.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Apelido e cor</DialogTitle>
          <DialogDescription>A cor marca os eventos e repositórios desta conta.</DialogDescription>
        </DialogHeader>
        <DialogBody>
          <form id={`label-${account.id}`} onSubmit={submit} className="space-y-4">
            <Field>
              <FieldLabel htmlFor={`nickname-${account.id}`}>Apelido</FieldLabel>
              <Input id={`nickname-${account.id}`} value={nickname} maxLength={40} className="text-base md:text-sm"
                onChange={(e) => setNickname(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel>Cor</FieldLabel>
              <ColorPicker value={color} onChange={setColor} />
            </Field>
          </form>
        </DialogBody>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
          <Button type="submit" form={`label-${account.id}`} disabled={save.isPending || !nickname.trim()}>Salvar</Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}

function HistoryDialog({ account, onOpenChange }: { account: IntegrationAccount | null; onOpenChange: (open: boolean) => void }) {
  const { data } = useSyncHistory(account?.id ?? null)
  return (
    <Dialog open={account !== null} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Histórico de {account?.nickname}</DialogTitle>
          <DialogDescription>As 20 últimas sincronizações.</DialogDescription>
        </DialogHeader>
        <DialogBody>
          {!data ? <Skeleton className="h-32" /> : data.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhuma sincronização ainda.</p>
          ) : (
            <ul className="divide-y">
              {data.map((entry) => (
                <li key={entry.at} className="space-y-0.5 py-2 text-sm">
                  <p className="flex items-center gap-2">
                    <span className={cn('size-2 rounded-full', entry.outcome === 'SUCCESS' ? 'bg-emerald-500' : 'bg-destructive')} aria-hidden />
                    {entry.outcome === 'SUCCESS' ? `${entry.items} ${entry.items === 1 ? 'item' : 'itens'}` : 'Falhou'}
                    <span className="text-xs text-muted-foreground">
                      {formatRelative(entry.at)} · {entry.manual ? 'na hora' : 'automática'} · {(entry.durationMs / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s
                    </span>
                  </p>
                  {entry.message && <p className="pl-4 text-xs text-destructive">{entry.message}</p>}
                </li>
              ))}
            </ul>
          )}
        </DialogBody>
      </DialogScrollContent>
    </Dialog>
  )
}

function daysUntil(date: string) {
  return Math.round((Date.parse(`${date}T00:00:00`) - Date.parse(`${todayIso()}T00:00:00`)) / 86_400_000)
}
