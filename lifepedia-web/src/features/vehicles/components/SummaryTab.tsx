import { FuelIcon, GaugeIcon, ReceiptIcon, RouteIcon } from 'lucide-react'
import { useState } from 'react'
import { StatCard } from '@/components/StatCard'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useMoney } from '@/lib/hide-values'
import { useVehicleStats } from '../api'
import { dueText, formatKm, formatNumber } from '../labels'
import type { VehicleDetail } from '../types'
import { ConsumptionChart, SpendChart } from './charts'
import { StageBadge } from './VehicleCard'

const PERIODS = [3, 6, 12, 24] as const

/** Próximos vencimentos, números do período e gráficos (tudo calculado pela API). */
export function SummaryTab({ detail }: { detail: VehicleDetail }) {
  const { formatCurrency } = useMoney()
  const [months, setMonths] = useState(6)
  const { data: stats } = useVehicleStats(detail.vehicle.id, months)
  const upcoming = detail.upcoming.slice(0, 6)

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Próximos</CardTitle>
          <CardDescription>Manutenção, revisões e documentos, do mais urgente. Avisamos quando chegar perto.</CardDescription>
        </CardHeader>
        <CardContent>
          {!upcoming.length ? (
            <p className="text-sm text-muted-foreground">Nada agendado. Marque a última troca de cada item em Manutenção para o app calcular a próxima.</p>
          ) : (
            <ul className="divide-y">
              {upcoming.map((u) => (
                <li key={`${u.kind}-${u.id}`} className="flex items-center justify-between gap-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{u.name}</p>
                    <p className="truncate text-xs text-muted-foreground">{dueText(u.due)}</p>
                  </div>
                  <StageBadge due={u.due} />
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Números do período</h2>
        <Select value={String(months)} onValueChange={(v) => setMonths(Number(v))}>
          <SelectTrigger className="w-40" size="sm" aria-label="Período"><SelectValue /></SelectTrigger>
          <SelectContent>{PERIODS.map((p) => <SelectItem key={p} value={String(p)}>Últimos {p} meses</SelectItem>)}</SelectContent>
        </Select>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Gasto total" tone="expense" icon={ReceiptIcon}>{stats ? formatCurrency(stats.total) : undefined}</StatCard>
        <StatCard label="Custo por km" tone="data" icon={RouteIcon}
          hint={stats?.kmDriven ? `${formatKm(stats.kmDriven)} rodados` : 'Precisa de leituras de km'}>
          {stats ? stats.costPerKm != null ? formatCurrency(stats.costPerKm) : '-' : undefined}
        </StatCard>
        <StatCard label="Consumo médio" tone="data" icon={GaugeIcon}
          hint={stats?.lastKmPerLiter != null ? `Último: ${formatNumber(stats.lastKmPerLiter)} km/l` : 'Dois tanques cheios com km'}>
          {stats ? stats.avgKmPerLiter != null ? `${formatNumber(stats.avgKmPerLiter)} km/l` : '-' : undefined}
        </StatCard>
        <StatCard label="Litro em média" tone="data" icon={FuelIcon}
          hint={stats?.liters != null ? `${formatNumber(stats.liters)} L abastecidos` : undefined}>
          {stats ? stats.avgFuelPrice != null ? formatCurrency(stats.avgFuelPrice) : '-' : undefined}
        </StatCard>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader><CardTitle className="text-base">Gasto por mês</CardTitle></CardHeader>
          <CardContent>{stats ? <SpendChart months={stats.byMonth} /> : <Skeleton className="h-64" />}</CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Consumo (km/l)</CardTitle>
            <CardDescription>Cada ponto é um trecho entre dois tanques cheios.</CardDescription>
          </CardHeader>
          <CardContent>
            {!stats ? <Skeleton className="h-56" /> : stats.consumption.length ? <ConsumptionChart points={stats.consumption} /> : (
              <p className="text-sm text-muted-foreground">Lance abastecimentos com o km e "completei o tanque" para ver o consumo.</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
