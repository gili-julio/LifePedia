import {
  ArchiveIcon,
  ArchiveRestoreIcon,
  FilePlus2Icon,
  NotebookPenIcon,
  PinIcon,
  PinOffIcon,
  RotateCcwIcon,
  SearchIcon,
  Trash2Icon,
  XIcon,
} from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { ActionSheet, type SheetAction } from '@/components/ActionSheet'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { SortSelect, type SortOption } from '@/components/data-table/SortSelect'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import {
  useArchiveNote,
  useDeleteNoteForever,
  useLinkOptions,
  useNoteLabels,
  useNotes,
  usePinNote,
  useRestoreNote,
  useTrashNote,
} from '../api'
import { noteUrl } from '../paths'
import type { NoteFilters, NoteStatus, NoteSummary } from '../types'
import { LabelsDialog } from './LabelsDialog'
import { NewNoteDialog } from './NewNoteDialog'
import { NoteCard } from './NoteCard'

const ALL = '__all'
const SORTS: SortOption[] = [
  { field: 'updatedAt', label: 'Editadas', firstDirection: 'desc' },
  { field: 'createdAt', label: 'Criadas', firstDirection: 'desc' },
  { field: 'title', label: 'Título' },
]
const STATUS_LABEL: Record<NoteStatus, string> = { ACTIVE: 'Notas', ARCHIVED: 'Arquivadas', TEMPLATE: 'Modelos', TRASH: 'Lixeira' }

export type BoardActions = { newNote: () => void; labels: () => void }

type Props = {
  groupId: string | null
  /** Ações do cabeçalho (página pessoal usa o `PageHeader`; a aba do grupo, uma linha própria). */
  header: (actions: BoardActions) => ReactNode
  /** Filtro vindo de outra tela (ex.: "Notas" de uma tarefa ou compromisso). */
  link?: { taskId: string | null; commitmentId: string | null; onClear: () => void }
}

