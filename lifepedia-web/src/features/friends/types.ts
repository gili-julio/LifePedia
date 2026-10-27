import type { UserSummary } from '@/features/groups/types'

export type Friend = { user: UserSummary; since: string }

export type FriendRequestItem = { id: string; user: UserSummary; createdAt: string }

export type FriendRequests = { received: FriendRequestItem[]; sent: FriendRequestItem[] }

/** `status`: já é amigo, pedido meu, pedido dela ou nulo (pode adicionar). */
export type FriendCandidate = UserSummary & { status: 'FRIEND' | 'SENT' | 'RECEIVED' | null }

/** Espelha `achievement.Achievement` da API; código novo = texto novo em `achievements.ts`. */
export type AchievementCode = 'TASKS_DONE' | 'GROUP_TASKS_DONE' | 'HABIT_STREAK' | 'FRIENDS' | 'GROUPS' | 'TRANSACTIONS'

export type Achievement = {
  code: AchievementCode
  value: number
  /** Níveis conquistados (0 = bloqueada); nunca diminui */
  level: number
  maxLevel: number
  /** Próxima meta; nulo no nível máximo */
  target: number | null
  /** Quando o nível atual foi conquistado (nulo = bloqueada) */
  unlockedAt: string | null
  /** Amigos não veem */
  ownerOnly: boolean
}

export type FriendProfile = { user: UserSummary; since: string; achievements: Achievement[] }
