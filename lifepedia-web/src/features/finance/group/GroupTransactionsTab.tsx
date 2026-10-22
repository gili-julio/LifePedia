import { PackagePlusIcon, PencilIcon, PlusIcon, UserMinusIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ActionSheet } from '@/components/ActionSheet'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { SortableHead } from '@/components/data-table/SortableHead'
import { SortSelect } from '@/components/data-table/SortSelect'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { defaultDateInMonth, formatDate, formatMonth, type YearMonth } from '@/lib/format'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { EntryListItem } from '../components/EntryListItem'
import { Money } from '../components/Money'
import { TransactionDialog } from '../components/TransactionDialog'
import { TypeIcon } from '../components/TypeIcon'
import type { EntryType, Transaction } from '../types'
import { useCreateGroupTransaction, useGroupTransactionsPage, useRemoveGroupItem, useUpdateGroupTransaction } from './api'
import { CategoryLabel, ShareBadge } from './GroupItemParts'
import type { GroupTransaction } from './types'

type Filter = 'ALL' | EntryType

const SORT_OPTIONS = [
  { field: 'date', label: 'Data', firstDirection: 'desc' as const },
  { field: 'description', label: 'Descrição' },
  { field: 'category', label: 'Categoria' },
  { field: 'member', label: 'Membro' },
  { field: 'amount', label: 'Valor', firstDirection: 'desc' as const },
]

type Props = {
  groupId: string
  month: YearMonth
  isAdmin: boolean
  onBring: () => void
}

/** Formato do dialog de lançamento para editar a cópia. */
function asTransaction(g: GroupTransaction): Transaction {
  return {
    id: g.id, description: g.description, type: g.type, categoryId: g.category.id, amount: g.amount,
    date: g.date, month: g.month, notes: g.notes, originalDescription: null,
  }
}

/** Ocasionais do grupo no mês, paginados e ordenados na API. */
export function GroupTransactionsTab({ groupId, month, isAdmin, onBring }: Props) {
  const [filter, setFilter] = useState<Filter>('ALL')
  const list = useListState({ field: 'date', direction: 'desc' })
  const { data, isLoading } = useGroupTransactionsPage(groupId, month, filter === 'ALL' ? undefined : filter, list.params)
  useClampPage(data, list.setPage)
  const create = useCreateGroupTransaction()
  const update = useUpdateGroupTransaction()
  const remove = useRemoveGroupItem()
  const [editing, setEditing] = useState<{ open: boolean; item?: GroupTransaction }>({ open: false })
  const [menu, setMenu] = useState<{ open: boolean; item?: GroupTransaction }>({ open: false })
  const [removing, setRemoving] = useState<GroupTransaction | null>(null)
  const items = data?.items ?? []

  const canEdit = (t: GroupTransaction) => t.mine && !t.synced
  const canRemove = (t: GroupTransaction) => t.mine || isAdmin

  async function handleRemove(t: GroupTransaction) {
    try {
      await remove.mutateAsync({ groupId, id: t.id, kind: 'transactions' })
      toast.success('Removido do grupo.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível remover.')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <Tabs value={filter} onValueChange={(v) => {
          setFilter(v as Filter)
          list.resetPage()
        }}>
          <TabsList>
            <TabsTrigger value="ALL">Todos</TabsTrigger>
            <TabsTrigger value="EXPENSE">Gastos</TabsTrigger>
            <TabsTrigger value="INCOME">Ganhos</TabsTrigger>
          </TabsList>
        </Tabs>
        <Button size="sm" onClick={() => setEditing({ open: true })}>
          <PlusIcon /> <span className="max-sm:sr-only">Novo lançamento</span>
        </Button>
      </div>

      {isLoading ? (
        <Skeleton className="h-48" />
      ) : !items.length ? (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <p className="text-sm text-muted-foreground">Nenhum lançamento no grupo em {formatMonth(month).toLowerCase()}.</p>
          <div className="flex flex-wrap justify-center gap-2">
            <Button variant="outline" onClick={() => setEditing({ open: true })}><PlusIcon /> Novo lançamento</Button>
            <Button variant="outline" onClick={onBring}><PackagePlusIcon /> Levar meus itens</Button>
          </div>
        </div>
      ) : (
        <>
          <div className="space-y-2 md:hidden">
            <SortSelect list={list} options={SORT_OPTIONS} />
            <ul className="divide-y">
              {items.map((t) => (
                <EntryListItem
                  key={t.id}
                  type={t.type}
                  title={t.description}
                  details={<><span className="truncate">{t.member.name}</span> · <CategoryLabel category={t.category} /> · {formatDate(t.date).slice(0, 5)}</>}
                  amount={<Money value={t.amount} type={t.type} />}
                  onMore={() => setMenu({ open: true, item: t })}
                />
              ))}
            </ul>
          </div>

          <Table className="hidden md:table">
            <TableHeader>
              <TableRow>
                <SortableHead field="date" list={list} firstDirection="desc" className="w-28">Data</SortableHead>
                <SortableHead field="description" list={list}>Descrição</SortableHead>
                <SortableHead field="category" list={list}>Categoria</SortableHead>
                <SortableHead field="member" list={list}>Membro</SortableHead>
                <SortableHead field="amount" list={list} firstDirection="desc" align="right">Valor</SortableHead>
                <TableHead className="w-20" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((t) => (
                <TableRow key={t.id}>
                  <TableCell className="text-muted-foreground tabular-nums">{formatDate(t.date)}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <TypeIcon type={t.type} />
                      <div className="min-w-0 space-y-1">
                        <p className="truncate font-medium">{t.description}</p>
                        <ShareBadge synced={t.synced} />
                      </div>
                    </div>
                  </TableCell>
                  <TableCell><CategoryLabel category={t.category} /></TableCell>
                  <TableCell>
                    <p className="truncate">{t.member.name}</p>
                    <p className="truncate text-xs text-muted-foreground">@{t.member.username}</p>
                  </TableCell>
                  <TableCell className="text-right"><Money value={t.amount} type={t.type} /></TableCell>
                  <TableCell>
                    <div className="flex justify-end">
                      {canEdit(t) && (
                        <Button variant="ghost" size="icon-sm" aria-label="Editar cópia" onClick={() => setEditing({ open: true, item: t })}>
                          <PencilIcon />
                        </Button>
                      )}
                      {canRemove(t) && (
                        <Button variant="ghost" size="icon-sm" aria-label="Tirar do grupo" title="Tirar do grupo" onClick={() => setRemoving(t)}>
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
        description={removing?.synced ? 'O lançamento pessoal continua como está.' : 'A cópia do grupo será apagada.'}
        confirmLabel="Tirar do grupo"
        onConfirm={() => removing && handleRemove(removing)}
      />
      <TransactionDialog
        open={editing.open}
        onOpenChange={(open) => setEditing((e) => ({ ...e, open }))}
        transaction={editing.item && asTransaction(editing.item)}
        defaultType={filter === 'INCOME' ? 'INCOME' : 'EXPENSE'}
        defaultDate={defaultDateInMonth(month)}
        onSave={(data) => editing.item
          ? update.mutateAsync({ groupId, id: editing.item.id, data })
          : create.mutateAsync({ groupId, data })}
      />
    </div>
  )
}
