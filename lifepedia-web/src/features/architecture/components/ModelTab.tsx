import { CheckIcon, MessageSquareIcon, MessageSquarePlusIcon, RotateCcwIcon, Trash2Icon } from 'lucide-react'
import { useCallback, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'
import { UserAvatar } from '@/components/UserAvatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { formatRelative } from '@/lib/format'
import { cn, skipAutoFocusOnTouch } from '@/lib/utils'
import {
  useAddComment,
  useAddLight,
  useAddRoom,
  useAddView,
  useComments,
  useDeleteComment,
  useDeleteLight,
  useDeleteRoom,
  useDeleteVariant,
  useDeleteView,
  useRenameRoom,
  useResolveComment,
  useSaveVariant,
  useScene,
} from '../api'
import { versionName } from '../labels'
import { canAct, type ModelComment, type Point, type ProjectDetail, type Version } from '../types'
import type { ModelViewer } from '../viewer/ModelViewer'
import { type CompareVersion, ModelStage, type SceneActions, type StageControl } from './ModelStage'

type Props = { project: ProjectDetail; version: Version }

/** Aba Maquete: a maquete da versão escolhida com os comentários presos nela. */
export function ModelTab({ project, version }: Props) {
  const { data: comments = [] } = useComments(project.id, version.id)
  const { data: scene } = useScene(project.id, version.id)
  const addView = useAddView()
  const deleteView = useDeleteView()
  const saveVariant = useSaveVariant()
  const deleteVariant = useDeleteVariant()
  const addRoom = useAddRoom()
  const renameRoom = useRenameRoom()
  const deleteRoom = useDeleteRoom()
  const addLight = useAddLight()
  const deleteLight = useDeleteLight()
  const stage = useRef<StageControl>(null)
  const [active, setActive] = useState<string | null>(null)
  const [draft, setDraft] = useState<Point | null>(null)
  const [viewer, setViewer] = useState<ModelViewer | null>(null)
  const canComment = canAct(project.role, 'COMMENT')
  const canEdit = canAct(project.role, 'EDIT')

  const projectId = project.id
  const versionId = version.id
  const actions = useMemo<SceneActions | undefined>(() => (canEdit ? {
    saveVariant: (input) => saveVariant.mutateAsync({ projectId, versionId, ...input }),
    deleteVariant: (id) => deleteVariant.mutateAsync({ projectId, id }),
    addRoom: async (room) => {
      await addRoom.mutateAsync({ projectId, versionId, ...room })
    },
    renameRoom: async (id, name) => {
      await renameRoom.mutateAsync({ projectId, id, name })
    },
    deleteRoom: (id) => deleteRoom.mutateAsync({ projectId, id }),
    addLight: async (point, color, intensity) => {
      await addLight.mutateAsync({ projectId, versionId, point, color, intensity })
    },
    deleteLight: (id) => deleteLight.mutateAsync({ projectId, id }),
  } : undefined), [canEdit, projectId, versionId, saveVariant, deleteVariant, addRoom, renameRoom, deleteRoom, addLight, deleteLight])

  const compareVersions = useMemo<CompareVersion[]>(() => project.versions.filter((v) => v.id !== versionId)
    .map((v) => ({ id: v.id, label: versionName(v), modelUrl: v.modelUrl })), [project.versions, versionId])

  const pins = useMemo(() => comments.map((c, i) => ({
    id: c.id, point: c.point, label: String(i + 1), resolved: c.resolved, active: c.id === active,
  })), [comments, active])

  const focus = useCallback((comment: ModelComment) => {
    setActive(comment.id)
    if (!viewer) return
    const { position } = viewer.view()
    const [x, y, z] = comment.point
    const dx = position[0] - x
    const dy = position[1] - y
    const dz = position[2] - z
    const k = 4 / Math.max(Math.hypot(dx, dy, dz), 0.001)
    viewer.goTo([x + dx * k, y + dy * k, z + dz * k], comment.point)
  }, [viewer])

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_20rem]">
      <ModelStage
        source={version.modelUrl}
        latitude={project.latitude}
        north={project.northDegrees}
        views={project.views}
        onSaveView={canEdit ? async (v) => {
          try {
            await addView.mutateAsync({ projectId: project.id, ...v })
            toast.success('Vista salva.')
          } catch (e) {
            toast.error(e instanceof ApiError ? e.message : 'Não foi possível salvar a vista.')
          }
        } : undefined}
        onDeleteView={canEdit ? (id) => deleteView.mutate({ projectId: project.id, id }) : undefined}
        pins={pins}
        onComment={canComment ? setDraft : undefined}
        onPinClick={(id) => setActive(id)}
        onViewer={setViewer}
        scene={scene}
        actions={actions}
        versionLabel={versionName(version)}
        compareVersions={compareVersions}
        controlRef={stage}
      />
      <CommentsPanel projectId={project.id} comments={comments} active={active} onSelect={focus}
        onNew={canComment ? () => stage.current?.pickTool('comment') : undefined} />
      <NewCommentDialog point={draft} projectId={project.id} versionId={version.id} onClose={() => setDraft(null)} />
    </div>
  )
}

