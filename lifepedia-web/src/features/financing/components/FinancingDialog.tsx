import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { MonthField } from '@/components/MonthField'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useGroup } from '@/features/groups/api'
import { ApiError } from '@/lib/api'
import { addMonths, amountToInput, currentYearMonth, parseAmount } from '@/lib/format'
import { useRateSuggestion, useSaveFinancing } from '../api'
import { conditionLabel, formatRate, INDEX_LABEL, isHousing, KINDS, MONTHS_LIMIT, monthlyFromYearly } from '../labels'
import type { CorrectionIndex, Financing, FinancingKind, FinancingRequest } from '../types'

const REMINDERS = [
  { value: 'none', label: 'Sem aviso' },
  { value: '0', label: 'No dia' },
  { value: '1', label: '1 dia antes' },
  { value: '3', label: '3 dias antes' },
  { value: '5', label: '5 dias antes' },
  { value: '7', label: '7 dias antes' },
  { value: '15', label: '15 dias antes' },
]

const optionalAmount = z.string().refine((v) => !v.trim() || parseAmount(v) >= 0, 'Valor inválido.')
const decimal = (v: string) => Number(v.trim().replace(',', '.'))

const schema = z.object({
  name: z.string().trim().min(1, 'Dê um nome ao financiamento.').max(120, 'Máximo de 120 caracteres.'),
  kind: z.enum(['HOUSE', 'APARTMENT', 'CAR', 'MOTORCYCLE', 'OTHER']),
  condition: z.enum(['NEW', 'USED']),
  price: optionalAmount,
  downPayment: optionalAmount,
  fgts: optionalAmount,
  financed: z.string().refine((v) => parseAmount(v) >= 1, 'Informe o valor financiado.'),
  rate: z.string().refine((v) => /^\d+([.,]\d{1,4})?$/.test(v.trim()), 'Informe a taxa (ex.: 1,25).'),
  rateUnit: z.enum(['month', 'year']),
  system: z.enum(['SAC', 'PRICE']),
  months: z.string().refine((v) => /^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 420, 'Prazo de 1 a 420 meses.'),
  firstMonth: z.string().regex(/^\d{4}-\d{2}$/, 'Informe o mês da 1ª parcela.'),
  dueDay: z.string().refine((v) => /^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 31, 'Dia entre 1 e 31.'),
  insurance: z.string().refine((v) => !v.trim() || /^\d+([.,]\d{1,5})?$/.test(v.trim()), 'Seguro inválido.'),
  monthlyFee: optionalAmount,
  index: z.enum(['NONE', 'TR', 'IPCA', 'INCC']),
  bank: z.string().max(160, 'Nome muito longo.'),
  reminder: z.string(),
  notes: z.string().max(2000, 'Máximo de 2000 caracteres.'),
})

type FormValues = z.infer<typeof schema>

const money = (v: number | null | undefined) => (v === null || v === undefined ? '' : amountToInput(v))
const rateText = (v: number | null | undefined) => (v === null || v === undefined ? '' : String(v).replace('.', ','))

function toForm(f: Partial<FinancingRequest> | Financing | undefined): FormValues {
  const kind = f?.kind ?? 'CAR'
  return {
    name: f?.name ?? '',
    kind,
    condition: f?.condition ?? 'NEW',
    price: money(f?.price),
    downPayment: money(f?.downPayment),
    fgts: money(f?.fgts),
    financed: money(f?.financed),
    rate: rateText(f?.monthlyRate),
    rateUnit: 'month',
    system: f?.system ?? (isHousing(kind) ? 'SAC' : 'PRICE'),
    months: String(f?.months ?? MONTHS_LIMIT[kind].default),
    firstMonth: f?.firstMonth ?? addMonths(currentYearMonth(), 1),
    dueDay: String(f?.dueDay ?? 10),
    insurance: f?.insuranceRate ? rateText(f.insuranceRate) : '',
    monthlyFee: f?.monthlyFee ? money(f.monthlyFee) : '',
    index: f?.index ?? (isHousing(kind) ? 'TR' : 'NONE'),
    bank: f?.bank ?? '',
    reminder: f?.reminderDays === null || f?.reminderDays === undefined ? 'none' : String(f.reminderDays),
    notes: f?.notes ?? '',
  }
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupId: string | null
  /** Edita este; senão cria. */
  financing?: Financing
  /** Valores iniciais ao criar (ex.: vindos da simulação). */
  initial?: Partial<FinancingRequest>
  onSaved?: (financing: Financing) => void
}

