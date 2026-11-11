import { FileTextIcon, ImagePlusIcon, Loader2Icon, PaperclipIcon, Trash2Icon } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatBytes, formatInstantDate } from '@/lib/format'
import { shrinkPhoto } from '@/lib/images'
import { useAddAttachment, useAttachments, useDeleteAttachment } from '../api'
import { canAct, type Attachment, type ProjectDetail } from '../types'

const IMAGE_SIDE = 2048
const IMAGE_BYTES = 1024 * 1024
const PDF_BYTES = 15 * 1024 * 1024

/** Renders, fotos da obra e plantas em PDF do projeto. */
export function AttachmentsTab({ project }: { project: ProjectDetail }) {
  const { data } = useAttachments(project.id)
  const add = useAddAttachment()
  const remove = useDeleteAttachment()
  const inputRef = useRef<HTMLInputElement>(null)
  const [sending, setSending] = useState(0)
  const [viewing, setViewing] = useState<Attachment | null>(null)
  const [deleting, setDeleting] = useState<Attachment | null>(null)
  const canEdit = canAct(project.role, 'EDIT')

  async function upload(files: File[]) {
    setSending(files.length)
    for (const file of files) {
      try {
        const pdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
        if (pdf && file.size > PDF_BYTES) throw new Error(`${file.name}: o PDF deve ter até 15 MB.`)
        const body = pdf ? file : await shrinkPhoto(file, IMAGE_SIDE, IMAGE_BYTES)
        const name = pdf ? file.name : file.name.replace(/\.[^.]+$/, '') + '.jpg'
        await add.mutateAsync({ projectId: project.id, file: body, name })
      } catch (e) {
        toast.error(e instanceof ApiError || e instanceof Error ? e.message : `Não foi possível enviar ${file.name}.`)
      }
      setSending((n) => n - 1)
    }
  }

  if (!data) return <Skeleton className="h-48" />

  return (
    <div className="space-y-4">
      {canEdit && (
        <div className="flex items-center gap-3">
          <Button onClick={() => inputRef.current?.click()} disabled={sending > 0}>
            {sending > 0 ? <Loader2Icon className="animate-spin motion-reduce:animate-none" /> : <ImagePlusIcon />}
            {sending > 0 ? `Enviando ${sending}…` : 'Adicionar anexos'}
          </Button>
          <p className="text-xs text-muted-foreground">Fotos e renders (reduzidos para 2048 px) e plantas em PDF de até 15 MB.</p>
          <input ref={inputRef} type="file" multiple accept="image/jpeg,image/png,image/webp,application/pdf" className="hidden"
            onChange={(e) => {
              void upload([...(e.target.files ?? [])])
              e.target.value = ''
            }} />
        </div>
      )}

      {data.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center text-sm text-muted-foreground">
            <PaperclipIcon className="size-6" aria-hidden />
            <p>Nenhum anexo ainda.{canEdit && ' Adicione renders, plantas em PDF ou fotos da obra.'}</p>
            {canEdit && <Button variant="outline" onClick={() => inputRef.current?.click()}>Adicionar anexos</Button>}
          </CardContent>
        </Card>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {data.map((a) => (
            <li key={a.id}>
              <Card className="overflow-hidden py-0">
                {a.kind === 'IMAGE' ? (
                  <button type="button" className="block aspect-video w-full bg-muted" onClick={() => setViewing(a)}
                    aria-label={`Abrir ${a.name}`}>
                    <img src={a.fileUrl} alt="" loading="lazy" className="size-full object-cover" />
                  </button>
                ) : (
                  <a href={a.fileUrl} target="_blank" rel="noopener noreferrer"
                    className="flex aspect-video w-full flex-col items-center justify-center gap-2 bg-muted text-muted-foreground">
                    <FileTextIcon className="size-8" aria-hidden /> <span className="text-xs">Abrir PDF</span>
                  </a>
                )}
                <CardContent className="flex items-center gap-2 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium" title={a.name}>{a.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatBytes(a.sizeBytes)} · {formatInstantDate(a.createdAt)}{a.uploadedBy ? ` · ${a.uploadedBy.name}` : ''}
                    </p>
                  </div>
                  {a.canDelete && (
                    <Button variant="ghost" size="icon-sm" aria-label={`Excluir ${a.name}`} onClick={() => setDeleting(a)}>
                      <Trash2Icon />
                    </Button>
                  )}
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={!!viewing} onOpenChange={(open) => !open && setViewing(null)}>
        <DialogContent className="sm:max-w-5xl">
          <DialogTitle className="truncate pr-8">{viewing?.name}</DialogTitle>
          {viewing && <img src={viewing.fileUrl} alt={viewing.name} className="max-h-[75vh] w-full rounded-lg object-contain" />}
        </DialogContent>
      </Dialog>
      <ConfirmDelete open={!!deleting} onOpenChange={(open) => !open && setDeleting(null)}
        title="Excluir este anexo?" description="O arquivo some para todos que veem o projeto."
        onConfirm={() => deleting && remove.mutate({ projectId: project.id, id: deleting.id })} />
    </div>
  )
}
