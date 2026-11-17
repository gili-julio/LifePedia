import { GaugeIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { useDeleteReading, useReadings } from '../api'
import { canAct, formatKm, formatNumber } from '../labels'
import type { VehicleCard } from '../types'

/** Leituras do odômetro (manuais e as que vieram dos gastos). */
export function ReadingsTab({ vehicle, onAdd }: { vehicle: VehicleCard; onAdd: () => void }) {
  const { data } = useReadings(vehicle.id)
  const remove = useDeleteReading()

  async function del(readingId: string) {
    try {
      await remove.mutateAsync({ id: vehicle.id, readingId })
      toast.success('Leitura excluída.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2">
        <div className="space-y-1">
          <CardTitle className="text-base">Quilometragem</CardTitle>
          <CardDescription>
            {vehicle.kmPerDay != null
              ? `Ritmo de uso: ${formatNumber(vehicle.kmPerDay)} km por dia. Hoje deve estar perto de ${formatKm(vehicle.estimatedKm)}.`
              : 'Com duas leituras em dias diferentes (uma semana ou mais), o app calcula o ritmo e prevê as trocas.'}
          </CardDescription>
        </div>
        {canAct(vehicle.role, 'LOG') && <Button size="sm" onClick={onAdd}><GaugeIcon /> Atualizar km</Button>}
      </CardHeader>
      <CardContent>
        {!data ? <Skeleton className="h-32" /> : !data.length ? <p className="text-sm text-muted-foreground">Nenhuma leitura ainda.</p> : (
          <ul className="divide-y">
            {data.map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                <div className="min-w-0">
                  <p className="font-medium tabular-nums">{formatKm(r.km)}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {[formatDate(r.date), r.fromExpense ? 'de um gasto' : null, r.user?.name].filter(Boolean).join(' · ')}
                  </p>
                </div>
                {r.canDelete && (
                  <Button size="icon-sm" variant="ghost" aria-label={`Excluir a leitura de ${formatKm(r.km)}`} onClick={() => void del(r.id)}>
                    <Trash2Icon />
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
