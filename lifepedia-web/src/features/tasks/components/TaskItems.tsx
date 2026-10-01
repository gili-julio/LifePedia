import { useState } from 'react'
import { toast } from 'sonner'
import { SortableItems } from '@/components/SortableItems'
import { useBoard } from '../board'
import { occurrenceKey, type LabelLookup } from '../labels'
import type { TaskItem } from '../types'
import type { TaskActions } from './task-actions'
import type { TaskTarget } from './TaskDialog'
import { TaskRow } from './TaskRow'

type Props = {
  items: TaskItem[]
  today: string
  labels: LabelLookup
  actions: TaskActions
  onEdit: (target: TaskTarget) => void
  /** Ordem manual: dá para arrastar as pendentes. */
  manual: boolean
  /** Atrasadas: mostra o dia e junta as da mesma tarefa repetida. */
  overdue?: boolean
}

/** Lista de ocorrências; a nova ordem aparece na hora e vai para a API. */
export function TaskItems({ items, today, labels, actions, onEdit, manual, overdue }: Props) {
  const board = useBoard()
  const [order, setOrder] = useState<{ keys: string[]; basis: TaskItem[] } | null>(null)
  const groups = overdue ? groupByTask(items) : items.map((item) => ({ item, count: 1 }))
  const byKey = new Map(groups.map((g) => [occurrenceKey(g.item), g]))
  const keys = order && order.basis === items ? order.keys : groups.map((g) => occurrenceKey(g.item))
  // Arrasta só as pendentes do próprio quadro (na visão pessoal, as de grupo ficam no fim, fixas).
  const movable = (item: TaskItem) => item.status === 'PENDING' && item.groupId === board.groupId
  const sortableKeys = keys.filter((k) => byKey.has(k) && movable(byKey.get(k)!.item))
  const canDrag = manual && !overdue && sortableKeys.length > 1

  async function onReorder(next: string[]) {
    const rest = keys.filter((k) => !next.includes(k))
    setOrder({ keys: [...next, ...rest], basis: items })
    const taskIds = [...new Set(next.map((k) => byKey.get(k)!.item.taskId))]
    try {
      await actions.reorder(taskIds)
    } catch {
      setOrder(null)
      toast.error('Não foi possível mudar a ordem.')
    }
  }

  return (
    <SortableItems ids={sortableKeys} label={(key) => byKey.get(key)?.item.title ?? ''} onReorder={onReorder}>
      <ul className="divide-y">
        {keys.map((key) => {
          const group = byKey.get(key)
          if (!group) return null
          const { item, count } = group
          return (
            <TaskRow key={key} item={item} labels={labels} today={today} sortId={key}
              dragDisabled={!canDrag || !movable(item)} showDate={overdue}
              extra={count > 1 && <span className="font-medium text-foreground">+{count - 1} anteriores</span>}
              busy={actions.busyKey === key}
              onToggle={() => void actions.toggle(item)}
              onOpen={() => onEdit({ mode: 'edit', groupId: item.groupId, taskId: item.taskId, occurrence: item })}
              onCheck={(subtaskId, done) => actions.checkSubtask(item, subtaskId, done)}
              actions={actions.rowActions(item, count)}
              onMore={() => actions.openSheet(item, count)} />
          )
        })}
      </ul>
    </SortableItems>
  )
}

/** Atrasadas da mesma tarefa repetida viram uma linha (a mais recente) com a contagem. */
function groupByTask(items: TaskItem[]) {
  const result: { item: TaskItem; count: number }[] = []
  const index = new Map<string, number>()
  for (const item of items) {
    const at = index.get(item.taskId)
    if (item.repeat && at !== undefined) {
      result[at] = { item, count: result[at].count + 1 }
    } else {
      index.set(item.taskId, result.length)
      result.push({ item, count: 1 })
    }
  }
  return result
}
