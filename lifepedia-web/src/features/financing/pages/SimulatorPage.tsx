import { BookmarkPlusIcon, CalculatorIcon, FilePlus2Icon, Loader2Icon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { amountToInput, formatCurrency, formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { financingUrl, useSaveSimulation, useSimulation } from '../api'
import { FinancingDialog } from '../components/FinancingDialog'
import { ScheduleChart, ScheduleTable } from '../components/Schedule'
import { SimulationForm } from '../components/SimulationForm'
import {
  BanksCard,
  CompareSystems,
  CostsCard,
  IncomeCard,
  ScenarioCards,
  SourceNote,
  TermsCard,
  Warnings,
} from '../components/SimulationResults'
import { kindInfo } from '../labels'
import { EMPTY_FORM, fromRequest, toRequest, type SimForm } from '../sim-form'
import type { AmortizationSystem, FinancingRequest, Scenario, SimulationRequest, SimulationResponse } from '../types'

type SavedState = { input?: SimulationRequest; name?: string } | null

/** Simulador: sem perguntar juros, mostra cenários pelas taxas dos bancos, SAC x Price, prazos, gastos e renda. */
export function SimulatorPage() {
  const [params] = useSearchParams()
  const groupId = params.get('grupo')
  const saved = useLocation().state as SavedState
  const navigate = useNavigate()
  const { formatCurrency: money } = useMoney()
  const [form, setForm] = useState<SimForm>(() => {
    if (saved?.input) return fromRequest(saved.input)
    const price = Number(params.get('preco'))
    return { ...EMPTY_FORM, price: price > 0 ? amountToInput(price) : '' }
  })
  const [costs, setCosts] = useState<Record<string, number>>(saved?.input?.costs ?? {})
  const [scenario, setScenario] = useState<Scenario | null>(saved?.input?.scenario ?? null)
  const [system, setSystem] = useState<AmortizationSystem | null>(saved?.input?.system ?? null)
  const [saveOpen, setSaveOpen] = useState(false)
  const [contractOpen, setContractOpen] = useState(false)
  const itemName = saved?.name ?? params.get('nome') ?? ''

  const base = toRequest(form)
  const key = base ? JSON.stringify({ ...base, costs: Object.keys(costs).length ? costs : undefined, scenario, system }) : ''
  const debounced = useDebouncedValue(key, 400)
  const request = useMemo(() => (debounced ? (JSON.parse(debounced) as SimulationRequest) : null), [debounced])
  const simulation = useSimulation(groupId, request)
  const data = base ? simulation.data : undefined
  const priceError = form.price.trim() && !base ? 'Informe um preço a partir de R$ 1.000.' : undefined

  function change(patch: Partial<SimForm>) {
    if (patch.kind && patch.kind !== form.kind) {
      setCosts({})
      setSystem(null)
    }
    if (patch.rate !== undefined && !patch.rate.trim() && scenario === 'CUSTOM') setScenario(null)
    setForm((f) => ({ ...f, ...patch }))
  }

  function editCost(costKey: string, amount: number | undefined) {
    setCosts((c) => {
      const next = { ...c }
      if (amount === undefined) delete next[costKey]
      else next[costKey] = amount
      return next
    })
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Simular financiamento"
        description="Diga o que quer comprar e veja as parcelas com as taxas médias dos bancos, sem precisar saber os juros."
        hideValues
        actions={data ? [
          { label: 'Salvar simulação', icon: BookmarkPlusIcon, primary: true, onSelect: () => setSaveOpen(true) },
          { label: 'Cadastrar como financiamento', icon: FilePlus2Icon, onSelect: () => setContractOpen(true) },
        ] : []}
      />
      <div className="grid items-start gap-6 xl:grid-cols-[22rem_minmax(0,1fr)]">
        <div className="xl:sticky xl:top-16">
          <SimulationForm value={form} onChange={change} priceError={priceError} />
        </div>
        <div className="@container min-w-0 space-y-4">
          {!base ? (
            <Card>
              <CardContent className="flex flex-col items-center gap-2 py-12 text-center">
                <CalculatorIcon className="size-8 text-muted-foreground" aria-hidden />
                <p className="font-medium">Informe o preço para ver as simulações</p>
                <p className="max-w-sm text-sm text-muted-foreground">
                  Mostramos a parcela com taxas boas, médias e altas, os gastos comuns do tipo de bem e quanto cabe na renda.
                </p>
              </CardContent>
            </Card>
          ) : simulation.error && !simulation.isFetching ? (
            <Card>
              <CardContent className="py-8 text-center text-sm text-destructive">
                {simulation.error instanceof ApiError ? simulation.error.message : 'Não foi possível simular.'}
              </CardContent>
            </Card>
          ) : !data ? (
            <div className="space-y-4">
              <Skeleton className="h-40" />
              <Skeleton className="h-64" />
            </div>
          ) : (
            <Results data={data} money={money} fetching={simulation.isFetching}
              onScenario={setScenario} onSystem={setSystem} onCost={editCost} />
          )}
        </div>
      </div>

      {data && request && (
        <>
          <SaveDialog open={saveOpen} onOpenChange={setSaveOpen} groupId={groupId} input={request}
            defaultName={itemName || `${kindInfo(request.kind).label} de ${formatCurrency(request.price)}`} />
          <FinancingDialog open={contractOpen} onOpenChange={setContractOpen} groupId={groupId}
            initial={contractFrom(data, request, itemName)} onSaved={(f) => navigate(financingUrl(f.groupId, f.id))} />
        </>
      )}
    </div>
  )
}

function Results({ data, money, fetching, onScenario, onSystem, onCost }: {
  data: SimulationResponse
  money: (v: number) => string
  fetching: boolean
  onScenario: (s: Scenario) => void
  onSystem: (s: AmortizationSystem) => void
  onCost: (key: string, amount: number | undefined) => void
}) {
  const chosen = data.scenarios.find((s) => s.scenario === data.scenario) ?? data.scenarios[0]
  const last = data.schedule.at(-1)
  return (
    <div className={fetching ? 'space-y-4 opacity-70 transition-opacity motion-reduce:transition-none' : 'space-y-4'} aria-busy={fetching}>
      <Warnings items={data.warnings} />
      <Card size="sm">
        <CardContent className="grid gap-3 text-sm @lg:grid-cols-3">
          <div>
            <p className="text-xs text-muted-foreground">Valor financiado</p>
            <p className="text-lg font-semibold tabular-nums">{money(data.financed)}</p>
            {data.financed !== data.base && <p className="text-xs text-muted-foreground">{money(data.base)} + gastos financiados</p>}
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Você paga na compra</p>
            <p className="text-lg font-semibold tabular-nums">{money(data.costTotals.upfront)}</p>
            <p className="text-xs text-muted-foreground">entrada e gastos à vista</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Parcelas</p>
            <p className="text-lg font-semibold tabular-nums">{data.months}x</p>
            <p className="text-xs text-muted-foreground">
              {formatMonth(data.firstMonth, 'short')}{last ? ` a ${formatMonth(last.month, 'short')}` : ''}
            </p>
          </div>
        </CardContent>
      </Card>
      <ScenarioCards scenarios={data.scenarios} selected={data.scenario} onSelect={onScenario} />
      <SourceNote rates={data.rates} correction={data.correction} />
      <CompareSystems scenario={chosen} system={data.system} onSystem={onSystem} />
      {data.income && <IncomeCard income={data.income} />}
      <TermsCard terms={data.terms} current={data.months} />
      <CostsCard costs={data.costs} totals={data.costTotals} onEdit={onCost} />
      <BanksCard banks={data.banks} system={data.system} months={data.months} />
      <ScheduleChart rows={data.schedule} />
      <ScheduleTable rows={data.schedule} fileName="simulacao-financiamento" />
    </div>
  )
}

/** Contrato pré-preenchido com o cenário e o sistema escolhidos. */
function contractFrom(data: SimulationResponse, request: SimulationRequest, name: string): Partial<FinancingRequest> {
  const chosen = data.scenarios.find((s) => s.scenario === data.scenario) ?? data.scenarios[0]
  const mip = data.costs.find((c) => c.key === 'mip')?.amount ?? 0
  const fee = data.costs.filter((c) => c.when === 'INSTALLMENT' && c.key !== 'mip').reduce((sum, c) => sum + c.amount, 0)
  return {
    name: name || kindInfo(request.kind).label,
    kind: request.kind,
    condition: request.condition,
    price: request.price,
    downPayment: request.downPayment,
    fgts: request.fgts ?? null,
    financed: data.financed,
    monthlyRate: chosen.monthlyRate,
    system: data.system,
    months: data.months,
    firstMonth: data.firstMonth,
    dueDay: 10,
    insuranceRate: mip ? Math.round((mip / data.financed) * 100 * 100000) / 100000 : null,
    monthlyFee: fee || null,
    index: data.correction?.index ?? 'NONE',
  }
}

function SaveDialog({ open, onOpenChange, groupId, input, defaultName }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupId: string | null
  input: SimulationRequest
  defaultName: string
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>Salvar simulação</DialogTitle>
          <DialogDescription>
            {groupId ? 'Fica salva no grupo para todos verem.' : 'Fica na sua lista de financiamentos.'} Ao abrir, refazemos com as taxas do dia.
          </DialogDescription>
        </DialogHeader>
        {open && <SaveForm groupId={groupId} input={input} defaultName={defaultName} onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  )
}

function SaveForm({ groupId, input, defaultName, onDone }: {
  groupId: string | null
  input: SimulationRequest
  defaultName: string
  onDone: () => void
}) {
  const save = useSaveSimulation(groupId)
  const [name, setName] = useState(defaultName)
  async function submit() {
    if (!name.trim()) return
    try {
      await save.mutateAsync({ name: name.trim(), input })
      toast.success('Simulação salva.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }
  return (
    <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <Field>
        <FieldLabel htmlFor="sim-name">Nome</FieldLabel>
        <Input id="sim-name" value={name} maxLength={120} onChange={(e) => setName(e.target.value)} className="text-base md:text-sm" />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" disabled={save.isPending || !name.trim()}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}
