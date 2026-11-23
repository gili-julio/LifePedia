import { FileTextIcon, Loader2Icon, SparklesIcon } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError } from '@/lib/api'
import { formatBytes } from '@/lib/format'
import { useImportCourses, usePreviewImport, useReadWithAi } from '../api'
import { CATEGORY_LABEL, formatGrade, STATUS_LABEL } from '../labels'
import type { Enrollment, HistoryRead } from '../types'

const AI_TYPES = ['application/pdf', 'image/png', 'image/jpeg', 'image/webp']
const AI_MAX_FILES = 3
const AI_MAX_BYTES = 15 * 1024 * 1024

type Props = { open: boolean; onOpenChange: (open: boolean) => void; enrollment: Enrollment; aiEnabled: boolean }

/** Importa o histórico: PDF do SIGAA lido no navegador ou PDF/foto de qualquer instituição lido pela IA; sempre com prévia. */
export function ImportDialog({ open, onOpenChange, enrollment, aiEnabled }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-2xl">
        {open && <Import enrollment={enrollment} aiEnabled={aiEnabled} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function Import({ enrollment, aiEnabled, onDone }: { enrollment: Enrollment; aiEnabled: boolean; onDone: () => void }) {
  const [read, setRead] = useState<HistoryRead | null>(null)
  if (read) return <Review enrollment={enrollment} read={read} onBack={() => setRead(null)} onDone={onDone} />
  return <Pick aiEnabled={aiEnabled} onRead={setRead} onCancel={onDone} />
}

function Pick({ aiEnabled, onRead, onCancel }: { aiEnabled: boolean; onRead: (read: HistoryRead) => void; onCancel: () => void }) {
  const pdfInput = useRef<HTMLInputElement>(null)
  const aiInput = useRef<HTMLInputElement>(null)
  const ai = useReadWithAi()
  const [reading, setReading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const busy = reading || ai.isPending

  async function readPdf(file: File | undefined) {
    if (!file) return
    setReading(true)
    setError(null)
    try {
      const [{ pdfLines }, { parseSigaaHistory }] = await Promise.all([import('../import/pdf-text'), import('../import/sigaa-history')])
      onRead(parseSigaaHistory(await pdfLines(file)))
    } catch (e) {
      setError(e instanceof Error && e.message ? e.message : 'Não foi possível ler o PDF.')
    } finally {
      setReading(false)
    }
  }

  async function readAi(list: FileList | null) {
    const files = Array.from(list ?? [])
    if (!files.length) return
    setError(null)
    if (files.length > AI_MAX_FILES) return setError(`Até ${AI_MAX_FILES} arquivos por vez.`)
    if (files.some((f) => !AI_TYPES.includes(f.type))) return setError('Use PDF ou foto (PNG, JPG ou WebP).')
    if (files.reduce((sum, f) => sum + f.size, 0) > AI_MAX_BYTES) return setError(`Os arquivos somados passam de ${formatBytes(AI_MAX_BYTES)}.`)
    try {
      onRead(await ai.mutateAsync(files))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Não foi possível ler os arquivos.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Importar histórico</DialogTitle>
        <DialogDescription>Matérias que já existem (mesmo semestre e código) são atualizadas; nada é apagado.</DialogDescription>
      </DialogHeader>
      <DialogBody>
        <div className="grid gap-3 sm:grid-cols-2">
          <button type="button" disabled={busy} onClick={() => pdfInput.current?.click()}
            className="flex flex-col gap-2 rounded-2xl border p-4 text-left transition-colors hover:bg-muted/50 disabled:opacity-60 motion-reduce:transition-none">
            <FileTextIcon className="size-5 text-data" aria-hidden />
            <span className="font-medium">PDF do SIGAA</span>
            <span className="text-sm text-muted-foreground">
              Ensino &gt; Emitir Histórico. O arquivo é lido no seu navegador e não sai do aparelho.
            </span>
          </button>
          <button type="button" disabled={busy || !aiEnabled} onClick={() => aiInput.current?.click()}
            className="flex flex-col gap-2 rounded-2xl border p-4 text-left transition-colors hover:bg-muted/50 disabled:opacity-60 motion-reduce:transition-none">
            <SparklesIcon className="size-5 text-data" aria-hidden />
            <span className="font-medium">Outra instituição (IA)</span>
            <span className="text-sm text-muted-foreground">
              {aiEnabled ? 'PDF ou foto do histórico ou boletim, até 3 arquivos. O arquivo vai para a IA do Google ler; nada fica guardado.'
                : 'A leitura por IA não está ligada neste servidor.'}
            </span>
          </button>
        </div>
        <input ref={pdfInput} type="file" accept="application/pdf" className="hidden"
          onChange={(e) => { void readPdf(e.target.files?.[0]); e.target.value = '' }} />
        <input ref={aiInput} type="file" multiple accept={AI_TYPES.join(',')} className="hidden"
          onChange={(e) => { void readAi(e.target.files); e.target.value = '' }} />
        {busy && (
          <p className="flex items-center gap-2 text-sm text-muted-foreground" aria-live="polite">
            <Loader2Icon className="size-4 animate-spin" aria-hidden /> {ai.isPending ? 'A IA está lendo (pode levar um minuto)...' : 'Lendo o PDF...'}
          </p>
        )}
        {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onCancel}>Cancelar</Button>
      </DialogFooter>
    </>
  )
}

function Review({ enrollment, read, onBack, onDone }: { enrollment: Enrollment; read: HistoryRead; onBack: () => void; onDone: () => void }) {
  const save = useImportCourses()
  const listId = useId()
  const [skipped, setSkipped] = useState<ReadonlySet<number>>(new Set())
  const [error, setError] = useState<string | null>(null)
  const chosen = read.courses.filter((_, i) => !skipped.has(i))
  const required = read.required && Object.values(read.required).some((v) => v != null) ? read.required : null
  const preview = usePreviewImport({ enrollmentId: enrollment.id, courses: chosen, required })

  function toggle(index: number) {
    setSkipped((set) => {
      const next = new Set(set)
      if (next.has(index)) next.delete(index)
      else next.add(index)
      return next
    })
  }

  async function submit() {
    try {
      const result = await save.mutateAsync({ enrollmentId: enrollment.id, courses: chosen, required })
      toast.success(`Histórico importado: ${result.created} nova(s), ${result.updated} atualizada(s).`)
      onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Não foi possível importar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Conferir antes de importar</DialogTitle>
        <DialogDescription>
          {read.course ? `${read.course}${read.institution ? `, ${read.institution}` : ''}. ` : ''}
          {read.courses.length} matéria(s) lida(s) para {enrollment.course}. Desmarque o que não quiser.
        </DialogDescription>
      </DialogHeader>
      <DialogBody>
        {required && (
          <p className="text-sm text-muted-foreground">
            Carga exigida: {required.mandatory ?? '-'} h obrigatórias, {required.optional ?? '-'} h optativas,{' '}
            {required.complementary ?? '-'} h complementares (vai para o vínculo).
          </p>
        )}
        <ul aria-label="Matérias lidas" className="divide-y rounded-xl border">
          {read.courses.map((c, i) => (
            <li key={i} className="flex items-start gap-3 p-3 text-sm">
              <Checkbox checked={!skipped.has(i)} onCheckedChange={() => toggle(i)} aria-label={`Importar ${c.name}`} className="mt-0.5" />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{c.code ? `${c.code} - ` : ''}{c.name}</p>
                <p className="text-xs text-muted-foreground">
                  {c.period ?? 'Aproveitada'} · {CATEGORY_LABEL[c.category]}{c.hours ? ` · ${c.hours} h` : ''}
                  {c.teacher ? ` · ${c.teacher}` : ''}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-xs font-medium">{STATUS_LABEL[c.status]}</p>
                {(c.average != null || c.concept) && (
                  <p className="text-xs tabular-nums text-muted-foreground">{c.concept ?? formatGrade(c.average)}</p>
                )}
              </div>
            </li>
          ))}
        </ul>
        <p id={listId} className="text-sm" aria-live="polite">
          {!chosen.length ? 'Nenhuma matéria marcada.'
            : preview.data ? `Serão criadas ${preview.data.created} e atualizadas ${preview.data.updated}.`
            : preview.isError ? (preview.error instanceof ApiError ? preview.error.message : 'Não foi possível conferir.')
            : 'Conferindo...'}
        </p>
        {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onBack}>Voltar</Button>
        <Button onClick={() => void submit()} disabled={!chosen.length || save.isPending || preview.isError}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Importar
        </Button>
      </DialogFooter>
    </>
  )
}