/** Dados do contrato: valor financiado, taxa, prazo, 1ª parcela e, no grupo, a parte de cada um. */
export function FinancingDialog({ open, onOpenChange, groupId, financing, initial, onSaved }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{financing ? 'Editar financiamento' : 'Cadastrar financiamento'}</DialogTitle>
          <DialogDescription>Use os dados do contrato ou do boleto. Sem a taxa, escolha uma das médias dos bancos.</DialogDescription>
        </DialogHeader>
        {open && (
          <FinancingForm groupId={groupId} financing={financing} initial={initial}
            onDone={(saved) => { onOpenChange(false); onSaved?.(saved) }} onCancel={() => onOpenChange(false)} />
        )}
      </DialogScrollContent>
    </Dialog>
  )
}

function FinancingForm({ groupId, financing, initial, onDone, onCancel }: {
  groupId: string | null
  financing?: Financing
  initial?: Partial<FinancingRequest>
  onDone: (financing: Financing) => void
  onCancel: () => void
}) {
  const save = useSaveFinancing(groupId)
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toForm(financing ?? initial) })
  const { errors, isSubmitting } = form.formState
  const [kind, rateUnit] = useWatch({ control: form.control, name: ['kind', 'rateUnit'] })
  const suggestion = useRateSuggestion(groupId, kind).data
  const members = useGroup(groupId ?? '').data?.members ?? []
  const [shares, setShares] = useState<Record<string, string>>(() =>
    Object.fromEntries((financing?.members ?? []).map((m) => [m.user.id, rateText(m.share)])))
  const equal = members.length ? Math.floor(10000 / members.length) / 100 : 0
  const shareOf = (userId: string) => {
    if (shares[userId] !== undefined) return shares[userId]
    if (financing) return '0'
    const first = members[0]?.user.id === userId
    return rateText(first ? Math.round((100 - equal * (members.length - 1)) * 100) / 100 : equal)
  }
  const shareTotal = Math.round(members.reduce((sum, m) => sum + (decimal(shareOf(m.user.id)) || 0), 0) * 100) / 100
  const housing = isHousing(kind)

  function changeKind(next: FinancingKind) {
    form.setValue('kind', next)
    form.setValue('index', isHousing(next) ? 'TR' : 'NONE')
    form.setValue('system', isHousing(next) ? 'SAC' : 'PRICE')
    if (!financing) form.setValue('months', String(MONTHS_LIMIT[next].default))
  }

  function applyRate(monthly: number) {
    form.setValue('rateUnit', 'month')
    form.setValue('rate', rateText(monthly), { shouldValidate: true })
  }

  async function onSubmit(v: FormValues) {
    const months = Number(v.months)
    if (months > MONTHS_LIMIT[v.kind].max) {
      form.setError('months', { message: `Máximo de ${MONTHS_LIMIT[v.kind].max} meses para este tipo.` })
      return
    }
    if (groupId && members.length && Math.abs(shareTotal - 100) > 0.001) {
      toast.error('As partes precisam somar 100%.')
      return
    }
    const rate = decimal(v.rate)
    const optional = (raw: string) => (raw.trim() ? parseAmount(raw) : null)
    const data: FinancingRequest = {
      name: v.name.trim(),
      kind: v.kind,
      condition: v.condition,
      price: optional(v.price),
      downPayment: optional(v.downPayment),
      fgts: isHousing(v.kind) ? optional(v.fgts) : null,
      financed: parseAmount(v.financed),
      monthlyRate: Math.round((v.rateUnit === 'year' ? monthlyFromYearly(rate) : rate) * 10000) / 10000,
      system: v.system,
      months,
      firstMonth: v.firstMonth,
      dueDay: Number(v.dueDay),
      insuranceRate: v.insurance.trim() ? decimal(v.insurance) : null,
      monthlyFee: optional(v.monthlyFee),
      index: v.index as CorrectionIndex,
      bank: v.bank.trim() || null,
      reminderDays: v.reminder === 'none' ? null : Number(v.reminder),
      notes: v.notes.trim() || null,
      shares: groupId && members.length ? members.map((m) => ({ userId: m.user.id, share: decimal(shareOf(m.user.id)) || 0 })) : null,
    }
    try {
      const saved = await save.mutateAsync({ id: financing?.id, data })
      toast.success(financing ? 'Financiamento atualizado.' : 'Financiamento cadastrado.')
      onDone(saved)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <>
      <DialogBody>
        <form id="financing-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup className="gap-4">
            <Field data-invalid={!!errors.name}>
              <FieldLabel htmlFor="fin-name">Nome</FieldLabel>
              <Input id="fin-name" placeholder="Ex.: Apartamento do centro" className="text-base md:text-sm"
                aria-invalid={!!errors.name} {...form.register('name')} />
              <FieldError errors={[errors.name]} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="fin-kind">Tipo</FieldLabel>
                <Select value={kind} onValueChange={(v) => changeKind(v as FinancingKind)}>
                  <SelectTrigger id="fin-kind" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {KINDS.map((k) => <SelectItem key={k.kind} value={k.kind}><k.icon /> {k.label}</SelectItem>)}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="fin-condition">Estado</FieldLabel>
                <Controller control={form.control} name="condition" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="fin-condition" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="NEW">{conditionLabel(kind, 'NEW')}</SelectItem>
                      <SelectItem value="USED">{conditionLabel(kind, 'USED')}</SelectItem>
                    </SelectContent>
                  </Select>
                )} />
              </Field>
            </div>
            <div className={housing ? 'grid gap-4 sm:grid-cols-3' : 'grid gap-4 sm:grid-cols-2'}>
              <Field>
                <FieldLabel htmlFor="fin-price">Preço do bem</FieldLabel>
                <Input id="fin-price" inputMode="decimal" placeholder="Opcional" className="text-base md:text-sm" {...form.register('price')} />
              </Field>
              <Field>
                <FieldLabel htmlFor="fin-down">Entrada</FieldLabel>
                <Input id="fin-down" inputMode="decimal" placeholder="Opcional" className="text-base md:text-sm" {...form.register('downPayment')} />
              </Field>
              {housing && (
                <Field>
                  <FieldLabel htmlFor="fin-fgts">FGTS usado</FieldLabel>
                  <Input id="fin-fgts" inputMode="decimal" placeholder="Opcional" className="text-base md:text-sm" {...form.register('fgts')} />
                </Field>
              )}
            </div>
            <Field data-invalid={!!errors.financed}>
              <FieldLabel htmlFor="fin-financed">Valor financiado (R$)</FieldLabel>
              <Input id="fin-financed" inputMode="decimal" placeholder="0,00" className="text-base md:text-sm"
                aria-invalid={!!errors.financed} {...form.register('financed')} />
              <FieldDescription>O valor do contrato, já com tarifas e IOF se foram financiados.</FieldDescription>
              <FieldError errors={[errors.financed]} />
            </Field>
            <Field data-invalid={!!errors.rate}>
              <FieldLabel htmlFor="fin-rate">Taxa de juros (%)</FieldLabel>
              <div className="flex gap-2">
                <Input id="fin-rate" inputMode="decimal" placeholder="Ex.: 1,49" className="text-base md:text-sm"
                  aria-invalid={!!errors.rate} {...form.register('rate')} />
                <Controller control={form.control} name="rateUnit" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger className="w-36 shrink-0" aria-label="Período da taxa"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="month">ao mês</SelectItem>
                      <SelectItem value="year">ao ano</SelectItem>
                    </SelectContent>
                  </Select>
                )} />
              </div>
              {suggestion && rateUnit === 'month' && (
                <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                  Médias dos bancos:
                  {([['Boa', suggestion.low], ['Média', suggestion.median], ['Alta', suggestion.high]] as const).map(([label, value]) => (
                    <Button key={label} type="button" variant="outline" size="xs" onClick={() => applyRate(value)}>
                      {label} {formatRate(value)}
                    </Button>
                  ))}
                </div>
              )}
              <FieldError errors={[errors.rate]} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="fin-system">Sistema</FieldLabel>
                <Controller control={form.control} name="system" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="fin-system" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="SAC">SAC (parcela diminui)</SelectItem>
                      <SelectItem value="PRICE">Price (parcela igual)</SelectItem>
                    </SelectContent>
                  </Select>
                )} />
              </Field>
              <Field data-invalid={!!errors.months}>
                <FieldLabel htmlFor="fin-months">Prazo (meses)</FieldLabel>
                <Input id="fin-months" inputMode="numeric" className="text-base md:text-sm" aria-invalid={!!errors.months}
                  {...form.register('months')} />
                <FieldError errors={[errors.months]} />
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={!!errors.firstMonth}>
                <FieldLabel htmlFor="fin-first">1ª parcela</FieldLabel>
                <Controller control={form.control} name="firstMonth" render={({ field }) => (
                  <MonthField id="fin-first" value={field.value} onChange={field.onChange} invalid={!!errors.firstMonth} />
                )} />
                <FieldError errors={[errors.firstMonth]} />
              </Field>
              <Field data-invalid={!!errors.dueDay}>
                <FieldLabel htmlFor="fin-day">Dia do vencimento</FieldLabel>
                <Input id="fin-day" inputMode="numeric" className="text-base md:text-sm" aria-invalid={!!errors.dueDay}
                  {...form.register('dueDay')} />
                <FieldError errors={[errors.dueDay]} />
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={!!errors.insurance}>
                <FieldLabel htmlFor="fin-insurance">Seguro sobre o saldo (% ao mês)</FieldLabel>
                <Input id="fin-insurance" inputMode="decimal" placeholder={housing ? 'Ex.: 0,025' : 'Opcional'}
                  className="text-base md:text-sm" {...form.register('insurance')} />
                <FieldError errors={[errors.insurance]} />
              </Field>
              <Field>
                <FieldLabel htmlFor="fin-fee">Outros valores fixos da parcela</FieldLabel>
                <Input id="fin-fee" inputMode="decimal" placeholder="Ex.: seguro do imóvel, tarifa" className="text-base md:text-sm"
                  {...form.register('monthlyFee')} />
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="fin-index">Correção do saldo</FieldLabel>
                <Controller control={form.control} name="index" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="fin-index" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {(Object.keys(INDEX_LABEL) as CorrectionIndex[]).map((i) => <SelectItem key={i} value={i}>{INDEX_LABEL[i]}</SelectItem>)}
                    </SelectContent>
                  </Select>
                )} />
                <FieldDescription>Imóvel costuma corrigir pela TR; veículo não tem correção.</FieldDescription>
              </Field>
              <Field>
                <FieldLabel htmlFor="fin-reminder">Avisar do vencimento</FieldLabel>
                <Controller control={form.control} name="reminder" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="fin-reminder" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {REMINDERS.map((r) => <SelectItem key={r.value} value={r.value}>{r.label}</SelectItem>)}
                    </SelectContent>
                  </Select>
                )} />
                <FieldDescription>Também vale para os gastos do bem.</FieldDescription>
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="fin-bank">Banco</FieldLabel>
              <Input id="fin-bank" placeholder="Opcional" className="text-base md:text-sm" {...form.register('bank')} />
            </Field>
            {groupId && members.length > 0 && (
              <Field>
                <FieldLabel>Parte de cada um (%)</FieldLabel>
                <ul className="space-y-2">
                  {members.map((m) => (
                    <li key={m.user.id} className="flex items-center gap-2">
                      <UserAvatar user={m.user} className="size-7" />
                      <span className="min-w-0 flex-1 truncate text-sm">{m.user.name}</span>
                      <Input inputMode="decimal" aria-label={`Parte de ${m.user.name}`} className="w-24 text-right text-base md:text-sm"
                        value={shareOf(m.user.id)} onChange={(e) => setShares((s) => ({ ...s, [m.user.id]: e.target.value }))} />
                    </li>
                  ))}
                </ul>
                <FieldDescription className={Math.abs(shareTotal - 100) > 0.001 ? 'text-destructive' : undefined}>
                  Total: {formatRate(shareTotal)} (precisa dar 100%)
                </FieldDescription>
              </Field>
            )}
            <Field>
              <FieldLabel htmlFor="fin-notes">Observações</FieldLabel>
              <Textarea id="fin-notes" rows={2} className="text-base md:text-sm" {...form.register('notes')} />
            </Field>
          </FieldGroup>
        </form>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>Cancelar</Button>
        <Button type="submit" form="financing-form" disabled={isSubmitting}>
          {isSubmitting && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </>
  )
}
