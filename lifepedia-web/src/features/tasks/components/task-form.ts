import { z } from 'zod'
import { isoWeekday } from '../dates'
import type { RepeatRule, Task, TaskItem, TaskRequest } from '../types'

const whole = (min: number, max: number) => (v: string) => /^\d+$/.test(v) && Number(v) >= min && Number(v) <= max

export const taskSchema = z.object({
  title: z.string().trim().min(1, 'Informe a tarefa.').max(200, 'Máximo de 200 caracteres.'),
  notes: z.string().max(10000, 'Máximo de 10000 caracteres.'),
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
  /** Só em grupo: ninguém, pessoas escolhidas, todos do grupo ou rodízio (precisa de repetição). */
  assignMode: z.enum(['NONE', 'PEOPLE', 'ALL', 'ROTATION']),
  assignees: z.array(z.string()),
  rotation: z.array(z.string()),
  /** Com mais de um responsável: basta um concluir ou cada um conclui a sua. */
  completion: z.enum(['ANY', 'EACH']),
  /** Só com repetição em dias fixos. */
  habit: z.boolean(),
  /** Minutos antes do horário ('' = sem lembrete; '0' = na hora). */
  reminder: z.string(),
  /** Fixo do financeiro ('' = nenhum; só pessoal). */
  recurringEntryId: z.string(),
}).superRefine((v, ctx) => {
  const issue = (path: string, message: string) => ctx.addIssue({ code: 'custom', path: [path], message })
  if (v.dueTime && !v.dueDate) issue('dueTime', 'Escolha o dia para definir o horário.')
  if (v.assignMode === 'PEOPLE' && !v.assignees.length) issue('assignees', 'Escolha pelo menos uma pessoa.')
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

/** Sugestões para tarefa nova (dia/horário de onde clicou, lista do filtro, responsável = quem cria). */
export type TaskDefaults = { dueDate?: string | null; dueTime?: string | null; listId?: string | null; assignees?: string[] }

/** Valores iniciais: tarefa nova, tarefa inteira ou a ocorrência clicada (título/horário/dia dela). */
export function toForm(task?: Task, occurrence?: TaskItem | null, defaults?: TaskDefaults): TaskFormInput {
  const rule = task?.repeat
  const dueDate = occurrence?.scheduledDate ?? task?.dueDate ?? defaults?.dueDate ?? ''
  return {
    title: occurrence?.title ?? task?.title ?? '',
    notes: occurrence?.notes ?? task?.notes ?? '',
    dueDate,
    dueTime: occurrence?.dueTime ?? task?.dueTime ?? defaults?.dueTime ?? '',
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
    assignMode: assignMode(task, defaults),
    assignees: task ? task.assignees : defaults?.assignees ?? [],
    rotation: task?.rotation ?? [],
    completion: task?.completion ?? 'ANY',
    habit: task?.habit ?? false,
    reminder: task ? (task.reminderMinutes === null ? '' : String(task.reminderMinutes)) : '0',
    recurringEntryId: task?.recurringEntryId ?? '',
  }
}

function assignMode(task?: Task, defaults?: TaskDefaults): TaskFormValues['assignMode'] {
  if (!task) return defaults?.assignees?.length ? 'PEOPLE' : 'NONE'
  if (task.rotation.length) return 'ROTATION'
  if (task.allMembers) return 'ALL'
  return task.assignees.length ? 'PEOPLE' : 'NONE'
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
    assignees: v.assignMode === 'PEOPLE' ? v.assignees : [],
    allMembers: v.assignMode === 'ALL',
    rotation: v.assignMode === 'ROTATION' && repeat ? v.rotation : [],
    completion: v.assignMode === 'ALL' || (v.assignMode === 'PEOPLE' && v.assignees.length > 1) ? v.completion : 'ANY',
    reminderMinutes: v.dueTime && v.reminder !== '' ? Number(v.reminder) : null,
    recurringEntryId: v.recurringEntryId || null,
  }
}

/** Requisição com os dados atuais da tarefa e o que mudar (ex.: horário da série, ao arrastar na agenda). */
export function fromTask(task: Task, changes: Partial<TaskRequest>): TaskRequest {
  return {
    title: task.title, notes: task.notes, priority: task.priority, dueDate: task.dueDate, dueTime: task.dueTime,
    durationMinutes: task.durationMinutes, listId: task.listId, tagIds: task.tagIds, habit: task.habit, repeat: task.repeat,
    subtasks: task.subtasks, assignees: task.assignees, allMembers: task.allMembers, rotation: task.rotation,
    completion: task.completion, reminderMinutes: task.reminderMinutes, recurringEntryId: task.recurringEntryId,
    ...changes,
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
    || !same([...request.assignees].sort(), [...task.assignees].sort())
    || request.allMembers !== task.allMembers
    || request.completion !== task.completion
    || !same(request.rotation, task.rotation)
    || request.habit !== task.habit
    || request.reminderMinutes !== task.reminderMinutes
    || request.recurringEntryId !== task.recurringEntryId
}
