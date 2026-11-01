import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { MonthField } from '@/components/MonthField'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { addMonths, amountToInput, currentYearMonth, formatMonth, parseAmount } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn, skipAutoFocusOnTouch } from '@/lib/utils'
import { previewExtra, useAddExtra, useSaveCost } from '../api'
import { MONTH_NAMES } from '../labels'
import type { ExtraMode, ExtraPreview, Financing, FinancingCost, Frequency } from '../types'

const MODES: { mode: ExtraMode; label: string; hint: string }[] = [
  { mode: 'TERM', label: 'Reduzir o prazo', hint: 'Parcela parecida, termina antes. Economiza mais juros.' },
  { mode: 'PAYMENT', label: 'Reduzir a parcela', hint: 'Mesmo prazo, parcela menor daqui em diante.' },
]

/** Simula e registra uma amortização extra. */
export function ExtraDialog({ open, onOpenChange, financing }: { open: boolean; onOpenChange: (open: boolean) => void; financing: Financing }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>Amortização extra</DialogTitle>
          <DialogDescription>Veja quanto economiza antes de registrar. Registrar muda as parcelas e o fixo ligado.</DialogDescription>
        </DialogHeader>
        {open && <ExtraForm financing={financing} onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  )
}

function ExtraForm({ financing, onDone }: { financing: Financing; onDone: () => void }) {
  const { formatCurrency } = useMoney()
  const add = useAddExtra(financing.groupId, financing.id)
  const [month, setMonth] = useState(financing.progress.next?.month ?? addMonths(currentYearMonth(), 1))
  const [amount, setAmount] = useState('')
  const [mode, setMode] = useState<ExtraMode>('TERM')
  const [preview, setPreview] = useState<ExtraPreview | null>(null)
  const [loading, setLoading] = useState(false)
  const value = parseAmount(amount)
  const valid = value >= 1 && !!month

  function reset() {
    setPreview(null)
  }

  async function simulate() {
    if (!valid) return
    setLoading(true)
    try {
      setPreview(await previewExtra(financing.groupId, financing.id, { month, amount: value, mode }))
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível simular.')
    } finally {
      setLoading(false)
    }
  }

  async function register() {
    try {
      await add.mutateAsync({ month, amount: value, mode })
      toast.success('Amortização registrada.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível registrar.')
    }
  }

  return (
    <>
      <FieldGroup className="gap-4">
        <div className="grid grid-cols-2 gap-3">
          <Field>
            <FieldLabel htmlFor="extra-month">Mês</FieldLabel>
            <MonthField id="extra-month" value={month} onChange={(m) => { setMonth(m || month); reset() }} />
          </Field>
          <Field>
            <FieldLabel htmlFor="extra-amount">Valor (R$)</FieldLabel>
            <Input id="extra-amount" inputMode="decimal" placeholder="0,00" value={amount} className="text-base md:text-sm"
              onChange={(e) => { setAmount(e.target.value); reset() }} />
          </Field>
        </div>
        <div role="radiogroup" aria-label="O que reduzir" className="grid gap-2">
          {MODES.map((m) => (
            <button key={m.mode} type="button" role="radio" aria-checked={mode === m.mode}
              onClick={() => { setMode(m.mode); reset() }}
              className={cn('rounded-xl border p-3 text-left hover:bg-accent/50', mode === m.mode && 'border-primary ring-2 ring-ring/40')}>
              <span className="block text-sm font-medium">{m.label}</span>
              <span className="block text-xs text-muted-foreground">{m.hint}</span>
            </button>
          ))}
        </div>
        {preview && (
          <dl className="grid grid-cols-2 gap-3 rounded-xl bg-muted/60 p-3 text-sm">
            <div>
              <dt className="text-xs text-muted-foreground">Economia de juros</dt>
              <dd className="font-semibold text-income tabular-nums">{formatCurrency(preview.interestSaved)}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Paga a menos no total</dt>
              <dd className="font-semibold tabular-nums">{formatCurrency(preview.totalSaved)}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Prazo</dt>
              <dd className="tabular-nums">{preview.monthsBefore} → {preview.monthsAfter} parcelas</dd>
              {preview.lastMonthAfter && <dd className="text-xs text-muted-foreground">termina em {formatMonth(preview.lastMonthAfter, 'short')}</dd>}
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Parcela seguinte</dt>
              <dd className="tabular-nums">{formatCurrency(preview.nextPaymentBefore)} → {formatCurrency(preview.nextPaymentAfter)}</dd>
            </div>
          </dl>
        )}
      </FieldGroup>
      <DialogFooter>
        <Button type="button" variant="outline" disabled={!valid || loading} onClick={() => void simulate()}>
          {loading && <Loader2Icon className="animate-spin" />} Ver economia
        </Button>
        <Button type="button" disabled={!valid || add.isPending} onClick={() => void register()}>
          {add.isPending && <Loader2Icon className="animate-spin" />} Registrar
        </Button>
      </DialogFooter>
    </>
  )
}

