import { TriangleAlertIcon } from 'lucide-react'
import { type Control, useWatch } from 'react-hook-form'
import { useGroup, useGroups } from '@/features/groups/api'
import { useAuth } from '@/features/auth/auth-context'
import { useConflicts } from '../api'
import { endTime } from '../dates'
import type { TaskFormInput } from './task-form'

type Props = {
  control: Control<TaskFormInput>
  groupId: string | null
  /** Tarefa sendo editada (sai da conta) */
  taskId: string | null
}

/** Avisa (sem impedir) quando o horário escolhido bate com outra tarefa dos responsáveis. */
export function ConflictWarning({ control, groupId, taskId }: Props) {
  const [dueDate, dueTime, duration, mode, assignees] =
    useWatch({ control, name: ['dueDate', 'dueTime', 'duration', 'assignMode', 'assignees'] })
  const { data: group } = useGroup(groupId ?? '')
  const { data: groups } = useGroups()
  const meId = useAuth().user?.id ?? null
  const members = group?.members ?? []
  const people = !groupId ? [] : mode === 'PEOPLE' ? assignees : mode === 'ALL' ? members.map((m) => m.user.id) : []
  const ready = !!dueDate && /^\d{2}:\d{2}$/.test(dueTime) && (!groupId || !!group)
  const { data } = useConflicts(groupId, ready
    ? { date: dueDate, time: dueTime, duration: duration ? Number(duration) : null, people, task: taskId }
    : null)
  if (!ready || !data?.length) return null

  const person = (id: string) => (id === meId ? 'você' : members.find((m) => m.user.id === id)?.user.name ?? 'alguém')
  const where = (id: string | null) => (id === null ? 'pessoal' : id === groupId ? null : groups?.find((g) => g.id === id)?.name ?? 'outro grupo')

  return (
    <div role="status" className="flex gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm">
      <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden />
      <div className="min-w-0 space-y-0.5">
        <p className="font-medium">Choque de horário</p>
        <ul className="text-xs text-muted-foreground">
          {data.map((c) => {
            const place = where(c.groupId)
            const who = c.people.map(person).join(', ')
            return (
              <li key={`${c.taskId}|${c.date}`} className="break-words">
                {c.dueTime}–{endTime(c.dueTime, c.durationMinutes ?? 30)} {c.title}
                {place && ` (${place})`}{who && ` · ${who}`}
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
