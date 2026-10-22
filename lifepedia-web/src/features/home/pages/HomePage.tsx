import { AlarmClockIcon, ArrowRightIcon, CalendarCheckIcon, ClockIcon, FlameIcon, UsersIcon, WalletIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { useSummary } from '@/features/finance/api'
import { useAgenda, useHabits } from '@/features/tasks/api'
import { formatTime } from '@/features/tasks/dates'
import type { TaskFilters, TaskItem } from '@/features/tasks/types'
import { currentYearMonth, formatCurrency, formatDate, formatMonth, todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'

const FILTERS: TaskFilters = { list: null, tag: null, assignee: null, sort: 'MANUAL' }
const SHOWN = 5

const longDate = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })

/** Link que abre a tarefa no quadro dela (pessoal ou do grupo). */
function taskLink(item: TaskItem) {
  const id = encodeURIComponent(item.taskId)
  return item.groupId ? `/grupos/${item.groupId}?aba=tarefas&tarefa=${id}` : `/tarefas?tarefa=${id}`
}

/** Início: resumo de tarefas de hoje, hábitos e o mês no financeiro. */
export function HomePage() {
  const { user } = useAuth()
  const today = todayIso()
  const firstName = user?.name.trim().split(/\s+/)[0] ?? ''
  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Olá{firstName && `, ${firstName}`}</h1>
        <p className="text-sm text-muted-foreground first-letter:uppercase">{longDate.format(new Date(`${today}T12:00:00`))}</p>
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        <TasksCard today={today} />
        <HabitsCard />
        <FinanceCard />
      </div>
    </div>
  )
}

function HomeCard({ title, description, to, children, className }: {
  title: string
  description: ReactNode
  to: string
  children: ReactNode
  className?: string
}) {
  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
        <CardAction>
          <Button asChild variant="ghost" size="sm">
            <Link to={to} aria-label={`Abrir ${title.toLowerCase()}`}>Abrir <ArrowRightIcon /></Link>
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="flex-1">{children}</CardContent>
    </Card>
  )
}

function TasksCard({ today }: { today: string }) {
  const { data, isLoading, isError } = useAgenda(null, today, today, true, FILTERS)
  const pending = data?.days.flatMap((d) => d.items).filter((i) => i.status === 'PENDING') ?? []
  const overdue = data?.overdue ?? []
  // Atrasadas da mesma tarefa numa linha só (como no quadro).
  const late = new Map<string, { item: TaskItem; count: number }>()
  for (const item of overdue) {
    const entry = late.get(item.taskId)
    if (entry) entry.count++
    else late.set(item.taskId, { item, count: 1 })
  }
  const rows = [...late.values(), ...pending.map((item) => ({ item, count: 1 }))]
  const items = rows.slice(0, SHOWN)
  const rest = rows.length - items.length

  return (
    <HomeCard title="Tarefas" to="/tarefas" className="lg:col-span-2"
      description={data ? `${pending.length} para hoje · ${overdue.length} atrasada${overdue.length === 1 ? '' : 's'}` : 'Hoje'}>
      {isError ? <p className="text-sm text-destructive">Não foi possível carregar as tarefas.</p>
        : isLoading ? <Skeleton className="h-32 w-full" />
        : items.length === 0 ? (
          <div className="flex flex-col items-start gap-3 py-2">
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <CalendarCheckIcon className="size-4" aria-hidden /> Nada pendente para hoje.
            </p>
            <Button asChild variant="outline" size="sm"><Link to="/tarefas">Planejar tarefas</Link></Button>
          </div>
        ) : (
          <ul className="divide-y">
            {items.map(({ item, count }) => {
              const time = formatTime(item.dueTime, null)
              return (
                <li key={`${item.taskId}|${item.date ?? ''}`}>
                  <Link to={taskLink(item)}
                    className="flex items-center gap-2 rounded-md py-2 text-sm outline-none hover:bg-muted/50 focus-visible:ring-[3px] focus-visible:ring-ring/50">
                    <span className="min-w-0 flex-1 truncate">{item.title}</span>
                    {item.groupId && <UsersIcon className="size-3.5 shrink-0 text-muted-foreground" aria-label="Tarefa de grupo" />}
                    {item.overdue ? (
                      <span className="flex shrink-0 items-center gap-1 text-xs text-destructive">
                        <AlarmClockIcon className="size-3.5" aria-hidden />
                        {count > 1 ? `${count} atrasadas` : item.scheduledDate ? formatDate(item.scheduledDate) : 'Atrasada'}
                      </span>
                    ) : time && (
                      <span className="flex shrink-0 items-center gap-1 text-xs text-muted-foreground tabular-nums">
                        <ClockIcon className="size-3.5" aria-hidden />{time}
                      </span>
                    )}
                  </Link>
                </li>
              )
            })}
            {rest > 0 && (
              <li className="pt-2 text-xs text-muted-foreground">
                e mais {rest} — <Link to="/tarefas" className="underline underline-offset-2">ver todas</Link>
              </li>
            )}
          </ul>
        )}
    </HomeCard>
  )
}

