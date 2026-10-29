import { InfoIcon, Loader2Icon } from 'lucide-react'
import { useId, useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { todayIso } from '@/lib/format'
import { endOf } from '../absences'
import { useImportCommitment } from '../api'
import { parseSigaa, type SigaaClass, sigaaMarks, sigaaMissing, sigaaUnregistered } from '../sigaa'
import type { Commitment, CommitmentRequest } from '../types'
import { CommitmentForm } from './CommitmentDialog'

const NEW = '__new'

type Props = { open: boolean; onOpenChange: (open: boolean) => void; commitments: Commitment[] }

/** Cola o texto da frequência da turma virtual do SIGAA e cria (ou atualiza) o compromisso com as marcações. */
export function SigaaImportDialog({ open, onOpenChange, commitments }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <SigaaImport commitments={commitments} onDone={() => onOpenChange(false)} />
      </DialogScrollContent>
    </Dialog>
  )
}

function SigaaImport({ commitments, onDone }: { commitments: Commitment[]; onDone: () => void }) {
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [parsed, setParsed] = useState<SigaaClass | null>(null)
  const fieldId = useId()

  function read() {
    try {
      setParsed(parseSigaa(text))
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível ler o texto.')
    }
  }

  if (parsed) return <Review parsed={parsed} commitments={commitments} onBack={() => setParsed(null)} onDone={onDone} />

  return (
    <>
      <DialogHeader>
        <DialogTitle>Importar do SIGAA</DialogTitle>
        <DialogDescription>
          Cole a página de frequência da turma virtual: o compromisso e as presenças são preenchidos para você conferir.
        </DialogDescription>
      </DialogHeader>
      <DialogBody>
        <div className="space-y-2">
          <div className="flex items-center gap-1.5">
            <Label htmlFor={fieldId}>Texto da página de frequência</Label>
            <HowToCopy />
          </div>
          <Textarea id={fieldId} value={text} onChange={(e) => setText(e.target.value)} rows={8} aria-invalid={!!error}
            placeholder="Cole aqui o texto copiado do SIGAA" className="max-h-64 min-h-40 text-base md:text-sm" />
          {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
          <p className="text-xs text-muted-foreground">
            O texto é lido no seu navegador; só o compromisso e as marcações são salvos.
          </p>
        </div>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button onClick={read} disabled={!text.trim()}>Ler texto</Button>
      </DialogFooter>
    </>
  )
}

/** Badge "i" com o passo a passo para copiar a página. */
function HowToCopy() {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button type="button" aria-label="Como copiar o texto do SIGAA"
          className="relative flex size-5 items-center justify-center rounded-full bg-data/15 text-data outline-none after:absolute after:-inset-2 hover:bg-data/25 focus-visible:ring-[3px] focus-visible:ring-ring/50">
          <InfoIcon className="size-3.5" aria-hidden />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-72 text-sm">
        <p className="font-medium">Como copiar do SIGAA</p>
        <ol className="mt-2 list-decimal space-y-1 pl-5 text-muted-foreground">
          <li>Entre no SIGAA.</li>
          <li>Abra a turma virtual da matéria.</li>
          <li>Na aba <strong className="text-foreground">Grupos</strong>, clique em <strong className="text-foreground">Frequência</strong>.</li>
          <li>Aperte <kbd className="rounded border bg-muted px-1 text-xs">Ctrl</kbd> + <kbd className="rounded border bg-muted px-1 text-xs">A</kbd> para selecionar tudo e copie (<kbd className="rounded border bg-muted px-1 text-xs">Ctrl</kbd> + <kbd className="rounded border bg-muted px-1 text-xs">C</kbd>).</li>
          <li>Cole o texto no campo.</li>
        </ol>
        <p className="mt-2 text-xs text-muted-foreground">No celular: segure na página, toque em Selecionar tudo e depois em Copiar.</p>
      </PopoverContent>
    </Popover>
  )
}

