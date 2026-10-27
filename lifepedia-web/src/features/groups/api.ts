import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api'
import type {
  GroupDetail,
  GroupRole,
  GroupSummary,
  InviteCandidate,
  InviteLink,
  LinkPreview,
  ReceivedInvite,
  SentInvite,
} from './types'

/** Chaves sob 'groups': toda mutação invalida o módulo (e o financeiro dos grupos). */
const keys = {
  all: ['groups'] as const,
  list: ['groups', 'list'] as const,
  shortcuts: ['groups', 'shortcuts'] as const,
  detail: (id: string) => ['groups', 'detail', id] as const,
  received: ['groups', 'invites', 'received'] as const,
  receivedCount: ['groups', 'invites', 'count'] as const,
  sent: (id: string) => ['groups', 'invites', 'sent', id] as const,
  candidates: (id: string, q: string) => ['groups', 'candidates', id, q] as const,
  link: (id: string) => ['groups', 'link', id] as const,
  preview: (token: string) => ['groups', 'join', token] as const,
}

export function useGroups() {
  return useQuery({ queryKey: keys.list, queryFn: () => api<GroupSummary[]>('/api/groups') })
}

/** Atalhos da sidebar (até 3, ordem da API). A atividade muda por mutações de outros módulos: ver `main.tsx`. */
export function useGroupShortcuts() {
  return useQuery({ queryKey: keys.shortcuts, queryFn: () => api<GroupSummary[]>('/api/groups/shortcuts') })
}

export function useGroup(id: string) {
  return useQuery({ queryKey: keys.detail(id), queryFn: () => api<GroupDetail>(`/api/groups/${id}`) })
}

export function useReceivedInvites() {
  return useQuery({ queryKey: keys.received, queryFn: () => api<ReceivedInvite[]>('/api/groups/invites') })
}

/** Convites pendentes recebidos; atualiza junto com as notificações. */
export function useReceivedInviteCount() {
  return useQuery({
    queryKey: keys.receivedCount,
    queryFn: () => api<{ count: number }>('/api/groups/invites/count'),
    select: (d) => d.count,
    refetchInterval: 60_000,
  })
}

export function useSentInvites(groupId: string, enabled: boolean) {
  return useQuery({ queryKey: keys.sent(groupId), queryFn: () => api<SentInvite[]>(`/api/groups/${groupId}/invites`), enabled })
}

/** Busca para convite (mín. 2 caracteres). */
export function useInviteCandidates(groupId: string, query: string) {
  return useQuery({
    queryKey: keys.candidates(groupId, query),
    queryFn: () => api<InviteCandidate[]>(`/api/groups/${groupId}/invite-candidates`, { query: { q: query } }),
    enabled: query.replace(/^@/, '').trim().length >= 2,
    staleTime: 10_000,
  })
}

/** Link ativo; sem link, `null`. */
export function useInviteLink(groupId: string, enabled: boolean) {
  return useQuery({
    queryKey: keys.link(groupId),
    queryFn: () =>
      api<InviteLink>(`/api/groups/${groupId}/invite-link`).catch((error) => {
        if (error instanceof ApiError && error.status === 404) return null
        throw error
      }),
    enabled,
  })
}

export function useLinkPreview(token: string) {
  return useQuery({ queryKey: keys.preview(token), queryFn: () => api<LinkPreview>(`/api/groups/join/${token}`) })
}

function useGroupMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.all })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['finance', 'groups'] })
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
    },
  })
}

export function useSaveGroup() {
  return useGroupMutation(({ id, name }: { id?: string; name: string }) =>
    id
      ? api<GroupSummary>(`/api/groups/${id}`, { method: 'PUT', body: { name } })
      : api<GroupSummary>('/api/groups', { method: 'POST', body: { name } }),
  )
}

/** Envia a imagem já cortada (JPEG). */
export function useUploadGroupImage() {
  return useGroupMutation(({ id, image }: { id: string; image: Blob }) => {
    const form = new FormData()
    form.append('file', image, 'grupo.jpg')
    return api<GroupSummary>(`/api/groups/${id}/image`, { method: 'PUT', body: form })
  })
}

export function useRemoveGroupImage() {
  return useGroupMutation((id: string) => api<GroupSummary>(`/api/groups/${id}/image`, { method: 'DELETE' }))
}

export function usePinGroup() {
  return useGroupMutation(({ id, pinned }: { id: string; pinned: boolean }) =>
    api<GroupSummary>(`/api/groups/${id}/pin`, { method: 'PUT', body: { pinned } }),
  )
}

export function useDeleteGroup() {
  return useGroupMutation((id: string) => api<void>(`/api/groups/${id}`, { method: 'DELETE' }))
}

export function useLeaveGroup() {
  return useGroupMutation((id: string) => api<void>(`/api/groups/${id}/leave`, { method: 'POST' }))
}

export function useRemoveMember() {
  return useGroupMutation(({ groupId, userId }: { groupId: string; userId: string }) =>
    api<void>(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' }),
  )
}

export function useSetMemberRole() {
  return useGroupMutation(({ groupId, userId, role }: { groupId: string; userId: string; role: GroupRole }) =>
    api<void>(`/api/groups/${groupId}/members/${userId}/role`, { method: 'PUT', body: { role } }),
  )
}

export function useInviteUsers() {
  return useGroupMutation(({ groupId, userIds }: { groupId: string; userIds: string[] }) =>
    api<{ invited: number; skipped: number }>(`/api/groups/${groupId}/invites`, { method: 'POST', body: { userIds } }),
  )
}

export function useCancelInvite() {
  return useGroupMutation(({ groupId, inviteId }: { groupId: string; inviteId: string }) =>
    api<void>(`/api/groups/${groupId}/invites/${inviteId}`, { method: 'DELETE' }),
  )
}

export function useRespondInvite() {
  return useGroupMutation(({ inviteId, accept }: { inviteId: string; accept: boolean }) =>
    api<GroupSummary | undefined>(`/api/groups/invites/${inviteId}/${accept ? 'accept' : 'decline'}`, { method: 'POST' }),
  )
}

export function useRenewInviteLink() {
  return useGroupMutation((groupId: string) => api<InviteLink>(`/api/groups/${groupId}/invite-link`, { method: 'POST' }))
}

export function useRevokeInviteLink() {
  return useGroupMutation((groupId: string) => api<void>(`/api/groups/${groupId}/invite-link`, { method: 'DELETE' }))
}

export function useJoinByLink() {
  return useGroupMutation((token: string) => api<GroupSummary>(`/api/groups/join/${token}`, { method: 'POST' }))
}

/** Link completo para compartilhar. */
export function inviteUrl(token: string) {
  return `${window.location.origin}/grupos/convite/${token}`
}
