import { DownloadIcon } from 'lucide-react'
import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { downloadText, scheduleCsv } from '../labels'
import type { ScheduleRow } from '../types'

const config = {
  amortization: { label: 'Abate a dívida', color: 'var(--data)' },
  interest: { label: 'Juros', color: 'var(--expense)' },
  charges: { label: 'Seguros e tarifas', color: 'var(--chart-4)' },
} satisfies ChartConfig

type YearPoint = { year: string; amortization: number; interest: number; charges: number }

function byYear(rows: ScheduleRow[]): YearPoint[] {
  const points = new Map<string, YearPoint>()
  for (const r of rows) {
    const year = r.month.slice(0, 4)
    const point = points.get(year) ?? { year, amortization: 0, interest: 0, charges: 0 }
    point.amortization += r.amortization + r.extra
    point.interest += r.interest
    point.charges += r.charges
    points.set(year, point)
  }
  return [...points.values()]
}

/** Quanto de cada ano vai para juros e quanto abate a dívida. */
export function ScheduleChart({ rows }: { rows: ScheduleRow[] }) {
  const { formatCurrency, formatCompactCurrency } = useMoney()
  const data = byYear(rows)
  return (
    <Card>
      <CardHeader>
        <CardTitle>Juros x amortização</CardTitle>
        <CardDescription>Soma das parcelas de cada ano: quanto é juros e quanto abate a dívida.</CardDescription>
      </CardHeader>
      <CardContent>
        <ChartContainer config={config} className="aspect-auto h-64 w-full">
          <BarChart data={data} margin={{ left: 4, right: 4 }}>
            <CartesianGrid vertical={false} strokeDasharray="3 3" />
            <XAxis dataKey="year" tickLine={false} axisLine={false} tickMargin={8} minTickGap={12} />
            <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
            <ChartTooltip
              cursor={{ fillOpacity: 0.4 }}
              content={(props) => {
                const { active, payload } = props as { active?: boolean; payload?: ReadonlyArray<{ payload?: unknown }> }
                if (!active || !payload?.length) return null
                const p = payload[0].payload as YearPoint
                return (
                  <div className="grid min-w-44 gap-1.5 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
                    <p className="font-medium">{p.year}</p>
                    {(['amortization', 'interest', 'charges'] as const).map((key) => (
                      <div key={key} className="flex items-center gap-2">
                        <span className="size-2.5 shrink-0 rounded-[2px]" style={{ background: config[key].color }} />
                        <span>{config[key].label}</span>
                        <span className="ml-auto font-medium tabular-nums">{formatCurrency(p[key])}</span>
                      </div>
                    ))}
                  </div>
                )
              }}
            />
            <ChartLegend content={<ChartLegendContent />} />
            <Bar dataKey="amortization" stackId="a" fill="var(--color-amortization)" />
            <Bar dataKey="interest" stackId="a" fill="var(--color-interest)" />
            <Bar dataKey="charges" stackId="a" fill="var(--color-charges)" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}

const SHOWN = 12

/** Todas as parcelas mês a mês, com baixar em CSV; `paid` marca as já pagas. */
export function ScheduleTable({ rows, paid = 0, fileName }: { rows: ScheduleRow[]; paid?: number; fileName: string }) {
  const { formatCurrency } = useMoney()
  const [all, setAll] = useState(false)
  const shown = all ? rows : rows.slice(0, SHOWN)
  const hasExtra = rows.some((r) => r.extra > 0)
  return (
    <Card>
      <CardHeader>
        <CardTitle>Parcelas</CardTitle>
        <CardDescription>{rows.length} parcelas. Valores com seguros e tarifas.</CardDescription>
        <CardAction>
          <Button variant="outline" size="sm"
            onClick={() => downloadText(scheduleCsv(rows), `${fileName}.csv`, 'text/csv;charset=utf-8')}>
            <DownloadIcon /> CSV
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="-mx-4 overflow-x-auto px-4">
          <Table className="min-w-[36rem]">
            <TableHeader>
              <TableRow>
                <TableHead className="w-12">Nº</TableHead>
                <TableHead>Mês</TableHead>
                <TableHead className="text-right">Parcela</TableHead>
                <TableHead className="text-right">Juros</TableHead>
                <TableHead className="text-right">Amortização</TableHead>
                {hasExtra && <TableHead className="text-right">Extra</TableHead>}
                <TableHead className="text-right">Saldo</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {shown.map((r) => (
                <TableRow key={r.number} className={cn(r.number <= paid && 'text-muted-foreground')}>
                  <TableCell className="tabular-nums">{r.number}</TableCell>
                  <TableCell className="whitespace-nowrap">
                    {formatMonth(r.month, 'short')}
                    {r.number <= paid && <span className="ml-1.5 text-xs">(paga)</span>}
                  </TableCell>
                  <TableCell className="text-right font-medium tabular-nums">{formatCurrency(r.payment)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCurrency(r.interest)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCurrency(r.amortization)}</TableCell>
                  {hasExtra && <TableCell className="text-right tabular-nums">{r.extra ? formatCurrency(r.extra) : '-'}</TableCell>}
                  <TableCell className="text-right tabular-nums">{formatCurrency(r.balance)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
        {rows.length > SHOWN && (
          <Button variant="ghost" size="sm" onClick={() => setAll((v) => !v)}>
            {all ? 'Mostrar menos' : `Ver as ${rows.length} parcelas`}
          </Button>
        )}
      </CardContent>
    </Card>
  )
}
