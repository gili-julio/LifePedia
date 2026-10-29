import type { UserSummary } from '@/features/groups/types'

/** Versão do arquivo exportado; espelha `AccountDtos.FORMAT` da API. */
export const ACCOUNT_FORMAT = 'lifepedia/account@3'
/** Versões que a importação aceita (o `@1` não tem tarefas; o `@2` não tem frequência). */
export const IMPORTABLE_FORMATS = ['lifepedia/account@1', 'lifepedia/account@2', ACCOUNT_FORMAT]

/** Arquivo exportado (só os campos que o front lê; o resto vai inteiro para a API). */
export type AccountFile = {
  format: string
  exportedAt?: string
  profile?: { name: string; username: string | null; email: string }
  categories: unknown[]
  recurring: unknown[]
  transactions: unknown[]
  groups?: unknown[]
  taskLists?: unknown[]
  taskTags?: unknown[]
  tasks?: unknown[]
}

export type ImportMode = 'MERGE' | 'REPLACE'

export type Counts = {
  categories: number
  transactions: number
  recurring: number
  adjustments: number
  tasks: number
  /** Listas e etiquetas de tarefas */
  taskLabels: number
}

export type ImportResult = {
  applied: boolean
  created: Counts
  /** Iguais aos que já existem (só ao somar) */
  skipped: Counts
  /** Apagados antes (só ao substituir) */
  removed: Counts
  categoriesReused: number
  /** Itens sincronizados em grupos que viraram cópia */
  syncedDetached: number
}

export type GroupImpact = {
  id: string
  name: string
  otherMembers: number
  deletesGroup: boolean
  /** Quem vira admin (nulo = ninguém muda) */
  newAdmin: UserSummary | null
  transactions: number
  recurring: number
  planItems: number
}

export type DeletionPreview = {
  categories: number
  transactions: number
  recurring: number
  tasks: number
  groups: GroupImpact[]
}