type PanelProps = {
  projectId: string
  comments: ModelComment[]
  active: string | null
  onSelect: (comment: ModelComment) => void
  /** Liga a ferramenta Comentar (só para quem pode comentar) */
  onNew?: () => void
}

function CommentsPanel({ projectId, comments, active, onSelect, onNew }: PanelProps) {
  const resolve = useResolveComment()
  const remove = useDeleteComment()
  const open = comments.filter((c) => !c.resolved).length

  return (
    <Card className="xl:max-h-[65vh] xl:overflow-hidden">
      <CardHeader className="space-y-1">
        <CardTitle className="flex items-center gap-2"><MessageSquareIcon className="size-4" aria-hidden /> Comentários</CardTitle>
        <CardDescription>
          {comments.length === 0 ? (onNew ? 'Toque em Novo comentário e depois no ponto da maquete.' : 'Nenhum comentário nesta versão.')
            : `${open} em aberto de ${comments.length}`}
        </CardDescription>
        {onNew && (
          <Button size="sm" variant="outline" className="w-fit" onClick={onNew}>
            <MessageSquarePlusIcon /> Novo comentário
          </Button>
        )}
      </CardHeader>
      <CardContent className="xl:overflow-y-auto">
        <ol className="space-y-3">
          {comments.map((c, i) => (
            <li key={c.id}>
              <div role="button" tabIndex={0} onClick={() => onSelect(c)}
                onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onSelect(c)}
                className={cn('space-y-2 rounded-xl border p-3 text-sm transition-colors motion-reduce:transition-none hover:bg-muted/60',
                  c.id === active && 'border-data bg-data/5', c.resolved && 'opacity-70')}>
                <div className="flex items-center gap-2">
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-data text-xs font-semibold text-white">
                    {i + 1}
                  </span>
                  <UserAvatar user={c.author} size="xs" />
                  <span className="min-w-0 flex-1 truncate font-medium">{c.author?.name ?? 'Conta excluída'}</span>
                  <span className="shrink-0 text-xs text-muted-foreground">{formatRelative(c.createdAt)}</span>
                </div>
                <p className="whitespace-pre-wrap break-words">{c.body}</p>
                <div className="flex items-center gap-1">
                  {c.resolved && <Badge variant="secondary">Resolvido</Badge>}
                  <span className="flex-1" />
                  {c.canResolve && (
                    <Button size="xs" variant="ghost" onClick={(e) => {
                      e.stopPropagation()
                      resolve.mutate({ projectId, id: c.id, resolved: !c.resolved })
                    }}>
                      {c.resolved ? <><RotateCcwIcon /> Reabrir</> : <><CheckIcon /> Resolver</>}
                    </Button>
                  )}
                  {c.canDelete && (
                    <Button size="icon-xs" variant="ghost" aria-label="Excluir comentário" onClick={(e) => {
                      e.stopPropagation()
                      remove.mutate({ projectId, id: c.id })
                    }}><Trash2Icon /></Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  )
}

type DialogProps = { point: Point | null; projectId: string; versionId: string; onClose: () => void }

function NewCommentDialog({ point, projectId, versionId, onClose }: DialogProps) {
  return (
    <Dialog open={point != null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        {point && <CommentForm point={point} projectId={projectId} versionId={versionId} onClose={onClose} />}
      </DialogContent>
    </Dialog>
  )
}

function CommentForm({ point, projectId, versionId, onClose }: DialogProps & { point: Point }) {
  const add = useAddComment()
  const [body, setBody] = useState('')

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!body.trim()) return
    try {
      await add.mutateAsync({ projectId, versionId, body: body.trim(), point })
      toast.success('Comentário adicionado.')
      onClose()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível comentar.')
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <DialogHeader>
        <DialogTitle>Comentar neste ponto</DialogTitle>
        <DialogDescription>O comentário fica preso no ponto que você tocou, nesta versão.</DialogDescription>
      </DialogHeader>
      <Textarea autoFocus value={body} maxLength={1000} rows={4} placeholder="Ex.: Trocar o piso desta área"
        className="text-base md:text-sm" onChange={(e) => setBody(e.target.value)} aria-label="Comentário" />
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
        <Button type="submit" disabled={add.isPending || !body.trim()}>Comentar</Button>
      </DialogFooter>
    </form>
  )
}
