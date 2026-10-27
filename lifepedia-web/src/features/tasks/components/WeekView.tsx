import {
  DndContext,
  type DragEndEvent,
  DragOverlay,
  type DragStartEvent,
  type KeyboardCoordinateGetter,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import {
  BellIcon,
  CheckIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ChevronsLeftRightIcon,
  ChevronsRightLeftIcon,
  ClockIcon,
  EllipsisVerticalIcon,
  FlameIcon,
  GripVerticalIcon,
  PlusIcon,
  UsersIcon,
  WalletIcon,
} from 'lucide-react'
import { type ReactNode, useState } from 'react'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { useIsMobile } from '@/hooks/use-mobile'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useAgenda } from '../api'
import { useBoard } from '../board'
import { addDays, formatTime, isoWeekday, mondayOf, PRIORITY_COLOR, WEEKDAYS_SHORT } from '../dates'
import { occurrenceKey } from '../labels'
import type { Agenda, TaskFilters, TaskItem } from '../types'
import type { TaskActions } from './task-actions'
import type { TaskTarget } from './TaskDialog'

type Props = {
  today: string
  filters: TaskFilters
  actions: TaskActions
  onEdit: (target: TaskTarget) => void
}

const range = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' })

function rangeLabel(start: string) {
  const [a, b] = [start, addDays(start, 6)].map((iso) => range.format(new Date(`${iso}T12:00:00`)).replace('.', ''))
  return `${a} – ${b}`
}

const COLLAPSED_KEY = 'lifepedia.tasks.week.collapsed'

/** Dias da semana (1 = segunda) recolhidos no quadro; preferência só deste navegador. */
function useCollapsedDays() {
  const [days, setDays] = useState<number[]>(() => {
    try {
      const saved: unknown = JSON.parse(localStorage.getItem(COLLAPSED_KEY) ?? '[]')
      return Array.isArray(saved) ? saved.filter((d): d is number => Number.isInteger(d) && d >= 1 && d <= 7) : []
    } catch {
      return []
    }
  })
  function toggle(day: number) {
    const next = days.includes(day) ? days.filter((d) => d !== day) : [...days, day]
    setDays(next)
    try {
      localStorage.setItem(COLLAPSED_KEY, JSON.stringify(next))
    } catch {
      // storage indisponível: vale só nesta tela
    }
  }
  return { days, toggle }
}

const STEP: Record<string, number> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }

/** Setas levam ao dia anterior/seguinte (o padrão move 25px). */
const dayCoordinates: KeyboardCoordinateGetter = (event, { context: { active, over, droppableRects, collisionRect } }) => {
  const step = STEP[event.code]
  const from = over ? String(over.id) : (active?.data.current?.day as string | undefined)
  if (!step || !from || !collisionRect) return undefined
  const rect = droppableRects.get(addDays(from, step))
  if (!rect) return undefined
  event.preventDefault()
  return { x: rect.left + (rect.width - collisionRect.width) / 2, y: rect.top + 8 }
}

