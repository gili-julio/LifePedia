import { Bar, CartesianGrid, ComposedChart, Line, ReferenceLine, XAxis, YAxis } from 'recharts'
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import type { TimelinePoint } from '../types'

const config = {
  balance: { label: 'Saldo com os planos', color: 'var(--data)' },
  withoutPlan: { label: 'Saldo sem os planos', color: 'var(--muted-foreground)' },
  planned: { label: 'Pagamentos do mês', color: 'var(--expense)' },
} satisfies ChartConfig

type TooltipProps = { active?: boolean; payload?: ReadonlyArray<{ payload?: unknown }> }

type Props = {
  points: TimelinePoint[]
  /** Nome dos itens por id (para o tooltip) */
  names: Map<string, string>
}

function TooltipRow({ label, value, color, negative }: { label: string; value: number; color?: string; negative?: boolean }) {
  const { formatCurrency } = useMoney()
  return (
    <div className="flex items-center gap-2">
      {color ? <span className="size-2.5 shrink-0 rounded-[2px]" style={{ background: color }} /> : <span className="w-2.5" />}
      <span className="text-muted-foreground">{label}</span>
      <span className={negative && value < 0 ? 'ml-auto font-medium tabular-nums text-destructive' : 'ml-auto font-medium tabular-nums'}>
        {formatCurrency(value)}
      </span>
    </div>
  )
}

/** Saldo acumulado mês a mês com e sem os planos, e os pagamentos de cada mês. */
export function PlanTimelineChart({ points, names }: Props) {
  const { formatCompactCurrency } = useMoney()
  const data = points.map((p) => ({ ...p, label: formatMonth(p.month, 'short') }))
  return (
    <ChartContainer config={config} className="aspect-auto h-72 w-full">
      <ComposedChart data={data} margin={{ top: 8, left: 4, right: 4 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={16} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ReferenceLine y={0} stroke="var(--destructive)" strokeDasharray="4 4" />
        <ChartTooltip
          cursor={{ fillOpacity: 0.3 }}
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <div className="grid min-w-52 gap-1.5 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
                <p className="font-medium">{formatMonth(row.month)}</p>
                <TooltipRow label="Saldo previsto do mês" value={row.base} />
                {row.planned > 0 && <TooltipRow color="var(--expense)" label="Pagamentos" value={row.planned} />}
                {row.due.map((id) => (
                  <p key={id} className="truncate pl-4.5 text-muted-foreground">• {names.get(id) ?? 'Item'}</p>
                ))}
                <div className="my-0.5 border-t" />
                <TooltipRow color="var(--data)" label="Saldo com os planos" value={row.balance} negative />
                <TooltipRow color="var(--muted-foreground)" label="Sem os planos" value={row.withoutPlan} negative />
              </div>
            )
          }}
        />
        <ChartLegend content={<ChartLegendContent />} />
        <Bar dataKey="planned" fill="var(--color-planned)" radius={[3, 3, 0, 0]} maxBarSize={20} />
        <Line dataKey="withoutPlan" stroke="var(--color-withoutPlan)" strokeDasharray="5 4" strokeWidth={1.5} dot={false} />
        <Line dataKey="balance" stroke="var(--color-balance)" strokeWidth={2} dot={false} />
      </ComposedChart>
    </ChartContainer>
  )
}
