import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Achievement, Friend, FriendCandidate, FriendProfile, FriendRequests } from './types'

/** Chaves sob 'friends': toda mutação invalida o módulo (e as notificações). */
const keys = {
  all: ['friends'] as const,
  list: ['friends', 'list'] as const,
  requests: ['friends', 'requests'] as const,
  candidates: (q: string) => ['friends', 'candidates', q] as const,
  profile: (id: string) => ['friends', 'profile', id] as const,
  achievements: ['friends', 'achievements'] as const,
}

export function useFriends() {
  return useQuery({ queryKey: keys.list, queryFn: () => api<Friend[]>('/api/friends') })
}

export function useFriendRequests() {
  return useQuery({ queryKey: keys.requests, queryFn: () => api<FriendRequests>('/api/friends/requests') })
}

/** Busca para adicionar (mín. 2 caracteres). */
export function useFriendCandidates(query: string) {
  return useQuery({
    queryKey: keys.candidates(query),
    queryFn: () => api<FriendCandidate[]>('/api/friends/candidates', { query: { q: query } }),
    enabled: query.replace(/^@/, '').trim().length >= 2,
    staleTime: 10_000,
  })
}

export function useFriendProfile(id: string) {
  return useQuery({ queryKey: keys.profile(id), queryFn: () => api<FriendProfile>(`/api/friends/${id}`) })
}

/** Conquistas de quem está logado (calculadas pela API a cada leitura). */
export function useMyAchievements() {
  return useQuery({ queryKey: keys.achievements, queryFn: () => api<Achievement[]>('/api/achievements'), staleTime: 0 })
}

function useFriendMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.all })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

/** Envia o pedido; se a pessoa já tinha pedido, viram amigos (`ACCEPTED`). */
export function useSendFriendRequest() {
  return useFriendMutation((userId: string) =>
    api<{ status: 'PENDING' | 'ACCEPTED' }>('/api/friends/requests', { method: 'POST', body: { userId } }),
  )
}

export function useAcceptFriendRequest() {
  return useFriendMutation((id: string) => api<void>(`/api/friends/requests/${id}/accept`, { method: 'POST' }))
}

/** Recusa (recebido) ou cancela (enviado). */
export function useDismissFriendRequest() {
  return useFriendMutation((id: string) => api<void>(`/api/friends/requests/${id}`, { method: 'DELETE' }))
}

export function useRemoveFriend() {
  return useFriendMutation((id: string) => api<void>(`/api/friends/${id}`, { method: 'DELETE' }))
}
