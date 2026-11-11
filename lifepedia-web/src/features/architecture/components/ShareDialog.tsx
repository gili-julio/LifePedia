import { CopyIcon, LinkIcon, Trash2Icon, UsersIcon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { PhotoAvatar } from '@/components/PhotoAvatar'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { formatInstantDate } from '@/lib/format'
import { type ShareTarget, useCreateLink, useDeleteLink, useShare, useShareOptions, useShares, useUnshare } from '../api'
import { LINK_DAYS, publicUrl, ROLE_HINT, ROLE_LABEL, SHARE_ROLES } from '../labels'
import type { ArchRole } from '../types'

type Props = { open: boolean; onOpenChange: (open: boolean) => void; projectId: string }

type ShareRole = Exclude<ArchRole, 'OWNER'>

/** Compartilhar (só o dono): com amigos, com grupos e por link público. */
export function ShareDialog({ open, onOpenChange, projectId }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Compartilhar projeto</DialogTitle>
          <DialogDescription>Quem recebe é avisado e vê o projeto em Arquitetura.</DialogDescription>
        </DialogHeader>
        <DialogBody>
          <ShareTabs projectId={projectId} />
        </DialogBody>
      </DialogScrollContent>
    </Dialog>
  )
}

function ShareTabs({ projectId }: { projectId: string }) {
  const { data: shares } = useShares(projectId, true)
  const { data: options } = useShareOptions(projectId, true)
  const share = useShare()
  const unshare = useUnshare()

  async function run(action: Promise<unknown>, done: string) {
    try {
      await action
      toast.success(done)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível salvar.')
    }
  }

  const setRole = (target: Omit<ShareTarget, 'projectId'>, role: ShareRole, done: string) =>
    run(share.mutateAsync({ projectId, ...target, role }), done)
  const remove = (target: Omit<ShareTarget, 'projectId'>) =>
    run(unshare.mutateAsync({ projectId, ...target }), 'Acesso removido.')

  if (!shares || !options) return <Skeleton className="h-48" />

  return (
    <Tabs defaultValue="people">
      <TabsList className="w-full">
        <TabsTrigger value="people">Amigos</TabsTrigger>
        <TabsTrigger value="groups">Grupos</TabsTrigger>
        <TabsTrigger value="link">Link</TabsTrigger>
      </TabsList>

      <TabsContent value="people" className="space-y-4 pt-3">
        <AddRow
          placeholder="Escolha um amigo"
          options={options.friends.map((f) => ({ id: f.id, name: f.name }))}
          empty={<>Ninguém para adicionar. <Link className="underline" to="/amigos">Adicione amigos</Link> para compartilhar.</>}
          onAdd={(id, role) => setRole({ kind: 'users', targetId: id }, role, 'Projeto compartilhado.')} />
        <ul className="space-y-2">
          {shares.users.map(({ user, role }) => (
            <ShareRow key={user.id} avatar={<UserAvatar user={user} size="sm" />} name={user.name}
              detail={user.username ? `@${user.username}` : null} role={role}
              onRole={(r) => setRole({ kind: 'users', targetId: user.id }, r, 'Permissão alterada.')}
              onRemove={() => remove({ kind: 'users', targetId: user.id })} />
          ))}
        </ul>
      </TabsContent>

      <TabsContent value="groups" className="space-y-4 pt-3">
        <AddRow
          placeholder="Escolha um grupo"
          options={options.groups.map((g) => ({ id: g.id, name: g.name }))}
          empty="Você não tem outro grupo para compartilhar."
          onAdd={(id, role) => setRole({ kind: 'groups', targetId: id }, role, 'Projeto compartilhado com o grupo.')} />
        <ul className="space-y-2">
          {shares.groups.map(({ group, role }) => (
            <ShareRow key={group.id} avatar={<PhotoAvatar name={group.name} imageUrl={group.imageUrl} icon={UsersIcon} size="sm" />}
              name={group.name} detail="Todos os membros" role={role}
              onRole={(r) => setRole({ kind: 'groups', targetId: group.id }, r, 'Permissão alterada.')}
              onRemove={() => remove({ kind: 'groups', targetId: group.id })} />
          ))}
        </ul>
      </TabsContent>

      <TabsContent value="link" className="pt-3">
        <LinkTab projectId={projectId} links={shares.links} />
      </TabsContent>
    </Tabs>
  )
}

type AddRowProps = {
  placeholder: string
  options: { id: string; name: string }[]
  empty: React.ReactNode
  onAdd: (id: string, role: ShareRole) => Promise<void>
}

