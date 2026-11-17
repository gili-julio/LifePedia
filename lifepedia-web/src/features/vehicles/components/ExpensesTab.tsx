import { FuelIcon, PaperclipIcon, PencilIcon, PlusIcon, Trash2Icon, WalletIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useDeleteExpense, useExpenses } from '../api'
import { EXPENSE_LABEL, canAct, formatKm, formatNumber } from '../labels'
import type { Expense, ExpenseKind, VehicleCard } from '../types'
import { ExpenseDialog } from './ExpenseDialog'
import { RowMenu } from './RowMenu'

const PERIODS = [3, 12, 36] as const

/** Gastos do período (mais novo primeiro): quem lançou, km, litros e comprovante. */
export function ExpensesTab({ vehicle }: { vehicle: VehicleCard }) {
  const { formatCurrency } = useMoney()
  const [months, setMonths] = useState(12)
  const { data, isError } = useExpenses(vehicle.id, months)
  const remove = useDeleteExpense()
  const [dialog, setDialog] = useState<{ open: boolean; expense: Expense | null; kind?: ExpenseKind }>({ open: false, expense: null })
  const [deleting, setDeleting] = useState<Expense | null>(null)
  const canLog = canAct(vehicle.role, 'LOG')

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Select value={String(months)} onValueChange={(v) => setMonths(Number(v))}>
          <SelectTrigger className="w-44" size="sm" aria-label="Período"><SelectValue /></SelectTrigger>
          <SelectContent>{PERIODS.map((p) => <SelectItem key={p} value={String(p)}>Últimos {p} meses</SelectItem>)}</SelectContent>
        </Select>
        {canLog && (
          <div className="flex gap-2">
            <Button size="sm" variant="outline" onClick={() => setDialog({ open: true, expense: null, kind: 'FUEL' })}><FuelIcon /> Abastecer</Button>
            <Button size="sm" onClick={() => setDialog({ open: true, expense: null, kind: 'SERVICE' })}><PlusIcon /> Gasto</Button>
          </div>
        )}
      </div>

      {isError ? <p className="text-sm text-destructive">Não foi possível carregar os gastos.</p>
        : !data ? <Skeleton className="h-48" />
        : !data.length ? (
          <Card><CardContent className="py-8 text-center text-sm text-muted-foreground">Nenhum gasto no período.</CardContent></Card>
        ) : (
          <Card>
            <CardContent className="px-0">
              <ul className="divide-y">
                {data.map((e) => (
                  <li key={e.id} className="flex items-start gap-3 px-4 py-3">
                    <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
                      {e.kind === 'FUEL' ? <FuelIcon className="size-4" aria-hidden /> : <WalletIcon className="size-4" aria-hidden />}
                    </span>
                    <div className="min-w-0 flex-1 space-y-0.5">
                      <div className="flex items-baseline justify-between gap-2">
                        <p className="min-w-0 truncate text-sm font-medium">{e.description ?? EXPENSE_LABEL[e.kind]}</p>
                        <span className="shrink-0 text-sm font-semibold tabular-nums">{formatCurrency(e.amount)}</span>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {[formatDate(e.date), e.description ? EXPENSE_LABEL[e.kind] : null, e.odometer != null ? formatKm(e.odometer) : null,
                          e.liters != null ? `${formatNumber(e.liters)} L` : null,
                          e.pricePerLiter != null ? `${formatCurrency(e.pricePerLiter)}/L` : null,
                          e.fullTank ? 'tanque cheio' : null, e.station].filter(Boolean).join(' · ')}
                      </p>
                      <div className="flex flex-wrap items-center gap-1.5">
                        {!e.mine && e.user && <Badge variant="secondary">{e.user.name}</Badge>}
                        {e.finance && <Badge variant="outline">No financeiro</Badge>}
                        {e.imageUrl && (
                          <a href={e.imageUrl} target="_blank" rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-xs underline-offset-2 hover:underline">
                            <PaperclipIcon className="size-3" aria-hidden /> Comprovante
                          </a>
                        )}
                      </div>
                    </div>
                    <RowMenu label={e.description ?? EXPENSE_LABEL[e.kind]} actions={[
                      ...(e.mine ? [{ label: 'Editar', icon: PencilIcon, onSelect: () => setDialog({ open: true, expense: e }) }] : []),
                      ...(e.canDelete ? [{ label: 'Excluir', icon: Trash2Icon, destructive: true, onSelect: () => setDeleting(e) }] : []),
                    ]} />
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        )}

      <ExpenseDialog open={dialog.open} onOpenChange={(open) => setDialog((d) => ({ ...d, open }))} vehicle={vehicle}
        expense={dialog.expense} kind={dialog.kind} />
      <ConfirmDelete open={!!deleting} onOpenChange={(open) => { if (!open) setDeleting(null) }} title="Excluir este gasto?"
        description={deleting?.mine && deleting.finance ? 'O lançamento no seu financeiro sai junto.'
          : deleting && !deleting.mine ? 'Some do veículo; o financeiro de quem lançou não muda.' : 'Some do veículo.'}
        onConfirm={async () => {
          if (!deleting) return
          try {
            await remove.mutateAsync({ id: vehicle.id, expenseId: deleting.id })
            toast.success('Gasto excluído.')
          } catch (error) {
            toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
          }
          setDeleting(null)
        }} />
    </div>
  )
}
