import { z } from 'zod'
import { isoWeekday } from '../dates'
import type { RepeatRule, Task, TaskItem, TaskRequest } from '../types'

const whole = (min: number, max: number) => (v: string) => /^\d+$/.test(v) && Number(v) >= min && Number(v) <= max

export const taskSchema = z.object({
  title: z.string().trim().min(1, 'Informe a tarefa.').max(200, 'Máximo de 200 caracteres.'),
  notes: z.string().max(5000, 'Máximo de 5000 caracteres.'),
  /** '' = sem data */
  dueDate: z.string(),
  dueTime: z.string(),
  duration: z.string(),
  priority: z.enum(['NONE', 'LOW', 'MEDIUM', 'HIGH']),
  listId: z.string(),
  tagIds: z.array(z.string()).max(10, 'Máximo de 10 etiquetas.'),
  repeatOn: z.boolean(),
  unit: z.enum(['DAY', 'WEEK', 'MONTH', 'YEAR']),
  interval: z.string(),
  weekdays: z.array(z.number()),
  monthMode: z.enum(['DAY_OF_MONTH', 'LAST_DAY', 'WEEKDAY']),
  fromCompletion: z.boolean(),
  end: z.enum(['NEVER', 'UNTIL', 'COUNT']),
  until: z.string(),
  count: z.string(),
  subtasks: z.array(z.object({ id: z.string().nullable(), title: z.string().max(200, 'Máximo de 200 caracteres.') }))
    .max(50, 'Máximo de 50 subtarefas.'),
  /** Só em grupo: ninguém, uma pessoa fixa ou rodízio (precisa de repetição). */
  assignMode: z.enum(['NONE', 'FIXED', 'ROTATION']),
  assigneeId: z.string(),
  rotation: z.array(z.string()),
  /** Só com repetição em dias fixos. */
  habit: z.boolean(),
  /** Minutos antes do horário ('' = sem lembrete; '0' = na hora). */
  reminder: z.string(),
  /** Fixo do financeiro ('' = nenhum; só pessoal). */
  recurringEntryId: z.string(),
}).superRefine((v, ctx) => {
  const issue = (path: string, message: string) => ctx.addIssue({ code: 'custom', path: [path], message })
  if (v.dueTime && !v.dueDate) issue('dueTime', 'Escolha o dia para definir o horário.')
  if (v.assignMode === 'FIXED' && !v.assigneeId) issue('assigneeId', 'Escolha o responsável.')
  if (v.assignMode === 'ROTATION') {
    if (!v.repeatOn) issue('rotation', 'Rodízio precisa de repetição.')
    else if (v.rotation.length < 2) issue('rotation', 'Escolha pelo menos 2 pessoas.')
  }
  if (!v.repeatOn) return
  if (!v.dueDate) issue('dueDate', 'Escolha o dia de início da repetição.')
  if (!whole(1, 999)(v.interval)) issue('interval', 'Intervalo de 1 a 999.')
  if (v.end === 'UNTIL' && (!v.until || (v.dueDate && v.until < v.dueDate))) issue('until', 'O fim deve ser depois do início.')
  if (v.end === 'COUNT' && !whole(1, 1000)(v.count)) issue('count', 'De 1 a 1000 vezes.')
})

export type TaskFormInput = z.input<typeof taskSchema>
export type TaskFormValues = z.output<typeof taskSchema>

