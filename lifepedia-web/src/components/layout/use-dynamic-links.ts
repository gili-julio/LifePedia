import { useGroupShortcuts } from '@/features/groups/api'
import type { DynamicLinks } from './nav-items'

/** Subitens que vêm da API (ex.: até 3 grupos: fixados e com atividade recente). */
export function useDynamicLinks(): DynamicLinks {
  const { data: groups = [] } = useGroupShortcuts()
  return {
    groups: groups.map((group) => ({
      to: `/grupos/${group.id}`,
      label: group.name,
      group: { id: group.id, name: group.name, imageUrl: group.imageUrl, pinned: group.pinned },
    })),
  }
}
