import { ChevronRightIcon, SearchIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { SortableHead } from '@/components/data-table/SortableHead'
import { SortSelect } from '@/components/data-table/SortSelect'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { UserAvatar } from '@/components/UserAvatar'
import { formatInstantDate, formatRelative } from '@/lib/format'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { useAdminUsers, useSetAdmin } from '../api'
import { errorMessage } from '../labels'
import type { AdminUser, UserFilter } from '../types'
import { AdminSwitch } from './AdminSwitch'
import { ConfirmAction, type PendingAction } from './ConfirmAction'
import { UserDetailSheet } from './UserDetailSheet'

const FILTERS: { value: UserFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'ADMIN', label: 'Só admins' },
  { value: 'BLOCKED', label: 'Só bloqueados' },
]

const SORT_OPTIONS = [
  { field: 'createdAt', label: 'Cadastro', firstDirection: 'desc' as const },
  { field: 'lastSeenAt', label: 'Último acesso', firstDirection: 'desc' as const },
  { field: 'name', label: 'Nome' },
]

/** Lista de usuários com busca, filtro, ordenação e o toggle de admin. */
export function UsersTab() {
  const list = useListState({ field: 'createdAt', direction: 'desc' })
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState<UserFilter>('ALL')
  const q = useDebouncedValue(search.trim(), 300)
  const { data, isLoading } = useAdminUsers({ ...list.params, q, filter })
  useClampPage(data, list.setPage)
  const [selected, setSelected] = useState<string | null>(null)
  const [pending, setPending] = useState<PendingAction | null>(null)
  const setAdmin = useSetAdmin()

  function askAdmin(user: AdminUser, admin: boolean) {
    setPending({
      title: admin ? `Dar acesso de admin para ${user.name}?` : `Tirar o acesso de admin de ${user.name}?`,
      description: admin
        ? 'A pessoa passa a ver o painel de administração, a lista de usuários e pode dar ou tirar acesso de outros admins.'
        : 'A pessoa perde o acesso ao painel na hora.',
      confirmLabel: admin ? 'Dar acesso' : 'Tirar acesso',
      destructive: !admin,
      run: () => setAdmin.mutate({ id: user.id, admin }, {
        onSuccess: () => toast.success(admin ? `${user.name} agora é admin.` : `${user.name} não é mais admin.`),
        onError: (e) => toast.error(errorMessage(e)),
      }),
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input value={search} placeholder="Buscar por nome, e-mail ou @usuário" className="pl-8 text-base md:text-sm"
            aria-label="Buscar usuários" onChange={(e) => {
              setSearch(e.target.value)
              list.resetPage()
            }} />
        </div>
        <Select value={filter} onValueChange={(v) => {
          setFilter(v as UserFilter)
          list.resetPage()
        }}>
          <SelectTrigger className="sm:w-44" aria-label="Filtrar usuários"><SelectValue /></SelectTrigger>
          <SelectContent>{FILTERS.map((f) => <SelectItem key={f.value} value={f.value}>{f.label}</SelectItem>)}</SelectContent>
        </Select>
      </div>
      <div className="md:hidden"><SortSelect list={list} options={SORT_OPTIONS} /></div>

      {isLoading ? (
        <div className="space-y-2">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-14" />)}</div>
      ) : !data?.items.length ? (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <p className="text-sm text-muted-foreground">Nenhum usuário encontrado.</p>
          <Button variant="outline" onClick={() => {
            setSearch('')
            setFilter('ALL')
            list.resetPage()
          }}>Limpar busca</Button>
        </div>
      ) : (
        <>
          <Table className="hidden md:table">
            <TableHeader>
              <TableRow>
                <SortableHead field="name" list={list}>Usuário</SortableHead>
                <SortableHead field="createdAt" list={list} firstDirection="desc">Cadastro</SortableHead>
                <SortableHead field="lastSeenAt" list={list} firstDirection="desc">Último acesso</SortableHead>
                <TableHead>Admin</TableHead>
                <TableHead><span className="sr-only">Detalhes</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.items.map((u) => (
                <TableRow key={u.id}>
                  <TableCell className="max-w-0 w-1/2">
                    <div className="flex items-center gap-3">
                      <UserAvatar user={u} size="sm" />
                      <div className="min-w-0">
                        <p className="flex items-center gap-1.5 font-medium">
                          <span className="truncate">{u.name}</span>
                          {u.self && <Badge variant="outline">Você</Badge>}
                          {u.blocked && <Badge variant="destructive">Bloqueado</Badge>}
                        </p>
                        <p className="truncate text-xs text-muted-foreground">{u.username ? `@${u.username} · ` : ''}{u.email}</p>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="whitespace-nowrap tabular-nums">{formatInstantDate(u.createdAt)}</TableCell>
                  <TableCell className="whitespace-nowrap text-muted-foreground">{u.lastSeenAt ? formatRelative(u.lastSeenAt) : 'Sem registro'}</TableCell>
                  <TableCell><AdminSwitch user={u} busy={setAdmin.isPending} onChange={askAdmin} /></TableCell>
                  <TableCell className="text-right">
                    <Button variant="ghost" size="sm" onClick={() => setSelected(u.id)}>Detalhes <ChevronRightIcon /></Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          <ul className="divide-y md:hidden">
            {data.items.map((u) => (
              <li key={u.id} className="flex items-center gap-3 py-3">
                <button type="button" onClick={() => setSelected(u.id)}
                  className="flex min-w-0 flex-1 items-center gap-3 rounded-md text-left focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">
                  <UserAvatar user={u} size="md" />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-1.5 font-medium">
                      <span className="truncate">{u.name}</span>
                      {u.blocked && <Badge variant="destructive">Bloqueado</Badge>}
                    </span>
                    <span className="block truncate text-xs text-muted-foreground">{u.email}</span>
                    <span className="block text-xs text-muted-foreground">Desde {formatInstantDate(u.createdAt)}</span>
                  </span>
                </button>
                <AdminSwitch user={u} busy={setAdmin.isPending} onChange={askAdmin} />
              </li>
            ))}
          </ul>
        </>
      )}
      <DataTablePagination data={data} list={list} />

      <UserDetailSheet id={selected} onClose={() => setSelected(null)} onAsk={setPending} onAskAdmin={askAdmin} />
      <ConfirmAction action={pending} onClose={() => setPending(null)} />
    </div>
  )
}
