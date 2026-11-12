import { MinusIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { MonthField } from '@/components/MonthField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { currentYearMonth, formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { HORIZONS, newChange, YIELDS, type ChangeForm, type SimForm } from '../form'
import type { SimulationResult, Yield } from '../types'

type Props = {
  value: SimForm
  onChange: (patch: Partial<SimForm>) => void
  /** Base e taxas da última simulação (para mostrar de onde vêm os números) */
  result?: SimulationResult
}

const AVERAGES = [
  { value: 0, label: 'Sem ocasionais' },
  { value: 3, label: 'Média de 3 meses' },
  { value: 6, label: 'Média de 6 meses' },
  { value: 12, label: 'Média de 12 meses' },
] as const

/** Ponto de partida, base (fixos e ocasionais), ganhos e gastos livres e investimento. */
export function SimulatorForm({ value, onChange, result }: Props) {
  const { formatCurrency } = useMoney()
  const editChange = (key: string, patch: Partial<ChangeForm>) =>
    onChange({ changes: value.changes.map((c) => (c.key === key ? { ...c, ...patch } : c)) })
  const yieldInfo = YIELDS[value.source]
  const indexRate = yieldInfo.rate && result ? result.rates[yieldInfo.rate] : null

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader><CardTitle className="text-base">Ponto de partida</CardTitle></CardHeader>
        <CardContent>
          <FieldGroup className="gap-4">
            <Field>
              <FieldLabel htmlFor="sim-balance">Quanto você tem hoje (R$)</FieldLabel>
              <Input id="sim-balance" inputMode="decimal" placeholder="0" value={value.startBalance} className="text-base md:text-sm"
                onChange={(e) => onChange({ startBalance: e.target.value })} />
              <FieldDescription>Saldo das contas. Use "-" se estiver negativo.</FieldDescription>
            </Field>
            <Field>
              <FieldLabel id="sim-horizon">Por quanto tempo</FieldLabel>
              <div role="radiogroup" aria-labelledby="sim-horizon" className="flex flex-wrap gap-1.5">
                {HORIZONS.map((h) => (
                  <button key={h.months} type="button" role="radio" aria-checked={value.months === String(h.months)}
                    onClick={() => onChange({ months: String(h.months) })}
                    className={cn('rounded-full border px-3 py-1 text-sm transition-colors motion-reduce:transition-none hover:bg-accent',
                      value.months === String(h.months) && 'border-primary bg-accent font-medium')}>
                    {h.label}
                  </button>
                ))}
              </div>
              <div className="flex items-center gap-2">
                <Input id="sim-months" inputMode="numeric" value={value.months} aria-label="Meses" className="w-24 text-base md:text-sm"
                  onChange={(e) => onChange({ months: e.target.value.replace(/\D/g, '').slice(0, 3) })} />
                <span className="text-sm text-muted-foreground">meses (até 600)</span>
              </div>
            </Field>
          </FieldGroup>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">Seu mês hoje</CardTitle></CardHeader>
        <CardContent>
          <FieldGroup className="gap-4">
            <Field orientation="horizontal">
              <Switch id="sim-fixed" checked={value.useFixed} onCheckedChange={(on) => onChange({ useFixed: on })} />
              <FieldLabel htmlFor="sim-fixed" className="font-normal">Somar meus fixos (com fim e ajustes de cada mês)</FieldLabel>
            </Field>
            <Field>
              <FieldLabel htmlFor="sim-average">Ocasionais</FieldLabel>
              <Select value={String(value.averageMonths)} onValueChange={(v) => onChange({ averageMonths: Number(v) as SimForm['averageMonths'] })}>
                <SelectTrigger id="sim-average"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {AVERAGES.map((a) => <SelectItem key={a.value} value={String(a.value)}>{a.label}</SelectItem>)}
                </SelectContent>
              </Select>
              <FieldDescription>Média dos meses completos que têm lançamentos.</FieldDescription>
            </Field>
            {result && (
              <dl className="grid grid-cols-2 gap-x-3 gap-y-1 rounded-lg bg-muted/50 p-3 text-xs">
                {value.useFixed && (
                  <>
                    <dt className="text-muted-foreground">Fixos de ganho</dt>
                    <dd className="text-right tabular-nums">{formatCurrency(result.base.fixedIncome)}</dd>
                    <dt className="text-muted-foreground">Fixos de gasto</dt>
                    <dd className="text-right tabular-nums">{formatCurrency(result.base.fixedExpense)}</dd>
                  </>
                )}
                {value.averageMonths > 0 && (
                  <>
                    <dt className="text-muted-foreground">Ocasionais por mês</dt>
                    <dd className="text-right tabular-nums">{formatCurrency(result.base.occasionalAverage)}</dd>
                  </>
                )}
                <dt className="font-medium">Sobra em {formatMonth(result.start, 'short')}</dt>
                <dd className={cn('text-right font-medium tabular-nums', result.base.monthly < 0 && 'text-destructive')}>
                  {formatCurrency(result.base.monthly)}
                </dd>
              </dl>
            )}
          </FieldGroup>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">Ganhos e gastos</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {value.changes.length === 0 && (
            <p className="text-sm text-muted-foreground">Some um aumento, um bônus, uma compra ou uma conta nova para ver o efeito.</p>
          )}
          {value.changes.map((c, i) => (
            <fieldset key={c.key} className="space-y-2 rounded-lg border p-3">
              <legend className="sr-only">{c.name || `Item ${i + 1}`}</legend>
              <div className="flex gap-2">
                <div role="radiogroup" aria-label="Tipo" className="flex shrink-0 rounded-full bg-muted p-0.5">
                  {(['INCOME', 'EXPENSE'] as const).map((t) => (
                    <button key={t} type="button" role="radio" aria-checked={c.type === t} aria-label={t === 'INCOME' ? 'Ganho' : 'Gasto'}
                      onClick={() => editChange(c.key, { type: t })}
                      className={cn('flex size-8 items-center justify-center rounded-full text-muted-foreground',
                        c.type === t && (t === 'INCOME' ? 'bg-background text-income shadow-sm' : 'bg-background text-expense shadow-sm'))}>
                      {t === 'INCOME' ? <PlusIcon className="size-4" aria-hidden /> : <MinusIcon className="size-4" aria-hidden />}
                    </button>
                  ))}
                </div>
                <Input aria-label="Nome" placeholder={c.type === 'INCOME' ? 'Ex.: Aumento' : 'Ex.: Academia'} value={c.name} maxLength={80}
                  className="min-w-0 text-base md:text-sm" onChange={(e) => editChange(c.key, { name: e.target.value })} />
                <Button type="button" size="icon" variant="ghost" aria-label={`Remover ${c.name || 'item'}`}
                  onClick={() => onChange({ changes: value.changes.filter((x) => x.key !== c.key) })}>
                  <Trash2Icon />
                </Button>
              </div>
              <div className="grid gap-2">
                <Input aria-label="Valor (R$)" inputMode="decimal" placeholder="Valor (R$)" value={c.amount} className="text-base md:text-sm"
                  onChange={(e) => editChange(c.key, { amount: e.target.value })} />
                <MonthField value={c.start} onChange={(m) => editChange(c.key, { start: m || currentYearMonth() })} />
              </div>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                <label className="flex items-center gap-2 text-sm">
                  <Checkbox checked={c.once} onCheckedChange={(on) => editChange(c.key, { once: on === true })} />
                  Só uma vez
                </label>
                {!c.once && (
                  <div className="min-w-40 flex-1">
                    <MonthField value={c.end} clearable placeholder="Até o fim" onChange={(m) => editChange(c.key, { end: m })} />
                  </div>
                )}
              </div>
            </fieldset>
          ))}
          {value.changes.length < 30 && (
            <div className="flex gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => onChange({ changes: [...value.changes, newChange('INCOME')] })}>
                <PlusIcon /> Ganho
              </Button>
              <Button type="button" variant="outline" size="sm" onClick={() => onChange({ changes: [...value.changes, newChange('EXPENSE')] })}>
                <MinusIcon /> Gasto
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-2">
            <CardTitle className="text-base"><label htmlFor="sim-invest">Investimento</label></CardTitle>
            <Switch id="sim-invest" checked={value.invest} onCheckedChange={(on) => onChange({ invest: on })} />
          </div>
        </CardHeader>
        {value.invest && (
          <CardContent>
            <FieldGroup className="gap-4">
              <div className="grid grid-cols-2 gap-3">
                <Field>
                  <FieldLabel htmlFor="sim-initial">Já investido (R$)</FieldLabel>
                  <Input id="sim-initial" inputMode="decimal" placeholder="0" value={value.initial} className="text-base md:text-sm"
                    onChange={(e) => onChange({ initial: e.target.value })} />
                </Field>
                <Field>
                  <FieldLabel htmlFor="sim-monthly">Aporte por mês (R$)</FieldLabel>
                  <Input id="sim-monthly" inputMode="decimal" placeholder="0" value={value.monthly} className="text-base md:text-sm"
                    onChange={(e) => onChange({ monthly: e.target.value })} />
                </Field>
              </div>
              <Field>
                <FieldLabel htmlFor="sim-source">Onde rende</FieldLabel>
                <Select value={value.source} onValueChange={(v) => {
                  const source = v as Yield
                  onChange({ source, rate: YIELDS[source].defaultRate, tax: source === 'SAVINGS' ? false : value.tax || value.source === 'SAVINGS' })
                }}>
                  <SelectTrigger id="sim-source"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {(Object.keys(YIELDS) as Yield[]).map((y) => <SelectItem key={y} value={y}>{YIELDS[y].label}</SelectItem>)}
                  </SelectContent>
                </Select>
                {indexRate !== null && (
                  <FieldDescription>
                    {yieldInfo.rate?.toUpperCase()} hoje: {indexRate.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}% ao ano (média dos últimos 12 meses).
                  </FieldDescription>
                )}
                {value.source === 'SAVINGS' && <FieldDescription>Regra da poupança com a Selic e a TR atuais, sem IR.</FieldDescription>}
              </Field>
              {value.source !== 'SAVINGS' && (
                <Field>
                  <FieldLabel htmlFor="sim-rate">{yieldInfo.rateLabel}</FieldLabel>
                  <Input id="sim-rate" inputMode="decimal" value={value.rate} className="text-base md:text-sm"
                    onChange={(e) => onChange({ rate: e.target.value })} />
                </Field>
              )}
              {value.source !== 'SAVINGS' && (
                <Field orientation="horizontal">
                  <Switch id="sim-tax" checked={value.tax} onCheckedChange={(on) => onChange({ tax: on })} />
                  <FieldLabel htmlFor="sim-tax" className="font-normal">Descontar o IR (tabela regressiva)</FieldLabel>
                </Field>
              )}
              <Field orientation="horizontal">
                <Switch id="sim-from" checked={value.fromBalance} onCheckedChange={(on) => onChange({ fromBalance: on })} />
                <FieldLabel htmlFor="sim-from" className="font-normal">O aporte sai do saldo do mês</FieldLabel>
              </Field>
              <FieldDescription className="-mt-2">Desligue se o aporte já é um gasto cadastrado (os fixos em "Investir" contam como sobra).</FieldDescription>
            </FieldGroup>
          </CardContent>
        )}
      </Card>
    </div>
  )
}
