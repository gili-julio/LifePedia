import { PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ActionSheet } from '@/components/ActionSheet'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState, type ListState, type Page } from '@/components/data-table/list-state'
import { SortableHead } from '@/components/data-table/SortableHead'
import { SortSelect } from '@/components/data-table/SortSelect'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { currentYearMonth, formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useDeleteRecurringEntry, useRecurringPage, useSummary } from '../api'
import { CategoryBadge } from '../components/CategoryBadge'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { EntryListItem } from '../components/EntryListItem'
import { Money } from '../components/Money'
import { RecurringEntryDialog } from '../components/RecurringEntryDialog'
import { TotalCard } from '../components/TotalCard'
import { TypeIcon } from '../components/TypeIcon'
import { isActiveIn, valueIn } from '../fixed'
import type { EntryType, RecurringEntry } from '../types'

const isCurrent = (entry: RecurringEntry) => isActiveIn(entry, currentYearMonth())
const currentValue = (entry: RecurringEntry) => valueIn(entry, currentYearMonth())

const SORT_OPTIONS = [
  { field: 'amount', label: 'Valor', firstDirection: 'desc' as const },
  { field: 'description', label: 'Descrição' },
  { field: 'category', label: 'Categoria' },
  { field: 'day', label: 'Dia' },
  { field: 'start', label: 'Início', firstDirection: 'desc' as const },
]

function period(entry: RecurringEntry) {
  const start = formatMonth(entry.startMonth, 'short')
  return entry.endMonth ? `${start} → ${formatMonth(entry.endMonth, 'short')}` : `desde ${start}`
}

function statusLabel(entry: RecurringEntry) {
  if (!entry.active) return 'Pausado'
  if (entry.startMonth > currentYearMonth()) return 'Futuro'
  if (entry.endMonth && entry.endMonth < currentYearMonth()) return 'Encerrado'
  return 'Vigente'
}

function StatusBadge({ entry }: { entry: RecurringEntry }) {
  const label = statusLabel(entry)
  return <Badge variant={label === 'Vigente' || label === 'Futuro' ? 'outline' : 'secondary'}>{label}</Badge>
}

type EntryListProps = {
  data?: Page<RecurringEntry>
  list: ListState
  type: EntryType
  /** Total vigente no mês atual, da API */
  total?: number
  onCreate: () => void
  onEdit: (entry: RecurringEntry) => void
  onDelete: (entry: RecurringEntry) => void
  onMore: (entry: RecurringEntry) => void
}

