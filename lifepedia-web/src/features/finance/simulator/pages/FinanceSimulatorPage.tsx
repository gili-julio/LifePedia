import { BookmarkPlusIcon, CopyPlusIcon, FilePlus2Icon, FolderOpenIcon, Loader2Icon, Trash2Icon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsContent, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { formatMonth, formatRelative } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useCompareSimulations, useDeleteFinanceSimulation, useFinanceSimulation, useSaveFinanceSimulation, useSavedFinanceSimulations } from '../api'
import { CompareChart } from '../components/charts'
import { SimulationResults } from '../components/SimulationResults'
import { SimulatorForm } from '../components/SimulatorForm'
import { EMPTY_FORM, fromInput, HORIZONS, toInput, YIELDS, type SimForm } from '../form'
import type { SavedFinanceSimulation, SimulationInput } from '../types'

type Tab = 'simular' | 'salvas'
type Opened = { id: string; name: string } | null

/** Simulador do financeiro: projeção do saldo com os seus dados, ganhos e gastos livres e investimento. */
export function FinanceSimulatorPage() {
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('aba') === 'salvas' ? 'salvas' : 'simular'
  const [form, setForm] = useState<SimForm>(EMPTY_FORM)
  const [real, setReal] = useState(false)
  const [opened, setOpened] = useState<Opened>(null)
  const [saving, setSaving] = useState<'new' | 'update' | null>(null)
  const saved = useSavedFinanceSimulations()

  const input = toInput(form)
  const debounced = useDebouncedValue(input ? JSON.stringify(input) : '', 400)
  const request = useMemo(() => (debounced ? (JSON.parse(debounced) as SimulationInput) : null), [debounced])
  const simulation = useFinanceSimulation(request, real)
  const data = input ? simulation.data : undefined

  const goTab = (next: Tab) => setParams((p) => { p.set('aba', next); return p }, { replace: true })

  function open(item: SavedFinanceSimulation) {
    setForm(fromInput(item.input))
    setOpened({ id: item.id, name: item.name })
    goTab('simular')
  }

  function reset() {
    setForm(EMPTY_FORM)
    setOpened(null)
  }

  const actions = tab === 'salvas' ? [] : [
    ...(input ? [
      opened
        ? { label: 'Salvar alterações', icon: BookmarkPlusIcon, primary: true, onSelect: () => setSaving('update' as const) }
        : { label: 'Salvar simulação', icon: BookmarkPlusIcon, primary: true, onSelect: () => setSaving('new' as const) },
      ...(opened ? [{ label: 'Salvar como nova', icon: CopyPlusIcon, onSelect: () => setSaving('new' as const) }] : []),
    ] : []),
    { label: 'Nova simulação', icon: FilePlus2Icon, secondary: true, onSelect: reset },
  ]

  return (
    <div className="space-y-6">
      <PageHeader title="Simulador" hideValues actions={actions}
        description="Veja como fica o seu saldo nos próximos meses com mudanças de ganhos e gastos e com dinheiro investido." />

      <Tabs value={tab} onValueChange={(v) => goTab(v as Tab)}>
        <ScrollTabsList value={tab}>
          <TabsTrigger value="simular">{opened ? opened.name : 'Simular'}</TabsTrigger>
          <TabsTrigger value="salvas">Salvas{saved.data ? ` (${saved.data.length})` : ''}</TabsTrigger>
        </ScrollTabsList>

        <TabsContent value="simular" className="pt-2">
          <div className="grid items-start gap-6 xl:grid-cols-[24rem_minmax(0,1fr)]">
            <SimulatorForm value={form} onChange={(patch) => setForm((f) => ({ ...f, ...patch }))} result={data} />
            <div className="@container min-w-0 space-y-4 xl:sticky xl:top-16">
              <div className="flex items-center gap-2">
                <Switch id="sim-real" checked={real} onCheckedChange={setReal} />
                <label htmlFor="sim-real" className="text-sm">Mostrar em reais de hoje (descontar a inflação)</label>
              </div>
              {!input ? (
                <Card><CardContent className="py-10 text-center text-sm text-muted-foreground">Confira o prazo (1 a 600 meses) e os valores digitados.</CardContent></Card>
              ) : simulation.error && !simulation.isFetching ? (
                <Card>
                  <CardContent className="py-8 text-center text-sm text-destructive">
                    {simulation.error instanceof ApiError ? simulation.error.message : 'Não foi possível simular.'}
                  </CardContent>
                </Card>
              ) : !data ? (
                <div className="space-y-4"><Skeleton className="h-28" /><Skeleton className="h-72" /></div>
              ) : (
                <div className={simulation.isFetching ? 'opacity-70 transition-opacity motion-reduce:transition-none' : undefined} aria-busy={simulation.isFetching}>
                  <SimulationResults data={data} invest={!!request?.investment} />
                </div>
              )}
            </div>
          </div>
        </TabsContent>

        <TabsContent value="salvas" className="pt-2">
          <SavedTab items={saved.data} loading={saved.isLoading} real={real} onOpen={open}
            onDeleted={(id) => { if (opened?.id === id) setOpened(null) }} onNew={() => { reset(); goTab('simular') }} />
        </TabsContent>
      </Tabs>

      {input && (
        <SaveDialog open={saving !== null} onOpenChange={(o) => !o && setSaving(null)} input={input}
          id={saving === 'update' ? opened?.id : undefined}
          defaultName={saving === 'update' && opened ? opened.name : opened ? `${opened.name} (cópia)` : defaultName(input)}
          onSaved={(item) => setOpened({ id: item.id, name: item.name })} />
      )}
    </div>
  )
}

