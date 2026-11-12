import {
  BookOpenIcon,
  FolderGit2Icon,
  GlobeIcon,
  KanbanSquareIcon,
  LinkIcon,
  MessagesSquareIcon,
  type LucideIcon,
  RocketIcon,
} from 'lucide-react'
import type { LinkKind, PipelineStatus } from './types'

export const LINK_KIND: Record<LinkKind, { label: string; icon: LucideIcon }> = {
  REPO: { label: 'Repositório', icon: FolderGit2Icon },
  BOARD: { label: 'Board', icon: KanbanSquareIcon },
  STAGING: { label: 'Homologação', icon: GlobeIcon },
  PRODUCTION: { label: 'Produção', icon: RocketIcon },
  DOCS: { label: 'Documentação', icon: BookOpenIcon },
  CHAT: { label: 'Conversa', icon: MessagesSquareIcon },
  OTHER: { label: 'Outro', icon: LinkIcon },
}

export const LINK_KINDS = Object.keys(LINK_KIND) as LinkKind[]

/** Texto e cor (bolinha) de cada status de pipeline. */
export const PIPELINE: Record<PipelineStatus, { label: string; dot: string }> = {
  SUCCESS: { label: 'Passou', dot: 'bg-emerald-500' },
  FAILED: { label: 'Falhou', dot: 'bg-destructive' },
  RUNNING: { label: 'Rodando', dot: 'bg-sky-500' },
  PENDING: { label: 'Esperando', dot: 'bg-amber-500' },
  CANCELED: { label: 'Cancelada', dot: 'bg-muted-foreground' },
  SKIPPED: { label: 'Pulada', dot: 'bg-muted-foreground' },
  MANUAL: { label: 'Manual', dot: 'bg-violet-500' },
}

export const devProjectUrl = (id: string) => `/dev/${id}`

/** GitLab chama de MR e o GitHub de PR: o número com o prefixo de cada um. */
export function itemNumber(provider: string | null | undefined, kind: 'MR' | 'ISSUE' | 'PIPELINE', number: string | null) {
  if (!number || kind === 'PIPELINE') return ''
  if (kind === 'ISSUE') return `#${number}`
  return provider === 'GITHUB' ? `#${number}` : `!${number}`
}
