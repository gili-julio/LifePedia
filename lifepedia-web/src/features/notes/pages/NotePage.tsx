import {
  ActivityIcon,
  AlertTriangleIcon,
  ArchiveIcon,
  ArchiveRestoreIcon,
  ArrowLeftIcon,
  CalendarCheck2Icon,
  CheckIcon,
  DownloadIcon,
  EllipsisVerticalIcon,
  FolderIcon,
  ListTodoIcon,
  Loader2Icon,
  PinIcon,
  PinOffIcon,
  RotateCcwIcon,
  SlidersHorizontalIcon,
  Trash2Icon,
} from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  uploadNoteImage,
  useArchiveNote,
  useCreateNote,
  useDeleteNoteForever,
  useNote,
  useNoteLabels,
  useNoteRefs,
  usePinNote,
  useRestoreNote,
  useTrashNote,
} from '../api'
import { ActivitySheet } from '../components/ActivitySheet'
import { NoteDetailsSheet } from '../components/NoteDetailsSheet'
import { NoteEditor } from '../editor/NoteEditor'
import { downloadMarkdown, readingMinutes, shrinkImage, WIKI_LINK, wordCount } from '../markdown'
import { notesListUrl, noteUrl } from '../paths'
import type { Note, NoteRef } from '../types'
import { useAutosave, type SaveState } from '../use-autosave'

const NO_REFS: NoteRef[] = []

/** Nota aberta (pessoal em `/notas/:noteId`, do grupo em `/grupos/:groupId/notas/:noteId`). */
export function NotePage() {
  const { groupId, noteId = '' } = useParams()
  const owner = groupId ?? null
  const { data: note, isLoading, error } = useNote(owner, noteId)

  if (isLoading) return <div className="space-y-4"><Skeleton className="h-10 w-2/3" /><Skeleton className="h-96" /></div>
  if (!note) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p className="text-sm text-muted-foreground">
          {error instanceof ApiError && error.status === 404 ? 'Nota não encontrada.' : 'Não foi possível abrir a nota.'}
        </p>
        <Button asChild variant="outline"><Link to={notesListUrl(owner)}><ArrowLeftIcon /> Voltar para as notas</Link></Button>
      </div>
    )
  }
  return <NoteWorkspace key={note.id} groupId={owner} note={note} />
}

