import { type InfiniteData, type QueryClient, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { ChatMessage, MessagePage, UnreadMap } from './types'

const PAGE = 30

export const chatKeys = {
  all: ['chat'] as const,
  unread: ['chat', 'unread'] as const,
  messages: (groupId: string) => ['chat', groupId, 'messages'] as const,
}

const base = (groupId: string) => `/api/groups/${groupId}/chat`

/** Histórico por cursor: a 1ª página são as mais recentes; as seguintes, cada vez mais antigas. O stream mantém em dia. */
export function useChatMessages(groupId: string) {
  return useInfiniteQuery({
    queryKey: chatKeys.messages(groupId),
    queryFn: ({ pageParam }) => api<MessagePage>(`${base(groupId)}/messages`, { query: { before: pageParam, size: PAGE } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore ? last.items[0]?.id : undefined),
    staleTime: Infinity,
  })
}

export function useChatUnread() {
  return useQuery({ queryKey: chatKeys.unread, queryFn: () => api<UnreadMap>('/api/groups/chat/unread') })
}

/** Mensagem nova entra no fim da página mais recente; editada/apagada é trocada no lugar. */
export function upsertMessage(queryClient: QueryClient, message: ChatMessage) {
  queryClient.setQueryData<InfiniteData<MessagePage, string | undefined>>(chatKeys.messages(message.groupId), (data) => {
    if (!data) return data
    const exists = data.pages.some((p) => p.items.some((m) => m.id === message.id))
    const pages = exists
      ? data.pages.map((p) => ({ ...p, items: p.items.map((m) => (m.id === message.id ? message : m)) }))
      : data.pages.map((p, i) => (i === 0 ? { ...p, items: [...p.items, message] } : p))
    return { ...data, pages }
  })
}

export function useSendMessage(groupId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { body: string; replyToId?: string }) =>
      api<ChatMessage>(`${base(groupId)}/messages`, { method: 'POST', body: data }),
    onSuccess: (message) => upsertMessage(queryClient, message),
  })
}

export function useEditMessage(groupId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: string }) =>
      api<ChatMessage>(`${base(groupId)}/messages/${id}`, { method: 'PUT', body: { body } }),
    onSuccess: (message) => upsertMessage(queryClient, message),
  })
}

export function useDeleteMessage(groupId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (message: ChatMessage) => api<void>(`${base(groupId)}/messages/${message.id}`, { method: 'DELETE' }),
    onSuccess: (_, message) => upsertMessage(queryClient, { ...message, body: null, deleted: true }),
  })
}

/** Marca o chat como lido e zera o contador do grupo. */
export function useMarkChatRead(groupId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>(`${base(groupId)}/read`, { method: 'POST' }),
    onSuccess: () => queryClient.setQueryData<UnreadMap>(chatKeys.unread, (data) => {
      if (!data?.[groupId]) return data
      const next = { ...data }
      delete next[groupId]
      return next
    }),
  })
}
