import type { UserSummary } from '@/features/groups/types'

/** Espelha `NoteDtos.Status`: em uso, arquivadas, lixeira ou modelos (só pessoal). */
export type NoteStatus = 'ACTIVE' | 'ARCHIVED' | 'TRASH' | 'TEMPLATE'
/** Espelha `NoteActivity.Action`. */
export type NoteAction = 'CREATED' | 'EDITED' | 'ARCHIVED' | 'UNARCHIVED' | 'TRASHED' | 'RESTORED'

export type LinkedTask = { id: string; title: string }
export type LinkedCommitment = { id: string; name: string }

export type NoteSummary = {
  id: string
  title: string
  excerpt: string
  icon: string | null
  color: string | null
  folderId: string | null
  tagIds: string[]
  pinned: boolean
  archivedAt: string | null
  deletedAt: string | null
  updatedAt: string
  editedBy: UserSummary | null
  canDelete: boolean
}

export type NoteRef = { id: string; title: string; icon: string | null }

export type Note = {
  id: string
  title: string
  content: string
  icon: string | null
  color: string | null
  folderId: string | null
  tagIds: string[]
  template: boolean
  pinned: boolean
  archivedAt: string | null
  deletedAt: string | null
  task: LinkedTask | null
  commitment: LinkedCommitment | null
  backlinks: NoteRef[]
  createdBy: UserSummary | null
  editedBy: UserSummary | null
  createdAt: string
  updatedAt: string
  version: number
  canDelete: boolean
}

/** `version`: a que a pessoa tinha ao editar (outra mais nova → 409). */
export type NoteRequest = {
  title: string
  content: string
  folderId: string | null
  tagIds: string[]
  icon: string | null
  color: string | null
  taskId: string | null
  commitmentId: string | null
  template?: boolean
  version?: number
}

export type NoteActivity = { id: string; action: NoteAction; user: UserSummary | null; at: string }

export type NoteFolder = { id: string; name: string; count: number }
export type NoteTag = { id: string; name: string; color: string; count: number }
export type NoteLabels = { folders: NoteFolder[]; tags: NoteTag[] }

export type LinkOptions = { tasks: LinkedTask[]; commitments: LinkedCommitment[] }

export type NoteFilters = {
  status: NoteStatus
  q: string
  folderId: string | null
  tagId: string | null
  taskId: string | null
  commitmentId: string | null
}
