import { Loader2Icon, PackagePlusIcon, SearchIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { normalizeName } from '../categories'
import { ApiError } from '@/lib/api'
import { formatDate, type YearMonth } from '@/lib/format'
import { useRecurringEntries, useTransactionsSearch } from '../api'
import { CategoryBadge } from '../components/CategoryBadge'
import { Money } from '../components/Money'
import { MonthPicker } from '../components/MonthPicker'
import type { EntryType } from '../types'
import { useGroupOrigins, useShareItems } from './api'
import { ShareModeRadio } from './GroupShareSection'
import type { Origin, ShareMode } from './types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupId: string
  groupName: string
  /** Mês inicial dos ocasionais */
  month: YearMonth
}

type Row = { id: string; description: string; type: EntryType; categoryId: string; amount: number; detail: string }

type ListProps = {
  rows: Row[] | undefined
  loading: boolean
  selected: Set<string>
  origins: Map<string, Origin>
  mode: ShareMode
  onToggle: (ids: string[], checked: boolean) => void
  empty: string
}

/** Checklist dos meus itens; já sincronizados ficam bloqueados no modo sincronizado. */
function ItemChecklist({ rows, loading, selected, origins, mode, onToggle, empty }: ListProps) {
  const [filter, setFilter] = useState('')
  if (loading) return <Skeleton className="h-40" />
  if (!rows?.length) return <p className="py-8 text-center text-sm text-muted-foreground">{empty}</p>

  const term = normalizeName(filter)
  const visible = rows.filter((r) => !term || normalizeName(r.description).includes(term))
  const blocked = (r: Row) => mode === 'SYNC' && origins.get(r.id)?.synced === true
  const selectable = visible.filter((r) => !blocked(r))
  const allChecked = selectable.length > 0 && selectable.every((r) => selected.has(r.id))

  return (
    <div className="space-y-2">
      <div className="relative">
        <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Filtrar pelo nome" className="pl-8" aria-label="Filtrar itens" />
      </div>
      <Label className="flex items-center gap-3 border-b px-1 pb-2 text-sm font-normal">
        <Checkbox checked={allChecked} disabled={!selectable.length}
          onCheckedChange={(v) => onToggle(selectable.map((r) => r.id), v === true)} />
        Selecionar todos ({selectable.length})
      </Label>
      <ul className="max-h-[45dvh] divide-y overflow-y-auto">
        {visible.map((r) => {
          const origin = origins.get(r.id)
          const disabled = blocked(r)
          return (
            <li key={r.id}>
              <Label className={`flex items-center gap-3 px-1 py-2 font-normal ${disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'}`}>
                <Checkbox checked={selected.has(r.id)} disabled={disabled} onCheckedChange={(v) => onToggle([r.id], v === true)} />
                <span className="min-w-0 flex-1 space-y-0.5">
                  <span className="flex items-center gap-2">
                    <span className="truncate text-sm font-medium">{r.description}</span>
                    {origin && <Badge variant="outline" className="font-normal">{origin.synced ? 'No grupo' : 'Copiado'}</Badge>}
                  </span>
                  <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <CategoryBadge categoryId={r.categoryId} /> · {r.detail}
                  </span>
                </span>
                <Money value={r.amount} type={r.type} className="shrink-0 text-sm" />
              </Label>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** Seletor para levar meus fixos e ocasionais para o grupo, sincronizados ou como cópia. */
export function BringItemsDialog(props: Props) {
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogScrollContent className="sm:max-w-xl">
        <BringItems {...props} />
      </DialogScrollContent>
    </Dialog>
  )
}

/** Montado só com o dialog aberto: seleção zerada a cada abertura. */
function BringItems({ onOpenChange, groupId, groupName, month: initialMonth }: Props) {
  const [tab, setTab] = useState<'recurring' | 'transactions'>('recurring')
  const [month, setMonth] = useState(initialMonth)
  const [mode, setMode] = useState<ShareMode>('SYNC')
  const [recurringIds, setRecurringIds] = useState<Set<string>>(new Set())
  const [transactionIds, setTransactionIds] = useState<Set<string>>(new Set())
  const share = useShareItems()
  const origins = useGroupOrigins(groupId, true)
  const recurring = useRecurringEntries()
  const transactions = useTransactionsSearch({ from: month, to: month }, true)

  const originMap = (list: Origin[] | undefined) => {
    const map = new Map<string, Origin>()
    for (const o of list ?? []) if (!map.get(o.originId)?.synced) map.set(o.originId, o)
    return map
  }
  const recurringOrigins = originMap(origins.data?.recurring)
  const transactionOrigins = originMap(origins.data?.transactions)

  // No modo sincronizado, o que já está sincronizado sai da seleção.
  const effective = (ids: Set<string>, map: Map<string, Origin>) =>
    [...ids].filter((id) => !(mode === 'SYNC' && map.get(id)?.synced))
  const chosenRecurring = effective(recurringIds, recurringOrigins)
  const chosenTransactions = effective(transactionIds, transactionOrigins)
  const total = chosenRecurring.length + chosenTransactions.length

  const toggle = (setter: typeof setRecurringIds) => (ids: string[], checked: boolean) =>
    setter((current) => {
      const next = new Set(current)
      for (const id of ids) {
        if (checked) next.add(id)
        else next.delete(id)
      }
      return next
    })

  async function submit() {
    try {
      const result = await share.mutateAsync({
        groupId,
        data: { recurringIds: chosenRecurring, transactionIds: chosenTransactions, mode },
      })
      toast.success(`${result.added} item(ns) levado(s) para ${groupName}${result.skipped ? ` · ${result.skipped} já estavam lá` : ''}.`)
      onOpenChange(false)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível levar os itens.')
    }
  }

  const recurringRows: Row[] | undefined = recurring.data?.map((r) => ({
    id: r.id, description: r.description, type: r.type, categoryId: r.categoryId, amount: r.amount,
    detail: `${r.dayOfMonth ? `dia ${r.dayOfMonth}` : 'mensal'}${r.active ? '' : ' · pausado'}`,
  }))
  const transactionRows: Row[] | undefined = transactions.data?.map((t) => ({
    id: t.id, description: t.description, type: t.type, categoryId: t.categoryId, amount: t.amount,
    detail: formatDate(t.date),
  }))

  return (
    <>
      <DialogHeader>
        <DialogTitle>Levar meus itens para {groupName}</DialogTitle>
        <DialogDescription>Escolha os fixos e ocasionais que entram nas contas do grupo.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <ShareModeRadio value={mode} onChange={setMode} idPrefix="bring-mode" />
        <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)}>
          <TabsList className="w-full">
            <TabsTrigger value="recurring">Fixos{recurringIds.size ? ` (${chosenRecurring.length})` : ''}</TabsTrigger>
            <TabsTrigger value="transactions">Ocasionais{transactionIds.size ? ` (${chosenTransactions.length})` : ''}</TabsTrigger>
          </TabsList>
          <TabsContent value="recurring" className="pt-3">
            <ItemChecklist rows={recurringRows} loading={recurring.isLoading} selected={recurringIds}
              origins={recurringOrigins} mode={mode} onToggle={toggle(setRecurringIds)} empty="Você não tem fixos cadastrados." />
          </TabsContent>
          <TabsContent value="transactions" className="space-y-3 pt-3">
            <MonthPicker value={month} onChange={setMonth} />
            <ItemChecklist rows={transactionRows} loading={transactions.isLoading} selected={transactionIds}
              origins={transactionOrigins} mode={mode} onToggle={toggle(setTransactionIds)} empty="Nenhum ocasional neste mês." />
          </TabsContent>
        </Tabs>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
        <Button onClick={submit} disabled={!total || share.isPending}>
          {share.isPending ? <Loader2Icon className="animate-spin" /> : <PackagePlusIcon />}
          Levar {total || ''} {total === 1 ? 'item' : 'itens'}
        </Button>
      </DialogFooter>
    </>
  )
}