/** Lista de notas de um dono: abas, busca, pasta, etiqueta, ordem e paginação (tudo pela API). */
export function NotesBoard({ groupId, header, link }: Props) {
  const [status, setStatus] = useState<NoteStatus>('ACTIVE')
  const [search, setSearch] = useState('')
  const q = useDebouncedValue(search.trim(), 300)
  const [folderId, setFolderId] = useState<string | null>(null)
  const [tagId, setTagId] = useState<string | null>(null)
  const [dialog, setDialog] = useState<'new' | 'labels' | null>(null)
  const [menu, setMenu] = useState<{ open: boolean; item?: NoteSummary }>({ open: false })
  const [forever, setForever] = useState<{ open: boolean; item?: NoteSummary }>({ open: false })
  const list = useListState({ field: 'updatedAt', direction: 'desc' }, 25)

  const filters: NoteFilters = {
    status, q, folderId, tagId, taskId: link?.taskId ?? null, commitmentId: link?.commitmentId ?? null,
  }
  const { data, isLoading, isError } = useNotes(groupId, filters, list.params)
  useClampPage(data, list.setPage)
  const labels = useNoteLabels(groupId).data
  const options = useLinkOptions(groupId, !!(link?.taskId || link?.commitmentId)).data
  const folders = useMemo(() => new Map(labels?.folders.map((f) => [f.id, f.name])), [labels])
  const tags = useMemo(() => new Map(labels?.tags.map((t) => [t.id, t])), [labels])

  const pin = usePinNote(groupId)
  const archive = useArchiveNote(groupId)
  const trash = useTrashNote(groupId)
  const restore = useRestoreNote(groupId)
  const remove = useDeleteNoteForever(groupId)

  const linkName = link?.taskId ? options?.tasks.find((t) => t.id === link.taskId)?.title
    : link?.commitmentId ? options?.commitments.find((c) => c.id === link.commitmentId)?.name : undefined
  const filtered = !!(q || folderId || tagId)
  const statuses: NoteStatus[] = groupId ? ['ACTIVE', 'ARCHIVED', 'TRASH'] : ['ACTIVE', 'ARCHIVED', 'TEMPLATE', 'TRASH']

  function change<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value)
      list.resetPage()
    }
  }

  async function act(action: Promise<unknown>, done: string) {
    try {
      await action
      toast.success(done)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível concluir.')
    }
  }

  function actionsFor(note: NoteSummary): SheetAction[] {
    if (note.deletedAt) {
      return note.canDelete ? [
        { label: 'Restaurar', icon: RotateCcwIcon, onSelect: () => void act(restore.mutateAsync(note.id), 'Nota restaurada.') },
        { label: 'Apagar de vez', icon: Trash2Icon, destructive: true, onSelect: () => setForever({ open: true, item: note }) },
      ] : []
    }
    const items: SheetAction[] = [
      { label: note.pinned ? 'Desafixar' : 'Fixar no topo', icon: note.pinned ? PinOffIcon : PinIcon,
        onSelect: () => void act(pin.mutateAsync({ id: note.id, pinned: !note.pinned }), note.pinned ? 'Nota desafixada.' : 'Nota fixada.') },
    ]
    if (status !== 'TEMPLATE') {
      items.push({ label: note.archivedAt ? 'Desarquivar' : 'Arquivar', icon: note.archivedAt ? ArchiveRestoreIcon : ArchiveIcon,
        onSelect: () => void act(archive.mutateAsync({ id: note.id, archived: !note.archivedAt }),
          note.archivedAt ? 'Nota desarquivada.' : 'Nota arquivada.') })
    }
    if (note.canDelete) {
      items.push({ label: 'Mover para a lixeira', icon: Trash2Icon, destructive: true,
        onSelect: () => void act(trash.mutateAsync(note.id), 'Nota na lixeira. Dá para restaurar por 30 dias.') })
    }
    return items
  }

  return (
    <div className="space-y-4">
      {header({ newNote: () => setDialog('new'), labels: () => setDialog('labels') })}

      <Tabs value={status} onValueChange={(value) => change(setStatus)(value as NoteStatus)}>
        <ScrollTabsList value={status}>
          {statuses.map((s) => <TabsTrigger key={s} value={s}>{STATUS_LABEL[s]}</TabsTrigger>)}
        </ScrollTabsList>
      </Tabs>

      <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        <div className="relative sm:col-span-2 xl:col-span-1">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input value={search} onChange={(e) => {
            setSearch(e.target.value)
            list.resetPage()
          }} placeholder="Buscar no título e no texto" aria-label="Buscar notas" className="pl-9 text-base md:text-sm" />
        </div>
        <Select value={folderId ?? ALL} onValueChange={(v) => change(setFolderId)(v === ALL ? null : v)}>
          <SelectTrigger aria-label="Pasta" className="w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todas as pastas</SelectItem>
            {labels?.folders.map((f) => <SelectItem key={f.id} value={f.id}>{f.name}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={tagId ?? ALL} onValueChange={(v) => change(setTagId)(v === ALL ? null : v)}>
          <SelectTrigger aria-label="Etiqueta" className="w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todas as etiquetas</SelectItem>
            {labels?.tags.map((t) => (
              <SelectItem key={t.id} value={t.id}>
                <span className="size-2 rounded-full" style={{ background: t.color }} aria-hidden /> {t.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <SortSelect list={list} options={SORTS} />
      </div>

      {link && (link.taskId || link.commitmentId) && (
        <div className="flex items-center gap-2 rounded-lg bg-data/10 px-3 py-2 text-sm">
          <span className="min-w-0 flex-1 truncate">
            Notas ligadas a {link.taskId ? 'tarefa' : 'compromisso'} <strong>{linkName ?? '…'}</strong>
          </span>
          <Button variant="ghost" size="icon-sm" aria-label="Tirar filtro" onClick={link.onClear}><XIcon /></Button>
        </div>
      )}

      {isError ? <p className="text-sm text-destructive">Não foi possível carregar as notas.</p>
        : isLoading || !data ? (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-36 rounded-xl" />)}
          </div>
        ) : data.items.length === 0 ? (
          <Empty status={status} filtered={filtered} onNew={() => setDialog('new')} />
        ) : (
          <>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {data.items.map((note) => (
                <NoteCard key={note.id} note={note} href={noteUrl(status === 'TEMPLATE' ? null : groupId, note.id)} tags={tags}
                  folder={note.folderId ? folders.get(note.folderId) : undefined} showEditor={!!groupId}
                  onMenu={() => setMenu({ open: true, item: note })} />
              ))}
            </div>
            <DataTablePagination data={data} list={list} />
          </>
        )}

      <NewNoteDialog open={dialog === 'new'} onOpenChange={(open) => !open && setDialog(null)} groupId={groupId}
        defaults={{ folderId, taskId: link?.taskId ?? null, commitmentId: link?.commitmentId ?? null }} />
      <LabelsDialog open={dialog === 'labels'} onOpenChange={(open) => !open && setDialog(null)} groupId={groupId} />
      <ActionSheet open={menu.open} onOpenChange={(open) => setMenu((s) => ({ ...s, open }))} title={menu.item?.title ?? ''}
        actions={menu.item ? actionsFor(menu.item) : []} />
      <ConfirmDelete open={forever.open} onOpenChange={(open) => setForever((s) => ({ ...s, open }))}
        title="Apagar de vez?" description="A nota e as fotos dela somem para sempre. Não dá para desfazer."
        confirmLabel="Apagar de vez"
        onConfirm={() => forever.item && void act(remove.mutateAsync(forever.item.id), 'Nota apagada.')} />
    </div>
  )
}

function Empty({ status, filtered, onNew }: { status: NoteStatus; filtered: boolean; onNew: () => void }) {
  const text = filtered ? 'Nenhuma nota com esses filtros.'
    : status === 'ARCHIVED' ? 'Nada arquivado. Arquive notas que não usa mais para tirar da lista sem excluir.'
      : status === 'TRASH' ? 'Lixeira vazia. Notas excluídas ficam aqui por 30 dias.'
        : status === 'TEMPLATE' ? 'Nenhum modelo ainda. Crie um com a estrutura que você mais usa.'
          : 'Nenhuma nota ainda. Anote ideias, aulas, reuniões e listas.'
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed py-10 text-center">
      <NotebookPenIcon className="size-8 text-muted-foreground" aria-hidden />
      <p className="max-w-sm text-sm text-muted-foreground">{text}</p>
      {(status === 'ACTIVE' || status === 'TEMPLATE') && <Button onClick={onNew}><FilePlus2Icon /> Nova nota</Button>}
    </div>
  )
}
