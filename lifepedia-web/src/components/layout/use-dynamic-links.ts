import { useGroups } from '@/features/groups/api'
import type { DynamicLinks } from './nav-items'

/** Subitens que vêm da API (ex.: os grupos do usuário). */
export function useDynamicLinks(): DynamicLinks {
  const { data: groups = [] } = useGroups()
  return { groups: groups.map((group) => ({ to: `/grupos/${group.id}`, label: group.name })) }
}
