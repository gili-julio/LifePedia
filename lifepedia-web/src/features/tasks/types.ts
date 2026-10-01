/** Espelha `task/Priority.java`. */
export type Priority = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH'
/** Espelha `Recurrence.Unit` / `Recurrence.MonthMode`. */
export type RepeatUnit = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'
export type MonthMode = 'DAY_OF_MONTH' | 'LAST_DAY' | 'WEEKDAY'
/** Espelha `TaskOccurrence.Status`. */
export type OccurrenceStatus = 'PENDING' | 'DONE' | 'SKIPPED'
/** Espelha `TaskDtos.Sort` / `TaskDtos.Scope`. */
export type TaskSort = 'MANUAL' | 'PRIORITY' | 'TIME'
export type Scope = 'THIS' | 'FOLLOWING' | 'ALL'

/** `weekdays`: 1 = segunda ... 7 = domingo. */
export type RepeatRule = {
  unit: RepeatUnit
  interval: number
  weekdays: number[]
  monthMode: MonthMode | null
  fromCompletion: boolean
  until: string | null
  count: number | null
}

export type SubtaskState = { id: string; title: string; done: boolean }

/** Uma ocorrência na agenda. `date` = chave (dia pela regra); `scheduledDate` = onde aparece. */
export type TaskItem = {
  taskId: string
  date: string | null
  scheduledDate: string | null
  title: string
  notes: string | null
  priority: Priority
  dueTime: string | null
  durationMinutes: number | null
  status: OccurrenceStatus
  overdue: boolean
  completedAt: string | null
  listId: string | null
  tagIds: string[]
  subtasks: SubtaskState[]
  repeat: RepeatRule | null
  habit: boolean
  edited: boolean
  /** Nulo = pessoal. Na visão pessoal vêm também as de grupo em que a pessoa é a responsável. */
  groupId: string | null
  /** Responsável desta ocorrência (fixo, vez do rodízio ou trocado só nesta) */
  assigneeId: string | null
  rotating: boolean
  createdBy: string | null
  completedBy: string | null
  commentCount: number
  /** Quem criou ou administrador do grupo */
  canDelete: boolean
  /** Sequência do hábito (nulo se não for hábito) */
  streak: number | null
  /** Lembrete N minutos antes (0 = na hora) */
  reminderMinutes: number | null
  /** Fixo do financeiro ligado (só pessoal) */
  recurringEntryId: string | null
}

export type AgendaDay = { date: string; items: TaskItem[] }
export type Agenda = { today: string; overdue: TaskItem[]; days: AgendaDay[] }

export type HistoryItem = {
  taskId: string
  date: string | null
  title: string
  listId: string | null
  completedAt: string
  recurring: boolean
  completedBy: string | null
}

export type Task = {
  id: string
  title: string
  notes: string | null
  priority: Priority
  dueDate: string | null
  dueTime: string | null
  durationMinutes: number | null
  listId: string | null
  tagIds: string[]
  habit: boolean
  repeat: RepeatRule | null
  subtasks: { id: string; title: string }[]
  completedAt: string | null
  groupId: string | null
  createdBy: string | null
  assigneeId: string | null
  /** Rodízio em ordem (vazio = sem rodízio) */
  rotation: string[]
  canDelete: boolean
  reminderMinutes: number | null
  recurringEntryId: string | null
}

export type TaskRequest = {
  title: string
  notes: string | null
  priority: Priority
  dueDate: string | null
  dueTime: string | null
  durationMinutes: number | null
  listId: string | null
  tagIds: string[]
  habit: boolean
  repeat: RepeatRule | null
  subtasks: { id: string | null; title: string }[]
  assigneeId: string | null
  rotation: string[]
  reminderMinutes: number | null
  recurringEntryId: string | null
}

export type OccurrenceRequest = {
  date: string
  moveTo: string
  title: string
  notes: string | null
  dueTime: string | null
  priority: Priority
}

/** Lista ou etiqueta; `openCount` = tarefas não concluídas com ela. */
export type Label = { id: string; name: string; color: string; openCount: number }
export type Labels = { lists: Label[]; tags: Label[] }
export type LabelKind = 'lists' | 'tags'

/** `assignee`: só em grupo. */
export type TaskFilters = { list: string | null; tag: string | null; assignee: string | null; sort: TaskSort }

/** `author` nulo = conta excluída. */
export type TaskComment = {
  id: string
  author: { id: string; name: string; username: string | null } | null
  body: string
  createdAt: string
  canDelete: boolean
}

/** Espelha `HabitStats.Day`: `PENDING` = ainda dá tempo (hoje ou adiada para frente). */
export type HabitState = 'DONE' | 'SKIPPED' | 'MISSED' | 'PENDING'

/** `state` nulo = não tinha ocorrência no dia. */
export type HabitDay = { date: string; state: HabitState | null }

/** Hábito com sequência, % do mês (nulo sem ocorrência vencida) e os últimos 7 dias. */
export type HabitSummary = {
  taskId: string
  title: string
  listId: string | null
  groupId: string | null
  repeat: RepeatRule
  start: string
  streak: number
  best: number
  monthRate: number | null
  monthDone: number
  monthDue: number
  days: HabitDay[]
}
