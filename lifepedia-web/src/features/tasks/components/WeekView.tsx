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
import { BellIcon, CheckIcon, ChevronLeftIcon, ChevronRightIcon, ClockIcon, EllipsisVerticalIcon, FlameIcon, GripVerticalIcon, PlusIcon, UsersIcon, WalletIcon } from 'lucide-react'
import { type ReactNode, useState } from 'react'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
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
        {/* 7 colunas só com largura para o título (a sidebar aberta tira 256px). */}
        <div className="@container">
          <div className="grid gap-3 @xl:grid-cols-2 @4xl:grid-cols-4 @min-[76rem]:grid-cols-7 @min-[76rem]:gap-2">
            {days.map((day) => (
              <DayColumn key={day.date} date={day.date} today={today}
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

function DayColumn({ date, today, onAdd, children }: { date: string; today: string; onAdd: () => void; children: ReactNode[] }) {
  const { setNodeRef, isOver } = useDroppable({ id: date })
  const isToday = date === today
  const weekday = WEEKDAYS_SHORT[isoWeekday(date) - 1]
  const label = `${weekday[0].toUpperCase()}${weekday.slice(1)} - ${formatDate(date).slice(0, 5)}`
  return (
    <section ref={setNodeRef} aria-label={formatDate(date)}
      className={cn('flex min-w-0 flex-col gap-1.5 rounded-xl border bg-card p-2 transition-colors motion-reduce:transition-none @min-[76rem]:min-h-48',
        isOver && 'border-primary bg-primary/5', date < today && 'bg-card/60')}>
      <div className="flex items-center justify-between gap-1 px-1">
        <h3 className={cn('text-xs font-medium whitespace-nowrap text-muted-foreground tabular-nums', isToday && 'text-foreground')}>
          {isToday ? <span className="rounded-full bg-primary px-1.5 py-0.5 text-primary-foreground">{label}</span> : label}
        </h3>
        <Button variant="ghost" size="icon-xs" aria-label={`Nova tarefa em ${formatDate(date)}`} onClick={onAdd}>
          <PlusIcon />
        </Button>
      </div>
      {children.length ? children : <p className="px-1 py-2 text-xs text-muted-foreground @min-[76rem]:hidden">Nada marcado.</p>}
    </section>
  )
}

function WeekCard({ item, actions, onEdit, busy }: {
  item: TaskItem
  actions: TaskActions
  onEdit: (target: TaskTarget) => void
  busy: boolean
}) {
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
function CardBody({ item, overlay, busy, onToggle, onOpen, handle, menu }: {
  item: TaskItem
  overlay?: boolean
  busy?: boolean
  onToggle?: () => void
  onOpen?: () => void
  handle?: ReactNode
  menu?: ReactNode
}) {
  const { groupId } = useBoard()
  const done = item.status === 'DONE'
  const color = PRIORITY_COLOR[item.priority]
  const time = formatTime(item.dueTime, null)
  return (
    <div className={cn('flex items-start gap-1.5 rounded-lg border bg-background px-2 py-1.5 text-sm',
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
      <div className="min-w-0 flex-1">
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
      {menu}
    </div>
  )
}
