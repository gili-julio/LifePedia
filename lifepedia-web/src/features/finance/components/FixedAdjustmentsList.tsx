import { XIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useRecurringEntries, useRemoveAdjustment } from '../api'

/** Valores reais do fixo por mês, com opção de voltar ao valor cadastrado. */
export function FixedAdjustmentsList({ entryId }: { entryId: string }) {
  const { formatCurrency } = useMoney()
  const { data } = useRecurringEntries()
  const remove = useRemoveAdjustment()
  const adjustments = data?.find((e) => e.id === entryId)?.adjustments ?? []
  if (!adjustments.length) return null

  async function handleRemove(month: string) {
    try {
      await remove.mutateAsync({ id: entryId, month })
      toast.success('Mês voltou ao valor cadastrado.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível remover.')
    }
  }

  return (
    <div className="space-y-2">
      <p className="text-sm font-medium">Valores reais por mês</p>
      <ul className="divide-y rounded-lg border text-sm">
        {adjustments.map((a) => (
          <li key={a.month} className="flex items-center justify-between gap-2 px-3 py-1.5">
            <span>{formatMonth(a.month)}</span>
            <span className="ml-auto tabular-nums">{formatCurrency(a.amount)}</span>
            <Button type="button" variant="ghost" size="icon-xs" aria-label={`Remover valor de ${formatMonth(a.month)}`}
              disabled={remove.isPending} onClick={() => handleRemove(a.month)}>
              <XIcon />
            </Button>
          </li>
        ))}
      </ul>
    </div>
  )
}
