import { Trash2Icon, UsersIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
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
import { useShare, useShareOptions, useShares, useUnshare, type ShareTarget } from '../api'
import { ROLE_HINT, ROLE_LABEL, SHARE_ROLES } from '../labels'
import type { VehicleRole } from '../types'

type ShareRole = Exclude<VehicleRole, 'OWNER'>
type Props = { open: boolean; onOpenChange: (open: boolean) => void; vehicleId: string }

/** Compartilhar (só o dono) com amigos e grupos, com a permissão de cada um. */
export function ShareDialog({ open, onOpenChange, vehicleId }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Compartilhar veículo</DialogTitle>
          <DialogDescription>Quem recebe é avisado e vê o veículo em Veículos, com o seu nome como dono.</DialogDescription>
        </DialogHeader>
        <DialogBody>{open && <ShareTabs vehicleId={vehicleId} />}</DialogBody>
      </DialogScrollContent>
    </Dialog>
  )
}

function ShareTabs({ vehicleId }: { vehicleId: string }) {
  const { data: shares } = useShares(vehicleId, true)
  const { data: options } = useShareOptions(vehicleId, true)
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

  const setRole = (target: Omit<ShareTarget, 'id'>, role: ShareRole, done: string) =>
    run(share.mutateAsync({ id: vehicleId, ...target, role }), done)
  const remove = (target: Omit<ShareTarget, 'id'>) => run(unshare.mutateAsync({ id: vehicleId, ...target }), 'Acesso removido.')

  if (!shares || !options) return <Skeleton className="h-48" />

  return (
    <Tabs defaultValue="people">
      <TabsList className="w-full">
        <TabsTrigger value="people">Amigos</TabsTrigger>
        <TabsTrigger value="groups">Grupos</TabsTrigger>
      </TabsList>
      <TabsContent value="people" className="space-y-4 pt-3">
        <AddRow placeholder="Escolha um amigo" options={options.friends.map((f) => ({ id: f.id, name: f.name }))}
          empty={<>Ninguém para adicionar. <Link className="underline" to="/amigos">Adicione amigos</Link> para compartilhar.</>}
          onAdd={(id, role) => setRole({ kind: 'users', targetId: id }, role, 'Veículo compartilhado.')} />
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
        <AddRow placeholder="Escolha um grupo" options={options.groups.map((g) => ({ id: g.id, name: g.name }))}
          empty="Você não tem outro grupo para compartilhar."
          onAdd={(id, role) => setRole({ kind: 'groups', targetId: id }, role, 'Veículo compartilhado com o grupo.')} />
        <ul className="space-y-2">
          {shares.groups.map(({ group, role }) => (
            <ShareRow key={group.id} avatar={<PhotoAvatar name={group.name} imageUrl={group.imageUrl} icon={UsersIcon} size="sm" />}
              name={group.name} detail="Todos os membros" role={role}
              onRole={(r) => setRole({ kind: 'groups', targetId: group.id }, r, 'Permissão alterada.')}
              onRemove={() => remove({ kind: 'groups', targetId: group.id })} />
          ))}
        </ul>
      </TabsContent>
    </Tabs>
  )
}

function AddRow({ placeholder, options, empty, onAdd }: {
  placeholder: string
  options: { id: string; name: string }[]
  empty: ReactNode
  onAdd: (id: string, role: ShareRole) => Promise<void>
}) {
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

function ShareRow({ avatar, name, detail, role, onRole, onRemove }: {
  avatar: ReactNode
  name: string
  detail: string | null
  role: VehicleRole
  onRole: (role: ShareRole) => void
  onRemove: () => void
}) {
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
