import { ChevronDownIcon } from 'lucide-react'
import { MonthField } from '@/components/MonthField'
import { Card, CardContent } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { currentYearMonth } from '@/lib/format'
import { cn } from '@/lib/utils'
import { conditionLabel, isHousing, KINDS, MONTHS_LIMIT } from '../labels'
import type { SimForm } from '../sim-form'

type Props = { value: SimForm; onChange: (patch: Partial<SimForm>) => void; priceError?: string }

/** O que a pessoa quer comprar: tipo, estado, preço, entrada e prazo; juros só em "Avançado". */
export function SimulationForm({ value, onChange, priceError }: Props) {
  const housing = isHousing(value.kind)
  const limit = MONTHS_LIMIT[value.kind]
  return (
    <Card>
      <CardContent>
        <FieldGroup className="gap-4">
          <Field>
            <FieldLabel id="sim-kind">O que vai financiar?</FieldLabel>
            <div role="radiogroup" aria-labelledby="sim-kind" className="grid grid-cols-3 gap-2 sm:grid-cols-5 xl:grid-cols-3">
              {KINDS.map((k) => (
                <button key={k.kind} type="button" role="radio" aria-checked={value.kind === k.kind}
                  onClick={() => onChange({ kind: k.kind, months: '' })}
                  className={cn('flex flex-col items-center gap-1 rounded-xl border p-2.5 text-xs font-medium transition-colors motion-reduce:transition-none hover:bg-accent',
                    value.kind === k.kind && 'border-primary bg-accent ring-2 ring-ring/40')}>
                  <k.icon className="size-5" aria-hidden />
                  {k.label}
                </button>
              ))}
            </div>
          </Field>
          <Field>
            <FieldLabel id="sim-condition">Estado</FieldLabel>
            <div role="radiogroup" aria-labelledby="sim-condition" className="flex rounded-full bg-muted p-0.5">
              {(['NEW', 'USED'] as const).map((c) => (
                <button key={c} type="button" role="radio" aria-checked={value.condition === c} onClick={() => onChange({ condition: c })}
                  className={cn('flex-1 rounded-full px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors motion-reduce:transition-none',
                    value.condition === c && 'bg-background text-foreground shadow-sm')}>
                  {conditionLabel(value.kind, c)}
                </button>
              ))}
            </div>
          </Field>
          <Field data-invalid={!!priceError}>
            <FieldLabel htmlFor="sim-price">Preço (R$)</FieldLabel>
            <Input id="sim-price" inputMode="decimal" placeholder="Ex.: 120.000" value={value.price} aria-invalid={!!priceError}
              className="text-base md:text-sm" onChange={(e) => onChange({ price: e.target.value })} />
            {priceError && <p className="text-sm text-destructive">{priceError}</p>}
          </Field>
          <div className={housing ? 'grid grid-cols-2 gap-3' : undefined}>
            <Field>
              <FieldLabel htmlFor="sim-down">Entrada (R$)</FieldLabel>
              <Input id="sim-down" inputMode="decimal" placeholder="0" value={value.downPayment} className="text-base md:text-sm"
                onChange={(e) => onChange({ downPayment: e.target.value })} />
            </Field>
            {housing && (
              <Field>
                <FieldLabel htmlFor="sim-fgts">FGTS (R$)</FieldLabel>
                <Input id="sim-fgts" inputMode="decimal" placeholder="0" value={value.fgts} className="text-base md:text-sm"
                  onChange={(e) => onChange({ fgts: e.target.value })} />
              </Field>
            )}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field>
              <FieldLabel htmlFor="sim-start">Mês da entrada</FieldLabel>
              <MonthField id="sim-start" value={value.startMonth} onChange={(m) => onChange({ startMonth: m || currentYearMonth() })} />
            </Field>
            <Field>
              <FieldLabel htmlFor="sim-months">Prazo (meses)</FieldLabel>
              <Input id="sim-months" inputMode="numeric" placeholder={String(limit.default)} value={value.months}
                className="text-base md:text-sm" onChange={(e) => onChange({ months: e.target.value.replace(/\D/g, '') })} />
            </Field>
          </div>
          <FieldDescription className="-mt-2">1ª parcela no mês seguinte à entrada. Prazo máximo: {limit.max} meses.</FieldDescription>
          <Field>
            <FieldLabel htmlFor="sim-income">Renda mensal (opcional)</FieldLabel>
            <Input id="sim-income" inputMode="decimal" placeholder="Para ver se a parcela cabe" value={value.income}
              className="text-base md:text-sm" onChange={(e) => onChange({ income: e.target.value })} />
          </Field>
          <Collapsible defaultOpen={!!value.rate}>
            <CollapsibleTrigger className="group flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground">
              Avançado: já tenho uma proposta
              <ChevronDownIcon className="size-4 transition-transform motion-reduce:transition-none group-data-[state=open]:rotate-180" aria-hidden />
            </CollapsibleTrigger>
            <CollapsibleContent className="pt-3">
              <Field>
                <FieldLabel htmlFor="sim-rate">Taxa da proposta (%)</FieldLabel>
                <div className="flex gap-2">
                  <Input id="sim-rate" inputMode="decimal" placeholder="Ex.: 1,39" value={value.rate} className="text-base md:text-sm"
                    onChange={(e) => onChange({ rate: e.target.value })} />
                  <Select value={value.rateUnit} onValueChange={(v) => onChange({ rateUnit: v as SimForm['rateUnit'] })}>
                    <SelectTrigger className="w-32 shrink-0" aria-label="Período da taxa"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="month">ao mês</SelectItem>
                      <SelectItem value="year">ao ano</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <FieldDescription>Sem taxa, usamos as médias dos bancos divulgadas pelo Banco Central.</FieldDescription>
              </Field>
            </CollapsibleContent>
          </Collapsible>
        </FieldGroup>
      </CardContent>
    </Card>
  )
}
