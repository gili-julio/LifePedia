import { Link } from 'react-router'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { useGroups } from '@/features/groups/api'
import { GroupAvatar } from '@/features/groups/components/GroupAvatar'
import type { TaskItem } from '../types'

type Props = {
  item: TaskItem | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onChoose: (groupId: string) => void
}

/** Escolhe o grupo para onde a tarefa pessoal vai (sai das pessoais; lista, etiquetas e fixo ficam para trás). */
export function GroupMoveDialog({ item, open, onOpenChange, onChoose }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Levar para um grupo</DialogTitle>
          <DialogDescription>
            {item ? `“${item.title}” vira tarefa do grupo, com você como responsável. Lista, etiquetas e fixo ligado não vão junto.` : ''}
          </DialogDescription>
        </DialogHeader>
        <DialogBody>
          <Groups onChoose={(id) => {
            onOpenChange(false)
            onChoose(id)
          }} />
        </DialogBody>
      </DialogScrollContent>
    </Dialog>
  )
}

function Groups({ onChoose }: { onChoose: (groupId: string) => void }) {
  const { data, isLoading } = useGroups()
  if (isLoading) return <Skeleton className="h-32 w-full" />
  const groups = (data ?? []).filter((g) => g.role !== null)
  if (!groups.length) {
    return (
      <div className="flex flex-col items-center gap-2 py-6 text-center text-sm text-muted-foreground">
        <p>Você ainda não participa de nenhum grupo.</p>
        <Button variant="outline" size="sm" asChild><Link to="/grupos">Ver grupos</Link></Button>
      </div>
    )
  }
  return (
    <ul className="-mx-2 space-y-0.5" aria-label="Grupos">
      {groups.map((g) => (
        <li key={g.id}>
          <button type="button" onClick={() => onChoose(g.id)}
            className="flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm outline-none hover:bg-muted focus-visible:ring-[3px] focus-visible:ring-ring/50">
            <GroupAvatar name={g.name} imageUrl={g.imageUrl} size="sm" />
            <span className="min-w-0 flex-1 truncate">{g.name}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