function HabitsCard() {
  const { data, isLoading, isError } = useHabits(null)
  const habits = [...(data ?? [])].sort((a, b) => b.streak - a.streak).slice(0, SHOWN)
  return (
    <HomeCard title="Hábitos" to="/tarefas?aba=habitos" description="Sequência atual e o mês">
      {isError ? <p className="text-sm text-destructive">Não foi possível carregar os hábitos.</p>
        : isLoading ? <Skeleton className="h-32 w-full" />
        : habits.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Nenhum hábito ainda. Numa tarefa repetida, ligue “Acompanhar como hábito”.
          </p>
        ) : (
          <ul className="space-y-2">
            {habits.map((h) => (
              <li key={h.taskId} className="flex items-center gap-2 text-sm">
                <span className="min-w-0 flex-1 truncate">{h.title}</span>
                {h.monthRate !== null && <span className="text-xs text-muted-foreground tabular-nums">{h.monthRate}% no mês</span>}
                <span className="flex w-10 shrink-0 items-center justify-end gap-0.5 font-semibold tabular-nums"
                  aria-label={`Sequência de ${h.streak}`}>
                  <FlameIcon className={cn('size-4', h.streak > 0 ? 'text-orange-500' : 'text-muted-foreground')} aria-hidden />
                  <span aria-hidden>{h.streak}</span>
                </span>
              </li>
            ))}
          </ul>
        )}
    </HomeCard>
  )
}

function FinanceCard() {
  const month = currentYearMonth()
  const { data, isLoading, isError } = useSummary(month)
  const rows = data && [
    { label: 'Entradas', value: data.total.income, sign: '+', className: 'text-income' },
    { label: 'Saídas', value: data.total.expense, sign: '−', className: 'text-expense' },
  ]
  return (
    <HomeCard title="Financeiro" to="/financeiro" description={formatMonth(month)} className="lg:col-span-3">
      {isError ? <p className="text-sm text-destructive">Não foi possível carregar o resumo.</p>
        : isLoading || !data || !rows ? <Skeleton className="h-16 w-full" />
        : data.total.income === 0 && data.total.expense === 0 ? (
          <div className="flex flex-col items-start gap-3">
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <WalletIcon className="size-4" aria-hidden /> Nada lançado neste mês.
            </p>
            <Button asChild variant="outline" size="sm"><Link to="/financeiro/lancamentos">Lançar</Link></Button>
          </div>
        ) : (
          <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {rows.map((r) => (
              <div key={r.label} className="min-w-0 space-y-0.5">
                <dt className="text-xs text-muted-foreground">{r.label}</dt>
                <dd className={cn('truncate font-semibold tabular-nums sm:text-lg', r.className)}>{r.sign} {formatCurrency(r.value)}</dd>
              </div>
            ))}
            <div className="col-span-2 min-w-0 space-y-0.5 sm:col-span-1">
              <dt className="text-xs text-muted-foreground">Saldo</dt>
              <dd className={cn('truncate font-semibold tabular-nums text-data sm:text-lg', data.total.balance < 0 && 'text-destructive')}>
                {formatCurrency(data.total.balance)}
              </dd>
            </div>
          </dl>
        )}
    </HomeCard>
  )
}