function Review({ parsed, commitments, onBack, onDone }: {
  parsed: SigaaClass
  commitments: Commitment[]
  onBack: () => void
  onDone: () => void
}) {
  const save = useImportCommitment()
  const today = todayIso()
  const same = commitments.find((c) => c.name.toLowerCase() === parsed.name.toLowerCase())
  const [target, setTarget] = useState(same?.id ?? NEW)
  const [useNews, setUseNews] = useState(true)
  const [useMissing, setUseMissing] = useState(true)
  const newsId = useId()
  const missingId = useId()
  const existing = commitments.find((c) => c.id === target)
  const missing = sigaaMissing(parsed, existing
    ? { weekdays: existing.weekdays, startDate: existing.startDate, endDate: existing.endDate, today }
    : { weekdays: parsed.weekdays, startDate: parsed.startDate, endDate: parsed.endDate, today })

  async function submit(data: { commitment: CommitmentRequest | null; weekdays: number[]; startDate: string; endDate: string | null }) {
    const { marks, skipped } = sigaaMarks(parsed, {
      weekdays: data.weekdays, startDate: data.startDate, endDate: data.endDate, today, useNews, useMissing,
    })
    try {
      await save.mutateAsync({ commitment: data.commitment, targetId: data.commitment ? null : target, marks })
      toast.success(`${data.commitment ? 'Compromisso criado' : 'Marcações atualizadas'}: ${marks.length} ${marks.length === 1 ? 'dia marcado' : 'dias marcados'}.`
        + (skipped ? ` ${skipped} fora dos dias ou do período ${skipped === 1 ? 'ficou' : 'ficaram'} de fora.` : ''))
      return true
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível importar.')
      return false
    }
  }

  const summary = (
    <div className="space-y-3">
      {commitments.length > 0 && (
        <div className="space-y-1.5">
          <Label htmlFor="sigaa-target">Importar em</Label>
          <Select value={target} onValueChange={setTarget}>
            <SelectTrigger id="sigaa-target" className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={NEW}>Novo compromisso</SelectItem>
              {commitments.map((c) => <SelectItem key={c.id} value={c.id}>{c.name} (só as marcações)</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      )}
      <Summary parsed={parsed} useNews={useNews} useMissing={useMissing} missing={missing} today={today} />
      {parsed.canceledByNews.length > 0 && (
        <div className="flex items-start gap-2">
          <Checkbox id={newsId} checked={useNews} onCheckedChange={(v) => setUseNews(v === true)} className="mt-0.5" />
          <Label htmlFor={newsId} className="flex-col items-start gap-0.5 font-normal">
            Marcar como sem aula os dias avisados nas notícias
            <span className="text-xs text-muted-foreground">{parsed.canceledByNews.map(short).join(', ')}</span>
          </Label>
        </div>
      )}
      {missing.length > 0 && (
        <div className="flex items-start gap-2">
          <Checkbox id={missingId} checked={useMissing} onCheckedChange={(v) => setUseMissing(v === true)} className="mt-0.5" />
          <Label htmlFor={missingId} className="flex-col items-start gap-0.5 font-normal">
            Marcar como sem aula os dias que não aparecem no SIGAA (ex.: feriados)
            <span className="text-xs text-muted-foreground">{missing.map(short).join(', ')}</span>
          </Label>
        </div>
      )}
    </div>
  )

  if (existing) {
    return (
      <>
        <DialogHeader>
          <DialogTitle>Atualizar {existing.name}</DialogTitle>
          <DialogDescription>Os dados do compromisso ficam como estão; as marcações do SIGAA valem sobre as que já havia.</DialogDescription>
        </DialogHeader>
        <DialogBody>{summary}</DialogBody>
        <DialogFooter>
          <Button variant="outline" onClick={onBack}>Voltar</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (await submit({ commitment: null, weekdays: existing.weekdays, startDate: existing.startDate, endDate: existing.endDate })) onDone()
          }}>
            {save.isPending && <Loader2Icon className="animate-spin" />} Atualizar marcações
          </Button>
        </DialogFooter>
      </>
    )
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Conferir importação</DialogTitle>
        <DialogDescription>Confira o que veio do SIGAA e ajuste se precisar.</DialogDescription>
      </DialogHeader>
      <CommitmentForm submitLabel="Importar" onBack={onBack} onDone={onDone}
        defaults={{
          name: parsed.name,
          weekdays: parsed.weekdays,
          startDate: parsed.startDate,
          endDate: parsed.endDate,
          maxAbsences: parsed.maxAbsences,
          ...(parsed.time ? { time: parsed.time } : {}),
          ...(parsed.durationMinutes ? { durationMinutes: parsed.durationMinutes } : {}),
        }}
        onSubmit={(data) => submit({ commitment: data, weekdays: data.weekdays, startDate: data.startDate, endDate: data.endDate })}>
        {summary}
      </CommitmentForm>
    </>
  )
}

function Summary({ parsed, useNews, useMissing, missing, today }: {
  parsed: SigaaClass
  useNews: boolean
  useMissing: boolean
  missing: string[]
  today: string
}) {
  const count = (status: string) => parsed.days.filter((d) => d.status === status).length
  const news = useNews ? parsed.canceledByNews.length : 0
  const holidays = useMissing ? missing.length : 0
  const unregistered = sigaaUnregistered(parsed, parsed.weekdays).filter((d) => d <= today).length - news + (useMissing ? 0 : missing.length)
  const allowed = parsed.totalClasses === null ? null : Math.floor((parsed.totalClasses * (100 - parsed.minPresence)) / 100)
  return (
    <ul className="space-y-1 rounded-lg border bg-muted/40 px-3 py-2 text-sm">
      <li>
        <span className="font-medium">{parsed.code ? `${parsed.code} · ` : ''}{parsed.name}</span>
      </li>
      <li className="text-muted-foreground">
        {count('PRESENT')} presenças, {count('ABSENT')} {count('ABSENT') === 1 ? 'falta' : 'faltas'}
        {news + holidays > 0 ? `, ${news + holidays} sem aula` : ''}
        {unregistered > 0 ? `. ${unregistered} não ${unregistered === 1 ? 'registrada fica pendente' : 'registradas ficam pendentes'}` : ''}.
      </li>
      <li className="text-muted-foreground">
        {parsed.time && parsed.durationMinutes
          ? `Horário ${parsed.slots}: ${parsed.time} às ${endOf(parsed.time, parsed.durationMinutes)} (aulas de 50 min da UFRN).`
          : 'O horário não veio no texto: confira abaixo.'}
      </li>
      <li className="text-muted-foreground">
        {allowed === null ? 'A carga horária não veio no texto: confira o limite de faltas.'
          : `Limite: ${parsed.totalClasses} aulas com ${parsed.minPresence}% de presença deixam ${allowed} aulas de falta; `
            + `com ${parsed.classesPerDay} ${parsed.classesPerDay === 1 ? 'aula' : 'aulas'} por dia, ${parsed.maxAbsences} dias.`}
      </li>
    </ul>
  )
}

function short(iso: string) {
  return `${iso.slice(8)}/${iso.slice(5, 7)}`
}
