import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Page } from '@/components/data-table/list-state'
import { api } from '@/lib/api'
import type {
  AdminAnnouncement,
  AdminUser,
  AdminUserDetail,
  AiOverview,
  AnnouncementRequest,
  AuditEntry,
  BroadcastRequest,
  Modules,
  Overview,
  ServiceLimit,
  SystemStatus,
  UserFilter,
} from './types'

type ListParams = { page: number; size: number; sort?: string }

/** Chaves sob 'admin'; toda mutação invalida o módulo. Sem retry: 403 = acesso tirado, não falha de rede. */
const keys = {
  all: ['admin'] as const,
  overview: ['admin', 'overview'] as const,
  modules: ['admin', 'modules'] as const,
  ai: ['admin', 'ai'] as const,
  status: ['admin', 'status'] as const,
  limits: ['admin', 'limits'] as const,
  users: (params: object) => ['admin', 'users', params] as const,
  user: (id: string) => ['admin', 'user', id] as const,
  audit: (params: ListParams) => ['admin', 'audit', params] as const,
  announcements: (params: ListParams) => ['admin', 'announcements', params] as const,
}

export function useAdminOverview() {
  return useQuery({ queryKey: keys.overview, queryFn: () => api<Overview>('/api/admin/overview'), retry: false })
}

export function useAdminModules() {
  return useQuery({ queryKey: keys.modules, queryFn: () => api<Modules>('/api/admin/modules'), retry: false })
}

export function useAdminAi() {
  return useQuery({ queryKey: keys.ai, queryFn: () => api<AiOverview>('/api/admin/ai'), retry: false })
}

export function useSystemStatus() {
  return useQuery({ queryKey: keys.status, queryFn: () => api<SystemStatus>('/api/admin/status'), retry: false, staleTime: 0 })
}

export function useAdminLimits() {
  return useQuery({ queryKey: keys.limits, queryFn: () => api<ServiceLimit[]>('/api/admin/limits'), retry: false, staleTime: 0 })
}

export function useAdminUsers(params: ListParams & { q: string; filter: UserFilter }) {
  return useQuery({
    queryKey: keys.users(params),
    queryFn: () => api<Page<AdminUser>>('/api/admin/users', { query: params }),
    placeholderData: keepPreviousData,
    retry: false,
  })
}

export function useAdminUser(id: string | null) {
  return useQuery({
    queryKey: keys.user(id ?? ''),
    queryFn: () => api<AdminUserDetail>(`/api/admin/users/${id}`),
    enabled: id !== null,
    retry: false,
  })
}

export function useAuditLog(params: ListParams) {
  return useQuery({
    queryKey: keys.audit(params),
    queryFn: () => api<Page<AuditEntry>>('/api/admin/audit', { query: params }),
    placeholderData: keepPreviousData,
    retry: false,
  })
}

export function useAdminAnnouncements(params: ListParams) {
  return useQuery({
    queryKey: keys.announcements(params),
    queryFn: () => api<Page<AdminAnnouncement>>('/api/admin/announcements', { query: params }),
    placeholderData: keepPreviousData,
    retry: false,
  })
}

function useAdminMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>, extraKeys: string[][] = []) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.all })
      for (const key of extraKeys) queryClient.invalidateQueries({ queryKey: key })
    },
  })
}

export function useSetAdmin() {
  return useAdminMutation(({ id, admin }: { id: string; admin: boolean }) =>
    api<AdminUser>(`/api/admin/users/${id}/admin`, { method: 'PUT', body: { admin } }),
  )
}

export function useSetBlocked() {
  return useAdminMutation(({ id, blocked }: { id: string; blocked: boolean }) =>
    api<AdminUser>(`/api/admin/users/${id}/blocked`, { method: 'PUT', body: { blocked } }),
  )
}

export function useRevokeSessions() {
  return useAdminMutation((id: string) => api<void>(`/api/admin/users/${id}/sessions`, { method: 'DELETE' }))
}

export function useCreateAnnouncement() {
  return useAdminMutation(
    (body: AnnouncementRequest) => api<unknown>('/api/admin/announcements', { method: 'POST', body }),
    [['announcements']],
  )
}

export function useEndAnnouncement() {
  return useAdminMutation((id: string) => api<void>(`/api/admin/announcements/${id}`, { method: 'DELETE' }), [['announcements']])
}

export function useBroadcast() {
  return useAdminMutation(
    (body: BroadcastRequest) => api<{ recipients: number }>('/api/admin/broadcast', { method: 'POST', body }),
    [['notifications']],
  )
}
