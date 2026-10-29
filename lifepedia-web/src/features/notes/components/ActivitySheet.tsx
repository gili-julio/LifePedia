import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { UserAvatar } from '@/components/UserAvatar'
import { formatRelative } from '@/lib/format'
import { useNoteActivity } from '../api'
import type { NoteAction } from '../types'

const ACTION: Record<NoteAction, string> = {
  CREATED: 'criou a nota',
  EDITED: 'editou',
  ARCHIVED: 'arquivou',
  UNARCHIVED: 'desarquivou',
  TRASHED: 'mandou para a lixeira',
  RESTORED: 'restaurou',
}

type Props = { open: boolean; onOpenChange: (open: boolean) => void; groupId: string | null; noteId: string }

/** Quem fez o quê, do mais recente; edições seguidas da mesma pessoa aparecem uma vez só. */
export function ActivitySheet({ open, onOpenChange, groupId, noteId }: Props) {
  const { data, isLoading } = useNoteActivity(groupId, noteId, open)
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Atividade</SheetTitle>
          <SheetDescription>As últimas 30 ações nesta nota.</SheetDescription>
        </SheetHeader>
        <ol className="space-y-3 px-4 pb-6">
          {isLoading || !data ? Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-10" />) : data.map((a) => (
            <li key={a.id} className="flex items-center gap-3 text-sm">
              {a.user ? <UserAvatar user={a.user} size="sm" /> : <span className="size-8 shrink-0 rounded-full bg-muted" aria-hidden />}
              <p className="min-w-0 flex-1">
                <span className="font-medium">{a.user?.name ?? 'Alguém'}</span> {ACTION[a.action]}
                <span className="block text-xs text-muted-foreground">{formatRelative(a.at)}</span>
              </p>
            </li>
          ))}
        </ol>
      </SheetContent>
    </Sheet>
  )
}
