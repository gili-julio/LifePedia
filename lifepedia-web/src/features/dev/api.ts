import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  ActivityResponse,
  Daily,
  DevProject,
  DevReport,
  DevRepo,
  ProjectDetail,
  ProjectInput,
  RepoOption,
  RulesInput,
  Today,
} from './types'

const BASE = '/api/dev'

/** Chaves sob 'dev'; mutação invalida o módulo, as integrações (onde a conta é usada) e as tarefas (issues viram tarefas). */
const keys = {
  all: ['dev'] as const,
  projects: ['dev', 'projects'] as const,
  detail: (id: string) => ['dev', 'project', id] as const,
  today: ['dev', 'today'] as const,
  activity: (weeks: number) => ['dev', 'activity', weeks] as const,
  options: (accountId: string, q: string) => ['dev', 'repo-options', accountId, q] as const,
  labels: (repoId: string) => ['dev', 'labels', repoId] as const,
  reports: (projectId: string, month: string) => ['dev', 'reports', projectId, month] as const,
}

function useDevMutation<TVars, TResult = void>(fn: (vars: TVars) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      client.invalidateQueries({ queryKey: keys.all })
      client.invalidateQueries({ queryKey: ['integrations'] })
      client.invalidateQueries({ queryKey: ['tasks'] })
    },
  })
}

export function useDevProjects() {
  return useQuery({ queryKey: keys.projects, queryFn: () => api<DevProject[]>(`${BASE}/projects`) })
}

export function useDevProject(id: string) {
  return useQuery({ queryKey: keys.detail(id), queryFn: () => api<ProjectDetail>(`${BASE}/projects/${id}`), enabled: !!id })
}

export function useDevToday(enabled = true) {
  return useQuery({ queryKey: keys.today, queryFn: () => api<Today>(`${BASE}/today`), enabled })
}

export function useDevActivity(weeks: number) {
  return useQuery({ queryKey: keys.activity(weeks), queryFn: () => api<ActivityResponse>(`${BASE}/activity?weeks=${weeks}`) })
}

export function useSaveDevProject() {
  return useDevMutation(({ id, ...body }: ProjectInput & { id?: string }) =>
    api<DevProject>(id ? `${BASE}/projects/${id}` : `${BASE}/projects`, { method: id ? 'PUT' : 'POST', body }))
}

export function useDeleteDevProject() {
  return useDevMutation((id: string) => api<void>(`${BASE}/projects/${id}`, { method: 'DELETE' }))
}

export function useReorderDevProjects() {
  return useDevMutation((ids: string[]) => api<void>(`${BASE}/projects/order`, { method: 'PUT', body: { ids } }))
}

/** Repositórios da conta (busca no GitLab/GitHub; espera digitar antes de buscar de novo). */
export function useRepoOptions(accountId: string | null, query: string) {
  return useQuery({
    queryKey: keys.options(accountId ?? '', query),
    queryFn: () => api<RepoOption[]>(`${BASE}/repo-options?accountId=${accountId}${query ? `&q=${encodeURIComponent(query)}` : ''}`),
    enabled: !!accountId,
    staleTime: 60_000,
    retry: false,
  })
}

export function useAddRepo() {
  return useDevMutation(({ projectId, ...body }: { projectId: string; accountId: string; externalId: string }) =>
    api<DevRepo>(`${BASE}/projects/${projectId}/repos`, { method: 'POST', body }))
}

export function useSaveRules() {
  return useDevMutation(({ repoId, ...body }: RulesInput & { repoId: string }) =>
    api<DevRepo>(`${BASE}/repos/${repoId}`, { method: 'PUT', body }))
}

export function useDeleteRepo() {
  return useDevMutation((repoId: string) => api<void>(`${BASE}/repos/${repoId}`, { method: 'DELETE' }))
}

export function useRepoLabels(repoId: string | null) {
  return useQuery({
    queryKey: keys.labels(repoId ?? ''),
    queryFn: () => api<string[]>(`${BASE}/repos/${repoId}/labels`),
    enabled: !!repoId,
    staleTime: 5 * 60_000,
    retry: false,
  })
}

/** Anotações do projeto no mês (`yyyy-MM`). */
export function useDevReports(projectId: string, month: string) {
  return useQuery({
    queryKey: keys.reports(projectId, month),
    queryFn: () => api<DevReport[]>(`${BASE}/projects/${projectId}/reports?month=${month}`),
  })
}

export function useSaveReport() {
  return useDevMutation(({ projectId, day, text }: { projectId: string; day: string; text: string }) =>
    api<DevReport>(`${BASE}/projects/${projectId}/reports/${day}`, { method: 'PUT', body: { text } }))
}

export function useDeleteReport() {
  return useDevMutation(({ projectId, day }: { projectId: string; day: string }) =>
    api<void>(`${BASE}/projects/${projectId}/reports/${day}`, { method: 'DELETE' }))
}

/** Rascunho com o que o GitLab/GitHub registrou no dia. */
export function useReportSuggestion() {
  return useMutation({
    mutationFn: ({ projectId, day }: { projectId: string; day: string }) =>
      api<{ text: string }>(`${BASE}/projects/${projectId}/reports/${day}/suggestion`),
  })
}

export function useDaily() {
  return useMutation({ mutationFn: () => api<Daily>(`${BASE}/daily`, { method: 'POST' }) })
}
