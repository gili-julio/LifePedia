import { AlertTriangleIcon, CircleXIcon } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { useAdminLimits } from '../api'
import { LIMIT_LABEL, percent } from '../labels'

const WARN = 80

/** Uso dos limites dos serviços externos; aviso a partir de 80%. */
export function LimitsCard() {
  const { data, error } = useAdminLimits()
  return (
    <Card>
      <CardHeader>
        <CardTitle>Limites dos serviços</CardTitle>
        <CardDescription>Cotas de planos gratuitos e tetos diários. Chegou perto? Hora de pagar o plano ou segurar o uso.</CardDescription>
      </CardHeader>
      <CardContent>
        {error ? <p className="text-sm text-destructive">Não foi possível ler os limites.</p> : !data ? <Skeleton className="h-32" /> : (
          <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {data.map((item) => {
              const info = LIMIT_LABEL[item.key]
              const known = item.used != null && item.limit != null && item.limit > 0
              const pct = known ? percent(item.used!, item.limit!) : 0
              const warn = known && pct >= WARN
              return (
                <li key={item.key} className="space-y-1.5 text-sm">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="font-medium">{info.label}</span>
                    {known && <span className={cn('tabular-nums', warn ? 'font-semibold text-destructive' : 'text-muted-foreground')}>{pct}%</span>}
                  </div>
                  {!item.configured ? (
                    <p className="flex items-center gap-1.5 text-muted-foreground"><CircleXIcon className="size-4" aria-hidden />{info.off || 'Desligado'}</p>
                  ) : item.error ? (
                    <p className="flex items-center gap-1.5 text-destructive"><AlertTriangleIcon className="size-4" aria-hidden />{item.error}</p>
                  ) : known ? (
                    <>
                      <div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuemin={0} aria-valuemax={item.limit!}
                        aria-valuenow={item.used!} aria-label={info.label}>
                        <div className={cn('h-full rounded-full', warn ? 'bg-destructive' : 'bg-data')} style={{ width: `${Math.min(100, pct)}%` }} />
                      </div>
                      <p className="text-xs text-muted-foreground tabular-nums">
                        {item.used!.toLocaleString('pt-BR')} de {item.limit!.toLocaleString('pt-BR')} {info.unit}
                        {warn && ' · perto do limite'}
                      </p>
                    </>
                  ) : <p className="text-muted-foreground">Sem dados.</p>}
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
