import { CheckCircle2Icon, PlusIcon, RepeatIcon, RotateCcwIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { useAgenda, useHistory, useSetStatus, useUndated } from '../api'
import { useBoard } from '../board'
import { addDays, dayLabel } from '../dates'
import type { LabelLookup } from '../labels'
import type { TaskFilters } from '../types'
import { QuickAdd } from './QuickAdd'
import type { TaskActions } from './task-actions'
import type { TaskTarget } from './TaskDialog'
import { TaskItems } from './TaskItems'

type ViewProps = {
  today: string
  filters: TaskFilters
  labels: LabelLookup
  actions: TaskActions
  onEdit: (target: TaskTarget) => void
}

function Section({ title, count, action, children }: { title: string; count?: number; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="space-y-1">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-medium">
          {title}{count !== undefined && count > 0 && <span className="ml-1.5 text-muted-foreground tabular-nums">{count}</span>}
        </h2>
        {action}
      </div>
      <div className="rounded-xl border bg-card px-4">{children}</div>
    </section>
  )
}

function Empty({ children, onAdd }: { children: ReactNode; onAdd?: () => void }) {
  return (
    <div className="flex flex-col items-center gap-2 py-8 text-center text-sm text-muted-foreground">
      <p>{children}</p>
      {onAdd && <Button variant="outline" size="sm" onClick={onAdd}><PlusIcon /> Nova tarefa</Button>}
    </div>
  )
}

const loading = <div className="space-y-2"><Skeleton className="h-10 w-full" /><Skeleton className="h-40 w-full" /></div>

/** Atrasadas + hoje. */
export function TodayView({ today, filters, labels, actions, onEdit }: ViewProps) {
  const { groupId } = useBoard()
  const { data, isLoading, isError } = useAgenda(groupId, today, today, true, filters)
  if (isError) return <p className="text-sm text-destructive">Não foi possível carregar as tarefas.</p>
  if (isLoading || !data) return loading
  const items = data.days[0]?.items ?? []
  const pending = items.filter((i) => i.status === 'PENDING').length
  const shared = { today: data.today, labels, actions, onEdit, manual: filters.sort === 'MANUAL' }

  return (
    <div className="space-y-5">
      <QuickAdd dueDate={data.today} listId={filters.list} placeholder="Adicionar tarefa para hoje" />
      {data.overdue.length > 0 && (
        <Section title="Atrasadas" count={data.overdue.length}>
          <TaskItems items={data.overdue} overdue {...shared} />
        </Section>
      )}
      <Section title="Hoje" count={pending}>
        {items.length ? <TaskItems items={items} {...shared} /> : (
          <Empty onAdd={() => onEdit({ mode: 'new', groupId, dueDate: data.today, listId: filters.list })}>
            {data.overdue.length ? 'Nada marcado para hoje.' : 'Nada para hoje. Aproveite!'}
          </Empty>
        )}
      </Section>
    </div>
  )
}

const STEP = 7
const MAX_SPAN = 56

/** Próximos dias, a partir de amanhã, um bloco por dia. */
export function UpcomingView({ today, filters, labels, actions, onEdit }: ViewProps) {
  const { groupId } = useBoard()
  const [span, setSpan] = useState(STEP)
  const from = addDays(today, 1)
  const { data, isLoading, isError } = useAgenda(groupId, from, addDays(today, span), false, filters)
  if (isError) return <p className="text-sm text-destructive">Não foi possível carregar as tarefas.</p>
  if (isLoading || !data) return loading
  const shared = { today: data.today, labels, actions, onEdit, manual: filters.sort === 'MANUAL' }

  return (
    <div className="space-y-5">
      {data.days.map((day) => (
        <Section key={day.date} title={dayLabel(day.date, data.today)} count={day.items.filter((i) => i.status === 'PENDING').length}
          action={
            <Button variant="ghost" size="icon-sm" aria-label={`Nova tarefa em ${dayLabel(day.date, data.today)}`}
              onClick={() => onEdit({ mode: 'new', groupId, dueDate: day.date, listId: filters.list })}>
              <PlusIcon />
            </Button>
          }>
          {day.items.length ? <TaskItems items={day.items} {...shared} /> : (
            <p className="py-3 text-sm text-muted-foreground">Nada marcado.</p>
          )}
        </Section>
      ))}
      {span < MAX_SPAN && (
        <Button variant="outline" className="w-full" onClick={() => setSpan((s) => s + STEP)}>Mostrar mais {STEP} dias</Button>
      )}
    </div>
  )
}

/** Tarefas sem data (pendentes). */
export function UndatedView({ filters, labels, actions, onEdit, today }: ViewProps) {
  const { groupId } = useBoard()
  const { data, isLoading, isError } = useUndated(groupId, filters)
  if (isError) return <p className="text-sm text-destructive">Não foi possível carregar as tarefas.</p>
  if (isLoading || !data) return loading

  return (
    <div className="space-y-5">
      <QuickAdd dueDate={null} listId={filters.list} placeholder="Adicionar tarefa sem data" />
      <Section title="Sem data" count={data.length}>
        {data.length ? (
          <TaskItems items={data} today={today} labels={labels} actions={actions} onEdit={onEdit} manual={filters.sort === 'MANUAL'} />
        ) : (
          <Empty onAdd={() => onEdit({ mode: 'new', groupId, dueDate: null, listId: filters.list })}>
            Tarefas sem dia ficam aqui, para fazer quando der.
          </Empty>
        )}
      </Section>
    </div>
  )
}

/** Histórico de concluídas (paginado pela API). */
export function DoneView({ labels }: { labels: LabelLookup }) {
  const board = useBoard()
  const list = useListState({ field: 'completedAt', direction: 'desc' }, 25)
  const { data, isLoading, isError } = useHistory(board.groupId, list.params)
  const setStatus = useSetStatus()
  useClampPage(data, list.setPage)
  if (isError) return <p className="text-sm text-destructive">Não foi possível carregar o histórico.</p>
  if (isLoading || !data) return loading

  async function reopen(taskId: string, date: string | null) {
    try {
      await setStatus.mutateAsync({ groupId: board.groupId, id: taskId, date, status: 'PENDING' })
      toast.success('Tarefa reaberta.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível reabrir.')
    }
  }

  return (
    <div className="space-y-3">
      <div className="rounded-xl border bg-card px-4">
        {data.items.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">Nenhuma tarefa concluída ainda.</p>
        ) : (
          <ul className="divide-y">
            {data.items.map((item) => {
              const listLabel = labels.list(item.listId)
              const doneBy = board.groupId ? board.personName(item.completedBy) : null
              return (
                <li key={`${item.taskId}|${item.date}`} className="flex items-start gap-3 py-3">
                  <CheckCircle2Icon className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden />
                  <div className="min-w-0 flex-1 space-y-1">
                    <p className="leading-snug">{item.title}</p>
                    <p className="flex flex-wrap items-center gap-x-2.5 text-xs text-muted-foreground">
                      <span>
                        Concluída em {new Date(item.completedAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}
                        {doneBy && ` por ${doneBy === 'Você' ? 'você' : doneBy}`}
                      </span>
                      {item.recurring && item.date && (
                        <span className="flex items-center gap-1"><RepeatIcon className="size-3.5" aria-hidden />de {formatDate(item.date)}</span>
                      )}
                      {listLabel && (
                        <span className="flex items-center gap-1">
                          <span className="size-2 rounded-full" style={{ background: listLabel.color }} aria-hidden />{listLabel.name}
                        </span>
                      )}
                    </p>
                  </div>
                  <Button variant="ghost" size="icon-sm" aria-label={`Reabrir ${item.title}`} title="Reabrir"
                    disabled={setStatus.isPending} onClick={() => void reopen(item.taskId, item.date)}>
                    <RotateCcwIcon />
                  </Button>
                </li>
              )
            })}
          </ul>
        )}
      </div>
      <DataTablePagination data={data} list={list} />
    </div>
  )
}
