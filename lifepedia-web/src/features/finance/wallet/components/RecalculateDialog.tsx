import { ArrowRightIcon, Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { MonthField } from '@/components/MonthField'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { addMonths, currentYearMonth, formatDate, formatMonth, type YearMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useRecalculate, useRecalculatePreview } from '../api'
import type { CardView, RecalculateItem } from '../types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  card: CardView
}

/** Lançamentos feitos à mão que estão numa fatura diferente da do ciclo: mostra antes e só muda os marcados. */
export function RecalculateDialog({ open, onOpenChange, card }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Recalcular faturas</DialogTitle>
          <DialogDescription>
            Só lançamentos feitos à mão e ainda não conferidos. Os que vieram de fatura importada ficam como estão.
          </DialogDescription>
        </DialogHeader>
        <RecalculateForm card={card} onDone={() => onOpenChange(false)} />
      </DialogScrollContent>
    </Dialog>
  )
}

function RecalculateForm({ card, onDone }: { card: CardView; onDone: () => void }) {
  const [from, setFrom] = useState<YearMonth>(addMonths(currentYearMonth(), -2))
  const preview = useRecalculatePreview(card.id, from, true)
  const recalculate = useRecalculate()
  const [unchecked, setUnchecked] = useState<Set<string>>(new Set())
  const items = preview.data ?? []
  const chosen = items.filter((i) => !unchecked.has(i.id))

  function toggle(item: RecalculateItem, checked: boolean) {
    setUnchecked((current) => {
      const next = new Set(current)
      if (checked) next.delete(item.id)
      else next.add(item.id)
      return next
    })
  }

  async function apply() {
    try {
      const result = await recalculate.mutateAsync({ cardId: card.id, from, ids: chosen.map((i) => i.id) })
      toast.success(`${result.moved} lançamento(s) movido(s) para a fatura certa.`)
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível recalcular.')
    }
  }

  return (
    <>
      <DialogBody className="space-y-4">
        <Field>
          <FieldLabel htmlFor="recalc-from">Compras a partir de</FieldLabel>
          <MonthField id="recalc-from" value={from} onChange={(m) => m && setFrom(m)} />
        </Field>
        {preview.isLoading ? <Skeleton className="h-24" /> : !items.length ? (
          <p className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">
            Tudo certo: nenhum lançamento fora da fatura do ciclo nesse período.
          </p>
        ) : (
          <ul className="divide-y rounded-lg border">
            {items.map((item) => <Row key={item.id} item={item} checked={!unchecked.has(item.id)} onToggle={(v) => toggle(item, v)} />)}
          </ul>
        )}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button onClick={apply} disabled={!chosen.length || recalculate.isPending}>
          {recalculate.isPending && <Loader2Icon className="animate-spin" />}
          Mover {chosen.length || ''}
        </Button>
      </DialogFooter>
    </>
  )
}

function Row({ item, checked, onToggle }: { item: RecalculateItem; checked: boolean; onToggle: (checked: boolean) => void }) {
  const { formatCurrency } = useMoney()
  return (
    <li>
      <Label htmlFor={`recalc-${item.id}`} className="flex cursor-pointer items-start gap-3 p-3 font-normal">
        <Checkbox id={`recalc-${item.id}`} checked={checked} onCheckedChange={(v) => onToggle(v === true)} className="mt-0.5" />
        <span className="min-w-0 flex-1 space-y-0.5">
          <span className="flex gap-2">
            <span className="min-w-0 flex-1 truncate text-sm font-medium">{item.description}</span>
            <span className="shrink-0 text-sm tabular-nums">{formatCurrency(item.amount)}</span>
          </span>
          <span className="flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
            {formatDate(item.date)}: {formatMonth(item.month, 'short')} <ArrowRightIcon className="size-3" aria-label="para" />
            <span className="font-medium text-foreground">{formatMonth(item.expected, 'short')}</span>
          </span>
        </span>
      </Label>
    </li>
  )
}
