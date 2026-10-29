import {
  CheckCircle2Icon,
  ChevronDownIcon,
  CircleCheckBigIcon,
  FlaskConicalIcon,
  PencilIcon,
  PlusIcon,
  SlidersHorizontalIcon,
  Trash2Icon,
  Undo2Icon,
  XIcon,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { ActionSheet } from '@/components/ActionSheet'
import { SortableItems } from '@/components/SortableItems'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Money } from '@/features/finance/components/Money'
import { ApiError } from '@/lib/api'
import { currentYearMonth, formatCurrency, formatDate, formatMonth, parseAmount } from '@/lib/format'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { cn } from '@/lib/utils'
import {
  useDeletePlanItem,
  useReorderItems,
  useSaveScenario,
  useScenarios,
  useSetItemActive,
  useSimulation,
  useUnacquireItem,
} from '../api'
import type { PlanBase, PlanItem, Simulation, WhatIf } from '../types'
import { AcquireDialog } from './AcquireDialog'
import { BaseSettingsDialog } from './BaseSettingsDialog'
import { ItemThumb } from './ItemThumb'
import { LinkChip } from './LinkChip'
import { PlanItemDialog } from './PlanItemDialog'
import { PlanItemRow, type RowAction } from './PlanItemRow'
import { PlanTimelineChart } from './PlanTimelineChart'
import { ScenariosCard } from './ScenariosCard'

type Dialog =
  | { kind: 'item'; item?: PlanItem }
  | { kind: 'acquire'; item: PlanItem }
  | { kind: 'delete'; item: PlanItem }
  | { kind: 'settings' }
  | null

const toNumber = (raw: string) => {
  const value = parseAmount(raw)
  return Number.isNaN(value) ? 0 : value
}

