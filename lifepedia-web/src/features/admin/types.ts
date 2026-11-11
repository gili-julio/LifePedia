/** Espelha `admin/AdminDtos` da API. */

export type DayCount = { date: string; count: number }

export type Overview = {
  totalUsers: number
  newUsers7: number
  newUsers30: number
  /** Último acesso nos últimos 7/30 dias */
  activeUsers7: number
  activeUsers30: number
  admins: number
  blocked: number
  /** 30 dias, dias sem cadastro com 0 */
  signupsDaily: DayCount[]
  /** 26 semanas (data = segunda-feira) */
  signupsWeekly: DayCount[]
}

/** Espelha as chaves de `AdminStats.MODULES`. */
export type ModuleKey =
  | 'FINANCE' | 'TASKS' | 'NOTES' | 'ATTENDANCE' | 'FINANCING' | 'GROUPS' | 'CHAT' | 'FRIENDS' | 'AI_IMPORT' | 'PUSH'
  | 'ARCHITECTURE'

export type Modules = { totalUsers: number; modules: { key: ModuleKey; users: number; items: number }[] }

export type AiOverview = {
  enabled: boolean
  model: string
  timeoutSeconds: number
  dailyLimit: number
  /** Leituras terminadas nas últimas 24 h */
  last24h: number
  running: number
  users30: number
  avgDurationMs: number
  /** Resultado (`SUCCESS` ou motivo da falha) → quantidade, 30 dias */
  outcomes: Record<string, number>
  daily: { date: string; success: number; failed: number }[]
}

export type JobStatus = 'NONE' | 'STARTED' | 'SUCCESS' | 'ERROR'

export type SystemStatus = {
  version: string | null
  buildTime: string | null
  startedAt: string
  uptimeSeconds: number
  javaVersion: string
  timeZone: string
  memoryUsedMb: number
  memoryMaxMb: number
  database: { up: boolean; latencyMs: number; version: string | null; sizeBytes: number; schemaVersion: string | null }
  features: { ai: boolean; aiModel: string | null; push: boolean; googleLogin: boolean; fixedAdmin: boolean }
  jobs: { name: string; lastRun: string | null; status: JobStatus; nextRun: string | null }[]
}

export type UserFilter = 'ALL' | 'ADMIN' | 'BLOCKED'

export type AdminUser = {
  id: string
  name: string
  username: string | null
  email: string
  imageUrl: string | null
  createdAt: string
  /** Nulo = sem acesso desde que o registro começou */
  lastSeenAt: string | null
  admin: boolean
  blocked: boolean
  /** Conta do ADMIN_EMAIL: sempre admin, não pode ser bloqueada */
  fixedAdmin: boolean
  /** Quem está vendo */
  self: boolean
}

/** Espelha as colunas de `AdminStats.USER_COUNTS`. */
export type UserCountKey =
  | 'transactions' | 'recurring' | 'tasks' | 'notes' | 'commitments' | 'financings' | 'groups' | 'friends' | 'messages'
  | 'ai_imports' | 'architecture'

export type AdminUserDetail = {
  user: AdminUser
  googleLinked: boolean
  hasPassword: boolean
  termsVersion: string | null
  termsAcceptedAt: string | null
  termsCurrent: boolean
  blockedAt: string | null
  activeSessions: number
  pushDevices: number
  counts: Record<UserCountKey, number>
}

/** Espelha `AuditEntry.Action`. */
export type AuditAction =
  | 'ADMIN_GRANTED' | 'ADMIN_REVOKED' | 'ADMIN_FIXED' | 'USER_BLOCKED' | 'USER_UNBLOCKED' | 'SESSIONS_REVOKED'
  | 'ANNOUNCEMENT_CREATED' | 'ANNOUNCEMENT_ENDED' | 'BROADCAST_SENT'

export type AuditEntry = {
  id: string
  action: AuditAction
  actorId: string | null
  actorName: string
  targetId: string | null
  targetName: string | null
  detail: string | null
  createdAt: string
}

/** Espelha `Announcement.Level`. */
export type AnnouncementLevel = 'INFO' | 'WARNING'

export type AdminAnnouncement = {
  id: string
  message: string
  level: AnnouncementLevel
  endsAt: string | null
  endedAt: string | null
  active: boolean
  createdByName: string | null
  createdAt: string
}

export type AnnouncementRequest = { message: string; level: AnnouncementLevel; endsAt: string | null }

/** Espelha `AdminDtos.Audience`. */
export type Audience = 'ALL' | 'ADMINS'

export type BroadcastRequest = { title: string; body: string; audience: Audience }
