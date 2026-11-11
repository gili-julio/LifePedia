import { FileUpIcon, Loader2Icon, TriangleAlertIcon } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { formatBytes } from '@/lib/format'
import { useUploadCover, useUploadVersion } from '../api'
import {
  ACCEPT,
  ConvertError,
  type ConvertedModel,
  type LoadedModel,
  sizeIn,
  STAGE_LABEL,
  type Stage,
  type Unit,
  UNITS,
} from '../convert/options'
import { filesFromDrop } from '../convert/dropped-files'
import { meters } from '../labels'
import type { ProjectDetail } from '../types'
import type { ModelViewer } from '../viewer/ModelViewer'
import { ModelStage } from './ModelStage'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  project: Pick<ProjectDetail, 'id' | 'latitude' | 'northDegrees' | 'usage'>
}

/** Nova versão: escolher a exportação do SketchUp, ajustar, comprimir no navegador, revisar e enviar. */
export function NewVersionDialog(props: Props) {
  const [busy, setBusy] = useState(false)
  return (
    <Dialog open={props.open} onOpenChange={(open) => !busy && props.onOpenChange(open)}>
      <DialogScrollContent className="sm:max-w-3xl" showCloseButton={!busy}>
        <NewVersionForm {...props} onBusy={setBusy} />
      </DialogScrollContent>
    </Dialog>
  )
}

type Step =
  | { kind: 'pick' }
  | { kind: 'working'; stage: Stage }
  | { kind: 'adjust'; model: LoadedModel }
  | { kind: 'preview'; model: LoadedModel; result: ConvertedModel }
  | { kind: 'uploading'; progress: number | null }

