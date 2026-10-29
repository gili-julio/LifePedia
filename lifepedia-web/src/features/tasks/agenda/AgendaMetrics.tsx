import { AlertTriangleIcon, BriefcaseIcon, CalendarClockIcon, HourglassIcon, Loader2Icon, MoonIcon, SunIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { StatCard } from '@/components/StatCard'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useAgenda, useAgendaSettings, useSaveAgendaSettings } from '../api'
import { addDays, isoWeekday, WEEKDAYS_SHORT } from '../dates'
import type { AgendaSettings, TaskFilters } from '../types'
import { type DayMetrics, formatHours, weekMetrics } from './metrics'

export const METRICS_ID = 'indicadores'
const FILTERS: TaskFilters = { list: null, tag: null, assignee: null, sort: 'TIME' }
/** Opções de sono: 0 a 16h, de meia em meia hora. */
const SLEEP_OPTIONS = Array.from({ length: 33 }, (_, i) => i * 30)

/** Indicadores da semana da agenda pessoal: horas ocupadas, livres e o sono de cada dia (editável). */
export function AgendaMetrics({ monday, defaultMinutes }: { monday: string; defaultMinutes: number }) {
  const settings = useAgendaSettings()
  const { data } = useAgenda(null, monday, addDays(monday, 6), false, FILTERS)
  const metrics = data && settings.data ? weekMetrics(data.days, settings.data.sleepMinutes, defaultMinutes) : null
  const awake = metrics ? 7 * 24 * 60 - metrics.sleep : 0

  return (
    <section aria-labelledby={METRICS_ID} className="space-y-3 pt-3">
      <div className="space-y-1">
        <h2 id={METRICS_ID} tabIndex={-1} className="scroll-mt-20 text-lg font-semibold tracking-tight outline-none">
          Indicadores da semana
        </h2>
        <p className="text-sm text-muted-foreground">
          {formatDate(monday).slice(0, 5)} a {formatDate(addDays(monday, 6)).slice(0, 5)}. Contam as tarefas com horário
          (sem duração valem {defaultMinutes} min); horários que se cruzam contam uma vez.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3 xl:grid-cols-5">
        <StatCard label="Ocupadas na semana" tone="data" icon={CalendarClockIcon}
          hint={metrics && `${metrics.tasks} ${metrics.tasks === 1 ? 'tarefa' : 'tarefas'} com horário`}>
          {metrics ? formatHours(metrics.busy) : undefined}
        </StatCard>
        <StatCard label="Segunda a sexta" tone="data" icon={BriefcaseIcon}
          hint={metrics && `Média de ${formatHours(metrics.weekdays / 5)} por dia`}>
          {metrics ? formatHours(metrics.weekdays) : undefined}
        </StatCard>
        <StatCard label="Fim de semana" tone="data" icon={SunIcon}
          hint={metrics && `Média de ${formatHours(metrics.weekend / 2)} por dia`}>
          {metrics ? formatHours(metrics.weekend) : undefined}
        </StatCard>
        <StatCard label="Sono na semana" tone="data" icon={MoonIcon}
          hint={metrics && `Média de ${formatHours(metrics.sleep / 7)} por noite`}>
          {metrics ? formatHours(metrics.sleep) : undefined}
        </StatCard>
        <div className="col-span-2 xl:col-span-1">
          <StatCard label="Horas livres" tone="income" icon={HourglassIcon}
            hint={metrics && `Já sem o sono${awake > 0
              ? `; ${Math.round((metrics.busy / awake) * 100)}% do tempo acordado ocupado` : ''}`}>
            {metrics ? formatHours(metrics.free) : undefined}
          </StatCard>
        </div>
      </div>

      <div className="grid gap-3 lg:grid-cols-2">
        <Card size="sm">
          <CardHeader>
            <CardTitle>Por dia</CardTitle>
            <CardDescription>Sono, ocupado e livre em cada dia (24h).</CardDescription>
          </CardHeader>
          <CardContent>
            {metrics ? <DayBars days={metrics.days} /> : <Skeleton className="h-52 w-full" />}
          </CardContent>
        </Card>
        <Card size="sm">
          <CardHeader>
            <CardTitle>Sono por dia</CardTitle>
            <CardDescription>Quanto você dorme em cada dia da semana. É descontado das horas livres.</CardDescription>
          </CardHeader>
          <CardContent>
            {settings.data
              ? <SleepForm key={settings.data.sleepMinutes.join()} settings={settings.data} />
              : <Skeleton className="h-28 w-full" />}
          </CardContent>
        </Card>
      </div>
    </section>
  )
}

