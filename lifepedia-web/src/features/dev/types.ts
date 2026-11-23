import type { CalendarEvent, IntegrationProvider } from '@/features/integrations/types'

/** Espelha `DevDtos.LinkKind` da API. */
export type LinkKind = 'REPO' | 'BOARD' | 'STAGING' | 'PRODUCTION' | 'DOCS' | 'CHAT' | 'OTHER'

export type DevLink = { label: string; url: string; kind: LinkKind }

/** Espelha `DevItem.Kind`. */
export type ItemKind = 'MR' | 'ISSUE' | 'PIPELINE'

/** Status normalizado das pipelines (`integration/code/Pipelines`). */
export type PipelineStatus = 'SUCCESS' | 'FAILED' | 'RUNNING' | 'PENDING' | 'CANCELED' | 'SKIPPED' | 'MANUAL'

/** Raia acompanhada: etiqueta da issue e o limite de dias nela (nulo = sem aviso). */
export type Lane = { label: string; days: number | null }

export type DevRepo = {
  id: string
  accountId: string
  accountName: string
  accountColor: string
  provider: IntegrationProvider | null
  accountActive: boolean
  canWrite: boolean
  fullName: string
  webUrl: string
  defaultBranch: string | null
  importIssues: boolean
  closeIssues: boolean
  labelMrOpened: string | null
  labelMrMerged: string | null
  labelsClear: string[]
  lanes: Lane[]
  syncedAt: string | null
}

export type Counts = { myMergeRequests: number; reviews: number; issues: number; failing: number }

export type DevProject = {
  id: string
  name: string
  color: string
  description: string | null
  links: DevLink[]
  calendarAccountId: string | null
  repos: DevRepo[]
  counts: Counts
}

export type DevItem = {
  id: string
  projectId: string | null
  projectName: string
  projectColor: string
  repoId: string
  repoName: string
  kind: ItemKind
  number: string | null
  title: string
  /** `opened`, `merged` ou `closed` (MR/issue); status da pipeline no item de pipeline */
  state: string
  webUrl: string
  author: string | null
  /** MR da pessoa; na issue, atribuída a ela (senão ela só participa) */
  mine: boolean
  review: boolean
  draft: boolean
  pipeline: PipelineStatus | null
  branch: string | null
  labels: string[]
  comments: number
  approved: boolean
  linkedIssues: string[]
  dueDate: string | null
  taskId: string | null
  updatedAt: string | null
  mergedAt: string | null
  /** Raia acompanhada em que a issue está */
  lane: string | null
  laneSince: string | null
  /** Dias na raia (calculado pela API) */
  laneDays: number | null
  /** Limite de dias da raia */
  laneLimit: number | null
}

export type DayActivity = { day: string; commits: number; mergeRequests: number; merged: number; reviews: number }

export type ProjectActivity = {
  id: string
  name: string
  color: string
  commits: number
  mergeRequests: number
  merged: number
  reviews: number
}

export type ActivityResponse = { days: DayActivity[]; projects: ProjectActivity[] }

/** `lanes` = colunas do quadro, em ordem. */
export type ProjectDetail = { project: DevProject; items: DevItem[]; events: CalendarEvent[]; activity: DayActivity[]; lanes: Lane[] }

export type ReportStatus = { projectId: string; projectName: string; projectColor: string; written: boolean }

export type DevReport = { day: string; text: string; updatedAt: string }

export type AccountProblem = { id: string; provider: IntegrationProvider; nickname: string }

export type Today = {
  date: string
  events: CalendarEvent[]
  myMergeRequests: DevItem[]
  reviews: DevItem[]
  failing: DevItem[]
  issues: DevItem[]
  problems: AccountProblem[]
  aiEnabled: boolean
  projects: number
  /** Anotação de hoje em cada projeto */
  reports: ReportStatus[]
}

export type RepoOption = { externalId: string; fullName: string; webUrl: string; linked: boolean }

export type Daily = { yesterday: string[]; today: string[]; blockers: string[]; text: string }

export type ProjectInput = {
  name: string
  color: string
  description: string | null
  links: DevLink[]
  calendarAccountId: string | null
}

export type RulesInput = {
  importIssues: boolean
  closeIssues: boolean
  labelMrOpened: string | null
  labelMrMerged: string | null
  labelsClear: string[]
  lanes: Lane[]
}
