import { ApiError } from '@/lib/api'
import type { AuditAction, JobStatus, ModuleKey, UserCountKey } from './types'

export const MODULE_LABEL: Record<ModuleKey, { label: string; items: string }> = {
  FINANCE: { label: 'Financeiro', items: 'lançamentos e fixos' },
  TASKS: { label: 'Tarefas', items: 'tarefas' },
  NOTES: { label: 'Notas', items: 'notas' },
  ATTENDANCE: { label: 'Frequência', items: 'compromissos' },
  FINANCING: { label: 'Financiamentos', items: 'financiamentos' },
  GROUPS: { label: 'Grupos', items: 'grupos' },
  CHAT: { label: 'Chat dos grupos', items: 'mensagens' },
  FRIENDS: { label: 'Amigos', items: 'amizades' },
  AI_IMPORT: { label: 'Importação pelo site', items: 'leituras' },
  PUSH: { label: 'Notificações no aparelho', items: 'aparelhos' },
  ARCHITECTURE: { label: 'Arquitetura', items: 'projetos' },
  DEV: { label: 'Dev', items: 'projetos' },
  INTEGRATIONS: { label: 'Integrações', items: 'contas ligadas' },
  AI_DAILY: { label: 'Resumo do daily (IA)', items: 'resumos' },
}

export const COUNT_LABEL: Record<UserCountKey, string> = {
  transactions: 'Lançamentos',
  recurring: 'Fixos',
  tasks: 'Tarefas',
  notes: 'Notas',
  commitments: 'Compromissos',
  financings: 'Financiamentos',
  groups: 'Grupos',
  friends: 'Amigos',
  messages: 'Mensagens no chat',
  ai_imports: 'Leituras por IA (30 dias)',
  architecture: 'Projetos de arquitetura',
  dev: 'Projetos do Dev',
  integrations: 'Contas ligadas',
}

/** Resultado de uma leitura (espelha `AiUsage.Outcome`). */
export const OUTCOME_LABEL: Record<string, string> = {
  SUCCESS: 'Concluída',
  EMPTY: 'Sem lançamentos',
  TOO_MANY: 'Lançamentos demais',
  TIMEOUT: 'Demorou demais',
  BUSY: 'IA sobrecarregada',
  REJECTED: 'Arquivo recusado',
  BLOCKED: 'Bloqueada pela IA',
  TOO_LONG: 'Resposta longa demais',
  FAILED: 'Falha de conexão',
  ERROR: 'Erro inesperado',
  STOPPED: 'Interrompida',
}

export const AUDIT_LABEL: Record<AuditAction, string> = {
  ADMIN_GRANTED: 'Deu acesso de admin',
  ADMIN_REVOKED: 'Tirou acesso de admin',
  ADMIN_FIXED: 'Admin definido no servidor',
  USER_BLOCKED: 'Bloqueou a conta',
  USER_UNBLOCKED: 'Desbloqueou a conta',
  SESSIONS_REVOKED: 'Encerrou as sessões',
  ANNOUNCEMENT_CREATED: 'Publicou um aviso',
  ANNOUNCEMENT_ENDED: 'Encerrou um aviso',
  BROADCAST_SENT: 'Enviou notificação para todos',
}

export const JOB_LABEL: Record<JobStatus, string> = {
  NONE: 'Ainda não rodou',
  STARTED: 'Rodando',
  SUCCESS: 'Ok',
  ERROR: 'Falhou',
}

/** 3725 → "1 h 2 min"; 50 → "50 s". */
export function formatDuration(seconds: number) {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d) return `${d} d ${h} h`
  if (h) return `${h} h ${m} min`
  if (m) return `${m} min`
  return `${seconds} s`
}

export function percent(part: number, total: number) {
  return total ? Math.round((part / total) * 100) : 0
}

export function errorMessage(error: unknown, fallback = 'Não foi possível concluir. Tente de novo.') {
  return error instanceof ApiError ? error.message : fallback
}
