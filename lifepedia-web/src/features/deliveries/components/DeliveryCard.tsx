import { AlertTriangleIcon, BanknoteIcon, ClockIcon, PackageIcon, TruckIcon, UsersIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'
import { forecast, STALL_DAYS, STATUS_LABEL } from '../labels'
import type { Delivery } from '../types'
import { StageBar } from './StageBar'

export function DeliveryThumb({ delivery, className }: { delivery: Delivery; className?: string }) {
  return delivery.imageUrl
    ? <img src={delivery.imageUrl} alt="" className={cn('shrink-0 rounded-xl object-cover', className)} />
    : (
      <span className={cn('flex shrink-0 items-center justify-center rounded-xl bg-muted text-muted-foreground', className)} aria-hidden>
        <PackageIcon className="size-1/2" />
      </span>
    )
}

/** Selos: chega hoje, taxa, parada, problema, grupo. */
export function DeliveryBadges({ delivery }: { delivery: Delivery }) {
  const stalled = delivery.stalledDays != null && delivery.stalledDays >= STALL_DAYS
  return (
    <div className="flex flex-wrap gap-1.5">
      {delivery.arrivesToday && <Badge className="bg-data/15 text-data"><TruckIcon aria-hidden /> Chega hoje</Badge>}
      {delivery.taxPending && <Badge variant="destructive"><BanknoteIcon aria-hidden /> Aguardando pagamento</Badge>}
      {delivery.problem && <Badge variant="destructive"><AlertTriangleIcon aria-hidden /> {STATUS_LABEL[delivery.status]}</Badge>}
      {stalled && !delivery.problem && <Badge variant="outline"><ClockIcon aria-hidden /> Parada há {delivery.stalledDays} dias</Badge>}
      {delivery.group && <Badge variant="secondary"><UsersIcon aria-hidden /> {delivery.group.name}</Badge>}
    </div>
  )
}

/** Entrega na lista: foto, nome, etapas, último evento e selos. */
export function DeliveryCard({ delivery }: { delivery: Delivery }) {
  const when = forecast(delivery)
  return (
    <Link to={`/entregas/${delivery.id}`}
      className="block space-y-3 rounded-2xl border bg-card p-4 transition-colors hover:bg-accent/40 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none motion-reduce:transition-none">
      <div className="flex items-start gap-3">
        <DeliveryThumb delivery={delivery} className="size-12" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{delivery.name}</p>
          <p className="truncate text-xs text-muted-foreground">
            {delivery.trackingCode}{delivery.carrier ? ` · ${delivery.carrier.name}` : ''}
            {!delivery.mine && delivery.owner ? ` · de ${delivery.owner.name}` : ''}
          </p>
        </div>
        <span className={cn('shrink-0 text-xs font-medium', delivery.problem ? 'text-destructive' : delivery.status === 'DELIVERED' ? 'text-income' : 'text-data')}>
          {STATUS_LABEL[delivery.status]}
        </span>
      </div>
      <StageBar delivery={delivery} compact />
      <div className="space-y-1 text-sm">
        {delivery.latestText ? (
          <p className="line-clamp-2">
            {delivery.latestText}
            {delivery.latestLocation && <span className="text-muted-foreground"> · {delivery.latestLocation}</span>}
          </p>
        ) : (
          <p className="text-muted-foreground">
            {delivery.tracking ? 'Ainda sem eventos. A transportadora pode levar algumas horas para registrar.' : 'Rastreio automático desligado no servidor.'}
          </p>
        )}
        <p className="text-xs text-muted-foreground">
          {delivery.latestAt && formatRelative(delivery.latestAt)}
          {when && delivery.status !== 'DELIVERED' && `${delivery.latestAt ? ' · ' : ''}Previsão: ${when}`}
        </p>
      </div>
      <DeliveryBadges delivery={delivery} />
    </Link>
  )
}
