import { useQueryClient } from '@tanstack/react-query'
import { AlertTriangleIcon, CheckIcon, ClipboardCopyIcon, Loader2Icon, ShieldCheckIcon, SparklesIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { useCreateDraft, useImportPrompt } from '../api'
import { IMPORT_FORMAT } from './format'
import { ImportParseError } from './parse'
import { rowsFromText, type ImportLookups } from './rows'
import { Step } from './Step'

/** Prompt para levar ao seu agente e resposta colada; a análise abre a revisão. */
export function ManualImport({ lookups, loading }: { lookups: ImportLookups; loading: boolean }) {
  const queryClient = useQueryClient()
  const { data, isLoading } = useImportPrompt(true)
  const createDraft = useCreateDraft()
  const prompt = data?.prompt ?? ''
  const [copied, setCopied] = useState(false)
  const [raw, setRaw] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [parseError, setParseError] = useState<string | null>(null)

  async function copyPrompt() {
    try {
      await navigator.clipboard.writeText(prompt)
      setCopied(true)
      toast.success('Prompt copiado.')
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error('Não consegui copiar. Selecione o texto e copie manualmente.')
    }
  }

  async function analyze() {
    setParseError(null)
    setAnalyzing(true)
    try {
      await createDraft.mutateAsync(await rowsFromText(raw, lookups, queryClient))
    } catch (error) {
      setParseError(error instanceof ImportParseError || error instanceof ApiError ? error.message : 'Não foi possível analisar.')
      setAnalyzing(false)
    }
  }

  return (
    <>
      <Step n={1} title="Copie o prompt" description="Ele já traz suas categorias, seus fixos, os nomes que você usa e o modelo de resposta que o LifePedia entende.">
        <Textarea readOnly value={isLoading ? 'Carregando…' : prompt}
          className="h-56 font-mono text-xs" onFocus={(e) => e.currentTarget.select()} />
        <div className="flex flex-wrap items-center gap-3">
          <Button onClick={copyPrompt} disabled={!prompt}>
            {copied ? <CheckIcon /> : <ClipboardCopyIcon />} {copied ? 'Copiado' : 'Copiar prompt'}
          </Button>
          <p className="text-sm text-muted-foreground">
            Cole no seu agente (ChatGPT, Claude, Gemini…) e anexe extratos e/ou faturas (PDF, print ou texto), de um ou
            mais meses. O agente separa a origem de cada item e tira as datas do documento.
          </p>
        </div>
        <p className="flex gap-2 rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">
          <ShieldCheckIcon className="size-4 shrink-0" />
          O documento e o prompt (com suas categorias, fixos e nomes de lançamentos recentes) vão para o serviço de IA que você escolher. O
          LifePedia não envia nada. Oculte números de conta/cartão e dados pessoais e prefira contas com o uso para treino desativado.
        </p>
      </Step>

      <Step n={2} title="Cole a resposta do agente" description="Pode colar a resposta inteira; o LifePedia encontra o bloco JSON.">
        <Textarea value={raw} onChange={(e) => setRaw(e.target.value)} className="h-40 font-mono text-base md:text-xs"
          placeholder={`\`\`\`json\n{ "format": "${IMPORT_FORMAT}", "items": [ ... ] }\n\`\`\``} />
        {parseError && (
          <p className="flex items-center gap-2 text-sm text-destructive">
            <AlertTriangleIcon className="size-4" /> {parseError}
          </p>
        )}
        <Button onClick={analyze} disabled={!raw.trim() || analyzing || loading}>
          {analyzing ? <Loader2Icon className="animate-spin" /> : <SparklesIcon />} Analisar
        </Button>
      </Step>
    </>
  )
}