/** Montado só com o dialog aberto: recomeça a cada abertura. */
function NewVersionForm({ onOpenChange, project, onBusy }: Props & { onBusy: (busy: boolean) => void }) {
  const upload = useUploadVersion()
  const uploadCover = useUploadCover()
  const inputRef = useRef<HTMLInputElement>(null)
  const folderRef = useRef<HTMLInputElement>(null)
  const addRef = useRef<HTMLInputElement>(null)
  const addFolderRef = useRef<HTMLInputElement>(null)
  const [files, setFiles] = useState<File[]>([])
  const viewerRef = useRef<ModelViewer | null>(null)
  const [step, setStep] = useState<Step>({ kind: 'pick' })
  const [unit, setUnit] = useState<Unit>('m')
  const [upright, setUpright] = useState(false)
  const [label, setLabel] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const loaded = step.kind === 'adjust' || step.kind === 'preview' ? step.model : null

  /** Lê o que foi escolhido; com {@code extra}, soma aos arquivos já escolhidos (texturas que faltaram). */
  async function read(chosen: File[], extra = false) {
    if (chosen.length === 0) return
    const all = extra ? [...files, ...chosen] : chosen
    setFiles(all)
    setError(null)
    onBusy(true)
    try {
      const { loadModel } = await import('../convert/convert')
      const model = await loadModel(all, (stage) => setStep({ kind: 'working', stage }))
      setUnit(model.unit)
      setUpright(false)
      setStep({ kind: 'adjust', model })
    } catch (e) {
      setError(e instanceof ConvertError ? e.message : 'Não foi possível ler os arquivos.')
      setStep({ kind: 'pick' })
    } finally {
      onBusy(false)
    }
  }

  async function convert(model: LoadedModel) {
    setError(null)
    onBusy(true)
    try {
      const { convertModel } = await import('../convert/convert')
      const result = await convertModel(model, unit, upright, (stage) => setStep({ kind: 'working', stage }))
      setStep({ kind: 'preview', model, result })
    } catch {
      setError('Não foi possível comprimir a maquete. Tente exportar de novo do SketchUp.')
      setStep({ kind: 'adjust', model })
    } finally {
      onBusy(false)
    }
  }

  async function send(model: LoadedModel, result: ConvertedModel) {
    setError(null)
    onBusy(true)
    const cover = await viewerRef.current?.snapshot().catch(() => null)
    setStep({ kind: 'uploading', progress: 0 })
    try {
      await upload.mutateAsync({
        projectId: project.id,
        model: result.glb,
        label: label.trim() || null,
        note: note.trim() || null,
        sourceName: model.sourceName,
        sourceBytes: model.sourceBytes,
        onProgress: (progress) => setStep({ kind: 'uploading', progress }),
      })
      if (cover) await uploadCover.mutateAsync({ id: project.id, image: cover }).catch(() => undefined)
      toast.success('Versão enviada.')
      onBusy(false)
      onOpenChange(false)
    } catch (e) {
      onBusy(false)
      setError(e instanceof ApiError ? e.message : 'Não foi possível enviar a maquete.')
      setStep({ kind: 'preview', model, result })
    }
  }

  const usage = project.usage
  const room = usage ? usage.quotaBytes - usage.usedBytes : null
  const dims = loaded ? sizeIn(loaded, unit, upright) : null
  const tooBig = step.kind === 'preview' && usage != null
    && (step.result.sizeBytes > usage.maxModelBytes || (room != null && step.result.sizeBytes > room))

  return (
    <>
      <DialogHeader>
        <DialogTitle>Nova versão da maquete</DialogTitle>
        <DialogDescription>
          No SketchUp, use Arquivo › Exportar › Modelo 3D em COLLADA (.dae) ou glTF (.glb). O arquivo é comprimido aqui
          no navegador antes de subir.
        </DialogDescription>
      </DialogHeader>
      <DialogBody>
        {step.kind === 'pick' && (
          <div
            className={`flex flex-col items-center gap-3 rounded-2xl border-2 border-dashed p-8 text-center transition-colors motion-reduce:transition-none ${dragging ? 'border-data bg-data/5' : ''}`}
            onDragOver={(e) => {
              e.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
              e.preventDefault()
              setDragging(false)
              void filesFromDrop(e.dataTransfer).then((dropped) => read(dropped))
            }}>
            <FileUpIcon className="size-8 text-muted-foreground" aria-hidden />
            <p className="text-sm">
              Arraste aqui o .dae <strong>e a pasta das texturas</strong> que o SketchUp cria ao lado dele (mesmo nome do
              arquivo), ou um .zip com tudo. Sem as texturas, madeira, pisos e tecidos ficam brancos.
            </p>
            <div className="flex flex-wrap justify-center gap-2">
              <Button type="button" onClick={() => inputRef.current?.click()}>Escolher arquivos</Button>
              <Button type="button" variant="outline" onClick={() => folderRef.current?.click()}>Escolher pasta</Button>
            </div>
            <input ref={inputRef} type="file" multiple accept={ACCEPT} className="hidden"
              onChange={(e) => void read([...(e.target.files ?? [])])} />
            <FolderInput inputRef={folderRef} onFiles={(chosen) => void read(chosen)} />
            <p className="text-xs text-muted-foreground">Aceita .dae, .glb, .gltf, .fbx e .obj (com .mtl). O .skp não abre fora do SketchUp.</p>
          </div>
        )}

        {step.kind === 'working' && <Working text={STAGE_LABEL[step.stage]} />}

        {step.kind === 'adjust' && dims && (
          <div className="space-y-4">
            <p className="text-sm">
              <span className="font-medium">{step.model.sourceName}</span> · {formatBytes(step.model.sourceBytes)} ·{' '}
              {step.model.triangles.toLocaleString('pt-BR')} triângulos
            </p>
            {step.model.missing.length > 0 && (
              <div className="space-y-2 rounded-xl border border-amber-500/40 bg-amber-500/10 p-3 text-sm" role="alert">
                <p className="flex items-center gap-2 font-medium">
                  <TriangleAlertIcon className="size-4 shrink-0" aria-hidden />
                  {step.model.missing.length === 1 ? '1 textura não veio' : `${step.model.missing.length} texturas não vieram`}:
                  essas partes vão ficar brancas.
                </p>
                <p className="break-words text-xs text-muted-foreground">
                  {step.model.missing.slice(0, 8).join(', ')}{step.model.missing.length > 8 ? ` e mais ${step.model.missing.length - 8}` : ''}
                </p>
                <p className="text-xs text-muted-foreground">
                  Adicione a pasta que o SketchUp criou ao lado do .dae (ou os arquivos de imagem). Imagens .tif e .tga o
                  navegador não lê: no SketchUp, troque-as por .jpg ou .png.
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" onClick={() => addFolderRef.current?.click()}>Adicionar pasta</Button>
                  <Button size="sm" variant="outline" onClick={() => addRef.current?.click()}>Adicionar arquivos</Button>
                </div>
                <input ref={addRef} type="file" multiple accept="image/*,.zip,.mtl,.bin" className="hidden"
                  onChange={(e) => void read([...(e.target.files ?? [])], true)} />
                <FolderInput inputRef={addFolderRef} onFiles={(chosen) => void read(chosen, true)} />
              </div>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="unit">Unidade do arquivo</FieldLabel>
                <Select value={unit} onValueChange={(v) => setUnit(v as Unit)}>
                  <SelectTrigger id="unit" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {UNITS.map((u) => <SelectItem key={u.value} value={u.value}>{u.label}</SelectItem>)}
                  </SelectContent>
                </Select>
                <FieldDescription>
                  Tamanho: {meters(dims[0])} × {meters(dims[2])}, altura {meters(dims[1])}. Não bate com a casa? Troque a unidade.
                </FieldDescription>
              </Field>
              <div className="flex items-start justify-between gap-3 rounded-xl border p-3">
                <div>
                  <Label htmlFor="upright">Maquete deitada</Label>
                  <p className="text-xs text-muted-foreground">Ligue se a casa aparecer de lado na prévia.</p>
                </div>
                <Switch id="upright" checked={upright} onCheckedChange={setUpright} />
              </div>
            </div>
            <VersionFields label={label} note={note} onLabel={setLabel} onNote={setNote} />
          </div>
        )}

        {step.kind === 'preview' && (
          <div className="space-y-3">
            <ModelStage source={step.result.glb} latitude={project.latitude} north={project.northDegrees} className="h-72 min-h-0"
              onViewer={(v) => {
                viewerRef.current = v
              }} />
            <p className="text-sm">
              {formatBytes(step.model.sourceBytes)} → <span className="font-semibold">{formatBytes(step.result.sizeBytes)}</span>
              {step.model.sourceBytes > step.result.sizeBytes && (
                <> ({Math.round((1 - step.result.sizeBytes / step.model.sourceBytes) * 100)}% menor)</>
              )}
              {usage && <span className="text-muted-foreground"> · espaço livre: {formatBytes(Math.max(room ?? 0, 0))}</span>}
            </p>
            {tooBig && (
              <p className="text-sm text-destructive">
                Não cabe: o limite é {formatBytes(usage!.maxModelBytes)} por maquete e o espaço livre é {formatBytes(Math.max(room ?? 0, 0))}.
                Exclua versões antigas ou exporte com menos detalhes.
              </p>
            )}
            <VersionFields label={label} note={note} onLabel={setLabel} onNote={setNote} />
          </div>
        )}

        {step.kind === 'uploading' && (
          <Working text={`Enviando${step.progress != null ? ` (${Math.round(step.progress * 100)}%)` : '…'}`} progress={step.progress} />
        )}

        {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
      </DialogBody>
      <DialogFooter>
        {step.kind === 'adjust' && (
          <>
            <Button variant="outline" onClick={() => setStep({ kind: 'pick' })}>Outro arquivo</Button>
            <Button onClick={() => void convert(step.model)}>Comprimir e ver prévia</Button>
          </>
        )}
        {step.kind === 'preview' && (
          <>
            <Button variant="outline" onClick={() => setStep({ kind: 'adjust', model: step.model })}>Voltar</Button>
            <Button disabled={tooBig} onClick={() => void send(step.model, step.result)}>Enviar versão</Button>
          </>
        )}
      </DialogFooter>
    </>
  )
}

/** Seletor de pasta inteira (o atributo não existe no tipo do React). */
function FolderInput({ inputRef, onFiles }: { inputRef: React.RefObject<HTMLInputElement | null>; onFiles: (files: File[]) => void }) {
  return (
    <input ref={(el) => {
      inputRef.current = el
      el?.setAttribute('webkitdirectory', '')
    }} type="file" multiple className="hidden" onChange={(e) => onFiles([...(e.target.files ?? [])])} />
  )
}

function Working({ text, progress }: { text: string; progress?: number | null }) {
  return (
    <div className="flex flex-col items-center gap-3 py-10 text-sm text-muted-foreground" role="status">
      <Loader2Icon className="size-6 animate-spin motion-reduce:animate-none" aria-hidden />
      <p>{text}</p>
      {progress != null && (
        <div className="h-1.5 w-56 overflow-hidden rounded-full bg-muted">
          <div className="h-full bg-data transition-[width] motion-reduce:transition-none" style={{ width: `${progress * 100}%` }} />
        </div>
      )}
      <p className="text-xs">Não feche esta janela.</p>
    </div>
  )
}

type FieldsProps = { label: string; note: string; onLabel: (v: string) => void; onNote: (v: string) => void }

function VersionFields({ label, note, onLabel, onNote }: FieldsProps) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field>
        <FieldLabel htmlFor="version-label">Nome da versão (opcional)</FieldLabel>
        <Input id="version-label" value={label} maxLength={120} placeholder="Ex.: Cozinha aberta" className="text-base md:text-sm"
          onChange={(e) => onLabel(e.target.value)} />
      </Field>
      <Field>
        <FieldLabel htmlFor="version-note">O que mudou (opcional)</FieldLabel>
        <Textarea id="version-note" value={note} maxLength={1000} rows={2} className="text-base md:text-sm"
          onChange={(e) => onNote(e.target.value)} />
      </Field>
    </div>
  )
}
