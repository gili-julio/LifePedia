import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Page } from '@/components/data-table/list-state'
import { api } from '@/lib/api'
import type { AppNotification } from './types'

const PAGE_SIZE = 20

const keys = {
  all: ['notifications'] as const,
  unread: ['notifications', 'unread'] as const,
  list: ['notifications', 'list'] as const,
}

/** Não lidas; atualiza a cada minuto e ao voltar para a aba. */
export function useUnreadNotifications() {
  return useQuery({
    queryKey: keys.unread,
    queryFn: () => api<{ count: number }>('/api/notifications/unread-count'),
    select: (data) => data.count,
    refetchInterval: 60_000,
  })
}

export function useNotifications(enabled: boolean) {
  return useInfiniteQuery({
    queryKey: keys.list,
    queryFn: ({ pageParam }) =>
      api<Page<AppNotification>>('/api/notifications', { query: { page: pageParam, size: PAGE_SIZE } }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    enabled,
  })
}

function useNotificationMutation<T>(fn: (variables: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all }),
  })
}

export function useMarkNotificationRead() {
  return useNotificationMutation((id: string) => api<void>(`/api/notifications/${id}/read`, { method: 'POST' }))
}

export function useMarkAllNotificationsRead() {
  return useNotificationMutation(() => api<void>('/api/notifications/read-all', { method: 'POST' }))
}
