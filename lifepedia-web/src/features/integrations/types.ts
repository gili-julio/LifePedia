/** Espelha `integration/IntegrationProvider` da API. */
export type IntegrationProvider = 'GOOGLE' | 'GITLAB' | 'GITHUB' | 'DUOLINGO'

/** `ERROR` = o serviço recusou o token (precisa reconectar). */
export type AccountStatus = 'ACTIVE' | 'ERROR'

export type DuolingoStats = {
  streak: number
  totalXp: number
  bestStreak: number
  lastLesson: string | null
  doneToday: boolean
}

export type IntegrationAccount = {
  id: string
  provider: IntegrationProvider
  nickname: string
  color: string
  baseUrl: string | null
  login: string
  status: AccountStatus
  lastSyncAt: string | null
  lastError: string | null
  tokenExpiresAt: string | null
  canWrite: boolean
  duolingo: DuolingoStats | null
  /** Projetos do Dev que usam a conta */
  projects: string[]
  createdAt: string
}

export type ProviderInfo = { provider: IntegrationProvider; available: boolean; unavailable: string | null }

export type IntegrationOverview = { providers: ProviderInfo[]; accounts: IntegrationAccount[] }

export type IntegrationSummary = { connected: number; problems: number; duolingo: DuolingoStats | null }

export type SyncEntry = {
  at: string
  outcome: 'SUCCESS' | 'FAILED'
  manual: boolean
  items: number
  message: string | null
  durationMs: number
}

export type CalendarEvent = {
  id: string
  accountId: string
  accountName: string
  color: string
  title: string
  start: string
  end: string
  allDay: boolean
  location: string | null
  meetUrl: string | null
  htmlUrl: string | null
}

export type TokenInput = { baseUrl?: string; token: string; nickname?: string; color?: string }