function defaultName(input: SimulationInput) {
  const horizon = HORIZONS.find((h) => h.months === input.months)?.label ?? `${input.months} meses`
  return input.investment ? `${YIELDS[input.investment.source].label} em ${horizon}` : `Saldo em ${horizon}`
}

function describe(input: SimulationInput) {
  const parts = [HORIZONS.find((h) => h.months === input.months)?.label ?? `${input.months} meses`]
  if (input.changes?.length) parts.push(`${input.changes.length} ${input.changes.length === 1 ? 'mudança' : 'mudanças'}`)
  if (input.investment) parts.push(YIELDS[input.investment.source].label)
  return parts.join(' · ')
}

function SavedTab({ items, loading, real, onOpen, onDeleted, onNew }: {
  items?: SavedFinanceSimulation[]
  loading: boolean
  real: boolean
  onOpen: (item: SavedFinanceSimulation) => void
  onDeleted: (id: string) => void
  onNew: () => void
}) {
  const { formatCurrency } = useMoney()
  const remove = useDeleteFinanceSimulation()
  const [selected, setSelected] = useState<string[]>([])
  const [deleting, setDeleting] = useState<SavedFinanceSimulation | null>(null)
  const ids = selected.filter((id) => items?.some((i) => i.id === id))
  const compare = useCompareSimulations(ids, real)

  async function confirmDelete(item: SavedFinanceSimulation) {
    try {
      await remove.mutateAsync(item.id)
      setSelected((s) => s.filter((id) => id !== item.id))
      onDeleted(item.id)
      toast.success('Simulação excluída.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  if (loading) return <Skeleton className="h-40" />
  if (!items?.length) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
          <p className="max-w-sm text-sm text-muted-foreground">Salve simulações para reabrir com os dados do dia e comparar cenários.</p>
          <Button onClick={onNew}><FilePlus2Icon /> Nova simulação</Button>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">Marque 2 ou 3 para comparar o saldo simulado.</p>
      <ul className="divide-y rounded-xl border">
        {items.map((item) => {
          const checked = ids.includes(item.id)
          return (
            <li key={item.id} className="flex items-center gap-3 p-3">
              <Checkbox checked={checked} disabled={!checked && ids.length >= 3} aria-label={`Comparar ${item.name}`}
                onCheckedChange={(on) => setSelected((s) => (on === true ? [...s, item.id] : s.filter((id) => id !== item.id)))} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{item.name}</p>
                <p className="truncate text-xs text-muted-foreground">{describe(item.input)} · salva {formatRelative(item.updatedAt)}</p>
              </div>
              <Button size="sm" variant="outline" onClick={() => onOpen(item)}><FolderOpenIcon /> Abrir</Button>
              <Button size="icon" variant="ghost" aria-label={`Excluir ${item.name}`} onClick={() => setDeleting(item)}><Trash2Icon /></Button>
            </li>
          )
        })}
      </ul>

      {ids.length >= 2 && (
        <Card>
          <CardHeader><CardTitle className="text-base">Comparar saldo simulado</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            {compare.error ? (
              <p className="text-sm text-destructive">{compare.error instanceof ApiError ? compare.error.message : 'Não foi possível comparar.'}</p>
            ) : !compare.data ? <Skeleton className="h-72" /> : (
              <>
                <CompareChart items={compare.data} />
                <ul className="grid gap-2 text-sm sm:grid-cols-3">
                  {compare.data.map((item) => {
                    const last = item.points.at(-1)
                    return (
                      <li key={item.id} className="rounded-lg bg-muted/50 p-2.5">
                        <p className="truncate font-medium">{item.name}</p>
                        <p className="text-xs text-muted-foreground">Saldo em {last ? formatMonth(last.month, 'short') : ''}</p>
                        <p className={item.summary.finalSimulated < 0 ? 'font-semibold tabular-nums text-destructive' : 'font-semibold tabular-nums'}>
                          {formatCurrency(item.summary.finalSimulated)}
                        </p>
                      </li>
                    )
                  })}
                </ul>
              </>
            )}
          </CardContent>
        </Card>
      )}

      <ConfirmDelete open={deleting !== null} onOpenChange={(o) => !o && setDeleting(null)} title={`Excluir ${deleting?.name ?? ''}?`}
        description="A simulação salva some. Seus lançamentos não mudam."
        onConfirm={() => { if (deleting) void confirmDelete(deleting); setDeleting(null) }} />
    </div>
  )
}

function SaveDialog({ open, onOpenChange, input, id, defaultName, onSaved }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  input: SimulationInput
  id?: string
  defaultName: string
  onSaved: (item: SavedFinanceSimulation) => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>{id ? 'Salvar alterações' : 'Salvar simulação'}</DialogTitle>
          <DialogDescription>Guardamos o que você digitou. Ao abrir, refazemos com seus fixos, lançamentos e taxas do dia.</DialogDescription>
        </DialogHeader>
        {open && <SaveForm input={input} id={id} defaultName={defaultName} onDone={(item) => { if (item) onSaved(item); onOpenChange(false) }} />}
      </DialogContent>
    </Dialog>
  )
}

function SaveForm({ input, id, defaultName, onDone }: {
  input: SimulationInput
  id?: string
  defaultName: string
  onDone: (item?: SavedFinanceSimulation) => void
}) {
  const save = useSaveFinanceSimulation()
  const [name, setName] = useState(defaultName)
  async function submit() {
    if (!name.trim()) return
    try {
      const item = await save.mutateAsync({ id, name: name.trim(), input })
      toast.success('Simulação salva.')
      onDone(item)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }
  return (
    <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <Field>
        <FieldLabel htmlFor="fsim-name">Nome</FieldLabel>
        <Input id="fsim-name" value={name} maxLength={120} onChange={(e) => setName(e.target.value)} className="text-base md:text-sm" />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={() => onDone()}>Cancelar</Button>
        <Button type="submit" disabled={save.isPending || !name.trim()}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}
