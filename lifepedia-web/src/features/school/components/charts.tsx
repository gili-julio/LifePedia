import { Bar, BarChart, CartesianGrid, ReferenceLine, XAxis, YAxis } from 'recharts'
import { ChartContainer, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { formatGrade } from '../labels'
import type { PeriodView } from '../types'

type TooltipProps = { active?: boolean; payload?: ReadonlyArray<{ payload?: unknown }> }

const config = { average: { label: 'Média', color: 'var(--data)' } } satisfies ChartConfig

/** Média ponderada pela carga horária de cada semestre (da API), com a linha da média para aprovar. */
export function PeriodsChart({ periods, passAverage }: { periods: PeriodView[]; passAverage: number | null }) {
  const data = [...periods].filter((p) => p.average != null).sort((a, b) => a.period.localeCompare(b.period))
  return (
    <ChartContainer config={config} className="aspect-auto h-56 w-full">
      <BarChart data={data} margin={{ top: 8, left: 4, right: 8 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="period" tickLine={false} axisLine={false} tickMargin={8} minTickGap={8} />
        <YAxis tickLine={false} axisLine={false} width={32} domain={[0, 10]} ticks={[0, 2, 4, 6, 8, 10]} />
        {passAverage != null && <ReferenceLine y={passAverage} stroke="var(--muted-foreground)" strokeDasharray="4 4" />}
        <ChartTooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }}
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as PeriodView
            return (
              <div className="grid min-w-36 gap-1 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
                <p className="font-medium">{row.period}</p>
                <p>Média <span className="font-medium tabular-nums">{formatGrade(row.average)}</span></p>
                <p className="text-muted-foreground">{row.courses} matéria(s), {row.hours} h</p>
              </div>
            )
          }} />
        <Bar dataKey="average" fill="var(--color-average)" radius={[4, 4, 0, 0]} maxBarSize={40} />
      </BarChart>
    </ChartContainer>
  )
}
