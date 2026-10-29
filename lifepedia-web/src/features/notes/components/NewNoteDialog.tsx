import { FilePlus2Icon, LayoutTemplateIcon, Loader2Icon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError } from '@/lib/api'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useCreateNote, useFetchNote, useNotes } from '../api'
import { noteUrl } from '../paths'
import { BUILT_IN_TEMPLATES } from '../templates'
import type { NoteRequest } from '../types'

/** Ligações que a nota nova já leva (ex.: filtro de pasta ou a tarefa de onde veio). */
export type NoteDefaults = Partial<Pick<NoteRequest, 'folderId' | 'taskId' | 'commitmentId' | 'title'>>

type Props = { open: boolean; onOpenChange: (open: boolean) => void; groupId: string | null; defaults?: NoteDefaults }

const TEMPLATE_FILTERS = { status: 'TEMPLATE', q: '', folderId: null, tagId: null, taskId: null, commitmentId: null } as const

/** Começa em branco, de um modelo pronto ou de um modelo seu; cria e abre a nota. */
export function NewNoteDialog({ open, onOpenChange, groupId, defaults }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>Nova nota</DialogTitle>
          <DialogDescription>Comece do zero ou de um modelo.</DialogDescription>
        </DialogHeader>
        <Choices groupId={groupId} defaults={defaults} onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  )
}

function Choices({ groupId, defaults, onDone }: { groupId: string | null; defaults?: NoteDefaults; onDone: () => void }) {
  const navigate = useNavigate()
  const create = useCreateNote(groupId)
  const createPersonal = useCreateNote(null)
  const fetchTemplate = useFetchNote(null)
  const templates = useNotes(null, TEMPLATE_FILTERS, { page: 0, size: 50, sort: 'title,asc' })
  const [busy, setBusy] = useState<string | null>(null)

  async function start(key: string, title: string, content: string, icon: string | null, template = false) {
    setBusy(key)
    try {
      const data: NoteRequest = {
        title: defaults?.title ?? title, content, icon, color: null, tagIds: [],
        folderId: template ? null : defaults?.folderId ?? null,
        taskId: template ? null : defaults?.taskId ?? null,
        commitmentId: template ? null : defaults?.commitmentId ?? null,
        template,
      }
      const note = template ? await createPersonal.mutateAsync(data) : await create.mutateAsync(data)
      onDone()
      navigate(noteUrl(template ? null : groupId, note.id))
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível criar a nota.')
    } finally {
      setBusy(null)
    }
  }

  /** O conteúdo do modelo vem da API (a lista só traz o começo do texto). */
  async function fromTemplate(id: string) {
    setBusy(id)
    try {
      const template = await fetchTemplate(id, true)
      await start(id, template.title, template.content, template.icon)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível abrir o modelo.')
      setBusy(null)
    }
  }

  const own = templates.data?.items ?? []
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        <Choice label="Em branco" icon={<FilePlus2Icon className="size-5" />} busy={busy === 'blank'} disabled={!!busy}
          onClick={() => void start('blank', 'Sem título', '', null)} />
        {BUILT_IN_TEMPLATES.map((t) => (
          <Choice key={t.id} label={t.name} icon={<span className="text-lg">{t.icon}</span>} busy={busy === t.id}
            disabled={!!busy} onClick={() => void start(t.id, t.title(), t.content(), t.icon)} />
        ))}
      </div>
      <div className="space-y-2">
        <p className="text-sm font-medium">Seus modelos</p>
        {own.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Nenhum ainda. Crie um modelo com a estrutura que você mais usa e ele aparece aqui.
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {own.map((t) => (
              <Choice key={t.id} label={t.title} icon={<span className="text-lg">{t.icon ?? '📄'}</span>}
                busy={busy === t.id} disabled={!!busy} onClick={() => void fromTemplate(t.id)} />
            ))}
          </div>
        )}
        <button type="button" disabled={!!busy} onClick={() => void start('new-template', 'Novo modelo', '', null, true)}
          className="flex items-center gap-1.5 text-sm font-medium text-data underline-offset-2 hover:underline disabled:opacity-50">
          <LayoutTemplateIcon className="size-4" aria-hidden /> Criar um modelo
        </button>
      </div>
    </div>
  )
}

function Choice({ label, icon, busy, disabled, onClick }: {
  label: string
  icon: ReactNode
  busy: boolean
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button type="button" disabled={disabled} onClick={onClick}
      className="flex min-h-20 flex-col items-center justify-center gap-1.5 rounded-xl border p-3 text-center text-sm transition-colors hover:bg-accent disabled:opacity-60 motion-reduce:transition-none">
      {busy ? <Loader2Icon className="size-5 animate-spin" aria-hidden /> : <span aria-hidden>{icon}</span>}
      <span className="line-clamp-2">{label}</span>
    </button>
  )
}