/** Semana (seg–dom) em colunas; arrastar uma pendente para outro dia muda o dia só dela. */
export function WeekView({ today, filters, actions, onEdit }: Props) {
  const { groupId } = useBoard()
  const [start, setStart] = useState(() => mondayOf(today))
  const { data, isLoading, isError } = useAgenda(groupId, start, addDays(start, 6), false, filters)
  // Mostra no dia novo até a agenda recarregar.
  const [moved, setMoved] = useState<{ basis: Agenda; key: string; to: string } | null>(null)
  const [dragging, setDragging] = useState<TaskItem | null>(null)
  const isMobile = useIsMobile()
  const collapsed = useCollapsedDays()
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: dayCoordinates }),
  )

  if (isError) return <p className="text-sm text-destructive">Não foi possível carregar a semana.</p>
  if (isLoading || !data) return <Skeleton className="h-96 w-full" />

  const pending = moved && moved.basis === data ? moved : null
  const days = data.days.map((day) => ({
    date: day.date,
    items: [
      ...day.items.filter((i) => occurrenceKey(i) !== pending?.key),
      ...(pending && pending.to === day.date
        ? data.days.flatMap((d) => d.items).filter((i) => occurrenceKey(i) === pending.key) : []),
    ],
  }))
  const byKey = new Map(data.days.flatMap((d) => d.items).map((i) => [occurrenceKey(i), i]))

  function onDragStart({ active }: DragStartEvent) {
    setDragging(byKey.get(String(active.id)) ?? null)
  }

  async function onDragEnd({ active, over }: DragEndEvent) {
    setDragging(null)
    const item = byKey.get(String(active.id))
    const to = over ? String(over.id) : null
    if (!item || !to || !data || to === item.scheduledDate) return
    setMoved({ basis: data, key: occurrenceKey(item), to })
    if (!(await actions.move(item, to))) setMoved(null)
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon-sm" aria-label="Semana anterior" onClick={() => setStart((s) => addDays(s, -7))}>
          <ChevronLeftIcon />
        </Button>
        <h2 className="min-w-32 text-center text-sm font-medium tabular-nums" aria-live="polite">{rangeLabel(start)}</h2>
        <Button variant="ghost" size="icon-sm" aria-label="Próxima semana" onClick={() => setStart((s) => addDays(s, 7))}>
          <ChevronRightIcon />
        </Button>
        {start !== mondayOf(today) && (
          <Button variant="outline" size="sm" className="ml-1" onClick={() => setStart(mondayOf(today))}>Esta semana</Button>
        )}
        <p className="ml-auto hidden text-xs text-muted-foreground md:block">Arraste pela alça para mudar o dia.</p>
      </div>

      <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={(e) => void onDragEnd(e)}
        onDragCancel={() => setDragging(null)}
        accessibility={{
          screenReaderInstructions: {
            draggable: 'Para mudar o dia, aperte espaço ou Enter, use as setas até o dia e aperte de novo. Esc cancela.',
          },
          announcements: {
            onDragStart: ({ active }) => `Pegou ${byKey.get(String(active.id))?.title ?? ''}.`,
            onDragOver: ({ over }) => (over ? `Sobre ${formatDate(String(over.id))}.` : undefined),
            onDragEnd: ({ over }) => (over ? `Solto em ${formatDate(String(over.id))}.` : 'Solto fora dos dias.'),
            onDragCancel: () => 'Cancelado.',
          },
        }}>
        {/* Desktop: quadro com os 7 dias lado a lado (dia recolhido dá espaço aos outros); celular: um embaixo do outro. */}
        <div className="@container">
          <div className="grid gap-3 @xl:grid-cols-2 lg:flex lg:items-stretch lg:gap-2">
            {days.map((day) => (
              <DayColumn key={day.date} date={day.date} today={today} count={day.items.length}
                collapsed={!isMobile && collapsed.days.includes(isoWeekday(day.date))}
                onToggle={isMobile ? undefined : () => collapsed.toggle(isoWeekday(day.date))}
                onAdd={() => onEdit({ mode: 'new', groupId, dueDate: day.date, listId: filters.list })}>
                {day.items.map((item) => (
                  <WeekCard key={occurrenceKey(item)} item={item} actions={actions} onEdit={onEdit}
                    busy={actions.busyKey === occurrenceKey(item)} />
                ))}
              </DayColumn>
            ))}
          </div>
        </div>
        <DragOverlay dropAnimation={null}>
          {dragging && <CardBody item={dragging} overlay />}
        </DragOverlay>
      </DndContext>
    </div>
  )
}

