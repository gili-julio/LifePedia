import { useMemo } from 'react'
import { useLabels } from './api'
import type { Label, TaskItem } from './types'

/** Listas e etiquetas por id (as tarefas só trazem os ids). `groupId` nulo = pessoais. */
export function useLabelLookup(groupId: string | null) {
  const { data } = useLabels(groupId)
  return useMemo(() => {
    const lists = new Map((data?.lists ?? []).map((l) => [l.id, l]))
    const tags = new Map((data?.tags ?? []).map((t) => [t.id, t]))
    return {
      lists: data?.lists ?? [],
      tags: data?.tags ?? [],
      list: (id: string | null): Label | undefined => (id ? lists.get(id) : undefined),
      tag: (id: string): Label | undefined => tags.get(id),
    }
  }, [data])
}

/** Chave única de uma ocorrência. */
export const occurrenceKey = (item: TaskItem) => `${item.taskId}|${item.date ?? ''}`

export type LabelLookup = ReturnType<typeof useLabelLookup>
