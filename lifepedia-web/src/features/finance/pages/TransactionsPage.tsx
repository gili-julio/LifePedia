import { CalendarClockIcon, CheckCircle2Icon, PencilIcon, PlusIcon, RepeatIcon, SparklesIcon, Trash2Icon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ActionSheet } from '@/components/ActionSheet'
import { BankBadge, BankMark } from '@/components/bank/BankLogo'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { Highlight } from '@/components/Highlight'
import { ListSearch } from '@/components/ListSearch'
import { useListSearch } from '@/components/use-list-search'
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
import { useMoney } from '@/lib/hide-values'
import {
  useBankOverview,
  useDeleteTransaction,
  useMoveTransactionMonth,
  useSummary,
  useTransactionFind,
  useTransactionsPage,
  useTransactionsSearch,
  type TransactionQuery,
} from '../api'
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
import { TransactionBatchActions } from '../components/TransactionBatchActions'
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
  const [params, setParams] = useSearchParams()
  const [filter, setFilter] = useState<Filter>(() => typeParam(params.get('tipo')))
  const bankValue = params.get('banco') ?? ''
  const bankFilter = toBankFilter(bankValue)
  const list = useListState({ field: 'date', direction: 'desc' })
  const [text, setText] = useListSearch()
  const query: TransactionQuery = {
    ...bankFilter,
    q: text || undefined,
    from: params.get('de') ?? undefined,
    to: params.get('ate') ?? undefined,
    min: numberParam(params.get('min')),
    max: numberParam(params.get('max')),
    type: filter === 'ALL' ? undefined : filter,
  }
  const extra = [query.from, query.to, query.min, query.max].some((v) => v !== undefined)
  const searching = !!text || extra
  const monthPage = useTransactionsPage(month, filter === 'ALL' ? undefined : filter, list.params, bankFilter, !searching)
  const found = useTransactionFind(query, list.params, searching)
  const { data, isLoading } = searching ? found : monthPage
  useClampPage(data, list.setPage)
  const monthTotals = useSummary(month, bankFilter).data?.occasional
  const totals = searching
    ? found.data && { income: found.data.income, expense: found.data.expense, balance: found.data.income - found.data.expense }
    : monthTotals
  const banks = useBankOverview(month).data?.banks
  const selection = useSelection()
  const remove = useDeleteTransaction()
  const [dialog, setDialog] = useState<{ open: boolean; transaction?: Transaction }>({ open: false })
  const [makeFixed, setMakeFixed] = useState<{ open: boolean; transaction?: Transaction }>({ open: false })
  const [menu, setMenu] = useState<{ open: boolean; item?: Transaction }>({ open: false })
  const [deleting, setDeleting] = useState<Transaction | null>(null)
  const [moving, setMoving] = useState<{ open: boolean; item?: Transaction }>({ open: false })
  const move = useMoveTransactionMonth()

  const targetId = params.get('lancamento')
  const target = useTransactionsSearch({ from: month, to: month }, !!targetId).data?.find((t) => t.id === targetId)
  const creating = params.get('novo') === '1'

  const items = data?.items ?? []
  const selectedTypes = new Set(items.filter((t) => selection.selected.has(t.id)).map((t) => t.type))
  const pageIds = items.map((t) => t.id)
  const allSelected = pageIds.length > 0 && pageIds.every((id) => selection.selected.has(id))
  const setMonth = (next: YearMonth) => {
    setMonthParam(next)
    list.resetPage()
    selection.clear()
  }
  const dropParams = (...names: string[]) =>
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      names.forEach((name) => next.delete(name))
      return next
    }, { replace: true })
  const search = (value: string) => {
    setText(value)
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
        toolbar={searching ? <p className="text-sm text-muted-foreground">Buscando em todos os meses</p>
          : <MonthPicker value={month} onChange={setMonth} />}
        actions={[
          { label: 'Importar com IA', icon: SparklesIcon, to: '/financeiro/importar' },
          { label: 'Novo lançamento', icon: PlusIcon, primary: true, onSelect: openNew },
        ]}
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <TotalCard label={searching ? 'Ganhos encontrados' : 'Ganhos ocasionais'} value={totals?.income} kind="INCOME" />
        <TotalCard label={searching ? 'Gastos encontrados' : 'Gastos ocasionais'} value={totals?.expense} kind="EXPENSE" />
        <TotalCard label={searching ? 'Saldo encontrado' : 'Saldo ocasional'} value={totals?.balance} kind="BALANCE" />
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
            <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
              <ListSearch value={text} onChange={search} placeholder="Buscar em todos os meses" />
              <BankFilterSelect lines={banks} value={bankValue} onChange={setBank} />
            </div>
          </div>
          {extra && <ExtraFilters query={query} onClear={() => { dropParams('de', 'ate', 'min', 'max'); list.resetPage() }} />}
          {searching && data && (
            <p className="text-sm text-muted-foreground">
              {data.totalItems === 1 ? '1 lançamento encontrado' : `${data.totalItems} lançamentos encontrados`}.
              Marque para mudar categoria, mês ou banco de uma vez.
            </p>
          )}
          <BankAssignBar ids={selection.ids} target="transactions" onClear={selection.clear}>
            <TransactionBatchActions ids={selection.ids} type={selectedTypes.size === 1 ? [...selectedTypes][0] : undefined}
              onDone={selection.clear} />
          </BankAssignBar>

          {isLoading ? (
            <Skeleton className="h-48" />
          ) : !items.length ? (
            <div className="flex flex-col items-center gap-3 py-12 text-center">
              <p className="text-sm text-muted-foreground">
                {searching ? 'Nada encontrado. Tente outra palavra ou tire um filtro.'
                  : `Nenhum lançamento${bankValue ? ' neste banco' : ''} em ${formatMonth(month).toLowerCase()}.`}
              </p>
              {searching ? (
                <Button variant="outline" onClick={() => { search(''); dropParams('busca', 'de', 'ate', 'min', 'max') }}>
                  <XIcon /> Limpar busca
                </Button>
              ) : (
                <Button variant="outline" onClick={openNew}>
                  <PlusIcon /> Registrar lançamento
                </Button>
              )}
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
                      highlight={searching ? text : undefined}
                      details={<>{t.bank && <BankMark bank={t.bank} />}<CategoryBadge categoryId={t.categoryId} /> · {searching ? formatDate(t.date) : formatDate(t.date).slice(0, 5)}
                        {t.checked && <CheckCircle2Icon className="size-3 shrink-0 text-income" aria-label="Conferido" />}</>}
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
                            <p className="flex items-center gap-1.5">
                              <span className="truncate font-medium">{searching ? <Highlight text={t.description} query={text} /> : t.description}</span>
                              {t.checked && <CheckCircle2Icon className="size-3.5 shrink-0 text-income" aria-label="Conferido com a fatura ou o extrato" />}
                            </p>
                            {t.notes && <p className="truncate text-xs text-muted-foreground">{searching ? <Highlight text={t.notes} query={text} /> : t.notes}</p>}
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
        open={dialog.open || creating || !!target}
        onOpenChange={(open) => {
          setDialog((d) => ({ ...d, open }))
          if (!open && (creating || targetId)) dropParams('novo', 'lancamento')
        }}
        transaction={dialog.open ? dialog.transaction : target}
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

/** Período e valor vindos da busca geral, com botão para tirar. */
function ExtraFilters({ query, onClear }: { query: TransactionQuery; onClear: () => void }) {
  const { formatCurrency } = useMoney()
  const value = query.min !== undefined && query.min === query.max ? `valor ${formatCurrency(query.min)}`
    : [query.min !== undefined && `a partir de ${formatCurrency(query.min)}`, query.max !== undefined && `até ${formatCurrency(query.max)}`]
      .filter(Boolean).join(' e ')
  const parts = [query.from && `desde ${formatDate(query.from)}`, query.to && `até ${formatDate(query.to)}`, value].filter(Boolean)
  return (
    <div className="flex flex-wrap items-center gap-2 text-sm">
      <span className="rounded-full bg-muted px-3 py-1">{parts.join(', ')}</span>
      <Button size="sm" variant="ghost" onClick={onClear}><XIcon /> Tirar filtros</Button>
    </div>
  )
}

function typeParam(value: string | null): Filter {
  return value === 'INCOME' || value === 'EXPENSE' ? value : 'ALL'
}

function numberParam(value: string | null) {
  if (value === null || value === '') return undefined
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}