function DayColumn({ date, today, count, collapsed, onToggle, onAdd, children }: Readonly<{
  date: string
  today: string
  count: number
  collapsed: boolean
  onToggle?: () => void
  onAdd: () => void
  children: ReactNode[]
}>) {
  const { setNodeRef, isOver } = useDroppable({ id: date })
  const isToday = date === today
  const weekday = WEEKDAYS_SHORT[isoWeekday(date) - 1]
  const label = `${weekday[0].toUpperCase()}${weekday.slice(1)} - ${formatDate(date).slice(0, 5)}`
  const tone = cn('rounded-xl border bg-card transition-colors motion-reduce:transition-none',
    isOver && 'border-primary bg-primary/5', date < today && 'bg-card/60')

  if (collapsed) {
    return (
      <section ref={setNodeRef} aria-label={formatDate(date)} className={cn(tone, 'flex w-10 shrink-0 lg:min-h-48')}>
        <button type="button" onClick={onToggle} aria-expanded={false}
          aria-label={`Expandir ${formatDate(date)} (${count} ${count === 1 ? 'tarefa' : 'tarefas'})`}
          className="flex w-full flex-col items-center gap-2 rounded-xl py-2 text-xs font-medium text-muted-foreground outline-none hover:bg-muted/50 hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50">
          <ChevronsLeftRightIcon className="size-3.5 shrink-0" aria-hidden />
          <span className={cn('whitespace-nowrap tabular-nums [writing-mode:vertical-rl]',
            isToday && 'rounded-full bg-primary py-1.5 text-primary-foreground')}>{label}</span>
          {count > 0 && <span className="rounded-full bg-muted px-1.5 text-[10px] text-foreground tabular-nums">{count}</span>}
        </button>
      </section>
    )
  }

  return (
    <section ref={setNodeRef} aria-label={formatDate(date)}
      className={cn(tone, 'flex min-w-0 flex-col gap-1.5 p-2 lg:min-h-48 lg:flex-1')}>
      <div className="flex items-center gap-1 px-1">
        <h3 className={cn('min-w-0 truncate text-xs font-medium whitespace-nowrap text-muted-foreground tabular-nums', isToday && 'text-foreground')}>
          {isToday ? <span className="rounded-full bg-primary px-1.5 py-0.5 text-primary-foreground">{label}</span> : label}
        </h3>
        <span className="ml-auto flex shrink-0">
          {onToggle && (
            <Button variant="ghost" size="icon-xs" aria-label={`Recolher ${formatDate(date)}`} aria-expanded onClick={onToggle}>
              <ChevronsRightLeftIcon />
            </Button>
          )}
          <Button variant="ghost" size="icon-xs" aria-label={`Nova tarefa em ${formatDate(date)}`} onClick={onAdd}>
            <PlusIcon />
          </Button>
        </span>
      </div>
      {children.length ? children : <p className="px-1 py-2 text-xs text-muted-foreground lg:hidden">Nada marcado.</p>}
    </section>
  )
}

