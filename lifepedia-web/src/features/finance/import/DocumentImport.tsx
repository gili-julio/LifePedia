import { Loader2Icon, ShieldCheckIcon, SparklesIcon, UploadIcon, XIcon } from 'lucide-react'
import { useId, useState, type DragEvent } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { formatInstantDate, formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useSendDocuments, useSendingDocuments } from '../api'
import type { ImportAssistant } from '../types'
import { Step } from './Step'

const ACCEPT = '.pdf,.png,.jpg,.jpeg,.webp,.txt,.csv,.ofx'

const MEGABYTE = 1024 * 1024

const megabytes = (bytes: number) =>
  bytes > 0 && bytes < MEGABYTE / 10 ? 'menos de 0,1' : (bytes / MEGABYTE).toLocaleString('pt-BR', { maximumFractionDigits: 1 })

const sameFile = (a: File, b: File) => a.name === b.name && a.size === b.size && a.lastModified === b.lastModified

/** Envio de extratos e faturas para a IA da API. A leitura segue em segundo plano. */
export function DocumentImport({ assistant }: { assistant: ImportAssistant }) {
  const { maxFiles, maxTotalBytes, maxPages, quota } = assistant
  const inputId = useId()
  const send = useSendDocuments()
  const sending = useSendingDocuments()
  const [files, setFiles] = useState<File[]>([])
  const [dragging, setDragging] = useState(false)
  const total = files.reduce((sum, file) => sum + file.size, 0)
  const blocked = quota.nextAt !== null

  function add(chosen: File[]) {
    const fresh = chosen.filter((file) => !files.some((f) => sameFile(f, file)))
    const next = [...files, ...fresh]
    if (next.length > maxFiles) {
      toast.error(`Envie no máximo ${maxFiles} arquivos por vez.`)
      return
    }
    if (next.reduce((sum, file) => sum + file.size, 0) > maxTotalBytes) {
      toast.error(`Os arquivos juntos passam de ${megabytes(maxTotalBytes)} MB.`)
      return
    }
    setFiles(next)
  }

  function onDrop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    add([...event.dataTransfer.files])
  }

  return (
    <Step n={1} title="Envie os documentos" description="Extratos e faturas, juntos ou separados, de um ou mais meses. A IA lê tudo de uma vez e separa nas suas categorias.">
      <label
        htmlFor={inputId}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'flex cursor-pointer flex-col items-center gap-1 rounded-xl border border-dashed px-4 py-8 text-center text-sm has-focus-visible:ring-2 has-focus-visible:ring-ring',
          dragging && 'border-primary bg-muted',
        )}>
        <UploadIcon className="size-5 text-muted-foreground" />
        <span className="font-medium">Escolher arquivos ou soltar aqui</span>
        <span className="text-xs text-muted-foreground">PDF, imagem ou texto (CSV, OFX, TXT)</span>
        <input id={inputId} type="file" multiple accept={ACCEPT} className="sr-only"
          onChange={(e) => {
            add([...(e.target.files ?? [])])
            e.target.value = ''
          }} />
      </label>

      {files.length > 0 && (
        <ul className="divide-y rounded-xl border text-sm">
          {files.map((file) => (
            <li key={`${file.name}-${file.size}-${file.lastModified}`} className="flex items-center gap-2 px-3 py-1.5">
              <span className="min-w-0 flex-1 truncate">{file.name}</span>
              <span className="shrink-0 text-xs text-muted-foreground tabular-nums">{megabytes(file.size)} MB</span>
              <Button type="button" variant="ghost" size="icon-sm" aria-label={`Tirar ${file.name}`}
                onClick={() => setFiles(files.filter((f) => f !== file))}>
                <XIcon />
              </Button>
            </li>
          ))}
        </ul>
      )}

      <div className="rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">
        <p className="font-medium text-foreground">Limites</p>
        <ul className="mt-1 grid gap-x-4 gap-y-0.5 sm:grid-cols-2">
          <li>Até {maxFiles} arquivos por envio ({files.length} escolhido{files.length === 1 ? '' : 's'})</li>
          <li>{megabytes(maxTotalBytes)} MB somando os arquivos ({megabytes(total)} MB agora)</li>
          <li>Até {maxPages} páginas por PDF, sem senha</li>
          <li>1 envio a cada {quota.intervalHours} horas</li>
          <li>{quota.weekLimit} envios em 7 dias ({quota.usedWeek} usado{quota.usedWeek === 1 ? '' : 's'})</li>
          <li>{quota.monthLimit} envios em 30 dias ({quota.usedMonth} usado{quota.usedMonth === 1 ? '' : 's'})</li>
        </ul>
        {blocked && (
          <p className="mt-1 font-medium text-foreground">
            Próximo envio em {formatInstantDate(quota.nextAt!)} às {formatTime(quota.nextAt!)}. Até lá, use a aba Com o seu agente.
          </p>
        )}
      </div>

      <p className="flex gap-2 rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">
        <ShieldCheckIcon className="size-4 shrink-0" />
        Os arquivos vão inteiros para o Google (Gemini), com nome, CPF, conta e o que mais tiverem, junto das suas categorias, fixos e
        nomes de lançamentos. O LifePedia não guarda os arquivos, só os lançamentos lidos, até você importar ou descartar. Prefere
        não enviar? Use a aba Com o seu agente.
      </p>

      <div className="flex flex-wrap items-center gap-3">
        <Button disabled={!files.length || blocked || sending} onClick={() => send.mutate(files)}>
          {sending ? <Loader2Icon className="animate-spin" /> : <SparklesIcon />} {sending ? 'Enviando…' : 'Enviar para a IA'}
        </Button>
        <p className="text-sm text-muted-foreground">Depois de enviar, pode sair desta tela. Avisamos quando a revisão estiver pronta.</p>
      </div>
    </Step>
  )
}
