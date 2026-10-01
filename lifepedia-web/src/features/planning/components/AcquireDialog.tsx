import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DatePicker } from '@/components/DatePicker'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { CategorySelect } from '@/features/finance/components/CategorySelect'
import { ApiError } from '@/lib/api'
import { amountToInput, parseAmount, todayIso } from '@/lib/format'
import { useAcquireItem } from '../api'
import type { PlanItem } from '../types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupId: string
  item: PlanItem | undefined
}

/** Marca como comprado/feito; opcionalmente lança a despesa no grupo (em nome de quem marca). */
export function AcquireDialog({ open, onOpenChange, groupId, item }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{item?.kind === 'INVESTMENT' ? 'Marcar como feito' : 'Marcar como comprado'}</DialogTitle>
          <DialogDescription>{item?.name} sai da simulação e vai para "Adquiridos".</DialogDescription>
        </DialogHeader>
        {open && item && <AcquireForm groupId={groupId} item={item} onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  )
}

function AcquireForm({ groupId, item, onDone }: { groupId: string; item: PlanItem; onDone: () => void }) {
  const acquire = useAcquireItem()
  const [date, setDate] = useState(todayIso())
  const [amount, setAmount] = useState(amountToInput(item.amount))
  const [launch, setLaunch] = useState(true)
  const [categoryId, setCategoryId] = useState('')
  const [errors, setErrors] = useState<{ amount?: string; category?: string }>({})

  async function submit() {
    const value = parseAmount(amount)
    const next = {
      amount: value > 0 ? undefined : 'Informe um valor maior que zero.',
      category: launch && !categoryId ? 'Escolha a categoria.' : undefined,
    }
    setErrors(next)
    if (next.amount || next.category) return
    try {
      await acquire.mutateAsync({ groupId, id: item.id, data: { date, amount: value, launch, categoryId: launch ? categoryId : null } })
      toast.success(launch ? 'Marcado e lançado no grupo.' : 'Marcado como adquirido.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <>
      <FieldGroup className="gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="acquire-date">Data</FieldLabel>
            <DatePicker id="acquire-date" value={date} onChange={setDate} />
          </Field>
          <Field data-invalid={!!errors.amount}>
            <FieldLabel htmlFor="acquire-amount">Valor pago (R$)</FieldLabel>
            <Input id="acquire-amount" inputMode="decimal" value={amount} aria-invalid={!!errors.amount}
              onChange={(e) => setAmount(e.target.value)} />
            <FieldError>{errors.amount}</FieldError>
          </Field>
        </div>
        <Label htmlFor="acquire-launch" className="flex cursor-pointer items-start gap-2.5 rounded-lg border p-2.5 font-normal">
          <Checkbox id="acquire-launch" checked={launch} onCheckedChange={(v) => setLaunch(v === true)} className="mt-0.5" />
          <span className="space-y-0.5">
            <span className="block text-sm font-medium">Lançar como despesa no grupo</span>
            <span className="block text-xs text-muted-foreground">
              Em seu nome, com a sua categoria
              {item.installments > 1 ? `, em ${item.installments} parcelas mensais a partir do mês da data.` : '.'}
            </span>
          </span>
        </Label>
        {launch && (
          <Field data-invalid={!!errors.category}>
            <FieldLabel htmlFor="acquire-category">Categoria</FieldLabel>
            <CategorySelect id="acquire-category" type="EXPENSE" value={categoryId} onChange={setCategoryId} invalid={!!errors.category} />
            <FieldDescription>Das suas categorias de despesa.</FieldDescription>
            <FieldError>{errors.category}</FieldError>
          </Field>
        )}
      </FieldGroup>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button onClick={submit} disabled={acquire.isPending}>
          {acquire.isPending && <Loader2Icon className="animate-spin" />} Confirmar
        </Button>
      </DialogFooter>
    </>
  )
}
