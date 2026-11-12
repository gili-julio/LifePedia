import { useMemo } from 'react'
import { api } from '@/lib/api'
import { useAuth } from './auth-context'
import type { AppModule, User } from './types'

/** Módulos que dá para esconder (espelha `AppModule` da API), na ordem do menu. */
export const MODULES: { id: AppModule; label: string; hint: string }[] = [
  { id: 'FINANCE', label: 'Financeiro', hint: 'Fixos, lançamentos, bancos e importação' },
  { id: 'FINANCING', label: 'Financiamentos', hint: 'Simulador e contratos' },
  { id: 'TASKS', label: 'Tarefas', hint: 'Tarefas e hábitos' },
  { id: 'AGENDA', label: 'Agenda', hint: 'Semana com os horários' },
  { id: 'ATTENDANCE', label: 'Frequência', hint: 'Presença e faltas' },
  { id: 'NOTES', label: 'Notas', hint: 'Notas e pastas' },
  { id: 'GROUPS', label: 'Grupos', hint: 'Grupos e atalhos no menu' },
  { id: 'FRIENDS', label: 'Amigos', hint: 'Amigos e conquistas' },
  { id: 'DEV', label: 'Dev', hint: 'Projetos de trabalho, MRs e daily' },
  { id: 'ARCHITECTURE', label: 'Arquitetura', hint: 'Maquetes 3D' },
]

export type Preferences = User['preferences']

/** Módulos escondidos da pessoa (somem do menu e do Início; o link direto continua abrindo). */
export function useHiddenModules(): ReadonlySet<AppModule> {
  const { user } = useAuth()
  const hidden = user?.preferences.hiddenModules
  return useMemo(() => new Set(hidden ?? []), [hidden])
}

/** Grava as preferências na conta (valem em todos os aparelhos). */
export function savePreferences(preferences: Preferences) {
  return api<User>('/api/users/me/preferences', { method: 'PUT', body: preferences })
}
