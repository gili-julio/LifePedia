import { FileJsonIcon, Loader2Icon, UploadIcon } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { useImportAccount } from '../api'
import { count } from '../count'
import { type AccountFile, type ImportMode, type ImportResult, IMPORTABLE_FORMATS } from '../types'

const MAX_BYTES = 25 * 1024 * 1024

type Props = { open: boolean; onOpenChange: (open: boolean) => void }

/** Importa um arquivo exportado do LifePedia (desta ou de outra conta) no financeiro pessoal. */
export function ImportDialog({ open, onOpenChange }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Importar dados</DialogTitle>
          <DialogDescription>
            Use um arquivo .json baixado em “Exportar dados”. Entram categorias, fixos (com os valores de cada mês),
            lançamentos e tarefas pessoais (com listas e etiquetas); itens de grupos não são importados.
          </DialogDescription>
        </DialogHeader>
        <ImportForm onDone={() => onOpenChange(false)} />
      </DialogScrollContent>
    </Dialog>
  )
}

function ImportForm({ onDone }: { onDone: () => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<{ name: string; data: AccountFile } | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [mode, setMode] = useState<ImportMode>('MERGE')
  const [confirmReplace, setConfirmReplace] = useState(false)
  const preview = useImportAccount()
  const run = useImportAccount()

  function loadPreview(data: AccountFile, nextMode: ImportMode) {
    preview.reset()
    preview.mutate({ file: data, mode: nextMode, dryRun: true })
  }

  async function onPick(picked: File | undefined) {
    if (!picked) return
    setFile(null)
    setFileError(null)
    if (picked.size > MAX_BYTES) {
      setFileError('Arquivo grande demais (máximo 25 MB).')
      return
    }
    try {
      const data = JSON.parse(await picked.text()) as AccountFile
      if (!IMPORTABLE_FORMATS.includes(data?.format) || !Array.isArray(data.transactions) || !Array.isArray(data.recurring)
        || !Array.isArray(data.categories) || (data.tasks != null && !Array.isArray(data.tasks))) {
        setFileError('Esse arquivo não é uma exportação do LifePedia.')
        return
      }
      setFile({ name: picked.name, data })
      loadPreview(data, mode)
    } catch {
      setFileError('Não foi possível ler o arquivo (JSON inválido).')
    }
  }

  function changeMode(next: ImportMode) {
    setMode(next)
    setConfirmReplace(false)
    if (file) loadPreview(file.data, next)
  }

  async function onImport() {
    if (!file) return
    try {
      const result = await run.mutateAsync({ file: file.data, mode, dryRun: false })
      toast.success(`Importado: ${created(result)}.`)
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível importar.')
    }
  }

  const ready = !!file && !!preview.data && !preview.isPending && (mode === 'MERGE' || confirmReplace)

  return (
    <>
      <DialogBody>
        <input ref={input} type="file" accept="application/json,.json" className="sr-only" tabIndex={-1}
          onChange={(e) => {
            void onPick(e.target.files?.[0])
            e.target.value = ''
          }} />
        {file ? (
          <div className="flex items-start gap-3 rounded-lg border p-3">
            <FileJsonIcon className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
            <div className="min-w-0 flex-1 text-sm">
              <p className="truncate font-medium">{file.name}</p>
              <p className="text-xs text-muted-foreground">
                {file.data.exportedAt && `Exportado em ${formatDate(file.data.exportedAt.slice(0, 10))}`}
                {file.data.profile?.name && ` por ${file.data.profile.name}`} ·{' '}
                {count(file.data.transactions.length, 'lançamento', 'lançamentos')},{' '}
                {count(file.data.recurring.length, 'fixo', 'fixos')},{' '}
                {count(file.data.categories.length, 'categoria', 'categorias')}
                {file.data.tasks && `, ${count(file.data.tasks.length, 'tarefa', 'tarefas')}`}
              </p>
            </div>
            <Button variant="ghost" size="sm" onClick={() => input.current?.click()}>Trocar</Button>
          </div>
        ) : (
          <Button variant="outline" className="h-20 w-full border-dashed" onClick={() => input.current?.click()}>
            <UploadIcon /> Escolher arquivo .json
          </Button>
        )}
        {fileError && <p className="text-sm text-destructive" role="alert">{fileError}</p>}

        <RadioGroup value={mode} onValueChange={(v) => changeMode(v as ImportMode)} className="gap-2">
          <ModeOption value="MERGE" title="Somar aos meus dados"
            description="Junta o arquivo ao que você já tem. Categorias com o mesmo nome são reaproveitadas e itens iguais não duplicam." />
          <ModeOption value="REPLACE" title="Substituir meus dados"
            description="Apaga seus lançamentos, fixos e tarefas pessoais atuais antes de importar (tarefas só se o arquivo tiver). Itens nos grupos continuam." />
        </RadioGroup>

        {preview.isPending && (
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2Icon className="size-4 animate-spin" /> Conferindo o arquivo…
          </p>
        )}
        {preview.error && (
          <p className="text-sm text-destructive" role="alert">
            {preview.error instanceof ApiError ? preview.error.message : 'Não foi possível conferir o arquivo.'}
          </p>
        )}
        {preview.data && <PreviewSummary result={preview.data} />}

        {mode === 'REPLACE' && preview.data && (
          <Label className="flex cursor-pointer items-start gap-2.5 rounded-lg border border-destructive/40 p-2.5 font-normal">
            <Checkbox checked={confirmReplace} onCheckedChange={(v) => setConfirmReplace(v === true)} className="mt-0.5" />
            <span className="text-sm">
              Entendi: meus {count(preview.data.removed.transactions, 'lançamento', 'lançamentos')},{' '}
              {count(preview.data.removed.recurring, 'fixo', 'fixos')} e{' '}
              {count(preview.data.removed.tasks, 'tarefa pessoal', 'tarefas pessoais')} atuais serão apagados.
            </span>
          </Label>
        )}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button variant={mode === 'REPLACE' ? 'destructive' : 'default'} disabled={!ready || run.isPending} onClick={onImport}>
          {run.isPending && <Loader2Icon className="animate-spin" />}
          {mode === 'REPLACE' ? 'Substituir e importar' : 'Importar'}
        </Button>
      </DialogFooter>
    </>
  )
}

function ModeOption({ value, title, description }: { value: ImportMode; title: string; description: string }) {
  const id = `import-mode-${value}`
  return (
    <Label htmlFor={id}
      className="flex cursor-pointer items-start gap-2.5 rounded-lg border p-2.5 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-primary/5">
      <RadioGroupItem id={id} value={value} className="mt-0.5" />
      <span className="space-y-0.5">
        <span className="block text-sm font-medium">{title}</span>
        <span className="block text-xs text-muted-foreground">{description}</span>
      </span>
    </Label>
  )
}

/** O que a importação vai fazer (resultado do dryRun). */
function PreviewSummary({ result }: { result: ImportResult }) {
  const { skipped, removed } = result
  const skippedTotal = skipped.transactions + skipped.recurring + skipped.tasks
  return (
    <div className="space-y-1 rounded-lg bg-muted/50 p-3 text-sm" role="status">
      <p className="font-medium">O que vai acontecer</p>
      <ul className="list-disc space-y-0.5 pl-5 text-muted-foreground">
        {(removed.transactions > 0 || removed.recurring > 0) && (
          <li>
            Apaga {count(removed.transactions, 'lançamento', 'lançamentos')} e {count(removed.recurring, 'fixo', 'fixos')}{' '}
            atuais{removed.categories > 0 && ` e ${count(removed.categories, 'categoria sem uso', 'categorias sem uso')}`}
          </li>
        )}
        {(removed.tasks > 0 || removed.taskLabels > 0) && (
          <li>
            Apaga {count(removed.tasks, 'tarefa pessoal', 'tarefas pessoais')}
            {removed.taskLabels > 0 && ` e ${count(removed.taskLabels, 'lista/etiqueta', 'listas e etiquetas')}`} atuais
          </li>
        )}
        {result.syncedDetached > 0 && (
          <li>
            {count(result.syncedDetached, 'item sincronizado em grupo vira cópia e continua lá',
              'itens sincronizados em grupos viram cópia e continuam lá')}
          </li>
        )}
        <li>{hasNew(result) ? `Adiciona ${created(result)}` : 'Nada novo para adicionar'}</li>
        {result.categoriesReused > 0 && (
          <li>Reaproveita {count(result.categoriesReused, 'categoria que você já tem', 'categorias que você já tem')}</li>
        )}
        {skippedTotal > 0 && <li>{count(skippedTotal, 'item igual já existe e fica', 'itens iguais já existem e ficam')} de fora</li>}
      </ul>
    </div>
  )
}

const hasNew = ({ created: c }: ImportResult) =>
  c.transactions + c.recurring + c.adjustments + c.categories + c.tasks + c.taskLabels > 0

function created({ created: c }: ImportResult) {
  const parts = [
    count(c.transactions, 'lançamento', 'lançamentos'),
    count(c.recurring, 'fixo', 'fixos'),
  ]
  if (c.adjustments) parts.push(count(c.adjustments, 'valor de mês', 'valores de mês'))
  if (c.categories) parts.push(count(c.categories, 'categoria nova', 'categorias novas'))
  if (c.tasks) parts.push(count(c.tasks, 'tarefa', 'tarefas'))
  if (c.taskLabels) parts.push(count(c.taskLabels, 'lista/etiqueta nova', 'listas e etiquetas novas'))
  return parts.join(', ')
}
