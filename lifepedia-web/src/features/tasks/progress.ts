import type { TaskItem } from './types'

/** Concluída para quem vê: a ocorrência inteira ou, em "cada um conclui a sua", a parte dessa pessoa. */
export function isDoneFor(item: TaskItem, meId: string | null) {
  return item.status === 'DONE' || (item.completion === 'EACH' && !!meId && item.doneBy.includes(meId))
}

/** "Cada um conclui a sua": quantos já fizeram a sua parte ("1/3"); nulo nos outros casos. */
export function eachProgress(item: TaskItem) {
  if (item.completion !== 'EACH' || item.assignees.length < 2) return null
  const done = item.status === 'DONE' ? item.assignees.length : item.doneBy.filter((id) => item.assignees.includes(id)).length
  return { done, total: item.assignees.length }
}
