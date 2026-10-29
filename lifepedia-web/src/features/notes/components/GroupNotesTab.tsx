import { FilePlus2Icon, TagsIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { NotesBoard } from './NotesBoard'

/** Aba Notas do grupo: todos os membros leem e editam; lixeira só de quem criou ou do admin. */
export function GroupNotesTab({ groupId }: { groupId: string }) {
  return (
    <NotesBoard groupId={groupId} header={({ newNote, labels }) => (
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">Notas do grupo: todo mundo lê e edita.</p>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={labels}><TagsIcon /> Pastas e etiquetas</Button>
          <Button size="sm" onClick={newNote}><FilePlus2Icon /> Nova nota</Button>
        </div>
      </div>
    )} />
  )
}
