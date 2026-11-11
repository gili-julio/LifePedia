import { AlarmClockIcon, ArrowRightIcon, CalendarCheckIcon, ClockIcon, FlameIcon, PinIcon, UsersIcon, WalletIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { absenceTone, remainingLabel, TONE_BAR, TONE_TEXT } from '@/features/attendance/absences'
import { useCommitments } from '@/features/attendance/api'
import { useAuth } from '@/features/auth/auth-context'
import { useHiddenModules } from '@/features/auth/preferences'
import type { AppModule } from '@/features/auth/types'
import { useSummary } from '@/features/finance/api'
import { useFinancingOverview } from '@/features/financing/api'
import { useNotes } from '@/features/notes/api'
import { useAgenda, useHabits } from '@/features/tasks/api'
import { formatTime } from '@/features/tasks/dates'
import type { TaskFilters, TaskItem } from '@/features/tasks/types'
import { currentYearMonth, formatDate, formatMonth, formatRelative, todayIso } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { RecentChanges } from '../components/RecentChanges'

const FILTERS: TaskFilters = { list: null, tag: null, assignee: null, sort: 'MANUAL' }
const NOTE_FILTERS = { status: 'ACTIVE', q: '', folderId: null, tagId: null, taskId: null, commitmentId: null } as const
const SHOWN = 5

const longDate = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })

/** Link que abre a tarefa no quadro dela (pessoal ou do grupo). */
function taskLink(item: TaskItem) {
  const id = encodeURIComponent(item.taskId)
  return item.groupId ? `/grupos/${item.groupId}?aba=tarefas&tarefa=${id}` : `/tarefas?tarefa=${id}`
}

/** Módulos com cartão no Início. */
const HOME_MODULES: AppModule[] = ['TASKS', 'ATTENDANCE', 'FINANCE', 'FINANCING', 'NOTES']

