import { SaveIcon, SparklesIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { ptBR } from 'react-day-picker/locale'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { formatInstantDate, formatMonth, formatTime, todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useDeleteReport, useDevReports, useReportSuggestion, useSaveReport } from '../api'
import type { DevReport } from '../types'

type Props = { projectId: string; day: string; onDayChange: (day: string) => void }

const MAX = 4000

/** Aba Reports: escolhe o dia no calendário e anota o que fez (com rascunho do GitLab/GitHub). */
export function ReportsTab({ projectId, day, onDayChange }: Props) {
  const [month, setMonth] = useState(day.slice(0, 7))
  const shown = useDevReports(projectId, month)
  const ofDay = useDevReports(projectId, day.slice(0, 7))
  const report = ofDay.data?.find((r) => r.day === day)
  const noted = (shown.data ?? []).map((r) => toDate(r.day))

  return (
    <div className="grid gap-4 lg:grid-cols-[auto_minmax(0,1fr)]">
      <div className="space-y-4">
        <Card className="items-center py-3">
          <Calendar
            mode="single"
            locale={ptBR}
            required
            selected={toDate(day)}
            month={toDate(`${month}-01`)}
            onMonthChange={(d) => setMonth(toIso(d).slice(0, 7))}
            onSelect={(d) => d && onDayChange(toIso(d))}
            disabled={{ after: new Date() }}
            modifiers={{ noted }}
            modifiersClassNames={{
              noted: 'relative after:pointer-events-none after:absolute after:bottom-1 after:left-1/2 after:size-1 after:-translate-x-1/2 after:z-20 after:rounded-full after:bg-data',
            }}
          />
        </Card>
        <p className="px-1 text-xs text-muted-foreground">Os dias com ponto já têm anotação.</p>
      </div>

      <div className="min-w-0 space-y-4">
        {!ofDay.data ? <Skeleton className="h-64" /> : (
          <ReportEditor key={`${day}:${report?.updatedAt ?? ''}`} projectId={projectId} day={day} report={report} />
        )}
        <Card className="gap-2">
          <CardHeader><CardTitle className="text-base">Anotações de {formatMonth(month as `${number}-${number}`)}</CardTitle></CardHeader>
          <CardContent>
            {!shown.data ? <Skeleton className="h-16" /> : shown.data.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nenhuma anotação neste mês.</p>
            ) : (
              <ul className="divide-y">
                {shown.data.map((r) => (
                  <li key={r.day}>
                    <button type="button" onClick={() => onDayChange(r.day)}
                      className={cn('w-full rounded-lg px-2 py-2 text-left hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
                        r.day === day && 'bg-muted')}>
                      <span className="block text-sm font-medium first-letter:uppercase">{dayLabel(r.day)}</span>
                      <span className="line-clamp-2 text-sm whitespace-pre-line text-muted-foreground">{r.text}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function ReportEditor({ projectId, day, report }: { projectId: string; day: string; report?: DevReport }) {
  const [text, setText] = useState(report?.text ?? '')
  const [deleting, setDeleting] = useState(false)
  const save = useSaveReport()
  const remove = useDeleteReport()
  const suggest = useReportSuggestion()
  const changed = text.trim() !== (report?.text ?? '')

  async function submit() {
    if (!text.trim() || !changed) return
    try {
      await save.mutateAsync({ projectId, day, text })
      toast.success('Anotação salva.')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível salvar.')
    }
  }

  async function fill() {
    try {
      const { text: draft } = await suggest.mutateAsync({ projectId, day })
      if (!draft) {
        toast.info('Nada registrado no GitLab/GitHub nesse dia.')
        return
      }
      setText((current) => (current.trim() ? `${current.trimEnd()}\n${draft}` : draft).slice(0, MAX))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível montar a sugestão.')
    }
  }

  async function confirmDelete() {
    try {
      await remove.mutateAsync({ projectId, day })
      toast.success('Anotação excluída.')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível excluir.')
    }
  }

  return (
    <Card className="gap-3">
      <CardHeader>
        <CardTitle className="text-base first-letter:uppercase">{day === todayIso() ? 'Hoje' : dayLabel(day)}</CardTitle>
        <p className="text-xs text-muted-foreground">
          {report ? `Salva em ${formatInstantDate(report.updatedAt)} às ${formatTime(report.updatedAt)}.` : 'O que você fez nesse dia? Issues, MRs, revisões, reuniões...'}
        </p>
      </CardHeader>
      <CardContent className="space-y-3">
        <Textarea aria-label="Anotação do dia" value={text} maxLength={MAX} rows={8} className="text-base md:text-sm"
          placeholder="Ex.: Trabalhei na issue #12, subi o MR e pedi revisão."
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) void submit()
          }} />
        <div className="flex flex-wrap items-center gap-2">
          <Button onClick={() => void submit()} disabled={!text.trim() || !changed || save.isPending}><SaveIcon /> Salvar</Button>
          <Button variant="outline" onClick={() => void fill()} disabled={suggest.isPending}><SparklesIcon /> Sugerir do dia</Button>
          {report && (
            <Button variant="ghost" className="text-destructive hover:text-destructive" onClick={() => setDeleting(true)}>
              <Trash2Icon /> Excluir
            </Button>
          )}
          <span className="ml-auto text-xs text-muted-foreground tabular-nums">{text.length}/{MAX}</span>
        </div>
        <p className="text-xs text-muted-foreground">
          "Sugerir do dia" junta o que o GitLab/GitHub registrou: MRs abertos e aceitos, issues que mudaram de raia, commits e revisões.
        </p>
      </CardContent>
      <ConfirmDelete open={deleting} onOpenChange={setDeleting} title="Excluir a anotação?"
        description="O texto desse dia some e não volta." onConfirm={() => void confirmDelete()} />
    </Card>
  )
}

function toDate(value: string) {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function toIso(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function dayLabel(day: string) {
  return toDate(day).toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })
}
