import { CalendarPlusIcon, CheckIcon, ListPlusIcon, PencilIcon, PlusIcon, RotateCcwIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useCare, useServiceLogs, useTemplates, type CareAction } from '../api'
import { canAct, dueText, formatKm, intervalText } from '../labels'
import type { Template, VehicleDetail, VehicleItem, VehicleRevision } from '../types'
import { DoneDialog, ItemDialog, PlanDialog, RevisionDialog } from './CareDialogs'
import { RowMenu } from './RowMenu'
import { StageBadge } from './VehicleCard'

type Done = { kind: 'item'; item: VehicleItem } | { kind: 'revision'; revision: VehicleRevision; items: VehicleItem[] }
type Removing = { label: string; action: CareAction } | null

/** Itens periódicos, plano de revisões e o histórico do que foi feito. */
export function CareTab({ detail }: { detail: VehicleDetail }) {
  const vehicle = detail.vehicle
  const canLog = canAct(vehicle.role, 'LOG')
  const canEdit = canAct(vehicle.role, 'EDIT')
  const care = useCare()
  const [item, setItem] = useState<{ open: boolean; item: VehicleItem | null; preset: Template | null }>({ open: false, item: null, preset: null })
  const [revision, setRevision] = useState<{ open: boolean; revision: VehicleRevision | null }>({ open: false, revision: null })
  const [planning, setPlanning] = useState(false)
  const [done, setDone] = useState<{ open: boolean; target: Done | null }>({ open: false, target: null })
  const [showTemplates, setShowTemplates] = useState(false)
  const [removing, setRemoving] = useState<Removing>(null)

  async function reopen(r: VehicleRevision) {
    try {
      await care.mutateAsync({ vehicleId: vehicle.id, kind: 'reopenRevision', revisionId: r.id })
      toast.success('Revisão reaberta.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2">
          <div className="space-y-1">
            <CardTitle className="text-base">Itens de manutenção</CardTitle>
            <CardDescription>Óleo, corrente, filtros... vence no km ou no tempo, o que vier primeiro.</CardDescription>
          </div>
          {canEdit && (
            <div className="flex flex-wrap gap-2">
              <Button size="sm" variant="outline" onClick={() => setShowTemplates((s) => !s)}><ListPlusIcon /> Tabela sugerida</Button>
              <Button size="sm" onClick={() => setItem({ open: true, item: null, preset: null })}><PlusIcon /> Item</Button>
            </div>
          )}
        </CardHeader>
        <CardContent className="space-y-3">
          {showTemplates && canEdit && (
            <Templates detail={detail} onPick={(t) => setItem({ open: true, item: null, preset: t })} />
          )}
          {!detail.items.length ? <p className="text-sm text-muted-foreground">Nenhum item ainda.</p> : (
            <ul className="divide-y">
              {detail.items.map((i) => (
                <li key={i.id} className="flex items-center gap-3 py-2.5">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="min-w-0 truncate text-sm font-medium">{i.name}</p>
                      <StageBadge due={i.due} />
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {[intervalText(i.intervalKm, i.intervalMonths),
                        i.lastOn || i.lastKm != null
                          ? `última: ${[i.lastOn ? formatDate(i.lastOn) : null, i.lastKm != null ? formatKm(i.lastKm) : null].filter(Boolean).join(', ')}`
                          : 'sem registro da última vez',
                        i.due ? dueText(i.due) : null].filter(Boolean).join(' · ')}
                    </p>
                  </div>
                  {canLog && (
                    <Button size="sm" variant="outline" onClick={() => setDone({ open: true, target: { kind: 'item', item: i } })}>
                      <CheckIcon /> <span className="max-sm:sr-only">Feito</span>
                    </Button>
                  )}
                  {canEdit && (
                    <RowMenu label={i.name} actions={[
                      { label: 'Editar', icon: PencilIcon, onSelect: () => setItem({ open: true, item: i, preset: null }) },
                      { label: 'Excluir', icon: Trash2Icon, destructive: true,
                        onSelect: () => setRemoving({ label: i.name, action: { vehicleId: vehicle.id, kind: 'deleteItem', itemId: i.id } }) },
                    ]} />
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2">
          <div className="space-y-1">
            <CardTitle className="text-base">Revisões</CardTitle>
            <CardDescription>O plano do manual (ex.: a cada 10.000 km ou 12 meses).</CardDescription>
          </div>
          {canEdit && (
            <div className="flex flex-wrap gap-2">
              <Button size="sm" variant="outline" onClick={() => setPlanning(true)}><CalendarPlusIcon /> Montar plano</Button>
              <Button size="sm" onClick={() => setRevision({ open: true, revision: null })}><PlusIcon /> Revisão</Button>
            </div>
          )}
        </CardHeader>
        <CardContent>
          {!detail.revisions.length ? <p className="text-sm text-muted-foreground">Nenhuma revisão no plano.</p> : (
            <ul className="divide-y">
              {detail.revisions.map((r) => (
                <li key={r.id} className="flex items-center gap-3 py-2.5">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="min-w-0 truncate text-sm font-medium">{r.name}</p>
                      {r.doneOn ? <span className="inline-flex items-center gap-1 text-xs font-medium text-income"><CheckIcon className="size-3.5" aria-hidden /> Feita</span>
                        : <StageBadge due={r.due} />}
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {r.doneOn
                        ? [`feita em ${formatDate(r.doneOn)}`, r.doneKm != null ? formatKm(r.doneKm) : null, r.shop].filter(Boolean).join(' · ')
                        : [r.dueKm != null ? `com ${formatKm(r.dueKm)}` : null, r.dueOn ? `até ${formatDate(r.dueOn)}` : null,
                          r.due ? dueText(r.due) : null].filter(Boolean).join(' · ')}
                    </p>
                  </div>
                  {canLog && !r.doneOn && (
                    <Button size="sm" variant="outline"
                      onClick={() => setDone({ open: true, target: { kind: 'revision', revision: r, items: detail.items } })}>
                      <CheckIcon /> <span className="max-sm:sr-only">Feita</span>
                    </Button>
                  )}
                  {canEdit && (
                    <RowMenu label={r.name} actions={[
                      ...(r.doneOn ? [{ label: 'Desfazer "feita"', icon: RotateCcwIcon, onSelect: () => void reopen(r) }] : []),
                      { label: 'Editar', icon: PencilIcon, onSelect: () => setRevision({ open: true, revision: r }) },
                      { label: 'Excluir', icon: Trash2Icon, destructive: true,
                        onSelect: () => setRemoving({ label: r.name, action: { vehicleId: vehicle.id, kind: 'deleteRevision', revisionId: r.id } }) },
                    ]} />
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <History vehicleId={vehicle.id} />

      <ItemDialog open={item.open} onOpenChange={(open) => setItem((s) => ({ ...s, open }))} vehicle={vehicle} item={item.item} preset={item.preset} />
      <RevisionDialog open={revision.open} onOpenChange={(open) => setRevision((s) => ({ ...s, open }))} vehicle={vehicle} revision={revision.revision} />
      <PlanDialog open={planning} onOpenChange={setPlanning} vehicle={vehicle} />
      <DoneDialog open={done.open} onOpenChange={(open) => setDone((s) => ({ ...s, open }))} vehicle={vehicle} target={done.target} />
      <ConfirmDelete open={!!removing} onOpenChange={(open) => { if (!open) setRemoving(null) }} title={`Excluir ${removing?.label ?? ''}?`}
        description="O histórico do que já foi feito continua."
        onConfirm={async () => {
          if (!removing) return
          try {
            await care.mutateAsync(removing.action)
            toast.success('Excluído.')
          } catch (error) {
            toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
          }
          setRemoving(null)
        }} />
    </div>
  )
}

/** Itens da tabela do tipo que ainda não estão no veículo (cada um abre o formulário já preenchido). */
function Templates({ detail, onPick }: { detail: VehicleDetail; onPick: (t: Template) => void }) {
  const { data } = useTemplates(detail.vehicle.type)
  if (!data) return <Skeleton className="h-16" />
  const names = new Set(detail.items.map((i) => i.name.toLowerCase()))
  const missing = data.filter((t) => !names.has(t.name.toLowerCase()))
  if (!missing.length) return <p className="text-sm text-muted-foreground">Todos os itens da tabela sugerida já estão aqui.</p>
  return (
    <div className="flex flex-wrap gap-2 rounded-2xl bg-muted/40 p-3">
      {missing.map((t) => (
        <Button key={t.name} size="sm" variant="outline" onClick={() => onPick(t)}>
          <PlusIcon /> {t.name}
        </Button>
      ))}
    </div>
  )
}

function History({ vehicleId }: { vehicleId: string }) {
  const { formatCurrency } = useMoney()
  const { data } = useServiceLogs(vehicleId)
  return (
    <Card>
      <CardHeader><CardTitle className="text-base">Histórico</CardTitle></CardHeader>
      <CardContent>
        {!data ? <Skeleton className="h-24" /> : !data.length ? <p className="text-sm text-muted-foreground">Nada feito ainda.</p> : (
          <ul className="divide-y">
            {data.map((l) => (
              <li key={l.id} className="flex items-baseline justify-between gap-3 py-2 text-sm">
                <div className="min-w-0">
                  <p className="truncate font-medium">{l.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {[formatDate(l.date), l.km != null ? formatKm(l.km) : null, l.user?.name, l.notes].filter(Boolean).join(' · ')}
                  </p>
                </div>
                {l.amount != null && <span className="shrink-0 tabular-nums">{formatCurrency(l.amount)}</span>}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