/** Planejamento do grupo: itens com prazo, simulação mês a mês, cenários e "e se". */
export function PlanningTab({ groupId }: { groupId: string }) {
  const [viewing, setViewing] = useState<string | null>(null)
  const [extraIncome, setExtraIncome] = useState('')
  const [extraExpense, setExtraExpense] = useState('')
  const debouncedIncome = useDebouncedValue(toNumber(extraIncome), 400)
  const debouncedExpense = useDebouncedValue(toNumber(extraExpense), 400)
  const whatIf = useMemo<WhatIf>(() => ({ extraIncome: debouncedIncome, extraExpense: debouncedExpense }), [debouncedIncome, debouncedExpense])

  const simulation = useSimulation(groupId, viewing, whatIf)
  const scenarios = useScenarios(groupId)
  const setActive = useSetItemActive()
  const saveScenario = useSaveScenario()
  const reorder = useReorderItems()
  const remove = useDeletePlanItem()
  const unacquire = useUnacquireItem()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [menu, setMenu] = useState<{ open: boolean; item?: PlanItem }>({ open: false })
  const data = simulation.data
  // Ordem arrastada, válida até chegar a simulação nova
  const [order, setOrder] = useState<{ ids: string[]; basis: Simulation } | null>(null)
  const items = !data ? [] : order?.basis !== data ? data.items
    : [...data.items].sort((a, b) => order.ids.indexOf(a.item.id) - order.ids.indexOf(b.item.id))
  const viewingScenario = viewing ? scenarios.data?.find((s) => s.id === viewing) : undefined

  async function run(action: () => Promise<unknown>, success?: string) {
    try {
      await action()
      if (success) toast.success(success)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível concluir.')
    }
  }

  function toggle(item: PlanItem, considered: boolean) {
    if (!viewingScenario) {
      void run(() => setActive.mutateAsync({ groupId, id: item.id, active: considered }))
      return
    }
    const ids = new Set(viewingScenario.itemIds)
    if (considered) ids.add(item.id)
    else ids.delete(item.id)
    void run(() => saveScenario.mutateAsync({ groupId, id: viewingScenario.id, name: viewingScenario.name, itemIds: [...ids] }))
  }

  /** Mostra a nova ordem na hora; a situação de cada item chega com a simulação refeita. */
  async function reorderTo(ids: string[]) {
    if (!data) return
    setOrder({ ids, basis: data })
    try {
      await reorder.mutateAsync({ groupId, itemIds: ids })
    } catch (error) {
      setOrder(null)
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível mudar a ordem.')
    }
  }

  function move(index: number, direction: -1 | 1) {
    const ids = items.map((r) => r.item.id)
    const target = index + direction
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    void reorderTo(ids)
  }

  function actionsOf(item: PlanItem): RowAction[] {
    if (!item.canEdit) return []
    return [
      { label: 'Editar', icon: PencilIcon, onSelect: () => setDialog({ kind: 'item', item }) },
      {
        label: item.kind === 'INVESTMENT' ? 'Marcar como feito' : 'Marcar como comprado',
        icon: CircleCheckBigIcon,
        onSelect: () => setDialog({ kind: 'acquire', item }),
      },
      { label: 'Excluir', icon: Trash2Icon, destructive: true, onSelect: () => setDialog({ kind: 'delete', item }) },
    ]
  }

  if (!data) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-28 rounded-xl" />)}
        </div>
        <Skeleton className="h-72 rounded-xl" />
      </div>
    )
  }

  const empty = !data.items.length && !data.acquired.length
  const names = new Map(data.items.map((r) => [r.item.id, r.item.name]))
  const busy = setActive.isPending || saveScenario.isPending || reorder.isPending
  const simulating = whatIf.extraIncome > 0 || whatIf.extraExpense > 0

  return (
    <div className="space-y-4">
      {viewing && (
        <div className="flex flex-col gap-3 rounded-xl border border-primary/40 bg-primary/5 p-3 text-sm sm:flex-row sm:items-center">
          <p className="flex-1">
            Vendo o cenário <strong>{viewingScenario?.name ?? '…'}</strong>. As chaves ligam e desligam itens só neste cenário.
          </p>
          <Button variant="outline" size="sm" onClick={() => setViewing(null)}><XIcon /> Voltar aos ativos</Button>
        </div>
      )}

      <SummaryCards data={data} onAdjust={() => setDialog({ kind: 'settings' })} simulating={simulating} />

      <WhatIfPanel income={extraIncome} expense={extraExpense} onIncome={setExtraIncome} onExpense={setExtraExpense}
        active={simulating} onClear={() => {
          setExtraIncome('')
          setExtraExpense('')
        }} />

      {empty ? (
        <Card className="border-dashed">
          <CardHeader>
            <CardTitle>Planeje o que o grupo quer conquistar</CardTitle>
            <CardDescription>
              Cadastre compras e investimentos com prazo. A simulação usa o saldo previsto do grupo mês a mês e mostra o
              que cabe, quanto falta e a partir de quando dá. Cole o link da loja para trazer nome, preço e foto.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={() => setDialog({ kind: 'item' })}><PlusIcon /> Adicionar item</Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Linha do tempo</CardTitle>
              <CardDescription>
                Saldo acumulado até {formatMonth(data.timeline[data.timeline.length - 1].month, 'short')}, com e sem os planos.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <PlanTimelineChart points={data.timeline} names={names} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Itens ({data.items.length})</CardTitle>
              <CardDescription>A ordem é a prioridade: o 1º recebe o dinheiro primeiro.</CardDescription>
              <CardAction>
                <Button size="sm" onClick={() => setDialog({ kind: 'item' })}><PlusIcon /> Novo item</Button>
              </CardAction>
            </CardHeader>
            <CardContent>
              {!data.items.length ? (
                <p className="py-6 text-center text-sm text-muted-foreground">Tudo o que estava planejado já foi adquirido.</p>
              ) : (
                <SortableItems ids={items.map((r) => r.item.id)} label={(id) => names.get(id) ?? ''}
                  onReorder={(ids) => void reorderTo(ids)}>
                  <ul className="-my-4 divide-y">
                    {items.map((result, index) => (
                      <PlanItemRow key={result.item.id} result={result} index={index} count={items.length} busy={busy}
                        onToggle={(considered) => toggle(result.item, considered)}
                        onMove={(direction) => move(index, direction)}
                        actions={actionsOf(result.item)}
                        onMore={() => setMenu({ open: true, item: result.item })} />
                    ))}
                  </ul>
                </SortableItems>
              )}
            </CardContent>
          </Card>

          <ScenariosCard groupId={groupId} whatIf={whatIf} viewing={viewing} onView={setViewing} />

          {data.acquired.length > 0 && (
            <AcquiredList items={data.acquired}
              onUndo={(item) => run(() => unacquire.mutateAsync({ groupId, id: item.id }), 'Voltou para o planejamento.')}
              onDelete={(item) => setDialog({ kind: 'delete', item })} />
          )}
        </>
      )}

      <PlanItemDialog open={dialog?.kind === 'item'} onOpenChange={(open) => !open && setDialog(null)} groupId={groupId}
        item={dialog?.kind === 'item' ? dialog.item : undefined} />
      <AcquireDialog open={dialog?.kind === 'acquire'} onOpenChange={(open) => !open && setDialog(null)} groupId={groupId}
        item={dialog?.kind === 'acquire' ? dialog.item : undefined} />
      <BaseSettingsDialog open={dialog?.kind === 'settings'} onOpenChange={(open) => !open && setDialog(null)} groupId={groupId}
        settings={data.settings} base={data.base} />
      <ConfirmDelete
        open={dialog?.kind === 'delete'}
        onOpenChange={(open) => !open && setDialog(null)}
        title={`Excluir "${dialog?.kind === 'delete' ? dialog.item.name : ''}"?`}
        description="O item sai do planejamento e dos cenários. Lançamentos já feitos no grupo continuam."
        onConfirm={() => {
          if (dialog?.kind !== 'delete') return
          const item = dialog.item
          void run(() => remove.mutateAsync({ groupId, id: item.id }), 'Item excluído.')
        }}
      />
      <ActionSheet
        open={menu.open}
        onOpenChange={(open) => setMenu((m) => ({ ...m, open }))}
        title={menu.item?.name ?? ''}
        actions={menu.item ? actionsOf(menu.item) : []}
      />
    </div>
  )
}

