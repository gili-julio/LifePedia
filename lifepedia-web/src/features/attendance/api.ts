import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { AttendanceAgendaItem, Commitment, CommitmentDay, CommitmentImport, CommitmentRequest, LinkedTask, Mark } from './types'

/** Chaves sob 'attendance': toda mutação invalida o módulo. */
const keys = {
  all: ['attendance'] as const,
  list: ['attendance', 'list'] as const,
  days: (id: string) => ['attendance', 'days', id] as const,
  agenda: (from: string, to: string) => ['attendance', 'agenda', from, to] as const,
  tasks: ['attendance', 'tasks'] as const,
}

export function useCommitments() {
  return useQuery({ queryKey: keys.list, queryFn: () => api<Commitment[]>('/api/attendance') })
}

/** Dias que já chegaram, do mais novo para o mais antigo. */
export function useCommitmentDays(id: string) {
  return useQuery({ queryKey: keys.days(id), queryFn: () => api<CommitmentDay[]>(`/api/attendance/${id}/days`) })
}

/** Dias dos compromissos no intervalo (até 62 dias), para a agenda pessoal. */
export function useAttendanceAgenda(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: keys.agenda(from, to),
    queryFn: () => api<AttendanceAgendaItem[]>('/api/attendance/agenda', { query: { from, to } }),
    placeholderData: keepPreviousData,
    enabled,
  })
}

/** Tarefas pessoais em aberto que dá para ligar. */
export function useLinkableTasks(enabled: boolean) {
  return useQuery({ queryKey: keys.tasks, queryFn: () => api<LinkedTask[]>('/api/attendance/tasks'), enabled })
}

function useAttendanceMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all }),
  })
}

export function useSaveCommitment() {
  return useAttendanceMutation(({ id, data }: { id?: string; data: CommitmentRequest }) =>
    api<Commitment>(id ? `/api/attendance/${id}` : '/api/attendance', { method: id ? 'PUT' : 'POST', body: data }))
}

export function useDeleteCommitment() {
  return useAttendanceMutation((id: string) => api<void>(`/api/attendance/${id}`, { method: 'DELETE' }))
}

/** `status` nulo tira a marcação do dia. */
export function useMarkDay() {
  return useAttendanceMutation(({ id, date, status }: { id: string; date: string; status: Mark | null }) =>
    api<CommitmentDay>(`/api/attendance/${id}/days/${date}`, { method: 'PUT', body: { status } }))
}

/** Cria (ou usa o `targetId`) e grava as marcações de uma vez (tudo ou nada). */
export function useImportCommitment() {
  return useAttendanceMutation((data: CommitmentImport) =>
    api<Commitment>('/api/attendance/import', { method: 'POST', body: data }))
}
