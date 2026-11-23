import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { useSaveAssessment } from '../api'
import { ASSESSMENT_LABEL, parseGrade } from '../labels'
import type { Assessment, AssessmentKind, UnitView } from '../types'

const NONE = 'none'
const REMINDERS = [
  { value: NONE, label: 'Sem lembrete' },
  { value: '60', label: '1 hora antes' },
  { value: '1440', label: '1 dia antes' },
  { value: '2880', label: '2 dias antes' },
  { value: '10080', label: '1 semana antes' },
]

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  courseId: string
  units: UnitView[]
  /** Editar; sem ela, cadastra */
  assessment?: Assessment | null
}

/** Prova ou trabalho: data, unidade, peso e nota (entra na média da unidade); com data vira tarefa com lembrete. */
export function AssessmentDialog({ open, onOpenChange, courseId, units, assessment }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{assessment ? 'Editar avaliação' : 'Nova avaliação'}</DialogTitle>
          <DialogDescription>Com nota e unidade, a média da unidade sai das avaliações (pelo peso).</DialogDescription>
        </DialogHeader>
        {open && <AssessmentForm courseId={courseId} units={units} assessment={assessment ?? null} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function AssessmentForm({ courseId, units, assessment, onDone }: {
  courseId: string
  units: UnitView[]
  assessment: Assessment | null
  onDone: () => void
}) {
  const save = useSaveAssessment()
  const [title, setTitle] = useState(assessment?.title ?? '')
  const [kind, setKind] = useState<AssessmentKind>(assessment?.kind ?? 'EXAM')
  const [dueDate, setDueDate] = useState(assessment?.dueDate ?? '')
  const [dueTime, setDueTime] = useState(assessment?.dueTime ?? '')
  const [unit, setUnit] = useState(assessment?.unit ? String(assessment.unit) : units.length ? '1' : NONE)
  const [weight, setWeight] = useState(assessment ? String(assessment.weight).replace('.', ',') : '1')
  const [grade, setGrade] = useState(assessment?.grade != null ? String(assessment.grade).replace('.', ',') : '')
  const [createTask, setCreateTask] = useState(true)
  const [reminder, setReminder] = useState('1440')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const unitCount = Math.max(units.length, assessment?.unit ?? 0)

  async function submit() {
    const parsedGrade = parseGrade(grade)
    const parsedWeight = parseGrade(weight)
    if (Number.isNaN(parsedGrade) || Number.isNaN(parsedWeight)) {
      setErrors({ grade: Number.isNaN(parsedGrade) ? 'Nota inválida.' : '', weight: Number.isNaN(parsedWeight) ? 'Peso inválido.' : '' })
      return
    }
    try {
      await save.mutateAsync({
        courseId,
        id: assessment?.id,
        title: title.trim(),
        kind,
        dueDate: dueDate || null,
        dueTime: dueDate && dueTime ? dueTime : null,
        unit: unit === NONE ? null : Number(unit),
        weight: parsedWeight ?? 1,
        grade: parsedGrade,
        createTask: !!dueDate && createTask,
        reminderMinutes: dueDate && dueTime && reminder !== NONE ? Number(reminder) : null,
      })
      toast.success(assessment ? 'Avaliação salva.' : 'Avaliação criada.')
      onDone()
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        toast.error(error.message)
      } else toast.error('Não foi possível salvar.')
    }
  }

  const err = (key: string) => errors[key] && <p className="text-sm text-destructive">{errors[key]}</p>

  return (
    <form className="contents" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <div className="grid gap-4 sm:grid-cols-[1fr_10rem]">
            <Field data-invalid={!!errors.title}>
              <FieldLabel htmlFor="asm-title">Nome</FieldLabel>
              <Input id="asm-title" placeholder="Ex.: Prova 1" maxLength={120} value={title} className="text-base md:text-sm"
                onChange={(e) => setTitle(e.target.value)} />
              {err('title')}
            </Field>
            <Field>
              <FieldLabel htmlFor="asm-kind">Tipo</FieldLabel>
              <Select value={kind} onValueChange={(v) => setKind(v as AssessmentKind)}>
                <SelectTrigger id="asm-kind"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(ASSESSMENT_LABEL).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="asm-date">Data</FieldLabel>
              <DatePicker id="asm-date" value={dueDate} onChange={setDueDate} placeholder="Sem data" />
            </Field>
            <Field>
              <FieldLabel htmlFor="asm-time">Horário</FieldLabel>
              <Input id="asm-time" type="time" value={dueTime} disabled={!dueDate} className="text-base md:text-sm"
                onChange={(e) => setDueTime(e.target.value)} />
            </Field>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Field>
              <FieldLabel htmlFor="asm-unit">Unidade</FieldLabel>
              <Select value={unit} onValueChange={setUnit}>
                <SelectTrigger id="asm-unit"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Nenhuma</SelectItem>
                  {Array.from({ length: unitCount }, (_, i) => (
                    <SelectItem key={i} value={String(i + 1)}>{units[i]?.label || `Unidade ${i + 1}`}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field data-invalid={!!errors.weight}>
              <FieldLabel htmlFor="asm-weight">Peso</FieldLabel>
              <Input id="asm-weight" inputMode="decimal" value={weight} className="text-base md:text-sm"
                onChange={(e) => setWeight(e.target.value)} />
              {err('weight')}
            </Field>
            <Field data-invalid={!!errors.grade}>
              <FieldLabel htmlFor="asm-grade">Nota</FieldLabel>
              <Input id="asm-grade" inputMode="decimal" placeholder="0 a 10" value={grade} className="text-base md:text-sm"
                onChange={(e) => setGrade(e.target.value)} />
              {err('grade')}
            </Field>
          </div>
          {dueDate && (assessment?.taskId ? (
            <FieldDescription>A tarefa ligada muda junto (nome, data e horário).</FieldDescription>
          ) : (
            <Field orientation="horizontal">
              <Checkbox id="asm-task" checked={createTask} onCheckedChange={(v) => setCreateTask(v === true)} />
              <Label htmlFor="asm-task">Criar tarefa nas Tarefas</Label>
            </Field>
          ))}
          {dueDate && (createTask || assessment?.taskId) && (
            <Field>
              <FieldLabel htmlFor="asm-reminder">Lembrete</FieldLabel>
              <Select value={reminder} onValueChange={setReminder} disabled={!dueTime}>
                <SelectTrigger id="asm-reminder"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {REMINDERS.map((r) => <SelectItem key={r.value} value={r.value}>{r.label}</SelectItem>)}
                </SelectContent>
              </Select>
              {!dueTime && <FieldDescription>Escolha o horário para ter lembrete.</FieldDescription>}
            </Field>
          )}
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" disabled={save.isPending || !title.trim()}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}
