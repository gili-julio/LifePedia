import { ActivityIcon, BotIcon, ShieldCheckIcon, UserPlusIcon, UsersIcon } from 'lucide-react'
import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { StatCard } from '@/components/StatCard'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, ChartTooltipContent, type ChartConfig } from '@/components/ui/chart'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDate } from '@/lib/format'
import { useAdminAi, useAdminModules, useAdminOverview } from '../api'
import { MODULE_LABEL, OUTCOME_LABEL, percent } from '../labels'
import type { AiOverview, DayCount } from '../types'

const number = (value: number) => value.toLocaleString('pt-BR')
const shortDay = (iso: string) => formatDate(iso).slice(0, 5)

/** Cards, cadastros, uso da IA e uso por módulo. */
export function OverviewTab() {
  const { data } = useAdminOverview()
  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Usuários" tone="data" icon={UsersIcon}
          hint={data && <>{number(data.admins)} admin(s) · {number(data.blocked)} bloqueado(s)</>}>
          {data && number(data.totalUsers)}
        </StatCard>
        <StatCard label="Novos em 7 dias" tone="data" icon={UserPlusIcon} hint={data && <>{number(data.newUsers30)} em 30 dias</>}>
          {data && number(data.newUsers7)}
        </StatCard>
        <StatCard label="Ativos em 7 dias" tone="data" icon={ActivityIcon}
          hint={data && <>{percent(data.activeUsers7, data.totalUsers)}% das contas</>}>
          {data && number(data.activeUsers7)}
        </StatCard>
        <StatCard label="Ativos em 30 dias" tone="data" icon={ShieldCheckIcon}
          hint={data && <>{percent(data.activeUsers30, data.totalUsers)}% das contas</>}>
          {data && number(data.activeUsers30)}
        </StatCard>
      </div>
      <SignupsCard daily={data?.signupsDaily} weekly={data?.signupsWeekly} />
      <div className="grid gap-6 xl:grid-cols-2">
        <AiCard />
        <ModulesCard />
      </div>
    </div>
  )
}

const signupsConfig = { count: { label: 'Cadastros', color: 'var(--data)' } } satisfies ChartConfig

