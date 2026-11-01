import { CalculatorIcon, FilePlus2Icon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate, formatInstantDate, formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { financingUrl, simulatorUrl, useDeleteSimulation, useSavedSimulations } from '../api'
import { conditionLabel, formatRate, kindInfo } from '../labels'
import type { FinancingSummary, SavedSimulation } from '../types'

/** Cartão de um financiamento com o progresso e a próxima parcela. */
export function FinancingCard({ item }: { item: FinancingSummary }) {
  const { formatCurrency } = useMoney()
  const kind = kindInfo(item.kind)
  const p = item.progress
  const mine = item.groupId && item.myShare !== null && p.next ? (p.next.amount * item.myShare) / 100 : null
  return (
    <Link to={financingUrl(item.groupId, item.id)} className="block rounded-2xl focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
      <Card className="h-full transition-colors hover:bg-accent/40 motion-reduce:transition-none">
        <CardContent className="space-y-3">
          <div className="flex items-start gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-data/15 text-data">
              <kind.icon className="size-5" aria-hidden />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold">{item.name}</p>
              <p className="truncate text-xs text-muted-foreground">
                {kind.label}{item.groupName ? ` · ${item.groupName}` : ''}
              </p>
            </div>
            {!p.next && <Badge variant="secondary">Quitado</Badge>}
          </div>
          <div className="space-y-1">
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>{p.paid} de {p.totalMonths} parcelas</span>
              <span>{formatRate(p.percent, 1)} quitado</span>
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-muted" role="presentation">
              <div className="h-full rounded-full bg-data" style={{ width: `${p.percent}%` }} />
            </div>
          </div>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <div>
              <dt className="text-xs text-muted-foreground">Saldo devedor</dt>
              <dd className="font-medium tabular-nums">{formatCurrency(p.balance)}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">{p.next ? `Próxima (${formatDate(p.next.date)})` : 'Terminou em'}</dt>
              <dd className="font-medium tabular-nums">
                {p.next ? formatCurrency(p.next.amount) : p.lastMonth ? formatMonth(p.lastMonth, 'short') : '-'}
              </dd>
              {mine !== null && <dd className="text-xs text-muted-foreground">sua parte {formatCurrency(mine)}</dd>}
            </div>
          </dl>
        </CardContent>
      </Card>
    </Link>
  )
}

export function FinancingGrid({ items, isLoading, onCreate, groupId }: {
  items: FinancingSummary[] | undefined
  isLoading: boolean
  onCreate: () => void
  groupId: string | null
}) {
  if (isLoading || !items) return <Skeleton className="h-40" />
  if (!items.length) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
          <p className="text-sm text-muted-foreground">Nenhum financiamento em andamento.</p>
          <div className="flex flex-wrap justify-center gap-2">
            <Button asChild variant="outline" size="sm"><Link to={simulatorUrl(groupId)}><CalculatorIcon /> Simular</Link></Button>
            <Button size="sm" onClick={onCreate}><FilePlus2Icon /> Cadastrar um que já tenho</Button>
          </div>
        </CardContent>
      </Card>
    )
  }
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {items.map((item) => <FinancingCard key={item.id} item={item} />)}
    </div>
  )
}

/** Simulações salvas; abrir refaz com as taxas do dia. */
export function SavedSimulations({ groupId }: { groupId: string | null }) {
  const { formatCurrency } = useMoney()
  const navigate = useNavigate()
  const { data, isLoading } = useSavedSimulations(groupId)
  const remove = useDeleteSimulation(groupId)
  const [deleting, setDeleting] = useState<SavedSimulation | null>(null)

  async function confirm() {
    if (!deleting) return
    try {
      await remove.mutateAsync(deleting.id)
      toast.success('Simulação excluída.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Simulações salvas</CardTitle>
        <CardDescription>Ao abrir, as parcelas são refeitas com as taxas do dia.</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading || !data ? <Skeleton className="h-20" /> : data.length === 0 ? (
          <div className="flex flex-col items-start gap-3">
            <p className="text-sm text-muted-foreground">Nenhuma simulação salva.</p>
            <Button asChild variant="outline" size="sm"><Link to={simulatorUrl(groupId)}><CalculatorIcon /> Simular agora</Link></Button>
          </div>
        ) : (
          <ul className="divide-y rounded-xl border">
            {data.map((s) => {
              const kind = kindInfo(s.input.kind)
              return (
                <li key={s.id} className="flex items-center gap-2 p-3">
                  <button type="button" className="flex min-w-0 flex-1 items-center gap-3 text-left"
                    onClick={() => navigate(simulatorUrl(groupId), { state: { input: s.input, name: s.name } })}>
                    <kind.icon className="size-5 shrink-0 text-muted-foreground" aria-hidden />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{s.name}</span>
                      <span className="block truncate text-xs text-muted-foreground">
                        {kind.label} {conditionLabel(s.input.kind, s.input.condition).toLowerCase()} · {formatCurrency(s.input.price)}
                        {' · '}{formatInstantDate(s.createdAt)}{groupId && s.createdBy ? ` · ${s.createdBy.name}` : ''}
                      </span>
                    </span>
                  </button>
                  {s.canDelete && (
                    <Button variant="ghost" size="icon-sm" aria-label={`Excluir ${s.name}`} onClick={() => setDeleting(s)}>
                      <Trash2Icon />
                    </Button>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
      <ConfirmDelete open={!!deleting} onOpenChange={(open) => !open && setDeleting(null)} title="Excluir simulação?"
        description={`"${deleting?.name ?? ''}" sai da lista.`} onConfirm={() => void confirm()} />
    </Card>
  )
}
