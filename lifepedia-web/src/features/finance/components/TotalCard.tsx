import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Money } from './Money'

/** Total calculado pela API (`/summary`); sem valor, mostra carregando. */
export function TotalCard({ label, value, signed }: { label: string; value?: number; signed?: boolean }) {
  return (
    <Card size="sm">
      <CardContent className="space-y-1">
        <p className="text-sm text-muted-foreground">{label}</p>
        {value === undefined ? <Skeleton className="h-7 w-28" /> : (
          <p className="text-xl font-semibold"><Money value={value} signed={signed} /></p>
        )}
      </CardContent>
    </Card>
  )
}