function SignupsCard({ daily, weekly }: { daily?: DayCount[]; weekly?: DayCount[] }) {
  const [range, setRange] = useState<'daily' | 'weekly'>('daily')
  const points = range === 'daily' ? daily : weekly

  return (
    <Card>
      <CardHeader className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <CardTitle>Cadastros</CardTitle>
          <CardDescription>
            {range === 'daily' ? 'Por dia, nos últimos 30 dias' : 'Por semana, nos últimos 6 meses'}
          </CardDescription>
        </div>
        <Tabs value={range} onValueChange={(v) => setRange(v as 'daily' | 'weekly')}>
          <TabsList>
            <TabsTrigger value="daily">30 dias</TabsTrigger>
            <TabsTrigger value="weekly">6 meses</TabsTrigger>
          </TabsList>
        </Tabs>
      </CardHeader>
      <CardContent>
        {!points ? <Skeleton className="h-56" /> : (
          <ChartContainer config={signupsConfig} className="aspect-auto h-56 w-full">
            <BarChart data={points} margin={{ top: 8, left: 0, right: 4 }}>
              <CartesianGrid vertical={false} strokeDasharray="3 3" />
              <XAxis dataKey="date" tickLine={false} axisLine={false} tickMargin={8} minTickGap={16} tickFormatter={shortDay} />
              <YAxis tickLine={false} axisLine={false} width={32} allowDecimals={false} />
              <ChartTooltip cursor={{ fillOpacity: 0.4 }} content={<ChartTooltipContent
                labelFormatter={(_, payload) => {
                  const date = (payload?.[0]?.payload as DayCount | undefined)?.date
                  return date ? (range === 'daily' ? formatDate(date) : `Semana de ${formatDate(date)}`) : ''
                }} />} />
              <Bar dataKey="count" fill="var(--color-count)" radius={[4, 4, 0, 0]} maxBarSize={28} />
            </BarChart>
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  )
}

/** Concluídas (azul) x falhas (vermelho): par que se distingue também com daltonismo, sempre com legenda. */
const aiConfig = {
  success: { label: 'Concluídas', color: 'var(--data)' },
  failed: { label: 'Falharam', color: 'var(--expense)' },
} satisfies ChartConfig

function AiCard() {
  const { data } = useAdminAi()
  return (
    <Card>
      <CardHeader className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <CardTitle className="flex items-center gap-2"><BotIcon className="size-4" aria-hidden /> IA da importação</CardTitle>
          <CardDescription>{data ? (data.enabled ? `Modelo ${data.model}` : 'Desligada (sem AI_API_KEY)') : 'Carregando'}</CardDescription>
        </div>
        {data && <Badge variant={data.enabled ? 'secondary' : 'outline'}>{data.enabled ? 'Ligada' : 'Desligada'}</Badge>}
      </CardHeader>
      <CardContent className="space-y-4">
        {!data ? <Skeleton className="h-64" /> : <AiBody data={data} />}
      </CardContent>
    </Card>
  )
}

function AiBody({ data }: { data: AiOverview }) {
  const used = percent(data.last24h + data.running, data.dailyLimit)
  const outcomes = Object.entries(data.outcomes)
  const total = outcomes.reduce((sum, [, n]) => sum + n, 0)
  return (
    <>
      <div className="space-y-1.5">
        <div className="flex items-baseline justify-between gap-2 text-sm">
          <span>Leituras nas últimas 24 h</span>
          <span className="font-medium tabular-nums">{number(data.last24h)} de {number(data.dailyLimit)}</span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-muted" role="meter" aria-valuemin={0} aria-valuemax={data.dailyLimit}
          aria-valuenow={data.last24h} aria-label="Leituras nas últimas 24 horas">
          <div className={used >= 90 ? 'h-full bg-destructive' : 'h-full bg-data'} style={{ width: `${Math.min(used, 100)}%` }} />
        </div>
        <p className="text-xs text-muted-foreground">
          {number(data.running)} rodando agora · {number(data.users30)} pessoa(s) em 30 dias
          {data.avgDurationMs > 0 && <> · média de {(data.avgDurationMs / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s</>}
        </p>
      </div>
      {total > 0 && <ChartContainer config={aiConfig} className="aspect-auto h-44 w-full">
        <BarChart data={data.daily} margin={{ top: 8, left: 0, right: 4 }}>
          <CartesianGrid vertical={false} strokeDasharray="3 3" />
          <XAxis dataKey="date" tickLine={false} axisLine={false} tickMargin={8} minTickGap={16} tickFormatter={shortDay} />
          <YAxis tickLine={false} axisLine={false} width={32} allowDecimals={false} />
          <ChartTooltip cursor={{ fillOpacity: 0.4 }} content={<ChartTooltipContent
            labelFormatter={(_, payload) => {
              const date = (payload?.[0]?.payload as { date?: string } | undefined)?.date
              return date ? formatDate(date) : ''
            }} />} />
          <ChartLegend content={<ChartLegendContent />} />
          <Bar dataKey="success" stackId="a" fill="var(--color-success)" maxBarSize={20} />
          <Bar dataKey="failed" stackId="a" fill="var(--color-failed)" radius={[4, 4, 0, 0]} maxBarSize={20} />
        </BarChart>
      </ChartContainer>}
      {total > 0 ? (
        <ul className="space-y-1 text-sm">
          {outcomes.map(([outcome, count]) => (
            <li key={outcome} className="flex justify-between gap-2">
              <span className="min-w-0 truncate">{OUTCOME_LABEL[outcome] ?? outcome}</span>
              <span className="tabular-nums text-muted-foreground">{number(count)} ({percent(count, total)}%)</span>
            </li>
          ))}
        </ul>
      ) : <p className="text-sm text-muted-foreground">Nenhuma leitura nos últimos 30 dias.</p>}
    </>
  )
}

function ModulesCard() {
  const { data } = useAdminModules()
  return (
    <Card>
      <CardHeader className="space-y-1">
        <CardTitle>Uso por módulo</CardTitle>
        <CardDescription>Pessoas que usam cada parte do app (só contagens)</CardDescription>
      </CardHeader>
      <CardContent>
        {!data ? <Skeleton className="h-64" /> : (
          <ul className="space-y-3">
            {data.modules.map((m) => {
              const share = percent(m.users, data.totalUsers)
              return (
                <li key={m.key} className="space-y-1">
                  <div className="flex items-baseline justify-between gap-2 text-sm">
                    <span className="min-w-0 truncate font-medium">{MODULE_LABEL[m.key]?.label ?? m.key}</span>
                    <span className="shrink-0 tabular-nums text-muted-foreground">{number(m.users)} ({share}%)</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                    <div className="h-full rounded-full bg-data" style={{ width: `${share}%` }} />
                  </div>
                  <p className="text-xs text-muted-foreground">{number(m.items)} {MODULE_LABEL[m.key]?.items ?? 'itens'}</p>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