function NoteWorkspace({ groupId, note }: { groupId: string | null; note: Note }) {
  const navigate = useNavigate()
  const { draft, update, state, flush, reload, keepMine } = useAutosave(groupId, note)
  const refs = useNoteRefs(groupId).data ?? NO_REFS
  const labels = useNoteLabels(groupId).data
  const create = useCreateNote(groupId)
  const pin = usePinNote(groupId)
  const archive = useArchiveNote(groupId)
  const trash = useTrashNote(groupId)
  const restore = useRestoreNote(groupId)
  const remove = useDeleteNoteForever(groupId)
  const [sheet, setSheet] = useState<'details' | 'activity' | null>(null)
  const [confirm, setConfirm] = useState(false)

  const trashed = !!note.deletedAt
  const words = useMemo(() => wordCount(draft.content), [draft.content])
  const outgoing = useMemo(() => [...new Set([...draft.content.matchAll(WIKI_LINK)].map((m) => m[1].trim()))], [draft.content])
  const folder = labels?.folders.find((f) => f.id === draft.folderId)
  const tags = labels?.tags.filter((t) => draft.tagIds.includes(t.id)) ?? []

  async function act(action: Promise<unknown>, done: string, after?: () => void) {
    try {
      await flush()
      await action
      toast.success(done)
      after?.()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível concluir.')
    }
  }

  async function openLink(title: string) {
    if (note.template) return
    await flush()
    const target = refs.find((r) => r.title.toLowerCase() === title.toLowerCase())
    if (target) {
      navigate(noteUrl(groupId, target.id))
      return
    }
    try {
      const created = await create.mutateAsync({
        title, content: '', folderId: null, tagIds: [], icon: null, color: null, taskId: null, commitmentId: null,
      })
      toast.success(`Nota "${title}" criada.`)
      navigate(noteUrl(groupId, created.id))
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível criar a nota.')
    }
  }

  async function upload(file: File) {
    const blob = await shrinkImage(file)
    return (await uploadNoteImage(groupId, note.id, blob)).url
  }

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <div className="flex items-center gap-2">
        <Button asChild variant="ghost" size="sm" className="-ml-2">
          <Link to={note.template ? '/notas' : notesListUrl(groupId)} onClick={() => void flush()}>
            <ArrowLeftIcon /> {note.template ? 'Modelos' : 'Notas'}
          </Link>
        </Button>
        <SaveStatus state={state} onRetry={() => void flush()} />
        <div className="ml-auto flex items-center gap-1">
          {!trashed && !note.template && (
            <Button variant="ghost" size="icon" aria-label={note.pinned ? 'Desafixar' : 'Fixar no topo'} aria-pressed={note.pinned}
              onClick={() => void act(pin.mutateAsync({ id: note.id, pinned: !note.pinned }), note.pinned ? 'Nota desafixada.' : 'Nota fixada.')}>
              {note.pinned ? <PinOffIcon /> : <PinIcon />}
            </Button>
          )}
          <Button variant="ghost" size="icon" aria-label="Detalhes da nota" onClick={() => setSheet('details')}>
            <SlidersHorizontalIcon />
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" aria-label="Mais opções"><EllipsisVerticalIcon /></Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => setSheet('activity')}><ActivityIcon /> Atividade</DropdownMenuItem>
              <DropdownMenuItem onClick={() => downloadMarkdown(draft.title, draft.content)}><DownloadIcon /> Baixar .md</DropdownMenuItem>
              {!trashed && !note.template && (
                <DropdownMenuItem onClick={() => void act(archive.mutateAsync({ id: note.id, archived: !note.archivedAt }),
                  note.archivedAt ? 'Nota desarquivada.' : 'Nota arquivada.')}>
                  {note.archivedAt ? <><ArchiveRestoreIcon /> Desarquivar</> : <><ArchiveIcon /> Arquivar</>}
                </DropdownMenuItem>
              )}
              {!trashed && note.canDelete && (
                <>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem variant="destructive" onClick={() => void act(trash.mutateAsync(note.id),
                    'Nota na lixeira. Dá para restaurar por 30 dias.', () => navigate(note.template ? '/notas' : notesListUrl(groupId)))}>
                    <Trash2Icon /> Mover para a lixeira
                  </DropdownMenuItem>
                </>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {trashed && (
        <Banner tone="danger" text={`Na lixeira desde ${formatRelative(note.deletedAt!)}. Some de vez depois de 30 dias.`}>
          {note.canDelete && (
            <>
              <Button size="sm" variant="outline" onClick={() => void act(restore.mutateAsync(note.id), 'Nota restaurada.')}>
                <RotateCcwIcon /> Restaurar
              </Button>
              <Button size="sm" variant="destructive" onClick={() => setConfirm(true)}>Apagar de vez</Button>
            </>
          )}
        </Banner>
      )}
      {note.archivedAt && !trashed && (
        <Banner tone="info" text="Nota arquivada: não aparece na lista principal.">
          <Button size="sm" variant="outline" onClick={() => void act(archive.mutateAsync({ id: note.id, archived: false }), 'Nota desarquivada.')}>
            <ArchiveRestoreIcon /> Desarquivar
          </Button>
        </Banner>
      )}
      {note.template && <Banner tone="info" text='Modelo: aparece em "Nova nota" para começar outras notas com esta estrutura.' />}
      {state === 'conflict' && (
        <Banner tone="warning" text="Esta nota foi alterada em outro lugar enquanto você editava. Suas mudanças ainda não foram salvas.">
          <Button size="sm" variant="outline" onClick={() => void reload()}>Ver a versão nova</Button>
          <Button size="sm" onClick={() => void keepMine()}>Manter a minha</Button>
        </Banner>
      )}

      <div className="flex items-start gap-2">
        <button type="button" onClick={() => setSheet('details')} disabled={trashed} aria-label="Trocar ícone"
          className="mt-1 shrink-0 rounded-lg text-3xl leading-none hover:bg-accent disabled:hover:bg-transparent">
          {draft.icon ?? '📄'}
        </button>
        <textarea value={draft.title} onChange={(e) => update({ title: e.target.value.replace(/\n/g, ' ') })} rows={1}
          readOnly={trashed} maxLength={200} placeholder="Sem título" aria-label="Título da nota"
          className="min-w-0 flex-1 resize-none overflow-hidden bg-transparent text-2xl font-bold tracking-tight outline-none [field-sizing:content] placeholder:text-muted-foreground sm:text-3xl" />
      </div>

      {(folder || tags.length > 0 || note.task || note.commitment) && (
        <div className="flex flex-wrap gap-1.5 text-xs">
          {folder && <Chip icon={<FolderIcon className="size-3.5" />}>{folder.name}</Chip>}
          {tags.map((t) => (
            <Chip key={t.id} icon={<span className="size-2 rounded-full" style={{ background: t.color }} />}>{t.name}</Chip>
          ))}
          {note.task && (
            <Chip icon={<ListTodoIcon className="size-3.5" />}>
              <Link to={groupId ? `/grupos/${groupId}?aba=tarefas` : '/tarefas'} className="underline-offset-2 hover:underline">{note.task.title}</Link>
            </Chip>
          )}
          {note.commitment && (
            <Chip icon={<CalendarCheck2Icon className="size-3.5" />}>
              <Link to="/frequencia" className="underline-offset-2 hover:underline">{note.commitment.name}</Link>
            </Chip>
          )}
        </div>
      )}

      <NoteEditor value={draft.content} onChange={(content) => update({ content })} readOnly={trashed} refs={refs}
        onOpenLink={(title) => void openLink(title)} onUploadImage={upload} />

      <footer className="space-y-3 border-t pt-3 text-xs text-muted-foreground">
        <p className="tabular-nums">
          {words} {words === 1 ? 'palavra' : 'palavras'}
          {words > 0 && ` · ${readingMinutes(words)} min de leitura`}
          {` · Editada ${formatRelative(note.updatedAt)}`}{note.editedBy && groupId ? ` por ${note.editedBy.name}` : ''}
        </p>
        {!note.template && outgoing.length > 0 && (
          <LinkList label="Links nesta nota">
            {outgoing.map((title) => {
              const exists = refs.some((r) => r.title.toLowerCase() === title.toLowerCase())
              return (
                <button key={title} type="button" onClick={() => void openLink(title)}
                  className={cn('rounded-full border px-2 py-0.5 text-foreground hover:bg-accent', !exists && 'border-dashed text-muted-foreground')}
                  title={exists ? `Abrir "${title}"` : `Criar "${title}"`}>
                  {exists ? title : `+ ${title}`}
                </button>
              )
            })}
          </LinkList>
        )}
        {note.backlinks.length > 0 && (
          <LinkList label="Citada em">
            {note.backlinks.map((b) => (
              <Link key={b.id} to={noteUrl(groupId, b.id)} onClick={() => void flush()}
                className="rounded-full border px-2 py-0.5 text-foreground hover:bg-accent">
                {b.icon ?? '📄'} {b.title}
              </Link>
            ))}
          </LinkList>
        )}
      </footer>

      <NoteDetailsSheet open={sheet === 'details'} onOpenChange={(open) => !open && setSheet(null)} groupId={groupId}
        draft={draft} template={note.template} readOnly={trashed} onChange={update} />
      <ActivitySheet open={sheet === 'activity'} onOpenChange={(open) => !open && setSheet(null)} groupId={groupId} noteId={note.id} />
      <ConfirmDelete open={confirm} onOpenChange={setConfirm} title="Apagar de vez?" confirmLabel="Apagar de vez"
        description="A nota e as fotos dela somem para sempre. Não dá para desfazer."
        onConfirm={() => void act(remove.mutateAsync(note.id), 'Nota apagada.', () => navigate(notesListUrl(groupId)))} />
    </div>
  )
}

function SaveStatus({ state, onRetry }: { state: SaveState; onRetry: () => void }) {
  if (state === 'error') {
    return (
      <span role="status" className="flex items-center gap-1 text-xs text-destructive">
        <AlertTriangleIcon className="size-3.5" aria-hidden /> Não salvou.
        <button type="button" onClick={onRetry} className="font-medium underline underline-offset-2">Tentar de novo</button>
      </span>
    )
  }
  if (state === 'conflict') return null
  return (
    <span role="status" className="flex items-center gap-1 text-xs text-muted-foreground">
      {state === 'saved' ? <><CheckIcon className="size-3.5" aria-hidden /> Salvo</>
        : <><Loader2Icon className="size-3.5 animate-spin motion-reduce:animate-none" aria-hidden /> Salvando…</>}
    </span>
  )
}

function Banner({ tone, text, children }: { tone: 'info' | 'warning' | 'danger'; text: string; children?: ReactNode }) {
  return (
    <div role={tone === 'info' ? 'note' : 'alert'} className={cn('flex flex-col items-start gap-2 rounded-lg px-3 py-2 text-sm sm:flex-row sm:items-center',
      tone === 'info' && 'bg-data/10', tone === 'warning' && 'bg-orange-500/10 text-orange-800 dark:text-orange-200',
      tone === 'danger' && 'bg-destructive/10')}>
      <p className="min-w-0 flex-1">{text}</p>
      {children && <div className="flex flex-wrap gap-2">{children}</div>}
    </div>
  )
}

function Chip({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return <span className="flex items-center gap-1 rounded-full bg-muted px-2 py-0.5">{icon}{children}</span>
}

function LinkList({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="font-medium">{label}:</span>
      {children}
    </div>
  )
}
