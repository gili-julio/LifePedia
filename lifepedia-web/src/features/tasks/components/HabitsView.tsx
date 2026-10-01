import { CheckIcon, FlameIcon, MinusIcon, XIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useHabits, useSetStatus } from '../api'
import { useBoard } from '../board'
import { describeRepeat, isoWeekday, WEEKDAYS_SHORT } from '../dates'
import type { LabelLookup } from '../labels'
import type { HabitDay, HabitState, HabitSummary } from '../types'
import type { TaskTarget } from './TaskDialog'

const STATE_LABEL: Record<HabitState, string> = {
  DONE: 'feito',
  SKIPPED: 'pulado',
  MISSED: 'não feito',
  PENDING: 'pendente',
}

/** Hábitos com sequência, % do mês e os últimos 7 dias; tocar no dia marca ou desmarca. */
export function HabitsView({ today, labels, onEdit }: {
  today: string
  labels: LabelLookup
  onEdit: (target: TaskTarget) => void
}) {
  const { groupId } = useBoard()
  const { data, isLoading, isError } = useHabits(groupId)
  const setStatus = useSetStatus()
  if (isError) return <p className="text-sm text-destructive">Não foi possível carregar os hábitos.</p>
  if (isLoading || !data) return <div className="space-y-2"><Skeleton className="h-24 w-full" /><Skeleton className="h-24 w-full" /></div>

  if (data.length === 0) {
    return (
      <p className="rounded-xl border bg-card px-4 py-8 text-center text-sm text-muted-foreground">
        Nenhum hábito ainda. Crie uma tarefa repetida e ligue “Acompanhar como hábito”.
      </p>
    )
  }

  async function toggle(habit: HabitSummary, day: HabitDay) {
    const next = day.state === 'DONE' || day.state === 'SKIPPED' ? 'PENDING' : 'DONE'
    try {
      await setStatus.mutateAsync({ groupId, id: habit.taskId, date: day.date, status: next })
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível atualizar.')
    }
  }

  return (
    <ul className="space-y-3">
      {data.map((habit) => {
        const list = labels.list(habit.listId)
        return (
          <li key={habit.taskId} className="space-y-3 rounded-xl border bg-card p-4">
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1 space-y-0.5">
                <button type="button" className="block text-left leading-snug font-medium outline-none focus-visible:underline"
                  onClick={() => onEdit({ mode: 'edit', groupId, taskId: habit.taskId, occurrence: null })}>
                  {habit.title}
                </button>
                <p className="flex flex-wrap items-center gap-x-2.5 text-xs text-muted-foreground">
                  <span>{describeRepeat(habit.repeat, habit.start)}</span>
                  {list && (
                    <span className="flex items-center gap-1">
                      <span className="size-2 rounded-full" style={{ background: list.color }} aria-hidden />{list.name}
                    </span>
                  )}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-1 text-lg font-semibold tabular-nums"
                aria-label={`Sequência de ${habit.streak}`}>
                <FlameIcon className={cn('size-5', habit.streak > 0 ? 'text-orange-500' : 'text-muted-foreground')} aria-hidden />
                <span aria-hidden>{habit.streak}</span>
              </div>
            </div>

            <ol className="grid grid-cols-7 gap-1" aria-label="Últimos 7 dias">
              {habit.days.map((day) => (
                <li key={day.date} className="flex flex-col items-center gap-1">
                  <span className={cn('text-[11px] text-muted-foreground', day.date === today && 'font-semibold text-foreground')}>
                    {day.date === today ? 'hoje' : WEEKDAYS_SHORT[isoWeekday(day.date) - 1]}
                  </span>
                  <DayButton day={day} disabled={setStatus.isPending} onToggle={() => void toggle(habit, day)} />
                </li>
              ))}
            </ol>

            <p className="flex flex-wrap gap-x-3 text-xs text-muted-foreground">
              <span>Recorde: <span className="font-medium text-foreground tabular-nums">{habit.best}</span></span>
              <span>
                No mês:{' '}
                {habit.monthRate === null ? '—' : (
                  <><span className="font-medium text-foreground tabular-nums">{habit.monthRate}%</span> ({habit.monthDone} de {habit.monthDue})</>
                )}
              </span>
            </p>
          </li>
        )
      })}
    </ul>
  )
}

function DayButton({ day, disabled, onToggle }: { day: HabitDay; disabled: boolean; onToggle: () => void }) {
  if (!day.state) return <span className="flex size-8 items-center justify-center text-muted-foreground/50" aria-hidden>·</span>
  const label = `${formatDate(day.date)}: ${STATE_LABEL[day.state]}`
  return (
    <button type="button" onClick={onToggle} disabled={disabled} aria-pressed={day.state === 'DONE'}
      aria-label={`${label}. ${day.state === 'DONE' || day.state === 'SKIPPED' ? 'Desmarcar' : 'Marcar como feito'}`}
      title={label}
      className={cn(
        'flex size-8 items-center justify-center rounded-full border-2 outline-none transition-colors motion-reduce:transition-none',
        'focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50',
        day.state === 'DONE' && 'border-transparent bg-primary text-primary-foreground',
        day.state === 'SKIPPED' && 'border-dashed border-muted-foreground/50 text-muted-foreground',
        day.state === 'MISSED' && 'border-destructive/60 text-destructive',
        day.state === 'PENDING' && 'border-muted-foreground/60 hover:bg-muted',
      )}>
      {day.state === 'DONE' && <CheckIcon className="size-4" strokeWidth={3} />}
      {day.state === 'SKIPPED' && <MinusIcon className="size-4" />}
      {day.state === 'MISSED' && <XIcon className="size-4" />}
    </button>
  )
}
