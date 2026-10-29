import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { useCategories, useRecurringEntries, useRemoveAdjustment, useSaveAdjustment } from '@/features/finance/api'
import { isActiveIn, valueIn } from '@/features/finance/fixed'
import type { RecurringEntry } from '@/features/finance/types'
import { ApiError } from '@/lib/api'
import { amountToInput, formatMonth, parseAmount, type YearMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'

/** Fixo ligado a uma tarefa concluída e o mês da ocorrência. */
export type FixedPayment = { entryId: string; month: YearMonth; title: string }

type Props = {
  payment: FixedPayment | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Depois de concluir a tarefa ligada a um fixo: confirma o valor do mês (diferente do cadastrado vira ajuste do mês). */
export function FixedPaymentDialog({ payment, open, onOpenChange }: Props) {
  const { data: entries } = useRecurringEntries()
  const entry = payment ? entries?.find((e) => e.id === payment.entryId) : undefined

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Registrar no financeiro?</DialogTitle>
          <DialogDescription>{payment ? `“${payment.title}” está ligada a um fixo.` : ''}</DialogDescription>
        </DialogHeader>
        {payment && !entries && <DialogBody><Skeleton className="h-24 w-full" /></DialogBody>}
        {payment && entries && !entry && (
          <DialogBody><p className="text-sm text-muted-foreground">O fixo ligado não existe mais.</p></DialogBody>
        )}
        {payment && entry && (
          <PaymentForm key={`${entry.id}|${payment.month}`} entry={entry} month={payment.month}
            onDone={() => onOpenChange(false)} />
        )}
      </DialogScrollContent>
    </Dialog>
  )
}

function PaymentForm({ entry, month, onDone }: { entry: RecurringEntry; month: YearMonth; onDone: () => void }) {
  const { formatCurrency } = useMoney()
  const { data: categories } = useCategories()
  const save = useSaveAdjustment()
  const remove = useRemoveAdjustment()
  const current = valueIn(entry, month)
  const [value, setValue] = useState(amountToInput(current))
  const [error, setError] = useState<string | null>(null)
  const category = categories?.find((c) => c.id === entry.categoryId)
  const adjusted = entry.adjustments.some((a) => a.month === month)
  const busy = save.isPending || remove.isPending

  if (!isActiveIn(entry, month)) {
    return (
      <>
        <DialogBody>
          <p className="text-sm text-muted-foreground">
            O fixo “{entry.description}” não vale em {formatMonth(month).toLowerCase()} (inativo ou fora do período).
          </p>
        </DialogBody>
        <DialogFooter><Button variant="outline" onClick={onDone}>Fechar</Button></DialogFooter>
      </>
    )
  }

  async function submit() {
    const amount = parseAmount(value)
    if (!(amount > 0)) {
      setError('Informe um valor maior que zero.')
      return
    }
    try {
      // Igual ao cadastrado: não precisa de ajuste (e o que houver sai).
      if (amount === entry.amount) {
        if (adjusted) await remove.mutateAsync({ id: entry.id, month })
      } else if (amount !== current || !adjusted) {
        await save.mutateAsync({ id: entry.id, month, amount })
      }
      toast.success(`Valor de ${formatMonth(month).toLowerCase()} registrado: ${formatCurrency(amount)}.`)
      onDone()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível registrar.')
    }
  }

  return (
    <>
      <DialogBody>
        <form id="fixed-payment" className="space-y-4" noValidate onSubmit={(e) => {
          e.preventDefault()
          void submit()
        }}>
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
            <dt className="text-muted-foreground">Fixo</dt>
            <dd className="min-w-0 truncate font-medium">{entry.description}</dd>
            <dt className="text-muted-foreground">Categoria</dt>
            <dd className="min-w-0 truncate">{category?.name ?? '—'}</dd>
            <dt className="text-muted-foreground">Mês</dt>
            <dd>{formatMonth(month)}</dd>
          </dl>
          <Field data-invalid={!!error}>
            <FieldLabel htmlFor="fixed-amount">Valor no mês (R$)</FieldLabel>
            <Input id="fixed-amount" inputMode="decimal" autoFocus value={value} aria-invalid={!!error}
              onChange={(e) => {
                setValue(e.target.value)
                setError(null)
              }} />
            <FieldDescription>Cadastrado: {formatCurrency(entry.amount)}. Valor diferente vale só neste mês.</FieldDescription>
            {error && <FieldError>{error}</FieldError>}
          </Field>
        </form>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Agora não</Button>
        <Button type="submit" form="fixed-payment" disabled={busy}>
          {busy && <Loader2Icon className="animate-spin" />} Registrar
        </Button>
      </DialogFooter>
    </>
  )
}
