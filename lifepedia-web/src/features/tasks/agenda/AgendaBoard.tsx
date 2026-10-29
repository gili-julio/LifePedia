import {
  DndContext,
  type DragEndEvent,
  MouseSensor,
  TouchSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { ChartColumnIcon, CheckIcon, ChevronLeftIcon, ChevronRightIcon, Clock3Icon, PlusIcon, UsersIcon } from 'lucide-react'
import { type ReactNode, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import type { GroupDetail } from '@/features/groups/types'
import { useIsMobile } from '@/hooks/use-mobile'
import { api, ApiError } from '@/lib/api'
import { formatDate, todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { tasksBase, useAgenda, useAgendaSettings, useSaveTask, useUpdateOccurrence } from '../api'
import { type Board, BoardContext, useBoardValue } from '../board'
import { addDays, dayLabel, endTime, fromMinutes, isoWeekday, mondayOf, toMinutes, WEEKDAYS_SHORT } from '../dates'
import { type LabelLookup, occurrenceKey, useLabelLookup } from '../labels'
import { eachProgress, isDoneFor } from '../progress'
import { ScopeDialog } from '../components/ScopeDialog'
import { type TaskActions, useTaskActions } from '../components/task-actions'
import { TaskDialog, type TaskTarget } from '../components/TaskDialog'
import { fromTask } from '../components/task-form'
import type { Agenda, Task, TaskItem } from '../types'
import { type ColorBy, COLOR_BY_LABEL, colorOf, personColor } from './colors'
import { AgendaMetrics, METRICS_ID } from './AgendaMetrics'
import { AgendaSettingsButton } from './AgendaSettingsButton'
import { fit, MIN_BLOCK_MINUTES, place, type Span } from './layout'

/** Altura de uma hora na grade (px). */
const HOUR = 48
const PX = HOUR / 60
/** Arrastar anda de 15 em 15 min; clicar no vazio sugere a meia hora. */
const SNAP = 15
const CLICK_SNAP = 30
/** Tarefa com horário e sem duração ocupa 30 min (só na tela). */
const DEFAULT_MINUTES = 30
const ALL = '__all'
const PERSONAL = '__me'

type View = 'dia' | 'semana'
type Props = {
  /** Nulo = agenda pessoal (com as de grupo em que você é responsável). */
  group: GroupDetail | null
  /** Cabeçalho da página pessoal; sem ele (aba do grupo) o botão de nova tarefa fica na barra. */
  header?: (newTask: () => void) => ReactNode
}

/** Bloco da ocorrência na grade (minutos desde 0h). */
type Entry = Span<TaskItem>
/** Mostra no lugar novo até a agenda recarregar. */
type Moved = { basis: Agenda; key: string; to: string; time: string }
type SeriesMove = { item: TaskItem; to: string; time: string }

const VIEW_KEY = 'lifepedia.agenda.color'

/** Cor escolhida, lembrada por tela (pessoal ou grupo) neste navegador. */
function useColorBy(scope: 'me' | 'group') {
  const fallback: ColorBy = scope === 'me' ? 'list' : 'person'
  const key = `${VIEW_KEY}.${scope}`
  const [value, setValue] = useState<ColorBy>(() => {
    try {
      const saved = localStorage.getItem(key)
      return saved && saved in COLOR_BY_LABEL ? (saved as ColorBy) : fallback
    } catch {
      return fallback
    }
  })
  function change(next: ColorBy) {
    setValue(next)
    try {
      localStorage.setItem(key, next)
    } catch {
      // storage indisponível: vale só nesta tela
    }
  }
  return [value, change] as const
}

const isDay = (v: string | null): v is string => !!v && /^\d{4}-\d{2}-\d{2}$/.test(v)

/**
 * Agenda em grade de horários (dia ou semana): as tarefas com horário ficam no intervalo da duração, lado a lado quando
 * se cruzam, coloridas por lista, responsável, prioridade ou grupo. Arrastar muda dia/horário; clicar no vazio cria.
 */
export function AgendaBoard({ group, header }: Props) {
  const board = useBoardValue(group)
  const groupId = board.groupId
  const isMobile = useIsMobile()
  const [params, setParams] = useSearchParams()
  const view: View = params.get('vista') === 'dia' || params.get('vista') === 'semana'
    ? (params.get('vista') as View) : isMobile ? 'dia' : 'semana'
  const day = isDay(params.get('dia')) ? params.get('dia') as string : todayIso()
  const [colorBy, setColorBy] = useColorBy(groupId ? 'group' : 'me')
  const [assignee, setAssignee] = useState<string | null>(null)
  const [source, setSource] = useState<string>(ALL)
  const [fullDay, setFullDay] = useState(false)
  const [chosen, setChosen] = useState<TaskTarget | null>(null)
  const [moved, setMoved] = useState<Moved | null>(null)
  const [series, setSeries] = useState<SeriesMove | null>(null)
  const scroller = useRef<HTMLDivElement>(null)
  const scrolled = useRef(false)
  // Soltar o bloco também dispara o clique: ignora a abertura logo depois de arrastar.
  const dragged = useRef(false)
  const settings = useAgendaSettings()
  const from = view === 'semana' ? mondayOf(day) : day
  const to = view === 'semana' ? addDays(from, 6) : day
  const { data, isLoading, isError } = useAgenda(groupId, from, to, false, { list: null, tag: null, assignee, sort: 'TIME' })
  const labels = useLabelLookup(groupId)
  const today = data?.today ?? todayIso()
  const actions = useTaskActions({ today, groupId, onEdit: setChosen })
  const updateOccurrence = useUpdateOccurrence()
  const saveTask = useSaveTask()
  const now = useNow()
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 8 } }),
  )
  const range = fullDay ? { start: 0, end: 24 } : settings.data ? { start: settings.data.dayStart, end: settings.data.dayEnd } : null
  const memberIds = board.members.map((m) => m.user.id)
  const rangeStart = range?.start ?? null

  // Rola até agora (ou até a 1ª tarefa) só na primeira carga.
  useEffect(() => {
    const el = scroller.current
    if (!el || !data || rangeStart === null || scrolled.current) return
    scrolled.current = true
    const first = data.days.flatMap((d) => d.items).map((i) => (i.dueTime ? toMinutes(i.dueTime) : null))
      .filter((m): m is number => m !== null).sort((a, b) => a - b)[0]
    const target = data.days.some((d) => d.date === today) ? now : first ?? rangeStart * 60
    el.scrollTop = Math.max(0, (target - rangeStart * 60) * PX - 96)
  }, [data, rangeStart, today, now])

  function update(changes: Record<string, string | null>) {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      for (const [key, value] of Object.entries(changes)) {
        if (value === null) next.delete(key)
        else next.set(key, value)
      }
      return next
    }, { replace: true })
  }

  const newTask = (dueDate = day, dueTime: string | null = null) =>
    setChosen({ mode: 'new', groupId, dueDate, listId: null, dueTime })

  if (isError) return <p className="text-sm text-destructive">Não foi possível carregar a agenda.</p>

  const pending = moved && moved.basis === data ? moved : null
  const all = (data?.days ?? []).flatMap((d) => d.items)
  const byKey = new Map(all.map((i) => [occurrenceKey(i), i]))
  const visible = (item: TaskItem) => source === ALL || (source === PERSONAL ? !item.groupId : item.groupId === source)
  const sources = [...new Set(all.map((i) => i.groupId).filter((g): g is string => !!g))]
  const days = (data?.days ?? []).map((d) => {
    const items = d.items.filter((i) => occurrenceKey(i) !== pending?.key)
    if (pending && pending.to === d.date) {
      const item = byKey.get(pending.key)
      if (item) items.push({ ...item, scheduledDate: pending.to, dueTime: pending.time })
    }
    return { date: d.date, items: items.filter(visible) }
  })
  const outside = !range ? 0 : days.flatMap((d) => d.items).filter((i) => {
    if (!i.dueTime) return false
    const start = toMinutes(i.dueTime)
    return start < range.start * 60 || start >= range.end * 60
  }).length

  async function apply(item: TaskItem, toDay: string, time: string, wholeSeries: boolean) {
    if (!data || !item.date) return
    setMoved({ basis: data, key: occurrenceKey(item), to: toDay, time })
    try {
      if (wholeSeries) {
        const task = await api<Task>(`${tasksBase(item.groupId)}/${item.taskId}`)
        await saveTask.mutateAsync({ groupId: item.groupId, id: item.taskId, data: fromTask(task, { dueTime: time }) })
        if (toDay !== item.scheduledDate && !(await actions.move(item, toDay))) setMoved(null)
      } else if (time === item.dueTime && item.repeat && !item.repeat.fromCompletion) {
        if (!(await actions.move(item, toDay))) setMoved(null)
        return
      } else {
        await updateOccurrence.mutateAsync({
          groupId: item.groupId,
          id: item.taskId,
          data: { date: item.date, moveTo: toDay, title: item.title, notes: item.notes, dueTime: time, priority: item.priority },
        })
      }
      toast.success(`${toDay === item.scheduledDate ? 'Horário mudou' : `Movida para ${dayLabel(toDay, today).toLowerCase()}`} (${time}).`)
      warnClash(item, toDay, time)
    } catch (error) {
      setMoved(null)
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível mover.')
    }
  }

  /** Aviso de choque com o que já está na tela no dia novo (mesmas pessoas; no pessoal, qualquer uma). */
  function warnClash(item: TaskItem, toDay: string, time: string) {
    const start = toMinutes(time)
    const end = start + (item.durationMinutes ?? DEFAULT_MINUTES)
    const clash = days.find((d) => d.date === toDay)?.items.find((other) => {
      if (occurrenceKey(other) === occurrenceKey(item) || !other.dueTime || other.status !== 'PENDING') return false
      const s = toMinutes(other.dueTime)
      const e = s + (other.durationMinutes ?? DEFAULT_MINUTES)
      const shared = !groupId || !item.assignees.length || !other.assignees.length
        || other.assignees.some((p) => item.assignees.includes(p))
      return s < end && start < e && shared
    })
    if (clash) toast.warning(`Choque de horário com “${clash.title}” (${clash.dueTime}).`)
  }

  function endDrag() {
    dragged.current = true
    window.setTimeout(() => {
      dragged.current = false
    }, 300)
  }

  function onDragEnd({ active, over, delta }: DragEndEvent) {
    endDrag()
    const item = byKey.get(String(active.id))
    if (!item?.dueTime || !item.scheduledDate) return
    const toDay = over ? String(over.id) : item.scheduledDate
    const shift = Math.round(delta.y / PX / SNAP) * SNAP
    const time = fromMinutes(Math.min(Math.max(toMinutes(item.dueTime) + shift, 0), 24 * 60 - SNAP))
    if (toDay === item.scheduledDate && time === item.dueTime) return
    if (item.repeat && !item.repeat.fromCompletion && time !== item.dueTime) setSeries({ item, to: toDay, time })
    else void apply(item, toDay, time, false)
  }

  const link = (date: string) => {
    if (groupId) return `/grupos/${groupId}?aba=tarefas${date === today ? '' : `&vista=semana&dia=${date}`}`
    return date === today ? '/tarefas' : `/tarefas?aba=semana&dia=${date}`
  }

  const toolbar = (
    <div className="flex flex-wrap items-center gap-2">
      <div className="flex rounded-full border p-0.5" role="group" aria-label="Visualização">
        {(['dia', 'semana'] as const).map((v) => (
          <Button key={v} size="sm" variant={view === v ? 'secondary' : 'ghost'} aria-pressed={view === v}
            className="h-7 rounded-full px-3" onClick={() => update({ vista: v })}>
            {v === 'dia' ? 'Dia' : 'Semana'}
          </Button>
        ))}
      </div>
      <div className="flex items-center gap-0.5">
        <Button variant="ghost" size="icon-sm" aria-label={view === 'dia' ? 'Dia anterior' : 'Semana anterior'}
          onClick={() => update({ dia: addDays(day, view === 'dia' ? -1 : -7) })}>
          <ChevronLeftIcon />
        </Button>
        <h2 className="min-w-28 text-center text-sm font-medium tabular-nums" aria-live="polite">
          {view === 'dia' ? dayLabel(day, today) : rangeLabel(from)}
        </h2>
        <Button variant="ghost" size="icon-sm" aria-label={view === 'dia' ? 'Próximo dia' : 'Próxima semana'}
          onClick={() => update({ dia: addDays(day, view === 'dia' ? 1 : 7) })}>
          <ChevronRightIcon />
        </Button>
        {(view === 'dia' ? day !== today : from !== mondayOf(today)) && (
          <Button variant="outline" size="sm" className="ml-1" onClick={() => update({ dia: null })}>Hoje</Button>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
        {groupId ? (
          <Select value={assignee ?? ALL} onValueChange={(v) => setAssignee(v === ALL ? null : v)}>
            <SelectTrigger size="sm" className="min-w-0 max-w-44" aria-label="Responsável"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Todos</SelectItem>
              {board.meId && <SelectItem value={board.meId}>Minhas</SelectItem>}
              {board.members.filter((m) => m.user.id !== board.meId).map((m) => (
                <SelectItem key={m.user.id} value={m.user.id}>{m.user.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : sources.length > 0 && (
          <Select value={source} onValueChange={setSource}>
            <SelectTrigger size="sm" className="min-w-0 max-w-44" aria-label="Mostrar"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Pessoais e grupos</SelectItem>
              <SelectItem value={PERSONAL}>Só pessoais</SelectItem>
              {sources.map((id) => <SelectItem key={id} value={id}>{board.groupName(id) ?? 'Grupo'}</SelectItem>)}
            </SelectContent>
          </Select>
        )}
        <Select value={colorBy} onValueChange={(v) => setColorBy(v as ColorBy)}>
          <SelectTrigger size="sm" className="min-w-0" aria-label="Colorir por"><SelectValue /></SelectTrigger>
          <SelectContent>
            {(groupId ? ['person', 'list', 'priority'] as const : ['list', 'group', 'priority'] as const).map((c) => (
              <SelectItem key={c} value={c}>Cor: {COLOR_BY_LABEL[c].toLowerCase()}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        {!groupId && (
          <Button variant="outline" size="sm" onClick={showMetrics}><ChartColumnIcon /> Indicadores</Button>
        )}
        <AgendaSettingsButton />
        {!header && <Button size="sm" onClick={() => newTask()}><PlusIcon /> Nova tarefa</Button>}
      </div>
    </div>
  )

  return (
    <BoardContext value={board}>
      <div className="space-y-3">
        {header?.(() => newTask())}
        {toolbar}
        <Legend colorBy={colorBy} board={board} labels={labels} />
        {view === 'dia' && (
          <WeekStrip day={day} today={today} onPick={(d) => update({ dia: d })} />
        )}
        {(outside > 0 || fullDay) && range && (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm">
            <Clock3Icon className="size-4 text-muted-foreground" aria-hidden />
            <span className="min-w-0 flex-1">
              {fullDay ? 'Mostrando o dia inteiro.'
                : `${outside} ${outside === 1 ? 'tarefa fora' : 'tarefas fora'} do horário visível (${range.start}h às ${range.end}h).`}
            </span>
            <Button variant="outline" size="sm" onClick={() => setFullDay((v) => !v)}>
              {fullDay ? 'Voltar ao horário' : 'Mostrar o dia todo'}
            </Button>
          </div>
        )}

        {isLoading || !data || !range ? <Skeleton className="h-[28rem] w-full" /> : (
          <DndContext sensors={sensors} onDragEnd={onDragEnd} onDragCancel={endDrag}
            accessibility={{
              screenReaderInstructions: { draggable: 'Arraste com o mouse ou segure no toque para mudar o dia ou o horário. Para o teclado, abra a tarefa e edite o horário.' },
            }}>
            <div ref={scroller} className="max-h-[calc(100dvh-15rem)] min-h-96 overflow-auto rounded-xl border bg-card">
              <div className={cn('grid', view === 'semana' && 'min-w-[44rem]')}
                style={{ gridTemplateColumns: `3rem repeat(${days.length}, minmax(0, 1fr))` }}>
                <div className="sticky top-0 left-0 z-30 border-b bg-card" />
                {days.map((d) => (
                  <DayHeader key={d.date} date={d.date} today={today} untimed={d.items.filter((i) => !i.dueTime).length}
                    link={link(d.date)} compact={view === 'semana'} onAdd={() => newTask(d.date)} />
                ))}
                <TimeAxis start={range.start} end={range.end} />
                {days.map((d) => (
                  <DayColumn key={d.date} date={d.date} range={range} max={view === 'semana' ? 2 : 6}
                    items={d.items.filter((i) => !!i.dueTime)} now={d.date === today ? now : null}
                    onCreate={(time) => newTask(d.date, time)}>
                    {(entry, cols) => (
                      <Block key={occurrenceKey(entry.value)} entry={entry} cols={cols} range={range}
                        color={colorOf(entry.value, colorBy, labels, memberIds)} actions={actions} board={board}
                        compact={view === 'semana'} onOpen={() => {
                          if (dragged.current) return
                          setChosen({ mode: 'edit', groupId: entry.value.groupId, taskId: entry.value.taskId, occurrence: entry.value })
                        }} />
                    )}
                  </DayColumn>
                ))}
              </div>
            </div>
          </DndContext>
        )}

        {!groupId && <AgendaMetrics monday={mondayOf(day)} defaultMinutes={DEFAULT_MINUTES} />}

        {actions.dialogs}
        <TaskDialog target={chosen} onOpenChange={(open) => !open && setChosen(null)} />
        <ScopeDialog open={series !== null} onOpenChange={(open) => !open && setSeries(null)}
          title="Tarefa repetida" description={series ? `Mudar o horário para ${series.time} em quais ocorrências?` : undefined}
          options={[
            { scope: 'THIS', label: 'Só esta' },
            { scope: 'ALL', label: 'Todas', hint: 'As próximas ocorrências passam a ser nesse horário.' },
          ]}
          onChoose={(scope) => {
            if (series) void apply(series.item, series.to, series.time, scope === 'ALL')
            setSeries(null)
          }} />
      </div>
    </BoardContext>
  )
}

/** Leva aos indicadores (no celular ficam bem abaixo da grade) e põe o foco no título. */
function showMetrics() {
  const title = document.getElementById(METRICS_ID)
  if (!title) return
  const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  title.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'start' })
  title.focus({ preventScroll: true })
}

const rangeFormat = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' })

function rangeLabel(start: string) {
  const [a, b] = [start, addDays(start, 6)].map((iso) => rangeFormat.format(new Date(`${iso}T12:00:00`)).replace('.', ''))
  return `${a} – ${b}`
}

/** Minutos desde 0h agora (atualiza a cada minuto). */
function useNow() {
  const read = () => {
    const d = new Date()
    return d.getHours() * 60 + d.getMinutes()
  }
  const [now, setNow] = useState(read)
  useEffect(() => {
    const id = window.setInterval(() => setNow(read()), 60_000)
    return () => window.clearInterval(id)
  }, [])
  return now
}

function WeekStrip({ day, today, onPick }: { day: string; today: string; onPick: (day: string) => void }) {
  const monday = mondayOf(day)
  return (
    <div className="grid grid-cols-7 gap-1" role="group" aria-label="Dias da semana">
      {Array.from({ length: 7 }, (_, i) => addDays(monday, i)).map((d) => (
        <button key={d} type="button" aria-pressed={d === day} onClick={() => onPick(d)}
          className={cn('flex flex-col items-center rounded-lg py-1 text-xs outline-none transition-colors motion-reduce:transition-none',
            'focus-visible:ring-[3px] focus-visible:ring-ring/50',
            d === day ? 'bg-foreground text-background' : 'hover:bg-muted', d === today && d !== day && 'font-semibold text-primary')}>
          <span>{WEEKDAYS_SHORT[isoWeekday(d) - 1]}</span>
          <span className="text-sm tabular-nums">{Number(d.slice(8))}</span>
        </button>
      ))}
    </div>
  )
}

function Legend({ colorBy, board, labels }: { colorBy: ColorBy; board: Board; labels: LabelLookup }) {
  const memberIds = board.members.map((m) => m.user.id)
  if (colorBy === 'person' && board.members.length) {
    return (
      <ul className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground" aria-label="Cores por responsável">
        {board.members.map((m) => (
          <li key={m.user.id} className="flex items-center gap-1.5">
            <span className="size-2.5 rounded-full" style={{ background: personColor(memberIds, m.user.id) }} aria-hidden />
            <UserAvatar user={m.user} size="xs" />
            {board.personName(m.user.id)}
          </li>
        ))}
      </ul>
    )
  }
  if (colorBy === 'list' && labels.lists.length) {
    return (
      <ul className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground" aria-label="Cores por lista">
        {labels.lists.map((l) => (
          <li key={l.id} className="flex items-center gap-1.5">
            <span className="size-2.5 rounded-full" style={{ background: l.color }} aria-hidden />{l.name}
          </li>
        ))}
      </ul>
    )
  }
  return null
}

function DayHeader({ date, today, untimed, link, compact, onAdd }: {
  date: string
  today: string
  untimed: number
  link: string
  compact: boolean
  onAdd: () => void
}) {
  const isToday = date === today
  return (
    <div className="sticky top-0 z-20 flex min-w-0 flex-col gap-1 border-b border-l bg-card px-1.5 py-1.5">
      <div className="flex items-center gap-1">
        <span className={cn('min-w-0 truncate text-xs font-medium text-muted-foreground tabular-nums', isToday && 'text-foreground')}>
          {WEEKDAYS_SHORT[isoWeekday(date) - 1]}{' '}
          <span className={cn(isToday && 'rounded-full bg-primary px-1.5 py-0.5 text-primary-foreground')}>{formatDate(date).slice(0, 5)}</span>
        </span>
        <Button variant="ghost" size="icon-xs" className="ml-auto" aria-label={`Nova tarefa em ${formatDate(date)}`} onClick={onAdd}>
          <PlusIcon />
        </Button>
      </div>
      {untimed > 0 && (
        <Link to={link} title="Ver as tarefas do dia sem horário"
          className="self-start truncate rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-foreground outline-none hover:bg-muted/70 focus-visible:ring-[3px] focus-visible:ring-ring/50">
          +{untimed}{compact ? ' sem hora' : ` ${untimed === 1 ? 'tarefa' : 'tarefas'} sem horário`}
        </Link>
      )}
    </div>
  )
}

function TimeAxis({ start, end }: { start: number; end: number }) {
  return (
    <div className="sticky left-0 z-10 bg-card" style={{ height: (end - start) * HOUR }} aria-hidden>
      {Array.from({ length: end - start }, (_, i) => (
        <span key={i} className="absolute right-1.5 -translate-y-1/2 text-[10px] text-muted-foreground tabular-nums"
          style={{ top: i * HOUR }}>
          {i === 0 ? '' : `${String(start + i).padStart(2, '0')}:00`}
        </span>
      ))}
    </div>
  )
}

function DayColumn({ date, range, max, items, now, onCreate, children }: {
  date: string
  range: { start: number; end: number }
  max: number
  items: TaskItem[]
  now: number | null
  onCreate: (time: string) => void
  children: (entry: Entry & { col: number }, cols: number) => ReactNode
}) {
  const { setNodeRef, isOver } = useDroppable({ id: date })
  const startMin = range.start * 60
  const endMin = range.end * 60
  const spans: Entry[] = items.map((item) => {
    const start = toMinutes(item.dueTime as string)
    return { value: item, start, end: start + (item.durationMinutes ?? DEFAULT_MINUTES) }
  }).filter((s) => s.end > startMin && s.start < endMin)
  const { shown, more } = fit(place(spans), max)

  return (
    <div ref={setNodeRef} role="presentation"
      className={cn('relative cursor-cell border-l transition-colors motion-reduce:transition-none', isOver && 'bg-primary/5')}
      style={{
        height: (range.end - range.start) * HOUR,
        backgroundImage: 'linear-gradient(to bottom, var(--border) 1px, transparent 1px)',
        backgroundSize: `100% ${HOUR}px`,
      }}
      onClick={(e) => {
        if (e.target !== e.currentTarget) return
        const y = e.clientY - e.currentTarget.getBoundingClientRect().top
        const minutes = startMin + Math.floor(y / PX / CLICK_SNAP) * CLICK_SNAP
        onCreate(fromMinutes(Math.min(minutes, 24 * 60 - CLICK_SNAP)))
      }}>
      {shown.map((entry) => children(entry, entry.cols))}
      {more.map((m) => (
        <Popover key={`more-${m.start}`}>
          <PopoverTrigger asChild>
            <button type="button"
              className="absolute z-[5] flex items-start justify-center rounded-md border bg-muted px-1 pt-0.5 text-[11px] font-medium outline-none hover:bg-muted/70 focus-visible:ring-[3px] focus-visible:ring-ring/50"
              style={{
                top: (Math.max(m.start, startMin) - startMin) * PX,
                height: Math.max((Math.min(m.end, endMin) - Math.max(m.start, startMin)) * PX, MIN_BLOCK_MINUTES * PX),
                left: `calc(${(m.col / m.cols) * 100}% + 1px)`,
                width: `calc(${100 / m.cols}% - 2px)`,
              }}
              aria-label={`Mais ${m.items.length} tarefas neste horário`}>
              +{m.items.length}
            </button>
          </PopoverTrigger>
          <PopoverContent className="w-64 p-2">
            <ul className="space-y-1">
              {m.items.map((p) => (
                <li key={occurrenceKey(p.value)} className="text-sm">
                  <span className="text-xs text-muted-foreground tabular-nums">{p.value.dueTime} </span>{p.value.title}
                </li>
              ))}
            </ul>
            <p className="pt-2 text-xs text-muted-foreground">Abra a visão de dia para ver todas lado a lado.</p>
          </PopoverContent>
        </Popover>
      ))}
      {now !== null && now >= startMin && now < endMin && (
        <div className="pointer-events-none absolute inset-x-0 z-10 flex items-center" style={{ top: (now - startMin) * PX }}
          aria-label={`Agora, ${fromMinutes(now)}`}>
          <span className="-ml-1 size-2 rounded-full bg-destructive" />
          <span className="h-0.5 flex-1 bg-destructive" />
        </div>
      )}
    </div>
  )
}

function Block({ entry, cols, range, color, actions, board, compact, onOpen }: {
  entry: Entry & { col: number }
  cols: number
  range: { start: number; end: number }
  color: string
  actions: TaskActions
  board: Board
  compact: boolean
  onOpen: () => void
}) {
  const item = entry.value
  const key = occurrenceKey(item)
  const busy = actions.busyKey === key
  const movable = item.status === 'PENDING' && !!item.date
  const { setNodeRef, attributes, listeners, transform, isDragging } = useDraggable({ id: key, disabled: !movable || busy })
  const startMin = range.start * 60
  const top = (Math.max(entry.start, startMin) - startMin) * PX
  const height = Math.max((Math.min(entry.end, range.end * 60) - Math.max(entry.start, startMin)) * PX, MIN_BLOCK_MINUTES * PX)
  const done = isDoneFor(item, board.meId)
  const progress = eachProgress(item)
  const duration = item.durationMinutes ?? DEFAULT_MINUTES
  const preview = isDragging && transform && item.dueTime
    ? fromMinutes(Math.min(Math.max(toMinutes(item.dueTime) + Math.round(transform.y / PX / SNAP) * SNAP, 0), 24 * 60 - SNAP))
    : null
  const faces = board.groupId ? item.assignees.slice(0, compact ? 2 : 4)
    .map((id) => board.members.find((m) => m.user.id === id)?.user).filter((u) => !!u) : []
  const foreign = item.groupId !== null && item.groupId !== board.groupId
  // Bloco estreito (semana dividida): título numa linha só, sem quebrar no meio da palavra.
  const short = height < 36 || (compact && cols > 1)

  return (
    <div ref={setNodeRef}
      className={cn('absolute overflow-hidden rounded-md border text-xs shadow-xs', isDragging ? 'z-40 shadow-lg' : 'z-[5]',
        done && 'opacity-60')}
      style={{
        top,
        height,
        left: `calc(${(entry.col / cols) * 100}% + 1px)`,
        width: `calc(${100 / cols}% - 2px)`,
        transform: CSS.Translate.toString(transform),
        background: `color-mix(in oklab, ${color} 16%, var(--card))`,
        borderColor: `color-mix(in oklab, ${color} 45%, transparent)`,
        borderLeft: `3px solid ${color}`,
      }}>
      <div className={cn('flex h-full gap-1 px-1.5', height < 36 ? 'items-center py-0' : 'items-start py-1')}>
        <button type="button" role="checkbox" aria-checked={done} disabled={busy}
          aria-label={done ? `Desmarcar ${item.title}` : `Concluir ${item.title}`}
          onPointerDown={(e) => e.stopPropagation()} onClick={() => void actions.toggle(item)}
          className={cn('relative mt-px flex size-3.5 shrink-0 items-center justify-center rounded-full border-[1.5px] outline-none after:absolute after:-inset-2',
            'focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50',
            done ? 'border-transparent bg-income text-background' : 'border-foreground/50 bg-card hover:bg-muted')}>
          {done && <CheckIcon className="size-2.5" strokeWidth={3} />}
        </button>
        <button type="button" {...attributes} {...listeners} onClick={onOpen}
          aria-label={`${item.title}, ${item.dueTime} às ${endTime(item.dueTime as string, duration)}. Abrir`}
          className={cn('min-w-0 flex-1 self-stretch text-left outline-none focus-visible:underline',
            movable ? 'cursor-grab touch-none active:cursor-grabbing' : 'cursor-pointer')}>
          <span className={cn('block leading-tight font-medium break-words', short ? 'truncate' : 'line-clamp-3', done && 'line-through')}>
            {item.title}
          </span>
          {!short || (compact && cols > 1 && height >= 36) ? (
            <span className="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-[11px] text-muted-foreground tabular-nums">
              <span>{preview ? `→ ${preview}` : `${item.dueTime}–${endTime(item.dueTime as string, duration)}`}</span>
              {progress && <span>{progress.done}/{progress.total}</span>}
              {foreign && (
                <span className="flex min-w-0 items-center gap-0.5">
                  <UsersIcon className="size-3 shrink-0" aria-hidden />
                  {!compact && <span className="truncate">{board.groupName(item.groupId)}</span>}
                </span>
              )}
            </span>
          ) : null}
          {!short && height >= 64 && faces.length > 0 && (
            <span className="mt-1 flex -space-x-1" aria-label={`Responsáveis: ${board.assigneesLabel(item)}`}>
              {faces.map((u) => <UserAvatar key={u.id} user={u} size="xs" className="ring-2 ring-card" />)}
              {item.assignees.length > faces.length && (
                <span className="flex size-5 items-center justify-center rounded-full bg-muted text-[9px] ring-2 ring-card">
                  +{item.assignees.length - faces.length}
                </span>
              )}
            </span>
          )}
        </button>
      </div>
    </div>
  )
}
