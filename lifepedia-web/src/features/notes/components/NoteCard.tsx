import { EllipsisVerticalIcon, PinIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { formatRelative } from '@/lib/format'
import type { NoteSummary, NoteTag } from '../types'

type Props = {
  note: NoteSummary
  href: string
  tags: Map<string, NoteTag>
  folder?: string
  showEditor: boolean
  onMenu: () => void
}

/** Nota na lista: ícone, título, começo do texto, pasta, etiquetas e quando mudou. */
export function NoteCard({ note, href, tags, folder, showEditor, onMenu }: Props) {
  const own = note.tagIds.map((id) => tags.get(id)).filter((t): t is NoteTag => !!t)
  return (
    <div className="group relative flex min-h-36 flex-col rounded-xl border bg-card p-4 ring-foreground/5 transition-shadow hover:shadow-sm motion-reduce:transition-none">
      {note.color && <span aria-hidden className="absolute inset-y-3 left-0 w-1 rounded-r-full" style={{ background: note.color }} />}
      <div className="flex items-start gap-2">
        <span className="mt-0.5 shrink-0 text-lg leading-none" aria-hidden>{note.icon ?? '📄'}</span>
        <Link to={href} className="min-w-0 flex-1 font-semibold leading-snug after:absolute after:inset-0 focus-visible:outline-none">
          <span className="line-clamp-2">{note.title}</span>
        </Link>
        {note.pinned && <PinIcon className="mt-0.5 size-4 shrink-0 fill-current text-muted-foreground" aria-label="Fixada" />}
        <Button variant="ghost" size="icon-sm" aria-label={`Opções de ${note.title}`} onClick={onMenu}
          className="relative z-10 -mt-1 -mr-2 shrink-0">
          <EllipsisVerticalIcon />
        </Button>
      </div>
      <p className="mt-2 line-clamp-3 flex-1 text-sm text-muted-foreground">{note.excerpt || 'Nota vazia'}</p>
      {(folder || own.length > 0) && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {folder && <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{folder}</span>}
          {own.map((t) => (
            <span key={t.id} className="flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs">
              <span className="size-2 rounded-full" style={{ background: t.color }} aria-hidden />{t.name}
            </span>
          ))}
        </div>
      )}
      <p className="mt-3 text-xs text-muted-foreground">
        {note.deletedAt ? `Na lixeira ${formatRelative(note.deletedAt)}` : `Editada ${formatRelative(note.updatedAt)}`}
        {showEditor && note.editedBy && ` por ${note.editedBy.name}`}
      </p>
    </div>
  )
}
