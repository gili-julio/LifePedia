import { useChatUnread } from '@/features/chat/api'
import { useGroupShortcuts } from '@/features/groups/api'
import type { DynamicLinks } from './nav-items'

/** Subitens que vêm da API (ex.: até 3 grupos: fixados e com atividade recente). */
export function useDynamicLinks(): DynamicLinks {
  const { data: groups = [] } = useGroupShortcuts()
  const { data: unread = {} } = useChatUnread()
  return {
    groups: groups.map((group) => ({
      to: `/grupos/${group.id}`,
      label: group.name,
      group: { id: group.id, name: group.name, imageUrl: group.imageUrl, pinned: group.pinned, unread: unread[group.id] ?? 0 },
    })),
  }
}
