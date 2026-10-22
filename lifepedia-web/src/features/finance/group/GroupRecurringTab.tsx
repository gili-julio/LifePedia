import { PackagePlusIcon, PencilIcon, PlusIcon, UserMinusIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ActionSheet } from '@/components/ActionSheet'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { SortableHead } from '@/components/data-table/SortableHead'
import { SortSelect } from '@/components/data-table/SortSelect'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { UserAvatar } from '@/components/UserAvatar'
import { ApiError } from '@/lib/api'
import { formatMonth } from '@/lib/format'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { EntryListItem } from '../components/EntryListItem'
import { Money } from '../components/Money'
import { RecurringEntryDialog } from '../components/RecurringEntryDialog'
import type { EntryType, RecurringEntry } from '../types'
import { useCreateGroupRecurring, useGroupRecurringPage, useRemoveGroupItem, useUpdateGroupRecurring } from './api'
import { CategoryLabel, ShareBadge } from './GroupItemParts'
import type { GroupRecurringEntry } from './types'

const SORT_OPTIONS = [
  { field: 'amount', label: 'Valor', firstDirection: 'desc' as const },
  { field: 'description', label: 'Descrição' },
  { field: 'category', label: 'Categoria' },
  { field: 'member', label: 'Membro' },
  { field: 'day', label: 'Dia' },
]

type Props = {
  groupId: string
  isAdmin: boolean
  onBring: () => void
}

function asEntry(g: GroupRecurringEntry): RecurringEntry {
  return {
    id: g.id, description: g.description, type: g.type, categoryId: g.category.id, amount: g.amount,
    dayOfMonth: g.dayOfMonth, startMonth: g.startMonth, endMonth: g.endMonth, active: g.active, notes: g.notes,
    adjustments: [],
  }
}

function period(g: GroupRecurringEntry) {
  const start = formatMonth(g.startMonth, 'short')
  return g.endMonth ? `${start} – ${formatMonth(g.endMonth, 'short')}` : `desde ${start}`
}

