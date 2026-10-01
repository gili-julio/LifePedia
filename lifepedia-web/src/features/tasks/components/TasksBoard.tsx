import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { GroupDetail } from '@/features/groups/types'
import { todayIso } from '@/lib/format'
import { type Board, BoardContext, useBoardValue } from '../board'
import { useLabelLookup, type LabelLookup } from '../labels'
import type { TaskFilters, TaskSort } from '../types'
import { LabelsDialog } from './LabelsDialog'
import { useTaskActions } from './task-actions'
import { TaskDialog, type TaskTarget } from './TaskDialog'
import { HabitsView } from './HabitsView'
import { DoneView, TodayView, UndatedView, UpcomingView } from './TaskViews'
import { WeekView } from './WeekView'

const TABS = ['hoje', 'semana', 'proximos', 'sem-data', 'habitos', 'concluidas'] as const
export type BoardTab = (typeof TABS)[number]
const ALL = '__all'

const SORTS: { value: TaskSort; label: string }[] = [
  { value: 'MANUAL', label: 'Minha ordem' },
  { value: 'PRIORITY', label: 'Prioridade' },
  { value: 'TIME', label: 'Horário' },
]

export type BoardHeader = {
  tab: BoardTab
  /** Filtros (somem em Hábitos e Concluídas) */
  filters: ReactNode
  newTask: () => void
  openLabels: () => void
}

type Props = {
  /** Nulo = tarefas pessoais. */
  group: GroupDetail | null
  /** Parâmetro da URL com a aba (a página do grupo já usa `aba`). */
  param: 'aba' | 'vista'
  header: (h: BoardHeader) => ReactNode
}

/**
 * Hoje (com atrasadas), semana, próximos dias, sem data, hábitos e concluídas, com filtros e dialogs. `?tarefa=<id>` abre a tarefa
 * (link das notificações).
 */
export function TasksBoard({ group, param, header }: Props) {
  const board = useBoardValue(group)
  const [params, setParams] = useSearchParams()
  const tab: BoardTab = TABS.find((t) => t === params.get(param)) ?? 'hoje'
  const [filters, setFilters] = useState<TaskFilters>({ list: null, tag: null, assignee: null, sort: 'MANUAL' })
  const [chosen, setChosen] = useState<TaskTarget | null>(null)
  const [labelsOpen, setLabelsOpen] = useState(false)
  const today = todayIso()
  const labels = useLabelLookup(board.groupId)
  const linked = params.get('tarefa')
  const target = chosen ?? (linked ? { mode: 'edit' as const, groupId: board.groupId, taskId: linked, occurrence: null } : null)
  const actions = useTaskActions({ today, groupId: board.groupId, onEdit: setChosen })
  const view = { today, filters, labels, actions, onEdit: setChosen }

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

  function closeDialog() {
    setChosen(null)
    if (linked) update({ tarefa: null })
  }

  return (
    <BoardContext value={board}>
      <div className="space-y-6">
        {header({
          tab,
          filters: tab !== 'concluidas' && tab !== 'habitos' && <Filters filters={filters} onChange={setFilters} labels={labels} board={board} />,
          newTask: () => setChosen({ mode: 'new', groupId: board.groupId, dueDate: tab === 'sem-data' ? null : today, listId: filters.list }),
          openLabels: () => setLabelsOpen(true),
        })}

        <Tabs value={tab} onValueChange={(value) => update({ [param]: value === 'hoje' ? null : value })}>
          <div className="-mx-4 overflow-x-auto px-4 [scrollbar-width:none] sm:mx-0 sm:px-0">
            <TabsList className="w-max">
              <TabsTrigger value="hoje">Hoje</TabsTrigger>
              <TabsTrigger value="semana">Semana</TabsTrigger>
              <TabsTrigger value="proximos">Próximos</TabsTrigger>
              <TabsTrigger value="sem-data">Sem data</TabsTrigger>
              <TabsTrigger value="habitos">Hábitos</TabsTrigger>
              <TabsTrigger value="concluidas">Concluídas</TabsTrigger>
            </TabsList>
          </div>
          <TabsContent value="hoje" className="pt-4"><TodayView {...view} /></TabsContent>
          <TabsContent value="semana" className="pt-4"><WeekView {...view} /></TabsContent>
          <TabsContent value="proximos" className="pt-4"><UpcomingView {...view} /></TabsContent>
          <TabsContent value="sem-data" className="pt-4"><UndatedView {...view} /></TabsContent>
          <TabsContent value="habitos" className="pt-4"><HabitsView today={today} labels={labels} onEdit={setChosen} /></TabsContent>
          <TabsContent value="concluidas" className="pt-4"><DoneView labels={labels} /></TabsContent>
        </Tabs>

        {actions.dialogs}
        <TaskDialog target={target} onOpenChange={(open) => !open && closeDialog()} />
        <LabelsDialog open={labelsOpen} onOpenChange={setLabelsOpen} />
      </div>
    </BoardContext>
  )
}

function Filters({ filters, onChange, labels, board }: {
  filters: TaskFilters
  onChange: (f: TaskFilters) => void
  labels: LabelLookup
  board: Board
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {board.groupId && (
        <Select value={filters.assignee ?? ALL} onValueChange={(v) => onChange({ ...filters, assignee: v === ALL ? null : v })}>
          <SelectTrigger size="sm" className="min-w-0 max-w-44" aria-label="Responsável"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todos</SelectItem>
            {board.meId && <SelectItem value={board.meId}>Minhas</SelectItem>}
            {board.members.filter((m) => m.user.id !== board.meId).map((m) => (
              <SelectItem key={m.user.id} value={m.user.id}>{m.user.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
      {labels.lists.length > 0 && (
        <Select value={filters.list ?? ALL} onValueChange={(v) => onChange({ ...filters, list: v === ALL ? null : v })}>
          <SelectTrigger size="sm" className="min-w-0 max-w-44" aria-label="Lista"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todas as listas</SelectItem>
            {labels.lists.map((l) => (
              <SelectItem key={l.id} value={l.id}>
                <span className="size-2 rounded-full" style={{ background: l.color }} aria-hidden />{l.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
      {labels.tags.length > 0 && (
        <Select value={filters.tag ?? ALL} onValueChange={(v) => onChange({ ...filters, tag: v === ALL ? null : v })}>
          <SelectTrigger size="sm" className="min-w-0 max-w-44" aria-label="Etiqueta"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todas as etiquetas</SelectItem>
            {labels.tags.map((t) => (
              <SelectItem key={t.id} value={t.id}>
                <span className="size-2 rounded-full" style={{ background: t.color }} aria-hidden />{t.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
      <Select value={filters.sort} onValueChange={(v) => onChange({ ...filters, sort: v as TaskSort })}>
        <SelectTrigger size="sm" className="min-w-0" aria-label="Ordenar"><SelectValue /></SelectTrigger>
        <SelectContent>
          {SORTS.map((s) => <SelectItem key={s.value} value={s.value}>{s.label}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  )
}