/** Início: resumo de tarefas de hoje, hábitos, frequência, o mês no financeiro, financiamentos, notas e as mudanças recentes do sistema. */
export function HomePage() {
  const { user } = useAuth()
  const today = todayIso()
  const firstName = user?.name.trim().split(/\s+/)[0] ?? ''
  const hidden = useHiddenModules()
  const show = (module: AppModule) => !hidden.has(module)
  const any = HOME_MODULES.some(show)
  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Olá{firstName && `, ${firstName}`}</h1>
        <p className="text-sm text-muted-foreground first-letter:uppercase">{longDate.format(new Date(`${today}T12:00:00`))}</p>
      </div>
      {/* Módulo escondido no perfil não aparece (nem busca os dados); `grid-flow-dense` fecha os buracos. */}
      {any ? (
        <div className="grid gap-4 lg:grid-flow-dense lg:grid-cols-3">
          {show('TASKS') && <TasksCard today={today} />}
          {show('TASKS') && <HabitsCard />}
          {show('ATTENDANCE') && <AttendanceCard />}
          {show('FINANCE') && <FinanceCard />}
          {show('FINANCING') && <FinancingsCard />}
          {show('NOTES') && <NotesCard />}
        </div>
      ) : (
        <p className="rounded-xl border border-dashed p-4 text-sm text-muted-foreground">
          Os módulos do Início estão desligados. <Link to="/perfil" className="font-medium text-foreground underline-offset-2 hover:underline">Ligue no perfil</Link>.
        </p>
      )}
      <section aria-labelledby="recent-changes" className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h2 id="recent-changes" className="text-lg font-semibold tracking-tight">Mudanças recentes no sistema</h2>
          <Button asChild variant="ghost" size="sm">
            <Link to="/novidades">Ver todas <ArrowRightIcon /></Link>
          </Button>
        </div>
        <div className="max-w-3xl">
          <RecentChanges limit={3} />
        </div>
      </section>
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

function AttendanceCard() {
  const { data, isLoading, isError } = useCommitments()
  const pending = (data ?? []).reduce((total, c) => total + c.stats.pending, 0)
  return (
    <HomeCard title="Frequência" to="/frequencia"
      description={pending > 0 ? `${pending} ${pending === 1 ? 'dia pendente' : 'dias pendentes'} para marcar` : 'Faltas de cada compromisso'}>
      {isError ? <p className="text-sm text-destructive">Não foi possível carregar a frequência.</p>
        : isLoading || !data ? <Skeleton className="h-32 w-full" />
        : data.length === 0 ? (
          <div className="flex flex-col items-start gap-3">
            <p className="text-sm text-muted-foreground">Cadastre suas matérias ou compromissos fixos para contar as faltas.</p>
            <Button asChild variant="outline" size="sm"><Link to="/frequencia">Cadastrar</Link></Button>
          </div>
        ) : (
          <ul className="space-y-3">
            {data.slice(0, SHOWN).map((c) => {
              const tone = absenceTone(c.stats.absences, c.maxAbsences)
              return (
                <li key={c.id} className="space-y-1 text-sm">
                  <div className="flex items-center gap-2">
                    <span className="size-2.5 shrink-0 rounded-full" style={{ background: c.color }} aria-hidden />
                    <span className="min-w-0 flex-1 truncate">{c.name}</span>
                    <span className="shrink-0 font-semibold tabular-nums">{c.stats.absences}/{c.maxAbsences}</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden>
                    <div className={cn('h-full rounded-full', TONE_BAR[tone])}
                      style={{ width: `${c.maxAbsences > 0 ? Math.min(100, (c.stats.absences / c.maxAbsences) * 100) : c.stats.absences > 0 ? 100 : 0}%` }} />
                  </div>
                  <p className={cn('text-xs', TONE_TEXT[tone])}>{remainingLabel(c.stats.absences, c.maxAbsences)}</p>
                </li>
              )
            })}
          </ul>
        )}
    </HomeCard>
  )
}

function FinanceCard() {
  const { formatCurrency } = useMoney()
  const month = currentYearMonth()
  const { data, isLoading, isError } = useSummary(month)
  const rows = data && [
    { label: 'Entradas', value: data.total.income, sign: '+', className: 'text-income' },
    { label: 'Saídas', value: data.total.expense, sign: '−', className: 'text-expense' },
  ]
  return (
    <HomeCard title="Financeiro" to="/financeiro" description={formatMonth(month)} className="lg:col-span-2">
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

/** Financiamentos em andamento (pessoais e dos grupos): próxima parcela e quanto já foi quitado. */
function FinancingsCard() {
  const { formatCurrency } = useMoney()
  const { data, isLoading, isError } = useFinancingOverview()
  const active = (data ?? []).filter((f) => f.progress.next).slice(0, 3)
  return (
    <HomeCard title="Financiamentos" to="/financiamentos" description="Próximas parcelas">
      {isError ? <p className="text-sm text-destructive">Não foi possível carregar os financiamentos.</p>
        : isLoading || !data ? <Skeleton className="h-32 w-full" />
        : active.length === 0 ? (
          <div className="flex flex-col items-start gap-3">
            <p className="text-sm text-muted-foreground">Simule a compra de uma casa, carro ou moto com as taxas médias dos bancos.</p>
            <Button asChild variant="outline" size="sm"><Link to="/financiamentos/simular">Simular financiamento</Link></Button>
          </div>
        ) : (
          <ul className="space-y-3">
            {active.map((f) => {
              const next = f.progress.next!
              const amount = f.groupId && f.myShare !== null ? (next.amount * f.myShare) / 100 : next.amount
              return (
                <li key={f.id}>
                  <Link to={f.groupId ? `/grupos/${f.groupId}/financiamentos/${f.id}` : `/financiamentos/${f.id}`}
                    className="block space-y-1.5 rounded-lg text-sm hover:underline">
                    <span className="flex items-center gap-2">
                      <span className="min-w-0 flex-1 truncate font-medium">{f.name}</span>
                      <span className="shrink-0 tabular-nums">{formatCurrency(amount)}</span>
                    </span>
                    <span className="flex items-center gap-2 text-xs text-muted-foreground">
                      <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted" role="presentation">
                        <span className="block h-full rounded-full bg-data" style={{ width: `${f.progress.percent}%` }} />
                      </span>
                      <span className="shrink-0">vence {formatDate(next.date)}</span>
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
    </HomeCard>
  )
}

/** Notas fixadas primeiro e depois as editadas por último (ordem da API). */
function NotesCard() {
  const { data, isLoading, isError } = useNotes(null, NOTE_FILTERS, { page: 0, size: SHOWN, sort: 'updatedAt,desc' })
  return (
    <HomeCard title="Notas" to="/notas" description="Fixadas e editadas por último">
      {isError ? <p className="text-sm text-destructive">Não foi possível carregar as notas.</p>
        : isLoading || !data ? <Skeleton className="h-32 w-full" />
        : data.items.length === 0 ? (
          <div className="flex flex-col items-start gap-3">
            <p className="text-sm text-muted-foreground">Anote ideias, aulas, reuniões e listas em um lugar só.</p>
            <Button asChild variant="outline" size="sm"><Link to="/notas">Criar nota</Link></Button>
          </div>
        ) : (
          <ul className="space-y-2">
            {data.items.map((n) => (
              <li key={n.id}>
                <Link to={`/notas/${n.id}`} className="flex items-center gap-2 rounded-lg text-sm hover:underline">
                  <span aria-hidden>{n.icon ?? '📄'}</span>
                  <span className="min-w-0 flex-1 truncate">{n.title}</span>
                  {n.pinned && <PinIcon className="size-3.5 shrink-0 fill-current text-muted-foreground" aria-label="Fixada" />}
                  <span className="shrink-0 text-xs text-muted-foreground">{formatRelative(n.updatedAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
    </HomeCard>
  )
}
