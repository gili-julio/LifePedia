import { AlertTriangleIcon, CheckCircle2Icon, ClockIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { ROLE_LABEL, STAGE_LABEL, TYPE_LABEL, dueText, formatKm, vehicleTitle } from '../labels'
import type { Due, VehicleCard as Vehicle, VehicleType } from '../types'

export function VehicleThumb({ vehicle, className }: { vehicle: { type: VehicleType; imageUrl: string | null }; className?: string }) {
  const Icon = TYPE_LABEL[vehicle.type].icon
  if (vehicle.imageUrl) return <img src={vehicle.imageUrl} alt="" className={cn('size-14 shrink-0 rounded-2xl object-cover', className)} />
  return (
    <span className={cn('flex size-14 shrink-0 items-center justify-center rounded-2xl bg-muted text-muted-foreground', className)}>
      <Icon className="size-1/2" aria-hidden />
    </span>
  )
}

const STAGE_STYLE = {
  OK: { icon: CheckCircle2Icon, className: 'text-income' },
  SOON: { icon: ClockIcon, className: 'text-amber-600 dark:text-amber-400' },
  DUE: { icon: AlertTriangleIcon, className: 'text-destructive' },
} as const

/** Situação sempre com ícone e texto (nunca só a cor). */
export function StageBadge({ due, className }: { due: Due | null; className?: string }) {
  if (!due) return <span className={cn('shrink-0 text-xs whitespace-nowrap text-muted-foreground', className)}>Sem registro</span>
  const style = STAGE_STYLE[due.stage]
  return (
    <span className={cn('inline-flex shrink-0 items-center gap-1 text-xs font-medium whitespace-nowrap', style.className, className)}>
      <style.icon className="size-3.5" aria-hidden /> {STAGE_LABEL[due.stage]}
    </span>
  )
}

/** Cartão da lista: foto, nome, km de hoje, próximo vencimento e, se não for seu, de quem é. */
export function VehicleCard({ vehicle }: { vehicle: Vehicle }) {
  const { formatCurrency } = useMoney()
  const title = vehicleTitle(vehicle)
  return (
    <Link to={`/veiculos/${vehicle.id}`} className="block rounded-4xl focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
      <Card className={cn('h-full transition-colors hover:bg-muted/40 motion-reduce:transition-none', vehicle.archivedAt && 'opacity-70')}>
        <CardContent className="space-y-3">
          <div className="flex items-start gap-3">
            <VehicleThumb vehicle={vehicle} />
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold">{vehicle.name}</p>
              {title && <p className="truncate text-sm text-muted-foreground">{title}</p>}
              <div className="mt-1 flex flex-wrap items-center gap-1.5">
                {vehicle.plate && <Badge variant="outline" className="font-mono">{vehicle.plate}</Badge>}
                {!vehicle.mine && <Badge variant="secondary">{ROLE_LABEL[vehicle.role]}</Badge>}
                {vehicle.archivedAt && <Badge variant="secondary">Arquivado</Badge>}
              </div>
            </div>
          </div>
          <div className="flex items-baseline justify-between gap-2 text-sm">
            <span className="font-medium tabular-nums">{formatKm(vehicle.estimatedKm)}</span>
            {vehicle.fipeValue != null && <span className="text-muted-foreground">FIPE {formatCurrency(vehicle.fipeValue)}</span>}
          </div>
          {vehicle.next ? (
            <div className="flex items-center justify-between gap-2 rounded-xl bg-muted/50 px-3 py-2 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium">{vehicle.next.name}</p>
                <p className="truncate text-xs text-muted-foreground">{dueText(vehicle.next.due)}</p>
              </div>
              <StageBadge due={vehicle.next.due} />
            </div>
          ) : <p className="text-xs text-muted-foreground">Nada agendado ainda.</p>}
          {!vehicle.mine && vehicle.owner && (
            <p className="truncate text-xs text-muted-foreground">
              De {vehicle.owner.name}{vehicle.sharedVia ? ` (grupo ${vehicle.sharedVia})` : ''}
            </p>
          )}
        </CardContent>
      </Card>
    </Link>
  )
}
