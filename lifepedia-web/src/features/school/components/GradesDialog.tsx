import { Loader2Icon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { useSaveGrades } from '../api'
import { parseGrade, STATUS_LABEL, STATUSES } from '../labels'
import type { Course, CourseStatus, Scale } from '../types'

const MAX_UNITS = 8
const KEEP = 'keep'

type Props = { open: boolean; onOpenChange: (open: boolean) => void; course: Course; scale: Scale }

/** Notas à mão: unidades, reposição, faltas e, se já fechou, média, frequência e situação finais. */
export function GradesDialog({ open, onOpenChange, course, scale }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Notas de {course.name}</DialogTitle>
          <DialogDescription>
            {scale === 'CONCEPT' ? 'Informe o conceito quando sair.' : 'A média e a situação são calculadas pela regra do vínculo.'}
          </DialogDescription>
        </DialogHeader>
        {open && <GradesForm course={course} scale={scale} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

const show = (value: number | null | undefined) => (value == null ? '' : String(value).replace('.', ','))

function GradesForm({ course, scale, onDone }: { course: Course; scale: Scale; onDone: () => void }) {
  const save = useSaveGrades()
  const [units, setUnits] = useState(course.units.map((u) => ({ label: u.label, grade: show(u.typed) })))
  const [recovery, setRecovery] = useState(show(course.recovery))
  const [absences, setAbsences] = useState(course.absences == null ? '' : String(course.absences))
  const [average, setAverage] = useState(show(course.average))
  const [attendance, setAttendance] = useState(show(course.attendancePercent))
  const [concept, setConcept] = useState(course.concept ?? '')
  const [status, setStatus] = useState<string>(KEEP)
  const [error, setError] = useState<string | null>(null)
  const fromAssessments = course.units.some((u) => u.fromAssessments)

  async function submit() {
    const grades = units.map((u) => parseGrade(u.grade))
    const values = [...grades, parseGrade(recovery), parseGrade(average)]
    if (values.some((v) => Number.isNaN(v) || (v != null && (v < 0 || v > 10)))) {
      setError('Notas de 0 a 10 (use vírgula ou ponto).')
      return
    }
    const attendanceValue = parseGrade(attendance)
    try {
      await save.mutateAsync({
        courseId: course.id,
        units: units.map((u, i) => ({ label: u.label.trim() || `Unid. ${i + 1}`, grade: grades[i] })),
        recovery: parseGrade(recovery),
        absences: absences.trim() ? Number(absences) : null,
        average: parseGrade(average),
        attendance: Number.isNaN(attendanceValue) ? null : attendanceValue,
        concept: concept.trim() || null,
        status: status === KEEP ? null : (status as CourseStatus),
      })
      toast.success('Notas salvas.')
      onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Não foi possível salvar.')
    }
  }

  return (
    <form className="contents" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          {scale === 'NUMERIC' && (
            <FieldSet>
              <FieldLegend>Unidades</FieldLegend>
              {fromAssessments && (
                <FieldDescription>Unidade com avaliações com nota usa a média delas; o valor daqui fica guardado.</FieldDescription>
              )}
              {units.map((unit, index) => (
                <div key={index} className="grid grid-cols-[1fr_6rem_auto] items-center gap-2">
                  <Input aria-label="Nome da unidade" maxLength={30} value={unit.label} className="text-base md:text-sm"
                    onChange={(e) => setUnits((list) => list.map((u, i) => (i === index ? { ...u, label: e.target.value } : u)))} />
                  <Input aria-label={`Nota de ${unit.label}`} inputMode="decimal" placeholder="-" value={unit.grade}
                    className="text-base md:text-sm"
                    onChange={(e) => setUnits((list) => list.map((u, i) => (i === index ? { ...u, grade: e.target.value } : u)))} />
                  <Button type="button" variant="ghost" size="icon" aria-label={`Tirar ${unit.label}`}
                    onClick={() => setUnits((list) => list.filter((_, i) => i !== index))}>
                    <Trash2Icon />
                  </Button>
                </div>
              ))}
              {units.length < MAX_UNITS && (
                <Button type="button" variant="outline" className="w-fit"
                  onClick={() => setUnits((list) => [...list, { label: `Unid. ${list.length + 1}`, grade: '' }])}>
                  <PlusIcon /> Adicionar unidade
                </Button>
              )}
            </FieldSet>
          )}
          <div className="grid grid-cols-2 gap-4">
            {scale === 'NUMERIC' ? (
              <Field>
                <FieldLabel htmlFor="grd-rec">Reposição</FieldLabel>
                <Input id="grd-rec" inputMode="decimal" placeholder="-" value={recovery} className="text-base md:text-sm"
                  onChange={(e) => setRecovery(e.target.value)} />
              </Field>
            ) : (
              <Field>
                <FieldLabel htmlFor="grd-concept">Conceito</FieldLabel>
                <Input id="grd-concept" maxLength={2} placeholder="A, B, C..." value={concept} autoCapitalize="characters"
                  className="text-base uppercase md:text-sm" onChange={(e) => setConcept(e.target.value)} />
              </Field>
            )}
            <Field>
              <FieldLabel htmlFor="grd-abs">Faltas (SIGAA)</FieldLabel>
              <Input id="grd-abs" inputMode="numeric" placeholder="-" value={absences} className="text-base md:text-sm"
                onChange={(e) => setAbsences(e.target.value.replace(/\D/g, ''))} />
            </Field>
          </div>
          <FieldSet>
            <FieldLegend>Fechamento (opcional)</FieldLegend>
            <FieldDescription>Quando a matéria fechar no SIGAA, informe a média e a situação finais.</FieldDescription>
            <div className="grid grid-cols-2 gap-4">
              {scale === 'NUMERIC' && (
                <Field>
                  <FieldLabel htmlFor="grd-avg">Média final</FieldLabel>
                  <Input id="grd-avg" inputMode="decimal" placeholder="-" value={average} className="text-base md:text-sm"
                    onChange={(e) => setAverage(e.target.value)} />
                </Field>
              )}
              <Field>
                <FieldLabel htmlFor="grd-att">Frequência (%)</FieldLabel>
                <Input id="grd-att" inputMode="decimal" placeholder="-" value={attendance} className="text-base md:text-sm"
                  onChange={(e) => setAttendance(e.target.value)} />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="grd-status">Situação</FieldLabel>
              <Select value={status} onValueChange={setStatus}>
                <SelectTrigger id="grd-status"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={KEEP}>Manter ({STATUS_LABEL[course.status]})</SelectItem>
                  {STATUSES.map((s) => <SelectItem key={s} value={s}>{STATUS_LABEL[s]} ({s})</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          </FieldSet>
          {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}