/** Valores iniciais: tarefa nova, tarefa inteira ou a ocorrência clicada (título/horário/dia dela). */
export function toForm(task?: Task, occurrence?: TaskItem | null, defaults?: { dueDate?: string | null; listId?: string | null }): TaskFormInput {
  const rule = task?.repeat
  const dueDate = occurrence?.scheduledDate ?? task?.dueDate ?? defaults?.dueDate ?? ''
  return {
    title: occurrence?.title ?? task?.title ?? '',
    notes: occurrence?.notes ?? task?.notes ?? '',
    dueDate,
    dueTime: occurrence?.dueTime ?? task?.dueTime ?? '',
    duration: task?.durationMinutes ? String(task.durationMinutes) : '',
    priority: occurrence?.priority ?? task?.priority ?? 'NONE',
    listId: task ? task.listId ?? '' : defaults?.listId ?? '',
    tagIds: task?.tagIds ?? [],
    repeatOn: !!rule,
    unit: rule?.unit ?? 'WEEK',
    interval: String(rule?.interval ?? 1),
    weekdays: rule?.weekdays.length ? rule.weekdays : dueDate ? [isoWeekday(dueDate)] : [],
    monthMode: rule?.monthMode ?? 'DAY_OF_MONTH',
    fromCompletion: rule?.fromCompletion ?? false,
    end: rule?.until ? 'UNTIL' : rule?.count ? 'COUNT' : 'NEVER',
    until: rule?.until ?? '',
    count: rule?.count ? String(rule.count) : '10',
    subtasks: task?.subtasks.map((s) => ({ id: s.id, title: s.title })) ?? [],
    assignMode: task?.rotation.length ? 'ROTATION' : task?.assigneeId ? 'FIXED' : 'NONE',
    assigneeId: task?.assigneeId ?? '',
    rotation: task?.rotation ?? [],
    habit: task?.habit ?? false,
    reminder: task ? (task.reminderMinutes === null ? '' : String(task.reminderMinutes)) : '0',
    recurringEntryId: task?.recurringEntryId ?? '',
  }
}

export function toRule(v: TaskFormValues): RepeatRule | null {
  if (!v.repeatOn) return null
  return {
    unit: v.unit,
    interval: Number(v.interval),
    weekdays: v.unit === 'WEEK' && !v.fromCompletion ? v.weekdays : [],
    monthMode: v.unit === 'MONTH' && !v.fromCompletion ? v.monthMode : null,
    fromCompletion: v.fromCompletion,
    until: v.end === 'UNTIL' ? v.until : null,
    count: v.end === 'COUNT' ? Number(v.count) : null,
  }
}

export function toRequest(v: TaskFormValues, dueDate = v.dueDate): TaskRequest {
  const repeat = toRule(v)
  return {
    title: v.title,
    notes: v.notes.trim() || null,
    priority: v.priority,
    dueDate: dueDate || null,
    dueTime: v.dueTime || null,
    durationMinutes: v.dueTime && v.duration ? Number(v.duration) : null,
    listId: v.listId || null,
    tagIds: v.tagIds,
    habit: v.habit && !!repeat && !repeat.fromCompletion,
    repeat,
    subtasks: v.subtasks.filter((s) => s.title.trim()).map((s) => ({ id: s.id, title: s.title.trim() })),
    assigneeId: v.assignMode === 'FIXED' ? v.assigneeId || null : null,
    rotation: v.assignMode === 'ROTATION' && repeat ? v.rotation : [],
    reminderMinutes: v.dueTime && v.reminder !== '' ? Number(v.reminder) : null,
    recurringEntryId: v.recurringEntryId || null,
  }
}

/** Mudou algo que não dá para editar "só nesta" (regra, lista, etiquetas, duração, checklist, responsável, extras)? */
export function changesSeries(v: TaskFormValues, task: Task) {
  const request = toRequest(v)
  const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)
  return !same(request.repeat, task.repeat)
    || request.listId !== task.listId
    || !same([...request.tagIds].sort(), [...task.tagIds].sort())
    || request.durationMinutes !== task.durationMinutes
    || !same(request.subtasks, task.subtasks.map((s) => ({ id: s.id, title: s.title })))
    || request.assigneeId !== task.assigneeId
    || !same(request.rotation, task.rotation)
    || request.habit !== task.habit
    || request.reminderMinutes !== task.reminderMinutes
    || request.recurringEntryId !== task.recurringEntryId
}
