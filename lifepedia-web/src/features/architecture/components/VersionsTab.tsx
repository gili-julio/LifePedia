import { EyeIcon, PencilIcon, StarIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { UserAvatar } from '@/components/UserAvatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { formatBytes, formatInstantDate } from '@/lib/format'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useDeleteVersion, useSetCurrentVersion, useUpdateVersion } from '../api'
import { versionName } from '../labels'
import { canAct, type ProjectDetail, type Version } from '../types'
import { UsageBar } from './UsageBar'

type Props = { project: ProjectDetail; onOpen: (versionId: string) => void }

/** Histórico de versões (mais nova primeiro, da API): ver, tornar atual, renomear e excluir. */
export function VersionsTab({ project, onOpen }: Props) {
  const setCurrent = useSetCurrentVersion()
  const remove = useDeleteVersion()
  const [editing, setEditing] = useState<Version | null>(null)
  const [deleting, setDeleting] = useState<Version | null>(null)
  const canEdit = canAct(project.role, 'EDIT')

  return (
    <div className="space-y-4">
      {project.usage && <Card><CardContent><UsageBar usage={project.usage} label="Espaço do dono do projeto" /></CardContent></Card>}
      <ul className="space-y-3">
        {project.versions.map((v) => (
          <li key={v.id}>
            <Card>
              <CardContent className="space-y-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="min-w-0 flex-1 truncate font-medium">{versionName(v)}</span>
                  {v.id === project.currentVersionId && <Badge>Atual</Badge>}
                </div>
                {v.note && <p className="whitespace-pre-wrap text-sm">{v.note}</p>}
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1.5"><UserAvatar user={v.createdBy} size="xs" />{v.createdBy?.name ?? 'Conta excluída'}</span>
                  <span>{formatInstantDate(v.createdAt)}</span>
                  <span className="tabular-nums">{formatBytes(v.sizeBytes)}</span>
                  {v.sourceName && (
                    <span className="min-w-0 truncate">de {v.sourceName}{v.sourceBytes ? ` (${formatBytes(v.sourceBytes)})` : ''}</span>
                  )}
                </div>
                <div className="flex flex-wrap gap-1">
                  <Button size="sm" variant="outline" onClick={() => onOpen(v.id)}><EyeIcon /> Ver</Button>
                  {canEdit && v.id !== project.currentVersionId && (
                    <Button size="sm" variant="ghost" onClick={() => setCurrent.mutate({ projectId: project.id, versionId: v.id },
                      { onSuccess: () => toast.success('Versão atual trocada.') })}>
                      <StarIcon /> Tornar atual
                    </Button>
                  )}
                  {canEdit && <Button size="sm" variant="ghost" onClick={() => setEditing(v)}><PencilIcon /> Renomear</Button>}
                  {canEdit && (
                    <Button size="sm" variant="ghost" className="text-destructive" onClick={() => setDeleting(v)}>
                      <Trash2Icon /> Excluir
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>

      <Dialog open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
          {editing && <RenameForm version={editing} onClose={() => setEditing(null)} />}
        </DialogContent>
      </Dialog>
      <ConfirmDelete open={!!deleting} onOpenChange={(open) => !open && setDeleting(null)}
        title="Excluir esta versão?"
        description="A maquete e os comentários dela somem para sempre. As outras versões continuam."
        onConfirm={() => deleting && remove.mutate(deleting.id, {
          onSuccess: () => toast.success('Versão excluída.'),
          onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Não foi possível excluir.'),
        })} />
    </div>
  )
}

function RenameForm({ version, onClose }: { version: Version; onClose: () => void }) {
  const update = useUpdateVersion()
  const [label, setLabel] = useState(version.label ?? '')
  const [note, setNote] = useState(version.note ?? '')

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await update.mutateAsync({ id: version.id, label: label.trim() || null, note: note.trim() || null })
      onClose()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível salvar.')
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <DialogHeader><DialogTitle>Versão {version.number}</DialogTitle></DialogHeader>
      <Field>
        <FieldLabel htmlFor="rename-label">Nome</FieldLabel>
        <Input id="rename-label" value={label} maxLength={120} className="text-base md:text-sm" onChange={(e) => setLabel(e.target.value)} />
      </Field>
      <Field>
        <FieldLabel htmlFor="rename-note">O que mudou</FieldLabel>
        <Textarea id="rename-note" value={note} maxLength={1000} rows={3} className="text-base md:text-sm"
          onChange={(e) => setNote(e.target.value)} />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
        <Button type="submit" disabled={update.isPending}>Salvar</Button>
      </DialogFooter>
    </form>
  )
}
