import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { MonthField } from '@/components/MonthField'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { ApiError } from '@/lib/api'
import { addMonths, formatMonth, type YearMonth } from '@/lib/format'
import { parseInstallment } from '../installments'

export type MovableItem = { id: string; description: string; date: string; month: YearMonth }

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  item?: MovableItem
  onMove: (data: { month: YearMonth; following: boolean }) => Promise<{ moved: number }>
}

/** Adianta ou adia uma parcela (e, se quiser, as seguintes da mesma compra). */
export function MoveInstallmentDialog({ open, onOpenChange, item, onMove }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Adiantar ou adiar parcela</DialogTitle>
          <DialogDescription className="break-words">{item?.description}</DialogDescription>
        </DialogHeader>
        {item && <MoveForm item={item} onMove={onMove} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function MoveForm({ item, onMove, onDone }: { item: MovableItem; onMove: Props['onMove']; onDone: () => void }) {
  const installment = parseInstallment(item.description)
  const bought = item.date.slice(0, 7) as YearMonth
  const [month, setMonth] = useState<YearMonth | ''>(addMonths(item.month, 1))
  const [scope, setScope] = useState<'one' | 'following'>('one')
  const [busy, setBusy] = useState(false)
  const error = !month ? 'Escolha o mês.' : month < bought ? 'Não pode ser antes do mês da compra.' : undefined
  const hasFollowing = !!installment && installment.number < installment.total

  async function submit() {
    if (error || !month) return
    setBusy(true)
    try {
      const { moved } = await onMove({ month, following: scope === 'following' })
      toast.success(moved > 1 ? `${moved} parcelas mudaram de mês.` : `Parcela movida para ${formatMonth(month)}.`)
      onDone()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível mover a parcela.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <DialogBody>
        <p className="text-sm text-muted-foreground">Hoje conta em <span className="font-medium text-foreground">{formatMonth(item.month)}</span>.</p>
        <Field data-invalid={!!error}>
          <FieldLabel htmlFor="move-month">Passa a contar em</FieldLabel>
          <MonthField id="move-month" value={month} onChange={setMonth} invalid={!!error} />
          {error ? <FieldError>{error}</FieldError>
            : <FieldDescription>{month < item.month ? 'Adiantando.' : month > item.month ? 'Adiando.' : 'Mesmo mês.'}</FieldDescription>}
        </Field>
        {hasFollowing && (
          <RadioGroup value={scope} onValueChange={(v) => setScope(v as 'one' | 'following')} className="gap-2">
            <ScopeOption value="one" title="Só esta parcela" description="As outras continuam onde estão." />
            <ScopeOption value="following" title="Esta e as seguintes"
              description={`Da ${installment.number}/${installment.total} até a ${installment.total}/${installment.total}, todas com o mesmo deslocamento.`} />
          </RadioGroup>
        )}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button disabled={busy || !!error || month === item.month} onClick={submit}>
          {busy && <Loader2Icon className="animate-spin" />}
          Mover
        </Button>
      </DialogFooter>
    </>
  )
}

function ScopeOption({ value, title, description }: { value: string; title: string; description: string }) {
  const id = `move-scope-${value}`
  return (
    <Label htmlFor={id}
      className="flex cursor-pointer items-start gap-2.5 rounded-lg border p-2.5 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-primary/5">
      <RadioGroupItem id={id} value={value} className="mt-0.5" />
      <span className="space-y-0.5">
        <span className="block text-sm font-medium">{title}</span>
        <span className="block text-xs text-muted-foreground">{description}</span>
      </span>
    </Label>
  )
}
