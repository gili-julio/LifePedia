import { Loader2Icon, TriangleAlertIcon } from 'lucide-react'
import { useId, useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { useSaveGrades } from '../api'
import { parseSigaaGrades, type SigaaGrades } from '../import/sigaa-grades'
import { formatGrade, STATUS_LABEL } from '../labels'
import type { Course } from '../types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Matérias onde procurar a da turma (código + semestre); uma só = tela da matéria */
  courses: Course[]
  /** Matrícula do vínculo, para achar a linha certa */
  registration: string | null
}

/** Cola o "Ver notas" da turma no SIGAA: lê no navegador, mostra e grava as notas na matéria certa. */
export function PasteGradesDialog({ open, onOpenChange, courses, registration }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        {open && <PasteGrades courses={courses} registration={registration} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function PasteGrades({ courses, registration, onDone }: { courses: Course[]; registration: string | null; onDone: () => void }) {
  const save = useSaveGrades()
  const fieldId = useId()
  const [text, setText] = useState('')
  const [parsed, setParsed] = useState<SigaaGrades | null>(null)
  const [courseId, setCourseId] = useState<string>(courses.length === 1 ? courses[0].id : '')
  const [error, setError] = useState<string | null>(null)
  const course = courses.find((c) => c.id === courseId) ?? null
  const mismatch = !!parsed && !!course && !!parsed.code && (parsed.code !== course.code || (!!parsed.period && parsed.period !== course.period))

  function read() {
    try {
      const result = parseSigaaGrades(text, registration)
      setParsed(result)
      setError(null)
      if (courses.length > 1) {
        const match = courses.find((c) => c.code === result.code && c.period === result.period)
          ?? courses.find((c) => c.code === result.code)
        setCourseId(match?.id ?? '')
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível ler o texto.')
    }
  }

  async function submit() {
    if (!parsed || !course) return
    try {
      await save.mutateAsync({
        courseId: course.id,
        units: parsed.units,
        recovery: parsed.recovery,
        absences: parsed.absences,
        average: parsed.status ? parsed.average : null,
        status: parsed.status,
      })
      toast.success(`Notas de ${course.name} salvas.`)
      onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Não foi possível salvar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Colar notas do SIGAA</DialogTitle>
        <DialogDescription>
          Na turma virtual, abra Ver notas, selecione tudo (Ctrl + A), copie e cole aqui. O texto é lido no seu navegador.
        </DialogDescription>
      </DialogHeader>
      <DialogBody>
        {!parsed ? (
          <div className="space-y-2">
            <Label htmlFor={fieldId}>Texto da tela de notas</Label>
            <Textarea id={fieldId} value={text} onChange={(e) => setText(e.target.value)} rows={8} aria-invalid={!!error}
              placeholder="Cole aqui o texto copiado do SIGAA" className="max-h-64 min-h-40 text-base md:text-sm" />
          </div>
        ) : (
          <div className="space-y-4">
            <div className="space-y-1 text-sm">
              <p className="font-medium">{parsed.code ? `${parsed.code} - ` : ''}{parsed.name ?? 'Turma'}</p>
              {parsed.period && <p className="text-muted-foreground">Semestre {parsed.period}{parsed.classCode ? `, turma ${parsed.classCode}` : ''}</p>}
            </div>
            {courses.length > 1 && (
              <div className="space-y-2">
                <Label htmlFor={`${fieldId}-course`}>Matéria</Label>
                <Select value={courseId} onValueChange={setCourseId}>
                  <SelectTrigger id={`${fieldId}-course`}><SelectValue placeholder="Escolha a matéria" /></SelectTrigger>
                  <SelectContent>
                    {courses.map((c) => (
                      <SelectItem key={c.id} value={c.id}>{c.code ? `${c.code} - ` : ''}{c.name}{c.period ? ` (${c.period})` : ''}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
            {mismatch && (
              <p className="flex gap-2 text-sm text-amber-700 dark:text-amber-400">
                <TriangleAlertIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
                O texto é de {parsed.code} ({parsed.period}), mas a matéria é {course?.code ?? 'sem código'} ({course?.period ?? 'sem semestre'}).
              </p>
            )}
            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-xl border p-3 text-sm sm:grid-cols-3">
              {parsed.units.map((u) => (
                <div key={u.label}>
                  <dt className="text-xs text-muted-foreground">{u.label}</dt>
                  <dd className="font-medium tabular-nums">{formatGrade(u.grade)}</dd>
                </div>
              ))}
              <div>
                <dt className="text-xs text-muted-foreground">Reposição</dt>
                <dd className="font-medium tabular-nums">{formatGrade(parsed.recovery)}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Faltas</dt>
                <dd className="font-medium tabular-nums">{parsed.absences ?? '-'}</dd>
              </div>
              {parsed.status && (
                <div>
                  <dt className="text-xs text-muted-foreground">Situação</dt>
                  <dd className="font-medium">{STATUS_LABEL[parsed.status]}</dd>
                </div>
              )}
            </dl>
          </div>
        )}
        {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
      </DialogBody>
      <DialogFooter>
        {parsed ? (
          <>
            <Button variant="outline" onClick={() => setParsed(null)}>Voltar</Button>
            <Button onClick={() => void submit()} disabled={!course || save.isPending}>
              {save.isPending && <Loader2Icon className="animate-spin" />} Salvar notas
            </Button>
          </>
        ) : (
          <>
            <Button variant="outline" onClick={onDone}>Cancelar</Button>
            <Button onClick={read} disabled={!text.trim()}>Ler texto</Button>
          </>
        )}
      </DialogFooter>
    </>
  )
}