function AddRow({ placeholder, options, empty, onAdd }: AddRowProps) {
  const [target, setTarget] = useState('')
  const [role, setRole] = useState<ShareRole>('VIEW')
  const [busy, setBusy] = useState(false)
  if (options.length === 0) return <p className="text-sm text-muted-foreground">{empty}</p>

  return (
    <div className="space-y-2">
      <div className="flex flex-col gap-2 sm:flex-row">
        <Select value={target} onValueChange={setTarget}>
          <SelectTrigger className="w-full sm:flex-1"><SelectValue placeholder={placeholder} /></SelectTrigger>
          <SelectContent>{options.map((o) => <SelectItem key={o.id} value={o.id}>{o.name}</SelectItem>)}</SelectContent>
        </Select>
        <RoleSelect value={role} onChange={setRole} />
        <Button disabled={!target || busy} onClick={async () => {
          setBusy(true)
          await onAdd(target, role)
          setTarget('')
          setBusy(false)
        }}>Adicionar</Button>
      </div>
      <p className="text-xs text-muted-foreground">{ROLE_HINT[role]}</p>
    </div>
  )
}

type ShareRowProps = {
  avatar: React.ReactNode
  name: string
  detail: string | null
  role: ArchRole
  onRole: (role: ShareRole) => void
  onRemove: () => void
}

function ShareRow({ avatar, name, detail, role, onRole, onRemove }: ShareRowProps) {
  return (
    <li className="flex items-center gap-3">
      {avatar}
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{name}</p>
        {detail && <p className="truncate text-xs text-muted-foreground">{detail}</p>}
      </div>
      <RoleSelect value={role as ShareRole} onChange={onRole} compact />
      <Button variant="ghost" size="icon-sm" aria-label={`Tirar o acesso de ${name}`} onClick={onRemove}><Trash2Icon /></Button>
    </li>
  )
}

function RoleSelect({ value, onChange, compact }: { value: ShareRole; onChange: (role: ShareRole) => void; compact?: boolean }) {
  return (
    <Select value={value} onValueChange={(v) => onChange(v as ShareRole)}>
      <SelectTrigger className={compact ? 'w-36' : 'w-full sm:w-40'} aria-label="Permissão"><SelectValue /></SelectTrigger>
      <SelectContent>{SHARE_ROLES.map((r) => <SelectItem key={r} value={r}>{ROLE_LABEL[r]}</SelectItem>)}</SelectContent>
    </Select>
  )
}

function LinkTab({ projectId, links }: { projectId: string; links: { id: string; token: string; expiresAt: string }[] }) {
  const create = useCreateLink()
  const remove = useDeleteLink()
  const [days, setDays] = useState('30')

  async function copy(token: string) {
    try {
      await navigator.clipboard.writeText(publicUrl(token))
      toast.success('Link copiado.')
    } catch {
      toast.error('Não foi possível copiar. Selecione o link e copie.')
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Quem tiver o link vê a maquete atual e as vistas salvas, sem precisar de conta. Comentários e anexos não aparecem.
      </p>
      <div className="flex gap-2">
        <Select value={days} onValueChange={setDays}>
          <SelectTrigger className="flex-1" aria-label="Validade"><SelectValue /></SelectTrigger>
          <SelectContent>
            {LINK_DAYS.map((d) => <SelectItem key={d} value={String(d)}>Vale por {d === 1 ? '1 dia' : `${d} dias`}</SelectItem>)}
          </SelectContent>
        </Select>
        <Button disabled={create.isPending} onClick={async () => {
          try {
            const link = await create.mutateAsync({ projectId, days: Number(days) })
            await copy(link.token)
          } catch (e) {
            toast.error(e instanceof ApiError ? e.message : 'Não foi possível criar o link.')
          }
        }}><LinkIcon /> Criar link</Button>
      </div>
      <ul className="space-y-2">
        {links.map((link) => (
          <li key={link.id} className="flex items-center gap-2 rounded-xl border p-2">
            <div className="min-w-0 flex-1">
              <p className="truncate font-mono text-xs">{publicUrl(link.token)}</p>
              <p className="text-xs text-muted-foreground">Vale até {formatInstantDate(link.expiresAt)}</p>
            </div>
            <Button variant="ghost" size="icon-sm" aria-label="Copiar link" onClick={() => void copy(link.token)}><CopyIcon /></Button>
            <Button variant="ghost" size="icon-sm" aria-label="Apagar link" onClick={async () => {
              try {
                await remove.mutateAsync({ projectId, id: link.id })
                toast.success('Link apagado: ele para de funcionar na hora.')
              } catch {
                toast.error('Não foi possível apagar o link.')
              }
            }}><Trash2Icon /></Button>
          </li>
        ))}
      </ul>
    </div>
  )
}