function WeekCard({ item, actions, onEdit, busy }: Readonly<{
  item: TaskItem
  actions: TaskActions
  onEdit: (target: TaskTarget) => void
  busy: boolean
}>) {
  const key = occurrenceKey(item)
  const movable = item.status === 'PENDING'
  const { setNodeRef, setActivatorNodeRef, attributes, listeners, isDragging } = useDraggable({ id: key, disabled: !movable || busy, data: { day: item.scheduledDate } })
  const rowActions = actions.rowActions(item)
  return (
    <div ref={setNodeRef} className={cn(isDragging && 'opacity-40')}>
      <CardBody item={item} busy={busy}
        onToggle={() => void actions.toggle(item)}
        onOpen={() => onEdit({ mode: 'edit', groupId: item.groupId, taskId: item.taskId, occurrence: item })}
        handle={movable && (
          <button ref={setActivatorNodeRef} type="button" {...attributes} {...listeners} disabled={busy}
            aria-label={`Arrastar ${item.title} para outro dia`}
            className="relative -my-1 -ml-1 flex w-4 shrink-0 cursor-grab touch-none items-center justify-center rounded text-muted-foreground outline-none after:absolute after:-inset-y-1 after:-left-2 after:right-0 hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 active:cursor-grabbing">
            <GripVerticalIcon className="size-3.5" />
          </button>
        )}
        menu={rowActions.length > 0 && (
          <>
            <Button variant="ghost" size="icon-xs" className="md:hidden" aria-label={`Opções de ${item.title}`}
              onClick={() => actions.openSheet(item)}>
              <EllipsisVerticalIcon />
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon-xs" className="hidden md:inline-flex" aria-label={`Opções de ${item.title}`}>
                  <EllipsisVerticalIcon />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {rowActions.map(({ label, icon: Icon, onSelect, destructive }) => (
                  <DropdownMenuItem key={label} onSelect={onSelect} variant={destructive ? 'destructive' : 'default'}>
                    <Icon /> {label}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </>
        )} />
    </div>
  )
}

/** Cartão compacto (também é o que aparece sob o dedo/mouse ao arrastar). */
function CardBody({ item, overlay, busy, onToggle, onOpen, handle, menu }: Readonly<{
  item: TaskItem
  overlay?: boolean
  busy?: boolean
  onToggle?: () => void
  onOpen?: () => void
  handle?: ReactNode
  menu?: ReactNode
}>) {
  const { groupId } = useBoard()
  const done = item.status === 'DONE'
  const color = PRIORITY_COLOR[item.priority]
  const time = formatTime(item.dueTime, null)
  // Cartão estreito (muitos dias abertos): título na linha de baixo, com a largura toda.
  return (
    <div className="@container">
      <div className={cn('flex flex-wrap items-start gap-1.5 rounded-lg border bg-background px-2 py-1.5 text-sm',
        overlay && 'shadow-lg ring-1 ring-border', item.overdue && 'border-destructive/50')}>
        {handle}
        <button type="button" role="checkbox" aria-checked={done} disabled={busy || overlay} onClick={onToggle}
          aria-label={done ? `Desmarcar ${item.title}` : `Concluir ${item.title}`}
          className={cn('relative mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-full border-2 outline-none after:absolute after:-inset-2',
            'focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-60',
            done ? 'border-transparent bg-income text-background' : 'border-muted-foreground/60 hover:bg-muted')}
          style={!done && color ? { borderColor: color } : undefined}>
          {done && <CheckIcon className="size-3" strokeWidth={3} />}
        </button>
        <div className="min-w-0 flex-1 @max-[11rem]:order-last @max-[11rem]:basis-full">
          <button type="button" onClick={onOpen} disabled={overlay} title={item.title}
            className={cn('block w-full text-left leading-snug break-words outline-none focus-visible:underline',
              done && 'text-muted-foreground line-through')}>
            <span className="line-clamp-2">{item.title}</span>
          </button>
          {(time || item.streak || item.recurringEntryId || (item.groupId && item.groupId !== groupId)) && (
            <p className="flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
              {time && (
                <span className="flex items-center gap-0.5">
                  <ClockIcon className="size-3" aria-hidden />{time}
                  {item.reminderMinutes !== null && <BellIcon className="size-3" aria-label="Com lembrete" />}
                </span>
              )}
              {!!item.streak && (
                <span className="flex items-center gap-0.5" aria-label={`Sequência de ${item.streak}`}>
                  <FlameIcon className="size-3 text-orange-500" aria-hidden /><span aria-hidden>{item.streak}</span>
                </span>
              )}
              {item.recurringEntryId && <WalletIcon className="size-3" aria-label="Ligada a um fixo" />}
              {item.groupId && item.groupId !== groupId && <UsersIcon className="size-3" aria-label="Tarefa de grupo" />}
            </p>
          )}
        </div>
        {menu && <span className="ml-auto flex shrink-0">{menu}</span>}
      </div>
    </div>
  )
}
