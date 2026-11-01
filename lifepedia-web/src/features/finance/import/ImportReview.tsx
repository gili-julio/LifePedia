import { AlertTriangleIcon, CheckIcon, Loader2Icon, Trash2Icon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { BankSelect } from '@/components/bank/BankSelect'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { DatePicker } from '@/components/DatePicker'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ApiError } from '@/lib/api'
import { OTHER_BANK } from '@/lib/banks'
import { currentYearMonth, parseAmount } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { useDiscardDraft, useImport } from '../api'
import { CategorySelect } from '../components/CategorySelect'
import type { EntryType, ImportDraft, ImportRequest, RecurringEntry } from '../types'
import { useCategoryLookup } from '../use-category-lookup'
import { FixedOutcomeNote } from './FixedOutcomeNote'
import { FixedSelect } from './FixedSelect'
import { SOURCE_LABEL, type ImportSource } from './format'
import { CountsIn, ImportRowItem } from './ImportRowItem'
import { ImportRowSheet } from './ImportRowSheet'
import { OriginalDescription } from './OriginalDescription'
import { fixedOutcome, monthsOf, rowBank, rowErrors, rowMonth, withBank, withSource, type ImportRow } from './parse'
import { Step } from './Step'
import { useDraftAutosave, type DraftSaveState } from './use-draft-autosave'

const SAVE_LABEL: Record<DraftSaveState, string> = {
  saved: 'Rascunho salvo. Pode sair e voltar depois.',
  saving: 'Salvando…',
  error: 'Não consegui salvar o rascunho. Tentamos de novo na próxima edição.',
}

type Props = {
  draft: ImportDraft
  initialRows: ImportRow[]
  fixedEntries: RecurringEntry[]
}

