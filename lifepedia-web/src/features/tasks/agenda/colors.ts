import { COLOR_SWATCHES } from '@/lib/colors'
import { PRIORITY_COLOR } from '../dates'
import type { LabelLookup } from '../labels'
import type { TaskItem } from '../types'

/** De onde vem a cor do bloco na agenda. */
export type ColorBy = 'list' | 'person' | 'priority' | 'group'

export const COLOR_BY_LABEL: Record<ColorBy, string> = {
  list: 'Lista',
  person: 'Responsável',
  priority: 'Prioridade',
  group: 'Grupo',
}

export const NEUTRAL = '#64748b'
const PALETTE = COLOR_SWATCHES.filter((c) => c !== NEUTRAL)

/** Cor estável para um id (grupo sem posição conhecida). */
export function hashColor(id: string) {
  let hash = 0
  for (const ch of id) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

/** Cor de cada membro pela ordem de entrada no grupo (a mesma em toda a tela). */
export function personColor(memberIds: string[], id: string) {
  const index = memberIds.indexOf(id)
  return index < 0 ? NEUTRAL : PALETTE[index % PALETTE.length]
}

export function colorOf(item: TaskItem, by: ColorBy, labels: LabelLookup, memberIds: string[]) {
  switch (by) {
    case 'list': {
      const list = labels.list(item.listId)
      if (list) return list.color
      return item.groupId ? hashColor(item.groupId) : NEUTRAL
    }
    case 'person': {
      // Vários responsáveis: a cor de quem entrou primeiro no grupo (a mesma da legenda).
      const first = memberIds.find((id) => item.assignees.includes(id)) ?? item.assignees[0]
      return first ? personColor(memberIds, first) : NEUTRAL
    }
    case 'priority':
      return PRIORITY_COLOR[item.priority] ?? NEUTRAL
    case 'group':
      return item.groupId ? hashColor(item.groupId) : PALETTE[0]
  }
}
