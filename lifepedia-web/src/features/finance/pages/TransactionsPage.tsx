import { CalendarClockIcon, PencilIcon, PlusIcon, RepeatIcon, SparklesIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ActionSheet } from '@/components/ActionSheet'
import { BankBadge, BankMark } from '@/components/bank/BankLogo'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { SortableHead } from '@/components/data-table/SortableHead'
import { SortSelect } from '@/components/data-table/SortSelect'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { defaultDateInMonth, formatDate, formatMonth, type YearMonth } from '@/lib/format'
import { useBankOverview, useDeleteTransaction, useMoveTransactionMonth, useSummary, useTransactionsPage } from '../api'
import { BankAssignBar } from '../components/BankAssignBar'
import { BankFilterSelect } from '../components/BankFilterSelect'
import { CategoryBadge } from '../components/CategoryBadge'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { EntryListItem } from '../components/EntryListItem'
import { Money } from '../components/Money'
import { MoveInstallmentDialog } from '../components/MoveInstallmentDialog'
import { MonthPicker } from '../components/MonthPicker'
import { RecurringEntryDialog } from '../components/RecurringEntryDialog'
import { TotalCard } from '../components/TotalCard'
import { TransactionDialog } from '../components/TransactionDialog'
import { TypeIcon } from '../components/TypeIcon'
import type { EntryType, Transaction } from '../types'
import { toBankFilter } from '../bank-filter'
import { parseInstallment } from '../installments'
import { useMonthParam } from '../use-month-param'
import { useSelection } from '../use-selection'

type Filter = 'ALL' | EntryType

const SORT_OPTIONS = [
  { field: 'date', label: 'Data', firstDirection: 'desc' as const },
  { field: 'description', label: 'Descrição' },
  { field: 'category', label: 'Categoria' },
  { field: 'amount', label: 'Valor', firstDirection: 'desc' as const },
]

