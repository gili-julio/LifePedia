import { keepPreviousData, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Page } from '@/components/data-table/list-state'
import { api } from '@/lib/api'
import type {
  Agenda,
  AgendaSettings,
  HabitSummary,
  HistoryItem,
  Label,
  LabelKind,
  Labels,
  OccurrenceRequest,
  OccurrenceStatus,
  Scope,
  Task,
  TaskComment,
  TaskConflict,
  TaskFilters,
  TaskItem,
  TaskRequest,
} from './types'

/** Rotas das tarefas pessoais (`groupId` nulo) ou de um grupo. */
export const tasksBase = (groupId: string | null) => (groupId ? `/api/groups/${groupId}/tasks` : '/api/tasks')
/** Chaves: `['tasks', 'me' | groupId, ...]`. */
const scopeKey = (groupId: string | null) => groupId ?? 'me'

const filterQuery = (f: TaskFilters) => ({ list: f.list, tag: f.tag, assignee: f.assignee, sort: f.sort })

export function useAgenda(groupId: string | null, from: string, to: string, overdue: boolean, filters: TaskFilters) {
  return useQuery({
    queryKey: ['tasks', scopeKey(groupId), 'agenda', from, to, overdue, filters],
    queryFn: () => api<Agenda>(`${tasksBase(groupId)}/agenda`, { query: { from, to, overdue: String(overdue), ...filterQuery(filters) } }),
    placeholderData: keepPreviousData,
  })
}

export function useUndated(groupId: string | null, filters: TaskFilters) {
  return useQuery({
    queryKey: ['tasks', scopeKey(groupId), 'undated', filters],
    queryFn: () => api<TaskItem[]>(`${tasksBase(groupId)}/undated`, { query: filterQuery(filters) }),
    placeholderData: keepPreviousData,
  })
}

export function useHistory(groupId: string | null, params: { page: number; size: number; sort: string }) {
  return useQuery({
    queryKey: ['tasks', scopeKey(groupId), 'history', params],
    queryFn: () => api<Page<HistoryItem>>(`${tasksBase(groupId)}/history`, { query: params }),
    placeholderData: keepPreviousData,
  })
}

export function useHabits(groupId: string | null) {
  return useQuery({
    queryKey: ['tasks', scopeKey(groupId), 'habits'],
    queryFn: () => api<HabitSummary[]>(`${tasksBase(groupId)}/habits`),
  })
}

export function useTask(groupId: string | null, id: string | null) {
  return useQuery({
    queryKey: ['tasks', scopeKey(groupId), 'task', id],
    queryFn: () => api<Task>(`${tasksBase(groupId)}/${id}`),
    enabled: !!id,
    staleTime: 0,
  })
}

export function useLabels(groupId: string | null) {
  return useQuery({ queryKey: ['tasks', scopeKey(groupId), 'labels'], queryFn: () => api<Labels>(`${tasksBase(groupId)}/labels`) })
}

/** Choque de horário (`people`: responsáveis pensados, só grupo; `task`: a própria tarefa sai da conta). */
export function useConflicts(groupId: string | null, params: {
  date: string
  time: string
  duration: number | null
  people: string[]
  task: string | null
} | null) {
  return useQuery({
    queryKey: ['tasks', scopeKey(groupId), 'conflicts', params],
    queryFn: () => api<TaskConflict[]>(`${tasksBase(groupId)}/conflicts`, {
      query: { date: params!.date, time: params!.time, duration: params!.duration, people: params!.people.join(',') || null, task: params!.task },
    }),
    enabled: !!params,
    placeholderData: keepPreviousData,
  })
}

export function useAgendaSettings() {
  return useQuery({ queryKey: ['tasks', 'me', 'agenda-settings'], queryFn: () => api<AgendaSettings>('/api/tasks/agenda-settings') })
}

export function useSaveAgendaSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: AgendaSettings) => api<AgendaSettings>('/api/tasks/agenda-settings', { method: 'PUT', body: data }),
    onSuccess: (data) => queryClient.setQueryData(['tasks', 'me', 'agenda-settings'], data),
  })
}

const COMMENTS_PAGE = 20

/** Comentários (grupo), dos mais recentes para os mais antigos, em páginas. */
export function useComments(groupId: string, taskId: string) {
  return useInfiniteQuery({
    queryKey: ['tasks', groupId, 'comments', taskId],
    queryFn: ({ pageParam }) =>
      api<Page<TaskComment>>(`${tasksBase(groupId)}/${taskId}/comments`, { query: { page: pageParam, size: COMMENTS_PAGE } }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

/** Toda mutação recarrega o módulo inteiro (agenda, contagens das listas...). */
function useTasksMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      // Tarefa ligada a um compromisso: concluir conta presença na frequência.
      queryClient.invalidateQueries({ queryKey: ['attendance'] })
    },
  })
}

