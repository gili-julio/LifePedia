import { LockIcon } from 'lucide-react'
import { Switch } from '@/components/ui/switch'
import type { AdminUser } from '../types'

/** Por que o toggle de admin está travado (ou nulo, se dá para mudar). */
function adminLock(user: AdminUser) {
  if (user.fixedAdmin) return 'Admin definido no servidor (ADMIN_EMAIL): não perde o acesso.'
  if (user.self && user.admin) return 'Você não pode tirar o seu próprio acesso.'
  return null
}

/** Toggle de admin; travado (com cadeado e motivo) na conta do servidor e no próprio admin. */
export function AdminSwitch({ user, busy, onChange }: { user: AdminUser; busy: boolean; onChange: (user: AdminUser, admin: boolean) => void }) {
  const lock = adminLock(user)
  return (
    <span className="inline-flex items-center gap-1.5" title={lock ?? undefined}>
      <Switch checked={user.admin} disabled={lock !== null || busy} aria-label={`Admin: ${user.name}`}
        onCheckedChange={(v) => onChange(user, v)} />
      {lock && <LockIcon className="size-3.5 text-muted-foreground" aria-label={lock} />}
    </span>
  )
}
