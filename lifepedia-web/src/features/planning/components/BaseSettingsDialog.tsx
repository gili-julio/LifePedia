import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { amountToInput, parseAmount } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useSaveSettings } from '../api'
import type { AverageMonths, PlanBase, PlanSettings } from '../types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupId: string
  settings: PlanSettings
  base: PlanBase
}

const AVERAGES: { value: AverageMonths; label: string }[] = [
  { value: 0, label: 'Não considerar (só fixos)' },
  { value: 3, label: 'Média dos últimos 3 meses' },
  { value: 6, label: 'Média dos últimos 6 meses' },
  { value: 12, label: 'Média dos últimos 12 meses' },
]

/** Aceita valor negativo ("-500" ou "−500"). */
function parseSigned(raw: string) {
  const trimmed = raw.trim()
  const negative = /^[-−]/.test(trimmed)
  const value = parseAmount(trimmed.replace(/^[-−]\s*/, ''))
  return negative ? -value : value
}

/** Base da simulação: reserva de hoje e saldo previsto por mês (automático ou definido pelo grupo). */
export function BaseSettingsDialog({ open, onOpenChange, groupId, settings, base }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>Base do planejamento</DialogTitle>
          <DialogDescription>Vale para todos do grupo.</DialogDescription>
        </DialogHeader>
        {open && <SettingsForm groupId={groupId} settings={settings} base={base} onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  )
}

function SettingsForm({ groupId, settings, base, onDone }: { groupId: string; settings: PlanSettings; base: PlanBase; onDone: () => void }) {
  const { formatCurrency } = useMoney()
  const save = useSaveSettings()
  const [reserve, setReserve] = useState(amountToInput(settings.reserve))
  const [mode, setMode] = useState<'auto' | 'manual'>(settings.manualBalance === null ? 'auto' : 'manual')
  const [average, setAverage] = useState<AverageMonths>(settings.averageMonths)
  const [manual, setManual] = useState(settings.manualBalance === null ? '' : amountToInput(settings.manualBalance).replace('-', '−'))
  const [errors, setErrors] = useState<{ reserve?: string; manual?: string }>({})

  async function submit() {
    const reserveValue = reserve.trim() ? parseAmount(reserve) : 0
    const manualValue = parseSigned(manual)
    const next = {
      reserve: Number.isNaN(reserveValue) ? 'Valor inválido.' : undefined,
      manual: mode === 'manual' && Number.isNaN(manualValue) ? 'Informe o valor por mês.' : undefined,
    }
    setErrors(next)
    if (next.reserve || next.manual) return
    try {
      await save.mutateAsync({
        groupId,
        data: { reserve: reserveValue, averageMonths: average, manualBalance: mode === 'manual' ? manualValue : null },
      })
      toast.success('Base atualizada.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  const fixedBalance = base.fixedIncome - base.fixedExpense

  return (
    <>
      <FieldGroup className="gap-4">
        <Field data-invalid={!!errors.reserve}>
          <FieldLabel htmlFor="plan-reserve">Reserva guardada fora dos lançamentos (R$)</FieldLabel>
          <Input id="plan-reserve" inputMode="decimal" placeholder="0,00" value={reserve} onChange={(e) => setReserve(e.target.value)} />
          <FieldDescription>
            Quanto o grupo já tinha guardado. Despesas do grupo em categoria "Investir" ou "Investimento" somam sozinhas
            {settings.invested > 0 && <> (hoje: {formatCurrency(settings.invested)})</>}.
          </FieldDescription>
          <FieldError>{errors.reserve}</FieldError>
        </Field>

        <Field>
          <FieldLabel>Saldo previsto por mês</FieldLabel>
          <RadioGroup value={mode} onValueChange={(v) => setMode(v as 'auto' | 'manual')} className="gap-2">
            <Label htmlFor="plan-mode-auto"
              className="flex cursor-pointer items-start gap-2.5 rounded-lg border p-2.5 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-primary/5">
              <RadioGroupItem id="plan-mode-auto" value="auto" className="mt-0.5" />
              <span className="space-y-0.5">
                <span className="block text-sm font-medium">Calcular pelos dados do grupo</span>
                <span className="block text-xs text-muted-foreground">
                  Ganhos fixos − despesas fixas ({formatCurrency(fixedBalance)} neste mês) + média dos ocasionais.
                </span>
              </span>
            </Label>
            <Label htmlFor="plan-mode-manual"
              className="flex cursor-pointer items-start gap-2.5 rounded-lg border p-2.5 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-primary/5">
              <RadioGroupItem id="plan-mode-manual" value="manual" className="mt-0.5" />
              <span className="space-y-0.5">
                <span className="block text-sm font-medium">Definir um valor</span>
                <span className="block text-xs text-muted-foreground">Quanto o grupo consegue guardar por mês.</span>
              </span>
            </Label>
          </RadioGroup>
        </Field>

        {mode === 'auto' ? (
          <Field>
            <FieldLabel htmlFor="plan-average">Ocasionais</FieldLabel>
            <Select value={String(average)} onValueChange={(v) => setAverage(Number(v) as AverageMonths)}>
              <SelectTrigger id="plan-average" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {AVERAGES.map((a) => <SelectItem key={a.value} value={String(a.value)}>{a.label}</SelectItem>)}
              </SelectContent>
            </Select>
            <FieldDescription>Média de (ganhos − despesas) ocasionais dos meses completos que têm lançamentos. Sem nenhum, usa o mês atual.</FieldDescription>
          </Field>
        ) : (
          <Field data-invalid={!!errors.manual}>
            <FieldLabel htmlFor="plan-manual">Valor por mês (R$)</FieldLabel>
            <Input id="plan-manual" inputMode="decimal" placeholder="0,00" value={manual} onChange={(e) => setManual(e.target.value)} />
            <FieldDescription>Use − na frente se o grupo fica no negativo.</FieldDescription>
            <FieldError>{errors.manual}</FieldError>
          </Field>
        )}
      </FieldGroup>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button onClick={submit} disabled={save.isPending}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </>
  )
}