/** De onde vem o saldo do mês: fixos e quais meses entraram na média dos ocasionais. */
function averageSource({ averageMonths, averageBasis }: PlanBase) {
  if (!averageMonths) return 'Só os fixos do grupo'
  if (!averageBasis.length) return `Só os fixos (sem ocasionais nos últimos ${averageMonths} meses)`
  const months = averageBasis.map((m) => formatMonth(m, 'short')).join(', ')
  if (averageBasis.length === 1 && averageBasis[0] === currentYearMonth()) return `Fixos + ocasionais do mês atual (${months})`
  return `Fixos + média dos ocasionais de ${averageBasis.length === 1 ? months : `${averageBasis.length} meses (${months})`}`
}

/** Base, reserva, total planejado e situação geral. */
function SummaryCards({ data, onAdjust, simulating }: { data: Simulation; onAdjust: () => void; simulating: boolean }) {
  const { base, outcome, settings } = data
  const source = base.manualBalance !== null ? 'Valor definido pelo grupo' : averageSource(base)
  const fits = outcome.shortfall === 0

  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
      <Card size="sm" className="bg-data/10 ring-data/20">
        <CardContent className="space-y-1">
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-semibold text-data">Saldo previsto por mês</p>
            {simulating && <Badge variant="secondary" className="gap-1"><FlaskConicalIcon className="size-3" /> E se</Badge>}
          </div>
          <p className="text-lg font-semibold text-data sm:text-xl"><Money value={base.monthly} signed /></p>
          <p className="text-xs text-muted-foreground">{source}</p>
          <Button variant="link" size="xs" className="h-auto px-0" onClick={onAdjust}>
            <SlidersHorizontalIcon /> Ajustar base
          </Button>
        </CardContent>
      </Card>
      <Card size="sm" className="bg-data/10 ring-data/20">
        <CardContent className="space-y-1">
          <p className="text-sm font-semibold text-data">Reserva hoje</p>
          <p className="text-lg font-semibold text-data sm:text-xl"><Money value={settings.totalReserve} /></p>
          <p className="text-xs text-muted-foreground">
            {settings.invested > 0
              ? `${formatCurrency(settings.reserve)} informados + ${formatCurrency(settings.invested)} investidos no grupo.`
              : 'Quanto o grupo já tem guardado.'}
          </p>
          <Button variant="link" size="xs" className="h-auto px-0" onClick={onAdjust}>
            <PencilIcon /> Alterar
          </Button>
        </CardContent>
      </Card>
      <Card size="sm" className="bg-data/10 ring-data/20">
        <CardContent className="space-y-1">
          <p className="text-sm font-semibold text-data">Planejado</p>
          <p className="text-lg font-semibold text-data sm:text-xl"><Money value={outcome.total} /></p>
          <p className="text-xs text-muted-foreground">
            {outcome.count} {outcome.count === 1 ? 'item considerado' : 'itens considerados'}
          </p>
        </CardContent>
      </Card>
      <Card size="sm" className={fits ? 'bg-income/10 ring-income/20' : 'bg-expense/10 ring-expense/20'}>
        <CardContent className="space-y-1">
          <p className={cn('text-sm font-semibold', fits ? 'text-income' : 'text-expense')}>Situação</p>
          {fits ? (
            <p className="flex items-center gap-1.5 text-lg font-semibold sm:text-xl">
              <CheckCircle2Icon className="size-5 text-income" aria-hidden /> Tudo cabe
            </p>
          ) : (
            <p className="text-lg font-semibold text-expense tabular-nums sm:text-xl">Faltam {formatCurrency(outcome.shortfall)}</p>
          )}
          <p className="text-xs text-muted-foreground">
            {fits
              ? `Sobram ${formatCurrency(outcome.endBalance)} em ${formatMonth(outcome.endMonth, 'short')}`
              : `Ponto mais baixo em ${formatMonth(outcome.lowestMonth, 'short')}`}
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

/** Simulação "e se" (só na tela, não salva). */
function WhatIfPanel({ income, expense, onIncome, onExpense, active, onClear }: {
  income: string
  expense: string
  onIncome: (v: string) => void
  onExpense: (v: string) => void
  active: boolean
  onClear: () => void
}) {
  const [open, setOpen] = useState(false)
  return (
    <Collapsible open={open || active} onOpenChange={setOpen} className="rounded-xl border">
      <div className="flex items-center gap-2 px-3 py-2">
        <CollapsibleTrigger asChild>
          <button type="button" className="flex flex-1 items-center gap-2 rounded-md text-left text-sm font-medium focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">
            <FlaskConicalIcon className="size-4 text-muted-foreground" />
            E se...?
            <span className="font-normal text-muted-foreground max-sm:hidden">Teste uma renda ou um gasto a mais por mês</span>
            <ChevronDownIcon className={cn('ml-auto size-4 text-muted-foreground transition-transform motion-reduce:transition-none', (open || active) && 'rotate-180')} />
          </button>
        </CollapsibleTrigger>
        {active && <Button variant="ghost" size="xs" onClick={onClear}><XIcon /> Limpar</Button>}
      </div>
      <CollapsibleContent>
        <div className="grid gap-4 border-t p-3 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="whatif-income">Ganho extra por mês (R$)</FieldLabel>
            <Input id="whatif-income" inputMode="decimal" placeholder="0,00" value={income} onChange={(e) => onIncome(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="whatif-expense">Gasto extra por mês (R$)</FieldLabel>
            <Input id="whatif-expense" inputMode="decimal" placeholder="0,00" value={expense} onChange={(e) => onExpense(e.target.value)} />
          </Field>
          <p className="text-xs text-muted-foreground sm:col-span-2">Só muda a simulação nesta tela; nada é salvo.</p>
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}

/** Itens já comprados/feitos. */
function AcquiredList({ items, onUndo, onDelete }: { items: PlanItem[]; onUndo: (item: PlanItem) => void; onDelete: (item: PlanItem) => void }) {
  return (
    <Collapsible className="rounded-xl border">
      <CollapsibleTrigger asChild>
        <button type="button" className="group flex w-full items-center gap-2 px-4 py-3 text-left text-sm font-medium focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">
          <CircleCheckBigIcon className="size-4 text-muted-foreground" />
          Adquiridos ({items.length})
          <ChevronDownIcon className="ml-auto size-4 text-muted-foreground transition-transform group-data-[state=open]:rotate-180 motion-reduce:transition-none" />
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <ul className="divide-y border-t px-4">
          {items.map((item) => (
            <li key={item.id} className="flex items-center gap-3 py-3">
              <ItemThumb src={item.imageUrl} kind={item.kind} className="size-10" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{item.name}</p>
                <p className="text-xs text-muted-foreground">
                  {formatDate(item.acquiredOn!)} · {formatCurrency(item.acquiredAmount ?? item.amount)}
                </p>
                {item.url && <LinkChip url={item.url} site={item.linkSite} />}
              </div>
              {item.canEdit && (
                <div className="flex shrink-0 gap-1">
                  <Button variant="ghost" size="icon-sm" aria-label={`Voltar ${item.name} para o planejamento`} title="Voltar para o planejamento"
                    onClick={() => onUndo(item)}>
                    <Undo2Icon />
                  </Button>
                  <Button variant="ghost" size="icon-sm" aria-label={`Excluir ${item.name}`} onClick={() => onDelete(item)}>
                    <Trash2Icon />
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      </CollapsibleContent>
    </Collapsible>
  )
}
