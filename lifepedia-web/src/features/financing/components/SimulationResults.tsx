import { InfoIcon, RotateCcwIcon, TriangleAlertIcon } from 'lucide-react'
import { useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { amountToInput, formatDate, parseAmount } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { formatMonths, formatRate, SCENARIO_HINT, SCENARIO_LABEL, SYSTEM_HINT, SYSTEM_LABEL, WHEN_LABEL } from '../labels'
import type {
  AmortizationSystem,
  BankOption,
  CostItem,
  CostTotals,
  CostWhen,
  IncomeCheck,
  RateSource,
  Scenario,
  ScenarioResult,
  SimulationResponse,
  TermRow,
} from '../types'

export function Warnings({ items }: { items: string[] }) {
  if (!items.length) return null
  return (
    <ul className="space-y-2">
      {items.map((w) => (
        <li key={w} className="flex items-start gap-2 rounded-xl border border-amber-500/40 bg-amber-500/10 p-3 text-sm">
          <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden />
          {w}
        </li>
      ))}
    </ul>
  )
}

export function SourceNote({ rates, correction }: { rates: RateSource; correction: SimulationResponse['correction'] }) {
  return (
    <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
      <InfoIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
      <span>
        {rates.fallback
          ? 'Taxa média aproximada (sem dados do Banco Central agora).'
          : `Taxas de ${rates.banks} bancos em "${rates.label}", divulgadas pelo Banco Central${rates.period ? ` (período de ${formatDate(rates.period)})` : ''}.`}
        {correction && ` Saldo corrigido pela ${correction.index} (média recente de ${formatRate(correction.monthly, 2)} ao mês).`}
        {' '}Valores estimados: a proposta do banco pode mudar com seu perfil.
      </span>
    </p>
  )
}

/** Cenários de taxa; o escolhido define a tabela de parcelas. */
export function ScenarioCards({ scenarios, selected, onSelect }: {
  scenarios: ScenarioResult[]
  selected: Scenario
  onSelect: (scenario: Scenario) => void
}) {
  const { formatCurrency } = useMoney()
  return (
    <div role="radiogroup" aria-label="Cenário de taxa" className={cn('grid gap-3', scenarios.length > 3 ? '@xl:grid-cols-2 @5xl:grid-cols-4' : '@2xl:grid-cols-3')}>
      {scenarios.map((s) => (
        <button key={s.scenario} type="button" role="radio" aria-checked={selected === s.scenario} onClick={() => onSelect(s.scenario)}
          className={cn('space-y-1.5 rounded-2xl border bg-card p-4 text-left transition-colors motion-reduce:transition-none hover:bg-accent/50',
            selected === s.scenario && 'border-primary ring-2 ring-ring/40')}>
          <div className="flex items-center justify-between gap-2">
            <span className="text-sm font-semibold">{SCENARIO_LABEL[s.scenario]}</span>
            {selected === s.scenario && <Badge variant="secondary">Escolhido</Badge>}
          </div>
          <p className="text-xs text-muted-foreground">{SCENARIO_HINT[s.scenario]}</p>
          <p className="text-sm tabular-nums">
            <span className="font-medium">{formatRate(s.monthlyRate)}</span> ao mês · {formatRate(s.yearlyRate)} ao ano
          </p>
          <dl className="grid grid-cols-2 gap-2 pt-1 text-xs">
            <div>
              <dt className="text-muted-foreground">Price (fixa)</dt>
              <dd className="text-base font-semibold tabular-nums">{formatCurrency(s.price.firstPayment)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">SAC (1ª parcela)</dt>
              <dd className="text-base font-semibold tabular-nums">{formatCurrency(s.sac.firstPayment)}</dd>
            </div>
          </dl>
        </button>
      ))}
    </div>
  )
}

/** SAC lado a lado com Price no cenário escolhido. */
export function CompareSystems({ scenario, system, onSystem }: {
  scenario: ScenarioResult
  system: AmortizationSystem
  onSystem: (system: AmortizationSystem) => void
}) {
  const { formatCurrency } = useMoney()
  const rows: { label: string; value: (r: ScenarioResult['sac']) => string }[] = [
    { label: '1ª parcela', value: (r) => formatCurrency(r.firstPayment) },
    { label: 'Última parcela', value: (r) => formatCurrency(r.lastPayment) },
    { label: 'Total pago', value: (r) => formatCurrency(r.totalPaid) },
    { label: 'Total de juros', value: (r) => formatCurrency(r.totalInterest) },
    { label: 'Custo efetivo total (CET)', value: (r) => (r.cetYearly === null ? '-' : `${formatRate(r.cetYearly)} ao ano`) },
  ]
  const cheaper: AmortizationSystem = scenario.sac.totalPaid <= scenario.price.totalPaid ? 'SAC' : 'PRICE'
  const saving = Math.abs(scenario.sac.totalPaid - scenario.price.totalPaid)
  return (
    <Card>
      <CardHeader>
        <CardTitle>SAC x Price</CardTitle>
        <CardDescription>
          {SYSTEM_LABEL[cheaper]} sai {formatCurrency(saving)} mais barato no total. O CET soma juros, seguros e tarifas do banco.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-[minmax(0,1fr)_auto_auto] gap-x-4 gap-y-2 text-sm">
          <span />
          {(['SAC', 'PRICE'] as const).map((s) => (
            <button key={s} type="button" onClick={() => onSystem(s)} aria-pressed={system === s}
              className={cn('rounded-full px-3 py-1 text-xs font-semibold', system === s ? 'bg-primary text-primary-foreground' : 'bg-muted')}>
              {SYSTEM_LABEL[s]}
            </button>
          ))}
          {rows.map((r) => (
            <div key={r.label} className="contents">
              <span className="text-muted-foreground">{r.label}</span>
              <span className="text-right font-medium tabular-nums">{r.value(scenario.sac)}</span>
              <span className="text-right font-medium tabular-nums">{r.value(scenario.price)}</span>
            </div>
          ))}
        </div>
        <p className="text-xs text-muted-foreground">{SYSTEM_HINT[system]} Toque no sistema para ver as parcelas dele.</p>
      </CardContent>
    </Card>
  )
}

export function TermsCard({ terms, current }: { terms: TermRow[]; current: number }) {
  const { formatCurrency } = useMoney()
  return (
    <Card>
      <CardHeader>
        <CardTitle>Prazos lado a lado</CardTitle>
        <CardDescription>Mesmo valor e taxa do cenário de referência, outros prazos.</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="-mx-4 overflow-x-auto px-4">
          <Table className="min-w-[30rem]">
            <TableHeader>
              <TableRow>
                <TableHead>Prazo</TableHead>
                <TableHead className="text-right">Price</TableHead>
                <TableHead className="text-right">Juros Price</TableHead>
                <TableHead className="text-right">SAC 1ª → última</TableHead>
                <TableHead className="text-right">Juros SAC</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {terms.map((t) => (
                <TableRow key={t.months} className={cn(t.months === current && 'bg-muted/60 font-medium')}>
                  <TableCell className="whitespace-nowrap">{t.months} meses</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCurrency(t.priceFirst)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCurrency(t.priceInterest)}</TableCell>
                  <TableCell className="text-right whitespace-nowrap tabular-nums">
                    {formatCurrency(t.sacFirst)} → {formatCurrency(t.sacLast)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatCurrency(t.sacInterest)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}

const WHEN_ORDER: CostWhen[] = ['UPFRONT', 'FINANCED', 'INSTALLMENT', 'YEARLY', 'MONTHLY']

/** Gastos padrão do tipo de bem, com valor editável (0 tira da conta). */
export function CostsCard({ costs, totals, onEdit }: {
  costs: CostItem[]
  totals: CostTotals
  onEdit: (key: string, amount: number | undefined) => void
}) {
  const { formatCurrency } = useMoney()
  return (
    <Card>
      <CardHeader>
        <CardTitle>Gastos além da parcela</CardTitle>
        <CardDescription>Estimativas comuns para este tipo de bem; ajuste com os valores da sua cidade ou proposta.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <dl className="grid grid-cols-2 gap-3 text-sm @2xl:grid-cols-4">
          <Total label="Na compra" hint="entrada + gastos à vista" value={formatCurrency(totals.upfront)} />
          <Total label="Financiados" hint="somados ao valor" value={formatCurrency(totals.financedCosts)} />
          <Total label="Por ano" hint="IPVA, IPTU, seguro..." value={formatCurrency(totals.yearly)} />
          <Total label="Por mês" hint="fora da parcela" value={formatCurrency(totals.monthly)} />
        </dl>
        {WHEN_ORDER.map((when) => {
          const items = costs.filter((c) => c.when === when)
          if (!items.length) return null
          return (
            <section key={when} className="space-y-2">
              <h3 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">{WHEN_LABEL[when]}</h3>
              <ul className="divide-y rounded-xl border">
                {items.map((c) => <CostRow key={`${c.key}-${c.amount}`} cost={c} onEdit={onEdit} />)}
              </ul>
            </section>
          )
        })}
      </CardContent>
    </Card>
  )
}

function Total({ label, hint, value }: { label: string; hint: string; value: string }) {
  return (
    <div className="rounded-xl bg-muted/60 p-3">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="font-semibold tabular-nums">{value}</dd>
      <dd className="text-[11px] text-muted-foreground">{hint}</dd>
    </div>
  )
}

function CostRow({ cost, onEdit }: { cost: CostItem; onEdit: (key: string, amount: number | undefined) => void }) {
  const [draft, setDraft] = useState(amountToInput(cost.amount))
  function commit() {
    const value = draft.trim() ? parseAmount(draft) : 0
    if (Number.isNaN(value)) {
      setDraft(amountToInput(cost.amount))
      return
    }
    if (value !== cost.amount) onEdit(cost.key, value)
  }
  return (
    <li className="flex flex-wrap items-center gap-x-3 gap-y-1 p-3">
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">
          {cost.name}
          {cost.bank && <span className="ml-1.5 text-xs font-normal text-muted-foreground">(entra no CET)</span>}
        </p>
        {cost.hint && <p className="text-xs text-muted-foreground">{cost.hint}</p>}
      </div>
      <div className="flex items-center gap-1">
        {cost.edited && (
          <Button type="button" variant="ghost" size="icon-sm" aria-label={`Voltar ${cost.name} ao valor padrão`}
            title="Voltar ao valor padrão" onClick={() => onEdit(cost.key, undefined)}>
            <RotateCcwIcon />
          </Button>
        )}
        <Input inputMode="decimal" aria-label={`Valor de ${cost.name}`} value={draft} onChange={(e) => setDraft(e.target.value)}
          onBlur={commit} onKeyDown={(e) => e.key === 'Enter' && commit()}
          className={cn('w-28 text-right text-base tabular-nums md:text-sm', cost.amount === 0 && 'text-muted-foreground')} />
      </div>
    </li>
  )
}

export function IncomeCard({ income }: { income: IncomeCheck }) {
  const { formatCurrency } = useMoney()
  const over = income.percent > income.limit
  return (
    <Card>
      <CardHeader>
        <CardTitle>Cabe na renda?</CardTitle>
        <CardDescription>Bancos costumam aprovar parcela de até {income.limit}% da renda.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="space-y-1.5">
          <div className="flex items-baseline justify-between gap-2 text-sm">
            <span>1ª parcela: <strong className="tabular-nums">{formatCurrency(income.payment)}</strong></span>
            <span className={cn('font-semibold tabular-nums', over ? 'text-destructive' : 'text-income')}>
              {formatRate(income.percent, 1)} da renda {over ? '(acima)' : '(dentro)'}
            </span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-muted" role="presentation">
            <div className={cn('h-full rounded-full', over ? 'bg-destructive' : 'bg-income')}
              style={{ width: `${Math.min(100, (income.percent / income.limit) * 100)}%` }} />
          </div>
        </div>
        <p className="text-sm text-muted-foreground">
          Com {formatCurrency(income.maxPayment)} de parcela máxima, dá para um bem de até{' '}
          <strong className="text-foreground tabular-nums">{formatCurrency(income.maxPrice)}</strong> (aproximado, mesma entrada).
        </p>
      </CardContent>
    </Card>
  )
}

const TOP = 5

/** Bancos da modalidade (Banco Central), do mais barato ao mais caro, com a 1ª parcela de cada um. */
export function BanksCard({ banks, system, months }: { banks: BankOption[]; system: AmortizationSystem; months: number }) {
  const { formatCurrency } = useMoney()
  const [all, setAll] = useState(false)
  if (!banks.length) return null
  const sorted = [...banks].sort((a, b) => a.monthlyRate - b.monthlyRate)
  const shown = all || sorted.length <= TOP + 3 ? sorted : [...sorted.slice(0, TOP), ...sorted.slice(-3)]
  return (
    <Card>
      <CardHeader>
        <CardTitle>Taxa por banco</CardTitle>
        <CardDescription>
          Média que cada banco cobrou na modalidade. 1ª parcela em {SYSTEM_LABEL[system]}, {formatMonths(months)}.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <ol className="divide-y rounded-xl border">
          {shown.map((b, i) => {
            const gap = !all && sorted.length > TOP + 3 && i === TOP
            return (
              <li key={b.bank} className={cn('flex items-center gap-3 p-3 text-sm', gap && 'border-t-4 border-t-muted')}>
                <span className="w-6 shrink-0 text-xs text-muted-foreground tabular-nums">{sorted.indexOf(b) + 1}º</span>
                <span className="min-w-0 flex-1 truncate" title={b.bank}>{b.bank}</span>
                <span className="shrink-0 text-xs text-muted-foreground tabular-nums">{formatRate(b.monthlyRate)} a.m.</span>
                <span className="w-24 shrink-0 text-right font-medium tabular-nums">{formatCurrency(b.payment)}</span>
              </li>
            )
          })}
        </ol>
        {sorted.length > TOP + 3 && (
          <Button variant="ghost" size="sm" onClick={() => setAll((v) => !v)}>
            {all ? 'Mostrar menos' : `Ver os ${sorted.length} bancos`}
          </Button>
        )}
      </CardContent>
    </Card>
  )
}