type Scoped = { groupId: string | null }

/** `from` = esta e as próximas (a API divide a série e devolve a nova). */
export function useSaveTask() {
  return useTasksMutation(({ groupId, id, from, data }: Scoped & { id?: string; from?: string; data: TaskRequest }) =>
    id
      ? api<Task>(`${tasksBase(groupId)}/${id}`, { method: 'PUT', body: data, query: { from } })
      : api<Task>(tasksBase(groupId), { method: 'POST', body: data }),
  )
}

export function useUpdateOccurrence() {
  return useTasksMutation(({ groupId, id, data }: Scoped & { id: string; data: OccurrenceRequest }) =>
    api<void>(`${tasksBase(groupId)}/${id}/occurrence`, { method: 'PUT', body: data }),
  )
}

export function useMoveTask() {
  return useTasksMutation(({ groupId, id, date, to }: Scoped & { id: string; date: string | null; to: string | null }) =>
    api<void>(`${tasksBase(groupId)}/${id}/move`, { method: 'PUT', body: { date, to } }),
  )
}

/** Responsável só desta ocorrência (`assigneeId` nulo = volta ao da tarefa). */
export function useAssignOccurrence() {
  return useTasksMutation(({ groupId, id, date, assigneeId }: Scoped & { id: string; date: string | null; assigneeId: string | null }) =>
    api<void>(`${tasksBase(groupId)}/${id}/assignee`, { method: 'PUT', body: { date, assigneeId } }),
  )
}

/** Leva a tarefa pessoal para um grupo (quem levou vira a responsável). */
export function useMoveToGroup() {
  return useTasksMutation(({ id, groupId }: { id: string; groupId: string }) =>
    api<Task>(`/api/tasks/${id}/group`, { method: 'PUT', body: { groupId } }),
  )
}

export function useDeleteTask() {
  return useTasksMutation(({ groupId, id, scope, date }: Scoped & { id: string; scope: Scope; date?: string | null }) =>
    api<void>(`${tasksBase(groupId)}/${id}`, { method: 'DELETE', query: { scope, date } }),
  )
}

export function useSetStatus() {
  return useTasksMutation(({ groupId, id, date, status }: Scoped & { id: string; date: string | null; status: OccurrenceStatus }) =>
    api<void>(`${tasksBase(groupId)}/${id}/status`, { method: 'PUT', body: { date, status } }),
  )
}

export function useResolveOverdue() {
  return useTasksMutation(({ groupId, id, status }: Scoped & { id: string; status: OccurrenceStatus }) =>
    api<{ updated: number }>(`${tasksBase(groupId)}/${id}/overdue`, { method: 'PUT', body: { status } }),
  )
}

export function useCheckSubtask() {
  return useTasksMutation(({ groupId, id, subtaskId, date, done }: Scoped & { id: string; subtaskId: string; date: string | null; done: boolean }) =>
    api<void>(`${tasksBase(groupId)}/${id}/subtasks/${subtaskId}/check`, { method: 'PUT', body: { date, done } }),
  )
}

export function useReorderTasks() {
  return useTasksMutation(({ groupId, taskIds }: Scoped & { taskIds: string[] }) =>
    api<void>(`${tasksBase(groupId)}/order`, { method: 'PUT', body: { taskIds } }),
  )
}

export function useAddComment() {
  return useTasksMutation(({ groupId, taskId, body }: { groupId: string; taskId: string; body: string }) =>
    api<TaskComment>(`${tasksBase(groupId)}/${taskId}/comments`, { method: 'POST', body: { body } }),
  )
}

export function useDeleteComment() {
  return useTasksMutation(({ groupId, taskId, id }: { groupId: string; taskId: string; id: string }) =>
    api<void>(`${tasksBase(groupId)}/${taskId}/comments/${id}`, { method: 'DELETE' }),
  )
}

export function useSaveLabel() {
  return useTasksMutation(({ groupId, kind, id, name, color }: Scoped & { kind: LabelKind; id?: string; name: string; color: string }) =>
    id
      ? api<Label>(`${tasksBase(groupId)}/${kind}/${id}`, { method: 'PUT', body: { name, color } })
      : api<Label>(`${tasksBase(groupId)}/${kind}`, { method: 'POST', body: { name, color } }),
  )
}

export function useDeleteLabel() {
  return useTasksMutation(({ groupId, kind, id }: Scoped & { kind: LabelKind; id: string }) =>
    api<void>(`${tasksBase(groupId)}/${kind}/${id}`, { method: 'DELETE' }),
  )
}
