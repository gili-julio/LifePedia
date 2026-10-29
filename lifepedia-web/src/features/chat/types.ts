import type { UserSummary } from '@/features/groups/types'

/** Trecho da mensagem respondida; `body` nulo se ela foi apagada. */
export type ReplyPreview = { id: string; author: UserSummary; body: string | null }

export type ChatMessage = {
  id: string
  groupId: string
  author: UserSummary
  /** Nulo se apagada */
  body: string | null
  replyTo: ReplyPreview | null
  createdAt: string
  editedAt: string | null
  deleted: boolean
}

/** Da mais antiga para a mais nova; `hasMore` = há mensagens antes da primeira. */
export type MessagePage = { items: ChatMessage[]; hasMore: boolean }

/** Não lidas por grupo (`{ groupId: total }`; grupo sem não lidas fica de fora). */
export type UnreadMap = Record<string, number>

export const MAX_MESSAGE = 2000
