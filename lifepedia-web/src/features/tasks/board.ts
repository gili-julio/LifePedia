import { createContext, useContext, useMemo } from 'react'
import { useAuth } from '@/features/auth/auth-context'
import { useGroups } from '@/features/groups/api'
import type { GroupDetail, GroupMember } from '@/features/groups/types'

/** Onde as tarefas estão sendo vistas: pessoal (`groupId` nulo) ou um grupo. */
export type Board = {
  groupId: string | null
  meId: string | null
  /** Membros do grupo (vazio na visão pessoal) */
  members: GroupMember[]
  isAdmin: boolean
  /** Nome de um membro do grupo ("Você" para quem está logado); nulo se não for membro. */
  personName: (id: string | null) => string | null
  /** "Você", "Ana e Bia", "Ana, Bia e +2" ou "Todos do grupo"; nulo sem responsável. */
  assigneesLabel: (item: { assignees: string[]; allMembers: boolean }) => string | null
  groupName: (id: string | null) => string | null
}

export const BoardContext = createContext<Board | null>(null)

export function useBoard() {
  const board = useContext(BoardContext)
  if (!board) throw new Error('useBoard precisa estar dentro de <BoardContext>')
  return board
}

/** Monta o contexto; `group` nulo = tarefas pessoais. */
export function useBoardValue(group: GroupDetail | null): Board {
  const { user } = useAuth()
  const { data: groups } = useGroups()
  return useMemo(() => {
    const meId = user?.id ?? null
    const people = new Map((group?.members ?? []).map((m) => [m.user.id, m.user.name]))
    const groupNames = new Map((groups ?? []).map((g) => [g.id, g.name]))
    const personName = (id: string | null) => (!id ? null : id === meId ? 'Você' : people.get(id) ?? null)
    return {
      groupId: group?.id ?? null,
      meId,
      members: group?.members ?? [],
      isAdmin: group?.role === 'ADMIN',
      personName,
      assigneesLabel: ({ assignees, allMembers }) => {
        if (allMembers) return 'Todos do grupo'
        const names = assignees.map((id) => personName(id) ?? 'Ex-membro')
        if (names.length <= 2) return names.join(' e ') || null
        return `${names.slice(0, 2).join(', ')} e +${names.length - 2}`
      },
      groupName: (id) => (id ? groupNames.get(id) ?? null : null),
    }
  }, [group, groups, user])
}