/** Gasto de ter o bem (IPVA, seguro, IPTU, condomínio...). */
export function CostDialog({ open, onOpenChange, financing, cost }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  financing: Financing
  cost?: FinancingCost
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>{cost ? 'Editar gasto' : 'Novo gasto do bem'}</DialogTitle>
          <DialogDescription>Fica listado no financiamento e, com o aviso ligado, lembra antes de vencer.</DialogDescription>
        </DialogHeader>
        {open && <CostForm financing={financing} cost={cost} onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  )
}

function CostForm({ financing, cost, onDone }: { financing: Financing; cost?: FinancingCost; onDone: () => void }) {
  const save = useSaveCost(financing.groupId, financing.id)
  const [name, setName] = useState(cost?.name ?? '')
  const [amount, setAmount] = useState(cost ? amountToInput(cost.amount) : '')
  const [frequency, setFrequency] = useState<Frequency>(cost?.frequency ?? 'YEARLY')
  const [dueMonth, setDueMonth] = useState(String(cost?.dueMonth ?? 1))
  const [dueDay, setDueDay] = useState(String(cost?.dueDay ?? 10))
  const value = parseAmount(amount)
  const day = Number(dueDay)
  const valid = !!name.trim() && value > 0 && day >= 1 && day <= 31

  async function submit() {
    if (!valid) return
    try {
      await save.mutateAsync({
        costId: cost?.id,
        data: { name: name.trim(), amount: value, frequency, dueMonth: frequency === 'YEARLY' ? Number(dueMonth) : null, dueDay: day },
      })
      toast.success(cost ? 'Gasto atualizado.' : 'Gasto adicionado.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <FieldGroup className="gap-4">
        <Field>
          <FieldLabel htmlFor="cost-name">Nome</FieldLabel>
          <Input id="cost-name" placeholder="Ex.: IPVA" maxLength={80} value={name} className="text-base md:text-sm"
            onChange={(e) => setName(e.target.value)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field>
            <FieldLabel htmlFor="cost-amount">Valor (R$)</FieldLabel>
            <Input id="cost-amount" inputMode="decimal" placeholder="0,00" value={amount} className="text-base md:text-sm"
              onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="cost-frequency">Repete</FieldLabel>
            <Select value={frequency} onValueChange={(v) => setFrequency(v as Frequency)}>
              <SelectTrigger id="cost-frequency" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="YEARLY">Todo ano</SelectItem>
                <SelectItem value="MONTHLY">Todo mês</SelectItem>
              </SelectContent>
            </Select>
          </Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          {frequency === 'YEARLY' && (
            <Field>
              <FieldLabel htmlFor="cost-month">Mês</FieldLabel>
              <Select value={dueMonth} onValueChange={setDueMonth}>
                <SelectTrigger id="cost-month" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {MONTH_NAMES.map((m, i) => <SelectItem key={m} value={String(i + 1)} className="capitalize">{m}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          )}
          <Field>
            <FieldLabel htmlFor="cost-day">Dia</FieldLabel>
            <Input id="cost-day" inputMode="numeric" value={dueDay} className="text-base md:text-sm"
              onChange={(e) => setDueDay(e.target.value.replace(/\D/g, ''))} />
            <FieldDescription>Dia do vencimento.</FieldDescription>
          </Field>
        </div>
      </FieldGroup>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" disabled={!valid || save.isPending}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}