function DayBars({ days }: { days: DayMetrics[] }) {
  const pct = (minutes: number) => `${(minutes / (24 * 60)) * 100}%`
  return (
    <ul className="space-y-2.5">
      {days.map((d) => (
        <li key={d.date} className="grid grid-cols-[2.75rem_1fr] items-center gap-x-2 gap-y-1 text-xs">
          <span className="font-medium tabular-nums">
            {WEEKDAYS_SHORT[isoWeekday(d.date) - 1]} <span className="text-muted-foreground">{Number(d.date.slice(8))}</span>
          </span>
          <span className="flex h-2.5 overflow-hidden rounded-full bg-muted" aria-hidden>
            <span className="bg-muted-foreground/40" style={{ width: pct(Math.min(d.sleep, 24 * 60)) }} />
            <span className="bg-data" style={{ width: pct(Math.min(d.busy, 24 * 60 - d.sleep)) }} />
            <span className="bg-income/60" style={{ width: pct(d.free) }} />
          </span>
          <span className="col-start-2 flex flex-wrap gap-x-3 text-muted-foreground tabular-nums">
            <span>Sono {formatHours(d.sleep)}</span>
            <span className="text-data">Ocupado {formatHours(d.busy)}</span>
            <span className="text-income">Livre {formatHours(d.free)}</span>
          </span>
        </li>
      ))}
    </ul>
  )
}

function SleepForm({ settings }: { settings: AgendaSettings }) {
  const save = useSaveAgendaSettings()
  const [sleep, setSleep] = useState(settings.sleepMinutes)
  const changed = sleep.map((m, i) => m !== settings.sleepMinutes[i])
  const dirty = changed.some(Boolean)

  async function submit() {
    try {
      await save.mutateAsync({ ...settings, sleepMinutes: sleep })
      toast.success('Sono salvo.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-4 gap-2 sm:grid-cols-7">
        {WEEKDAYS_SHORT.map((label, i) => {
          const options = SLEEP_OPTIONS.includes(sleep[i]) ? SLEEP_OPTIONS : [...SLEEP_OPTIONS, sleep[i]].sort((a, b) => a - b)
          return (
            <div key={label} className="min-w-0 space-y-1">
              <Label htmlFor={`sleep-${i}`} className="text-xs capitalize">{label}</Label>
              <Select value={String(sleep[i])} onValueChange={(v) => setSleep((s) => s.map((m, j) => (j === i ? Number(v) : m)))}>
                <SelectTrigger id={`sleep-${i}`} size="sm" aria-label={`Sono de ${label}${changed[i] ? ', não salvo' : ''}`}
                  className={cn('w-full min-w-0 px-2 tabular-nums', changed[i] && 'border-orange-500 ring-1 ring-orange-500/40')}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {options.map((m) => <SelectItem key={m} value={String(m)}>{formatHours(m)}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
          )
        })}
      </div>
      <div role="status" aria-live="polite">
        {dirty && (
          <p className="flex gap-2 rounded-lg border border-orange-500/40 bg-orange-500/10 px-3 py-2 text-sm">
            <AlertTriangleIcon className="mt-0.5 size-4 shrink-0 text-orange-600 dark:text-orange-400" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="font-medium text-orange-700 dark:text-orange-300">Alterações não salvas.</span>{' '}
              Os dias em laranja mudaram; as horas livres só mudam depois de salvar.
            </span>
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" onClick={() => void submit()} disabled={!dirty || save.isPending}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar sono
        </Button>
        <Button size="sm" variant="outline" disabled={sleep.every((m) => m === sleep[0])}
          onClick={() => setSleep((s) => s.map(() => s[0]))}>
          Usar o de segunda em todos
        </Button>
        {dirty && (
          <Button size="sm" variant="ghost" onClick={() => setSleep(settings.sleepMinutes)} disabled={save.isPending}>
            Descartar
          </Button>
        )}
      </div>
    </div>
  )
}