/** Revisão da importação. Cada edição vai para o rascunho na API: dá para sair e voltar sem perder nada. */
export function ImportReview({ draft, initialRows, fixedEntries }: Props) {
  const { formatCurrency } = useMoney()
  const navigate = useNavigate()
  const { byId } = useCategoryLookup()
  const fixedById = useMemo(() => new Map(fixedEntries.map((f) => [f.id, f])), [fixedEntries])
  const importMutation = useImport()
  const discardDraft = useDiscardDraft()
  const [rows, setRows] = useState(initialRows)
  const [editor, setEditor] = useState<{ open: boolean; key?: string; seq: number }>({ open: false, seq: 0 })
  const [batchBank, setBatchBank] = useState('')
  const [confirmDiscard, setConfirmDiscard] = useState(false)
  const autosave = useDraftAutosave(draft.id, rows)

  function updateRow(key: string, patch: Partial<ImportRow>) {
    setRows((current) => current.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  }

  const editingRow = rows.find((r) => r.key === editor.key)
  const selected = rows.filter((r) => r.include)
  const errorsOf = (row: ImportRow) => (row.include ? rowErrors(row, byId, fixedById) : [])
  const invalidCount = selected.filter((r) => errorsOf(r).length > 0).length
  const plan = buildPlan(selected, fixedById)
  const hasWork = plan.request.transactions.length + plan.request.fixedAdjustments.length + plan.request.fixedBanks.length > 0

  /** Mesmo banco em todas as linhas selecionadas. */
  function applyBank() {
    setRows((current) => current.map((r) => (r.include ? { ...r, ...withBank(r, batchBank) } : r)))
  }

  async function save() {
    try {
      await autosave.flush()
      const saved = await importMutation.mutateAsync({ ...plan.request, draftId: draft.id })
      const banks = plan.request.fixedBanks.length
      toast.success(`${saved.transactions} lançamento(s) importado(s) · ${saved.fixedAdjustments} fixo(s) atualizado(s)${banks ? ` · ${banks} fixo(s) com banco definido` : ''}.`)
      const month = monthsOf(selected).sort().at(-1) ?? currentYearMonth()
      navigate(month === currentYearMonth() ? '/financeiro/lancamentos' : `/financeiro/lancamentos?mes=${month}`)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível importar.')
    }
  }

  async function discard() {
    try {
      await autosave.flush()
      await discardDraft.mutateAsync(draft.id)
      toast.success('Revisão descartada.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível descartar.')
    }
  }

  return (
    <>
      <Step n={draft.origin === 'SITE' ? 2 : 3} title="Revise e importe" description={`Itens de fatura contam no mês da fatura em que vieram (sem ele, no mês da compra); itens de extrato, no mês da data. A descrição pode ser ajustada; a do documento fica guardada como veio. Itens de fixos não viram lançamento: atualizam o valor real do fixo no mês. Nada é lançado antes de confirmar.`}>
        <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
          <p className={cn('flex items-center gap-1.5', autosave.state === 'error' && 'text-destructive')} aria-live="polite">
            {autosave.state === 'saving' ? <Loader2Icon className="size-4 animate-spin" /> : autosave.state === 'saved' ? <CheckIcon className="size-4" /> : <AlertTriangleIcon className="size-4" />}
            {SAVE_LABEL[autosave.state]}
          </p>
          <Button size="sm" variant="outline" onClick={() => setConfirmDiscard(true)}><Trash2Icon /> Descartar revisão</Button>
        </div>
        <div className="flex flex-wrap items-center gap-2 rounded-xl bg-muted p-2">
          <span className="px-1 text-sm">Banco das linhas selecionadas:</span>
          <div className="w-full sm:w-52"><BankSelect size="sm" value={batchBank} onChange={setBatchBank} /></div>
          <Button size="sm" variant="outline" disabled={batchBank === OTHER_BANK} onClick={applyBank}>Aplicar</Button>
        </div>
        <div className="md:hidden">
          <label className="flex items-center gap-3 border-b pb-3 text-sm">
            <Checkbox checked={selected.length === rows.length}
              onCheckedChange={(v) => setRows(rows.map((r) => ({ ...r, include: v === true })))} />
            Selecionar todos
          </label>
          <ul className="divide-y">
            {rows.map((row) => (
              <ImportRowItem
                key={row.key}
                row={row}
                errors={errorsOf(row)}
                category={byId.get(row.categoryId)}
                fixed={fixedById.get(row.fixedEntryId)}
                onToggle={(include) => updateRow(row.key, { include })}
                onOpen={() => setEditor((e) => ({ open: true, key: row.key, seq: e.seq + 1 }))}
              />
            ))}
          </ul>
        </div>

        <div className="hidden overflow-x-auto md:block">
          <Table className="min-w-[1040px]">
            <TableHeader>
              <TableRow>
                <TableHead className="w-8">
                  <Checkbox
                    aria-label="Selecionar todos"
                    checked={selected.length === rows.length}
                    onCheckedChange={(v) => setRows(rows.map((r) => ({ ...r, include: v === true })))}
                  />
                </TableHead>
                <TableHead className="w-36">Data</TableHead>
                <TableHead>Descrição</TableHead>
                <TableHead className="w-40">Tipo, origem e banco</TableHead>
                <TableHead className="w-52">Lançar como</TableHead>
                <TableHead className="w-44">Categoria</TableHead>
                <TableHead className="w-28 text-right">Valor (R$)</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const errors = errorsOf(row)
                const fixed = fixedById.get(row.fixedEntryId)
                return (
                  <TableRow key={row.key} className={cn(!row.include && 'opacity-50')}>
                    <TableCell className="align-top">
                      <Checkbox aria-label="Importar linha" checked={row.include}
                        onCheckedChange={(v) => updateRow(row.key, { include: v === true })} />
                    </TableCell>
                    <TableCell className="align-top">
                      <DatePicker size="sm" value={row.date} invalid={errors.includes('data')}
                        onChange={(date) => updateRow(row.key, { date })} />
                      <CountsIn row={row} className="mt-1" />
                    </TableCell>
                    <TableCell className="align-top">
                      <Input className="h-7" value={row.description} maxLength={120} aria-invalid={errors.includes('descrição')}
                        onChange={(e) => updateRow(row.key, { description: e.target.value })} />
                      <OriginalDescription className="mt-1" original={row.originalDescription} description={row.description} />
                      {row.warnings.map((w) => (
                        <p key={w} className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                          <AlertTriangleIcon className="size-3 shrink-0" /> {w}
                        </p>
                      ))}
                      {fixed && row.include && <div className="mt-1"><FixedOutcomeNote row={row} fixed={fixed} /></div>}
                    </TableCell>
                    <TableCell className="align-top">
                      <Select value={row.type}
                        onValueChange={(v) => {
                          const type = v as EntryType
                          const keep = byId.get(row.categoryId)?.type === type
                          updateRow(row.key, {
                            type,
                            categoryId: keep ? row.categoryId : '',
                            fixedEntryId: fixed?.type === type ? row.fixedEntryId : '',
                          })
                        }}>
                        <SelectTrigger size="sm" className="w-full" aria-label="Tipo"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value="EXPENSE">Despesa</SelectItem>
                          <SelectItem value="INCOME">Ganho</SelectItem>
                        </SelectContent>
                      </Select>
                      <Select value={row.source} onValueChange={(v) => updateRow(row.key, withSource(row, v as ImportSource))}>
                        <SelectTrigger size="sm" className="mt-1 w-full" aria-label="Origem"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          {(Object.keys(SOURCE_LABEL) as ImportSource[]).map((key) => (
                            <SelectItem key={key} value={key}>{SOURCE_LABEL[key]}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <div className="mt-1">
                        <BankSelect size="sm" value={row.bankCode}
                          onChange={(code) => updateRow(row.key, withBank(row, code, row.bankName))} />
                      </div>
                      {row.bankCode === OTHER_BANK && (
                        <Input className="mt-1 h-7" aria-label="Nome do banco" placeholder="Nome do banco" maxLength={60}
                          value={row.bankName} onChange={(e) => updateRow(row.key, { bankName: e.target.value })} />
                      )}
                    </TableCell>
                    <TableCell className="align-top">
                      <FixedSelect size="sm" type={row.type} month={rowMonth(row)} value={row.fixedEntryId}
                        fixedEntries={fixedEntries} invalid={errors.includes('fixo')}
                        onChange={(fixedEntryId) => updateRow(row.key, { fixedEntryId })} />
                    </TableCell>
                    <TableCell className="align-top">
                      {fixed ? (
                        <span className="text-xs text-muted-foreground">A do fixo</span>
                      ) : (
                        <CategorySelect size="sm" type={row.type} value={row.categoryId} invalid={errors.includes('categoria')}
                          onChange={(categoryId) => updateRow(row.key, { categoryId })} />
                      )}
                    </TableCell>
                    <TableCell className="align-top">
                      <Input className="h-7 text-right" inputMode="decimal" value={row.amount} aria-invalid={errors.includes('valor')}
                        onChange={(e) => updateRow(row.key, { amount: e.target.value })} />
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>

        <div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="text-sm">
            <p>
              <span className="font-medium">{plan.request.transactions.length}</span> lançamento(s) novo(s)
              (despesas <span className="tabular-nums">{formatCurrency(plan.expense)}</span>, ganhos{' '}
              <span className="tabular-nums">{formatCurrency(plan.income)}</span>) ·{' '}
              <span className="font-medium">{plan.request.fixedAdjustments.length}</span> fixo(s) com valor real ·{' '}
              {plan.covered} já coberto(s) por fixos
            </p>
            {invalidCount > 0 && (
              <p className="text-destructive">{invalidCount} linha(s) com campos a corrigir.</p>
            )}
          </div>
          <Button className="w-full sm:w-auto" onClick={save} disabled={!hasWork || invalidCount > 0 || importMutation.isPending}>
            {importMutation.isPending && <Loader2Icon className="animate-spin" />}
            Importar
          </Button>
        </div>
      </Step>
    {editingRow && (
      <ImportRowSheet
        key={editor.seq}
        open={editor.open}
        onOpenChange={(open) => setEditor((e) => ({ ...e, open }))}
        row={editingRow}
        categoriesById={byId}
        fixedEntries={fixedEntries}
        fixedById={fixedById}
        onSave={(row) => updateRow(row.key, row)}
      />
    )}
      <ConfirmDelete
        open={confirmDiscard}
        onOpenChange={setConfirmDiscard}
        title="Descartar esta revisão?"
        description={`A revisão, com ${rows.length} item(ns) e suas edições, será apagada e nada será importado.${draft.origin === 'SITE' ? ' O envio à IA continua contando nos limites.' : ''}`}
        confirmLabel="Descartar"
        onConfirm={discard}
      />
    </>
  )
}

/** Separa as linhas selecionadas em ocasionais novos, ajustes de fixos e itens já cobertos. */
function buildPlan(selected: ImportRow[], fixedById: Map<string, RecurringEntry>) {
  const request: ImportRequest = { transactions: [], fixedAdjustments: [], fixedBanks: [] }
  const adjustments = new Map<string, ImportRequest['fixedAdjustments'][number]>()
  const banks = new Map<string, ImportRequest['fixedBanks'][number]>()
  let covered = 0
  let expense = 0
  let income = 0
  for (const row of selected) {
    const fixed = fixedById.get(row.fixedEntryId)
    const bank = rowBank(row)
    if (fixed) {
      if (bank && !fixed.bank) banks.set(fixed.id, { recurringEntryId: fixed.id, bank })
      const outcome = fixedOutcome(row, fixed)
      if (outcome.changes) {
        adjustments.set(`${fixed.id}|${outcome.month}`, { recurringEntryId: fixed.id, month: outcome.month, amount: outcome.amount })
      } else covered++
      continue
    }
    const amount = parseAmount(row.amount)
    if (row.type === 'EXPENSE') expense += amount || 0
    else income += amount || 0
    request.transactions.push({
      date: row.date,
      description: row.description.trim(),
      type: row.type,
      categoryId: row.categoryId,
      amount,
      notes: row.notes.trim() || null,
      originalDescription: row.originalDescription || null,
      bank,
      ...(row.month && { month: row.month }),
    })
  }
  request.fixedAdjustments = [...adjustments.values()]
  request.fixedBanks = [...banks.values()]
  return { request, covered, expense, income }
}
