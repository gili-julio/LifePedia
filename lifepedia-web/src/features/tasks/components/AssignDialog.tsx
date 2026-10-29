import { CheckIcon } from 'lucide-react'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { useGroup } from '@/features/groups/api'
import { cn } from '@/lib/utils'
import { dayLabel } from '../dates'
import type { TaskItem } from '../types'

type Props = {
  item: TaskItem | null
  today: string
  open: boolean
  onOpenChange: (open: boolean) => void
  /** `null` = ninguém */
  onChoose: (assigneeId: string | null) => void
}

/** Uma pessoa só nesta ocorrência (tarefa sem repetição: vira a responsável da tarefa). */
export function AssignDialog({ item, today, open, onOpenChange, onChoose }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Mudar responsável</DialogTitle>
          <DialogDescription>
            {item?.repeat && item.scheduledDate
              ? `Só em ${dayLabel(item.scheduledDate, today).toLowerCase()}; as outras ocorrências continuam iguais.`
              : item?.title}
          </DialogDescription>
        </DialogHeader>
        <DialogBody>
          {item?.groupId && (
            <People groupId={item.groupId} current={item.assignees.length === 1 ? item.assignees[0] : null} allowNone={!item.repeat}
              onChoose={(id) => {
                onOpenChange(false)
                if (item.assignees.length !== 1 || id !== item.assignees[0]) onChoose(id)
              }} />
          )}
        </DialogBody>
      </DialogScrollContent>
    </Dialog>
  )
}

function People({ groupId, current, allowNone, onChoose }: {
  groupId: string
  current: string | null
  allowNone: boolean
  onChoose: (id: string | null) => void
}) {
  const { data: group } = useGroup(groupId)
  const { user } = useAuth()
  if (!group) return <Skeleton className="h-32 w-full" />
  const options = [
    ...(allowNone ? [{ id: null, name: 'Ninguém' }] : []),
    ...group.members.map((m) => ({ id: m.user.id, name: m.user.id === user?.id ? `${m.user.name} (você)` : m.user.name })),
  ]
  return (
    <ul className="-mx-2 space-y-0.5" role="listbox" aria-label="Responsável">
      {options.map((o) => (
        <li key={o.id ?? 'none'}>
          <button type="button" role="option" aria-selected={o.id === current} onClick={() => onChoose(o.id)}
            className={cn('flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm outline-none hover:bg-muted',
              'focus-visible:ring-[3px] focus-visible:ring-ring/50', o.id === current && 'font-medium')}>
            <span className="min-w-0 flex-1 truncate">{o.name}</span>
            {o.id === current && <CheckIcon className="size-4" aria-hidden />}
          </button>
        </li>
      ))}
    </ul>
  )
}