export function TransactionsPage() {
  const [month, setMonthParam] = useMonthParam()
  const [filter, setFilter] = useState<Filter>('ALL')
  const [params, setParams] = useSearchParams()
  const bankValue = params.get('banco') ?? ''
  const bankFilter = toBankFilter(bankValue)
  const list = useListState({ field: 'date', direction: 'desc' })
  const { data, isLoading } = useTransactionsPage(month, filter === 'ALL' ? undefined : filter, list.params, bankFilter)
  useClampPage(data, list.setPage)
  const totals = useSummary(month, bankFilter).data?.occasional
  const banks = useBankOverview(month).data?.banks
  const selection = useSelection()
  const remove = useDeleteTransaction()
  const [dialog, setDialog] = useState<{ open: boolean; transaction?: Transaction }>({ open: false })
  const [makeFixed, setMakeFixed] = useState<{ open: boolean; transaction?: Transaction }>({ open: false })
  const [menu, setMenu] = useState<{ open: boolean; item?: Transaction }>({ open: false })
  const [deleting, setDeleting] = useState<Transaction | null>(null)
  const [moving, setMoving] = useState<{ open: boolean; item?: Transaction }>({ open: false })
  const move = useMoveTransactionMonth()

  const items = data?.items ?? []
  const pageIds = items.map((t) => t.id)
  const allSelected = pageIds.length > 0 && pageIds.every((id) => selection.selected.has(id))
  const setMonth = (next: YearMonth) => {
    setMonthParam(next)
    list.resetPage()
    selection.clear()
  }
  const setBank = (value: string) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      if (value) next.set('banco', value)
      else next.delete('banco')
      return next
    }, { replace: true })
    list.resetPage()
    selection.clear()
  }
  const openNew = () => setDialog({ open: true })
  const openEdit = (transaction: Transaction) => setDialog({ open: true, transaction })
  const openMakeFixed = (transaction: Transaction) => setMakeFixed({ open: true, transaction })

  async function handleDelete(transaction: Transaction) {
    try {
      await remove.mutateAsync(transaction.id)
      toast.success('Lançamento excluído.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        hideValues
        title="Lançamentos ocasionais"
        description="Ganhos e gastos que não se repetem todo mês."
        toolbar={<MonthPicker value={month} onChange={setMonth} />}
        actions={[
          { label: 'Importar com IA', icon: SparklesIcon, to: '/financeiro/importar' },
          { label: 'Novo lançamento', icon: PlusIcon, primary: true, onSelect: openNew },
        ]}
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <TotalCard label="Ganhos ocasionais" value={totals?.income} kind="INCOME" />
        <TotalCard label="Gastos ocasionais" value={totals?.expense} kind="EXPENSE" />
        <TotalCard label="Saldo ocasional" value={totals?.balance} kind="BALANCE" />
      </div>

      <Card>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
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
            <BankFilterSelect lines={banks} value={bankValue} onChange={setBank} />
          </div>
          <BankAssignBar ids={selection.ids} target="transactions" onClear={selection.clear} />

          {isLoading ? (
            <Skeleton className="h-48" />
          ) : !items.length ? (
            <div className="flex flex-col items-center gap-3 py-12 text-center">
              <p className="text-sm text-muted-foreground">
                Nenhum lançamento{bankValue ? ' neste banco' : ''} em {formatMonth(month).toLowerCase()}.
              </p>
              <Button variant="outline" onClick={openNew}>
                <PlusIcon /> Registrar lançamento
              </Button>
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
                      details={<>{t.bank && <BankMark bank={t.bank} />}<CategoryBadge categoryId={t.categoryId} /> · {formatDate(t.date).slice(0, 5)}</>}
                      amount={<Money value={t.amount} type={t.type} />}
                      onMore={() => setMenu({ open: true, item: t })}
                      selected={selection.selected.has(t.id)}
                      onSelect={(checked) => selection.set([t.id], checked)}
                    />
                  ))}
                </ul>
              </div>

              <Table className="hidden md:table">
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-8">
                      <Checkbox aria-label="Selecionar todos da página" checked={allSelected}
                        onCheckedChange={(v) => selection.set(pageIds, v === true)} />
                    </TableHead>
                    <SortableHead field="date" list={list} firstDirection="desc" className="w-28">Data</SortableHead>
                    <SortableHead field="description" list={list}>Descrição</SortableHead>
                    <SortableHead field="category" list={list}>Categoria</SortableHead>
                    <TableHead className="hidden xl:table-cell">Banco</TableHead>
                    <SortableHead field="amount" list={list} firstDirection="desc" align="right">Valor</SortableHead>
                    <TableHead className="w-28" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((t) => (
                    <TableRow key={t.id}>
                      <TableCell>
                        <Checkbox aria-label={`Selecionar ${t.description}`} checked={selection.selected.has(t.id)}
                          onCheckedChange={(v) => selection.set([t.id], v === true)} />
                      </TableCell>
                      <TableCell className="tabular-nums text-muted-foreground">{formatDate(t.date)}</TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <TypeIcon type={t.type} />
                          {t.bank && <BankMark bank={t.bank} className="xl:hidden" />}
                          <div className="min-w-0">
                            <p className="truncate font-medium">{t.description}</p>
                            {t.notes && <p className="truncate text-xs text-muted-foreground">{t.notes}</p>}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell><CategoryBadge categoryId={t.categoryId} /></TableCell>
                      <TableCell className="hidden xl:table-cell"><BankBadge bank={t.bank} /></TableCell>
                      <TableCell className="text-right"><Money value={t.amount} type={t.type} /></TableCell>
                      <TableCell>
                        <div className="flex justify-end">
                          {parseInstallment(t.description) && (
                            <Button variant="ghost" size="icon-sm" aria-label="Adiantar ou adiar parcela" title="Adiantar ou adiar parcela"
                              onClick={() => setMoving({ open: true, item: t })}>
                              <CalendarClockIcon />
                            </Button>
                          )}
                          <Button variant="ghost" size="icon-sm" aria-label="Tornar fixo" title="Tornar fixo" onClick={() => openMakeFixed(t)}>
                            <RepeatIcon />
                          </Button>
                          <Button variant="ghost" size="icon-sm" aria-label="Editar" onClick={() => openEdit(t)}>
                            <PencilIcon />
                          </Button>
                          <Button variant="ghost" size="icon-sm" aria-label="Excluir" onClick={() => setDeleting(t)}>
                            <Trash2Icon />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </>
          )}
          {!isLoading && <DataTablePagination data={data} list={list} />}
        </CardContent>
      </Card>

      {menu.item && (
        <ActionSheet
          open={menu.open}
          onOpenChange={(open) => setMenu((m) => ({ ...m, open }))}
          title={menu.item.description}
          description={formatDate(menu.item.date)}
          actions={[
            { label: 'Editar', icon: PencilIcon, onSelect: () => openEdit(menu.item!) },
            ...(parseInstallment(menu.item.description)
              ? [{ label: 'Adiantar ou adiar parcela', icon: CalendarClockIcon, onSelect: () => setMoving({ open: true, item: menu.item }) }]
              : []),
            { label: 'Tornar fixo', icon: RepeatIcon, onSelect: () => openMakeFixed(menu.item!) },
            { label: 'Excluir', icon: Trash2Icon, destructive: true, onSelect: () => setDeleting(menu.item!) },
          ]}
        />
      )}
      <ConfirmDelete
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={`Excluir "${deleting?.description}"?`}
        description="Essa ação não pode ser desfeita."
        onConfirm={() => deleting && handleDelete(deleting)}
      />
      <TransactionDialog
        open={dialog.open}
        onOpenChange={(open) => setDialog((d) => ({ ...d, open }))}
        transaction={dialog.transaction}
        defaultDate={defaultDateInMonth(month)}
      />
      <MoveInstallmentDialog
        open={moving.open}
        onOpenChange={(open) => setMoving((m) => ({ ...m, open }))}
        item={moving.item}
        onMove={(data) => move.mutateAsync({ id: moving.item!.id, ...data })}
      />
      <RecurringEntryDialog
        open={makeFixed.open}
        onOpenChange={(open) => setMakeFixed((m) => ({ ...m, open }))}
        fromTransaction={makeFixed.transaction}
      />
    </div>
  )
}