function EntryList({ data, list, type, total, onCreate, onEdit, onDelete, onMore }: EntryListProps) {
  const { formatCurrency } = useMoney()
  const entries = data?.items ?? []
  const noun = type === 'INCOME' ? 'ganho fixo' : 'despesa fixa'
  const totalLabel = total === undefined ? '—' : formatCurrency(total)

  if (!data) return <Skeleton className="h-48" />
  if (!data.totalItems) {
    return (
      <div className="flex flex-col items-center gap-3 py-12 text-center">
        <p className="text-sm text-muted-foreground">Nenhum {noun} cadastrado ainda.</p>
        <Button onClick={onCreate}>
          <PlusIcon /> Cadastrar {noun}
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="space-y-2 md:hidden">
        <SortSelect list={list} options={SORT_OPTIONS} />
        <ul className="divide-y">
          {entries.map((entry) => (
            <EntryListItem
              key={entry.id}
              type={entry.type}
              title={entry.description}
              muted={!isCurrent(entry)}
              details={
                <>
                  <CategoryBadge categoryId={entry.categoryId} />
                  {entry.dayOfMonth && <> · dia {entry.dayOfMonth}</>}
                  {!isCurrent(entry) && <> · {statusLabel(entry)}</>}
                  {isCurrent(entry) && currentValue(entry) !== entry.amount && <> · este mês {formatCurrency(currentValue(entry))}</>}
                </>
              }
              amount={<Money value={entry.amount} type={entry.type} />}
              onMore={() => onMore(entry)}
            />
          ))}
          <li className="flex justify-between py-3 text-sm">
            <span className="font-medium">Total vigente</span>
            <span className="font-semibold tabular-nums">{totalLabel}</span>
          </li>
        </ul>
      </div>

      <Table className="hidden md:table">
        <TableHeader>
          <TableRow>
            <SortableHead field="description" list={list}>Descrição</SortableHead>
            <SortableHead field="category" list={list}>Categoria</SortableHead>
            <SortableHead field="day" list={list}>Dia</SortableHead>
            <SortableHead field="start" list={list} firstDirection="desc" className="hidden lg:table-cell">Vigência</SortableHead>
            <TableHead>Status</TableHead>
            <SortableHead field="amount" list={list} firstDirection="desc" align="right">Valor/mês</SortableHead>
            <TableHead className="w-20" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map((entry) => (
            <TableRow key={entry.id} className={isCurrent(entry) ? undefined : 'text-muted-foreground'}>
              <TableCell>
                <div className="flex items-center gap-2">
                  <TypeIcon type={entry.type} />
                  <span className="font-medium">{entry.description}</span>
                </div>
              </TableCell>
              <TableCell><CategoryBadge categoryId={entry.categoryId} /></TableCell>
              <TableCell>{entry.dayOfMonth ?? '—'}</TableCell>
              <TableCell className="hidden lg:table-cell">{period(entry)}</TableCell>
              <TableCell><StatusBadge entry={entry} /></TableCell>
              <TableCell className="text-right">
                <Money value={entry.amount} type={entry.type} />
                {isCurrent(entry) && currentValue(entry) !== entry.amount && (
                  <p className="text-xs text-muted-foreground">este mês: {formatCurrency(currentValue(entry))}</p>
                )}
              </TableCell>
              <TableCell>
                <div className="flex justify-end">
                  <Button variant="ghost" size="icon-sm" aria-label="Editar" onClick={() => onEdit(entry)}>
                    <PencilIcon />
                  </Button>
                  <Button variant="ghost" size="icon-sm" aria-label="Excluir" onClick={() => onDelete(entry)}>
                    <Trash2Icon />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell colSpan={2} className="font-medium">Total vigente neste mês</TableCell>
            <TableCell colSpan={5} className="text-right font-semibold">{totalLabel}</TableCell>
          </TableRow>
        </TableFooter>
      </Table>

      <DataTablePagination data={data} list={list} />
    </div>
  )
}

export function RecurringEntriesPage() {
  const { formatCurrency } = useMoney()
  const [tab, setTab] = useState<EntryType>('EXPENSE')
  const list = useListState({ field: 'amount', direction: 'desc' })
  const { data } = useRecurringPage(tab, list.params)
  useClampPage(data, list.setPage)
  const remove = useDeleteRecurringEntry()
  const [dialog, setDialog] = useState<{ open: boolean; entry?: RecurringEntry; type: EntryType }>({
    open: false,
    type: 'EXPENSE',
  })
  const [menu, setMenu] = useState<{ open: boolean; item?: RecurringEntry }>({ open: false })
  const [deleting, setDeleting] = useState<RecurringEntry | null>(null)
  const fixedTotals = useSummary(currentYearMonth()).data?.fixed

  const openCreate = (type: EntryType) => setDialog({ open: true, type })
  const openEdit = (entry: RecurringEntry) => setDialog({ open: true, entry, type: entry.type })
  const openMenu = (entry: RecurringEntry) => setMenu({ open: true, item: entry })

  async function handleDelete(entry: RecurringEntry) {
    try {
      await remove.mutateAsync(entry.id)
      toast.success('Excluído.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        hideValues
        title="Ganhos e despesas fixas"
        description="O que entra e sai todo mês. É a base do seu orçamento."
        actions={[
          { label: 'Nova despesa fixa', icon: PlusIcon, primary: tab === 'EXPENSE', onSelect: () => openCreate('EXPENSE') },
          { label: 'Novo ganho fixo', icon: PlusIcon, primary: tab === 'INCOME', onSelect: () => openCreate('INCOME') },
        ]}
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <TotalCard label="Ganhos fixos / mês" value={fixedTotals?.income} kind="INCOME" />
        <TotalCard label="Despesas fixas / mês" value={fixedTotals?.expense} kind="EXPENSE" />
        <TotalCard label="Sobra fixa / mês" value={fixedTotals?.balance} kind="BALANCE" />
      </div>

      <Card>
        <CardContent className="space-y-2">
          <Tabs value={tab} onValueChange={(v) => {
            setTab(v as EntryType)
            list.resetPage()
          }}>
            <TabsList>
              <TabsTrigger value="EXPENSE">Despesas</TabsTrigger>
              <TabsTrigger value="INCOME">Ganhos</TabsTrigger>
            </TabsList>
          </Tabs>
          <EntryList
            data={data?.items.every((e) => e.type === tab) ? data : undefined}
            list={list}
            type={tab}
            total={tab === 'EXPENSE' ? fixedTotals?.expense : fixedTotals?.income}
            onCreate={() => openCreate(tab)}
            onEdit={openEdit}
            onDelete={setDeleting}
            onMore={openMenu}
          />
        </CardContent>
      </Card>

      {menu.item && (
        <ActionSheet
          open={menu.open}
          onOpenChange={(open) => setMenu((m) => ({ ...m, open }))}
          title={menu.item.description}
          description={`${formatCurrency(menu.item.amount)} por mês · ${period(menu.item)}`}
          actions={[
            { label: 'Editar', icon: PencilIcon, onSelect: () => openEdit(menu.item!) },
            { label: 'Excluir', icon: Trash2Icon, destructive: true, onSelect: () => setDeleting(menu.item!) },
          ]}
        />
      )}
      <ConfirmDelete
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={`Excluir "${deleting?.description}"?`}
        description="Ele some de todos os meses, inclusive dos anteriores. Para parar de contar a partir de agora, edite e defina um mês de fim."
        onConfirm={() => deleting && handleDelete(deleting)}
      />
      <RecurringEntryDialog
        open={dialog.open}
        onOpenChange={(open) => setDialog((d) => ({ ...d, open }))}
        entry={dialog.entry}
        defaultType={dialog.type}
      />
    </div>
  )
}