/** Fixos do grupo, por tipo, paginados e ordenados na API. */
export function GroupRecurringTab({ groupId, isAdmin, onBring }: Props) {
  const [type, setType] = useState<EntryType>('EXPENSE')
  const list = useListState({ field: 'amount', direction: 'desc' })
  const { data, isLoading } = useGroupRecurringPage(groupId, type, list.params)
  useClampPage(data, list.setPage)
  const create = useCreateGroupRecurring()
  const update = useUpdateGroupRecurring()
  const remove = useRemoveGroupItem()
  const [editing, setEditing] = useState<{ open: boolean; item?: GroupRecurringEntry }>({ open: false })
  const [menu, setMenu] = useState<{ open: boolean; item?: GroupRecurringEntry }>({ open: false })
  const [removing, setRemoving] = useState<GroupRecurringEntry | null>(null)
  const items = data?.items ?? []

  const canEdit = (r: GroupRecurringEntry) => r.mine && !r.synced
  const canRemove = (r: GroupRecurringEntry) => r.mine || isAdmin

  async function handleRemove(r: GroupRecurringEntry) {
    try {
      await remove.mutateAsync({ groupId, id: r.id, kind: 'recurring' })
      toast.success('Removido do grupo.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível remover.')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <Tabs value={type} onValueChange={(v) => {
          setType(v as EntryType)
          list.resetPage()
        }}>
          <TabsList>
            <TabsTrigger value="EXPENSE">Despesas fixas</TabsTrigger>
            <TabsTrigger value="INCOME">Ganhos fixos</TabsTrigger>
          </TabsList>
        </Tabs>
        <Button size="sm" onClick={() => setEditing({ open: true })}>
          <PlusIcon /> <span className="max-sm:sr-only">Novo fixo</span>
        </Button>
      </div>

      {isLoading ? (
        <Skeleton className="h-48" />
      ) : !items.length ? (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <p className="text-sm text-muted-foreground">Nenhum fixo deste tipo no grupo.</p>
          <div className="flex flex-wrap justify-center gap-2">
            <Button variant="outline" onClick={() => setEditing({ open: true })}><PlusIcon /> Novo fixo</Button>
            <Button variant="outline" onClick={onBring}><PackagePlusIcon /> Levar meus itens</Button>
          </div>
        </div>
      ) : (
        <>
          <div className="space-y-2 md:hidden">
            <SortSelect list={list} options={SORT_OPTIONS} />
            <ul className="divide-y">
              {items.map((r) => (
                <EntryListItem
                  key={r.id}
                  type={r.type}
                  title={r.description}
                  muted={!r.active}
                  details={<><span className="truncate">{r.member.name}</span> · <CategoryLabel category={r.category} />{r.dayOfMonth ? ` · dia ${r.dayOfMonth}` : ''}</>}
                  amount={<Money value={r.amount} type={r.type} />}
                  onMore={() => setMenu({ open: true, item: r })}
                />
              ))}
            </ul>
          </div>

          <Table className="hidden md:table">
            <TableHeader>
              <TableRow>
                <SortableHead field="description" list={list}>Descrição</SortableHead>
                <SortableHead field="category" list={list}>Categoria</SortableHead>
                <SortableHead field="member" list={list}>Membro</SortableHead>
                <SortableHead field="day" list={list} className="w-16">Dia</SortableHead>
                <SortableHead field="amount" list={list} firstDirection="desc" align="right">Valor</SortableHead>
                <TableHead className="w-20" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((r) => (
                <TableRow key={r.id} className={r.active ? undefined : 'text-muted-foreground'}>
                  <TableCell>
                    <div className="min-w-0 space-y-1">
                      <p className="truncate font-medium">{r.description}</p>
                      <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                        <ShareBadge synced={r.synced} />
                        {!r.active && <Badge variant="secondary">Pausado</Badge>}
                        {period(r)}
                      </div>
                    </div>
                  </TableCell>
                  <TableCell><CategoryLabel category={r.category} /></TableCell>
                  <TableCell>
                    <div className="flex min-w-0 items-center gap-2">
                      <UserAvatar user={r.member} size="sm" />
                      <div className="min-w-0">
                        <p className="truncate">{r.member.name}</p>
                        <p className="truncate text-xs text-muted-foreground">@{r.member.username}</p>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="tabular-nums">{r.dayOfMonth ?? '—'}</TableCell>
                  <TableCell className="text-right"><Money value={r.amount} type={r.type} /></TableCell>
                  <TableCell>
                    <div className="flex justify-end">
                      {canEdit(r) && (
                        <Button variant="ghost" size="icon-sm" aria-label="Editar cópia" onClick={() => setEditing({ open: true, item: r })}>
                          <PencilIcon />
                        </Button>
                      )}
                      {canRemove(r) && (
                        <Button variant="ghost" size="icon-sm" aria-label="Tirar do grupo" title="Tirar do grupo" onClick={() => setRemoving(r)}>
                          <UserMinusIcon />
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </>
      )}
      {!isLoading && <DataTablePagination data={data} list={list} />}

      {menu.item && (
        <ActionSheet
          open={menu.open}
          onOpenChange={(open) => setMenu((m) => ({ ...m, open }))}
          title={menu.item.description}
          description={`${menu.item.member.name} · ${menu.item.synced ? 'Sincronizado' : 'Cópia'}`}
          actions={[
            ...(canEdit(menu.item) ? [{ label: 'Editar cópia', icon: PencilIcon, onSelect: () => setEditing({ open: true, item: menu.item }) }] : []),
            ...(canRemove(menu.item) ? [{ label: 'Tirar do grupo', icon: UserMinusIcon, destructive: true, onSelect: () => setRemoving(menu.item!) }] : []),
          ]}
        />
      )}
      <ConfirmDelete
        open={!!removing}
        onOpenChange={(open) => !open && setRemoving(null)}
        title={`Tirar "${removing?.description}" do grupo?`}
        description={removing?.synced ? 'O fixo pessoal continua como está.' : 'A cópia do grupo será apagada.'}
        confirmLabel="Tirar do grupo"
        onConfirm={() => removing && handleRemove(removing)}
      />
      <RecurringEntryDialog
        open={editing.open}
        onOpenChange={(open) => setEditing((e) => ({ ...e, open }))}
        entry={editing.item && asEntry(editing.item)}
        defaultType={type}
        onSave={(data) => editing.item
          ? update.mutateAsync({ groupId, id: editing.item.id, data })
          : create.mutateAsync({ groupId, data })}
      />
    </div>
  )
}
