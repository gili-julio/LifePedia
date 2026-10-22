import { useQueryClient } from '@tanstack/react-query'
import { AlertTriangleIcon, CheckIcon, ClipboardCopyIcon, Loader2Icon, ShieldCheckIcon, SparklesIcon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { DatePicker } from '@/components/DatePicker'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { currentYearMonth, formatCurrency, parseAmount } from '@/lib/format'
import { cn } from '@/lib/utils'
import { fetchTransactions, useImport, useRecurringEntries, useTransactionDescriptions } from '../api'
import { CategorySelect } from '../components/CategorySelect'
import { FixedOutcomeNote } from '../import/FixedOutcomeNote'
import { FixedSelect } from '../import/FixedSelect'
import {
  fixedOutcome,
  ImportParseError,
  linkFixed,
  markDuplicates,
  monthsOf,
  parseAiOutput,
  rowErrors,
  rowMonth,
  withSource,
  type ImportRow,
} from '../import/parse'
import { CountsIn, ImportRowItem } from '../import/ImportRowItem'
import { ImportRowSheet } from '../import/ImportRowSheet'
import { OriginalDescription } from '../import/OriginalDescription'
import { buildImportPrompt, IMPORT_FORMAT, SOURCE_LABEL, type ImportSource } from '../import/prompt'
import type { EntryType, ImportRequest, RecurringEntry } from '../types'
import { useCategoryLookup } from '../use-category-lookup'

function Step({ n, title, description, children }: { n: number; title: string; description: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <span className="flex size-6 items-center justify-center rounded-full bg-primary text-xs text-primary-foreground">{n}</span>
          {title}
        </CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">{children}</CardContent>
    </Card>
  )
}

export function ImportPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { list: categories, byId, isLoading: loadingCategories } = useCategoryLookup()
  const { data: fixedData, isLoading: loadingFixed } = useRecurringEntries()
  const fixedEntries = useMemo(() => fixedData ?? [], [fixedData])
  const fixedById = useMemo(() => new Map(fixedEntries.map((f) => [f.id, f])), [fixedEntries])
  const { data: namesData, isLoading: loadingNames } = useTransactionDescriptions()
  const knownNames = useMemo(() => namesData ?? [], [namesData])
  const importMutation = useImport()
  const loading = loadingCategories || loadingFixed || loadingNames

  const [copied, setCopied] = useState(false)
  const [raw, setRaw] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [parseError, setParseError] = useState<string | null>(null)
  const [rows, setRows] = useState<ImportRow[] | null>(null)
  const [editor, setEditor] = useState<{ open: boolean; key?: string; seq: number }>({ open: false, seq: 0 })

  const prompt = useMemo(
    () => buildImportPrompt(categories, fixedEntries, knownNames),
    [categories, fixedEntries, knownNames],
  )

  async function copyPrompt() {
    try {
      await navigator.clipboard.writeText(prompt)
      setCopied(true)
      toast.success('Prompt copiado.')
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error('Não consegui copiar. Selecione o texto e copie manualmente.')
    }
  }

  async function analyze() {
    setParseError(null)
    setAnalyzing(true)
    try {
      const parsed = linkFixed(parseAiOutput(raw, categories, fixedEntries, knownNames), fixedEntries)
      const existing = (await Promise.all(monthsOf(parsed).map((m) => fetchTransactions(queryClient, m)))).flat()
      setRows(markDuplicates(parsed, existing))
    } catch (error) {
      setRows(null)
      setParseError(error instanceof ImportParseError || error instanceof ApiError ? error.message : 'Não foi possível analisar.')
    } finally {
      setAnalyzing(false)
    }
  }

  function updateRow(key: string, patch: Partial<ImportRow>) {
    setRows((current) => current?.map((r) => (r.key === key ? { ...r, ...patch } : r)) ?? null)
  }

  const editingRow = rows?.find((r) => r.key === editor.key)
  const selected = rows?.filter((r) => r.include) ?? []
  const errorsOf = (row: ImportRow) => (row.include ? rowErrors(row, byId, fixedById) : [])
  const invalidCount = selected.filter((r) => errorsOf(r).length > 0).length
  const plan = buildPlan(selected, fixedById)
  const hasWork = plan.request.transactions.length + plan.request.fixedAdjustments.length > 0

  async function save() {
    try {
      const saved = await importMutation.mutateAsync(plan.request)
      toast.success(`${saved.transactions} lançamento(s) importado(s) · ${saved.fixedAdjustments} fixo(s) atualizado(s).`)
      const month = monthsOf(selected).sort().at(-1) ?? currentYearMonth()
      navigate(month === currentYearMonth() ? '/financeiro/lancamentos' : `/financeiro/lancamentos?mes=${month}`)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível importar.')
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Importar com IA"
        description="Use um agente de IA para separar extratos e faturas nas suas categorias e importe tudo de uma vez — um mês ou um período inteiro."
      />

      <Step n={1} title="Copie o prompt" description="Ele já traz suas categorias, seus fixos, os nomes que você usa e o modelo de resposta que o LifePedia entende.">
        <Textarea readOnly value={loading ? 'Carregando…' : prompt}
          className="h-56 font-mono text-xs" onFocus={(e) => e.currentTarget.select()} />
        <div className="flex flex-wrap items-center gap-3">
          <Button onClick={copyPrompt} disabled={loading}>
            {copied ? <CheckIcon /> : <ClipboardCopyIcon />} {copied ? 'Copiado' : 'Copiar prompt'}
          </Button>
          <p className="text-sm text-muted-foreground">
            Cole no seu agente (ChatGPT, Claude, Gemini…) e anexe extratos e/ou faturas (PDF, print ou texto), de um ou
            mais meses. O agente separa a origem de cada item e tira as datas do documento.
          </p>
        </div>
        <p className="flex gap-2 rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">
          <ShieldCheckIcon className="size-4 shrink-0" />
          O documento e o prompt (com suas categorias, fixos e nomes de lançamentos recentes) vão para o serviço de IA que você escolher — o LifePedia
          não envia nada. Oculte números de conta/cartão e dados pessoais e prefira contas com o uso para treino desativado.
        </p>
      </Step>

      <Step n={2} title="Cole a resposta do agente" description="Pode colar a resposta inteira; o LifePedia encontra o bloco JSON.">
        <Textarea value={raw} onChange={(e) => setRaw(e.target.value)} className="h-40 font-mono text-base md:text-xs"
          placeholder={`\`\`\`json\n{ "format": "${IMPORT_FORMAT}", "items": [ ... ] }\n\`\`\``} />
        {parseError && (
          <p className="flex items-center gap-2 text-sm text-destructive">
            <AlertTriangleIcon className="size-4" /> {parseError}
          </p>
        )}
        <Button onClick={analyze} disabled={!raw.trim() || analyzing || loading}>
          {analyzing ? <Loader2Icon className="animate-spin" /> : <SparklesIcon />} Analisar
        </Button>
      </Step>

      {rows && (
        <Step n={3} title="Revise e importe" description={`Itens de fatura contam no mês da fatura em que vieram (sem ele, no mês da compra); itens de extrato, no mês da data. A descrição pode ser ajustada; a do documento fica guardada como veio. Itens de fixos não viram lançamento: atualizam o valor real do fixo no mês. Nada é salvo antes de confirmar.`}>
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
                  <TableHead className="w-32">Tipo e origem</TableHead>
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
      )}
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
    </div>
  )
}

/** Separa as linhas selecionadas em ocasionais novos, ajustes de fixos e itens já cobertos. */
function buildPlan(selected: ImportRow[], fixedById: Map<string, RecurringEntry>) {
  const request: ImportRequest = { transactions: [], fixedAdjustments: [] }
  const adjustments = new Map<string, ImportRequest['fixedAdjustments'][number]>()
  let covered = 0
  let expense = 0
  let income = 0
  for (const row of selected) {
    const fixed = fixedById.get(row.fixedEntryId)
    if (fixed) {
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
      ...(row.month && { month: row.month }),
    })
  }
  request.fixedAdjustments = [...adjustments.values()]
  return { request, covered, expense, income }
}
