import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ColorPicker } from '@/components/ColorPicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { ApiError } from '@/lib/api'
import { COLOR_SWATCHES } from '@/lib/colors'
import { useSaveEnrollment } from '../api'
import { defaultRules, KIND_LABEL } from '../labels'
import type { Enrollment, EnrollmentKind, Scale } from '../types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Editar; sem ele, cadastra */
  enrollment?: Enrollment | null
  onSaved?: (enrollment: Enrollment) => void
}

/** Cadastrar ou editar vínculo: curso, instituição, regra de aprovação e carga horária exigida. */
export function EnrollmentDialog({ open, onOpenChange, enrollment, onSaved }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{enrollment ? 'Editar vínculo' : 'Novo vínculo'}</DialogTitle>
          <DialogDescription>
            Um vínculo por curso (graduação, segunda graduação, mestrado...). Cada um tem a própria regra de aprovação.
          </DialogDescription>
        </DialogHeader>
        {open && <EnrollmentForm enrollment={enrollment ?? null} onDone={(saved) => { onOpenChange(false); if (saved) onSaved?.(saved) }} />}
      </DialogScrollContent>
    </Dialog>
  )
}

const text = (value: number | null | undefined) => (value == null ? '' : String(value).replace('.', ','))
const num = (raw: string) => (raw.trim() ? Number(raw.trim().replace(',', '.')) : null)

function EnrollmentForm({ enrollment, onDone }: { enrollment: Enrollment | null; onDone: (saved?: Enrollment) => void }) {
  const save = useSaveEnrollment()
  const [kind, setKind] = useState<EnrollmentKind>(enrollment?.kind ?? 'UNDERGRADUATE')
  const [course, setCourse] = useState(enrollment?.course ?? '')
  const [institution, setInstitution] = useState(enrollment?.institution ?? '')
  const [registration, setRegistration] = useState(enrollment?.registration ?? '')
  const [startPeriod, setStartPeriod] = useState(enrollment?.startPeriod ?? '')
  const [active, setActive] = useState(enrollment?.active ?? true)
  const [color, setColor] = useState(enrollment?.color ?? COLOR_SWATCHES[0])
  const initial = enrollment?.rules ?? defaultRules(kind)
  const [scale, setScale] = useState<Scale>(initial.scale)
  const [passAverage, setPassAverage] = useState(text(initial.passAverage))
  const [lowAverage, setLowAverage] = useState(text(initial.lowAverage))
  const [minUnit, setMinUnit] = useState(text(initial.minUnit))
  const [minAttendance, setMinAttendance] = useState(String(initial.minAttendance))
  const [mandatory, setMandatory] = useState(text(enrollment?.required.mandatory))
  const [optional, setOptional] = useState(text(enrollment?.required.optional))
  const [complementary, setComplementary] = useState(text(enrollment?.required.complementary))
  const [complementaryDone, setComplementaryDone] = useState(enrollment?.complementaryDone ? String(enrollment.complementaryDone) : '')
  const [errors, setErrors] = useState<Record<string, string>>({})

  function changeKind(value: EnrollmentKind) {
    setKind(value)
    if (!enrollment) setScale(defaultRules(value).scale)
  }

  async function submit() {
    try {
      const saved = await save.mutateAsync({
        id: enrollment?.id,
        kind,
        course: course.trim(),
        institution: institution.trim() || null,
        registration: registration.trim() || null,
        startPeriod: startPeriod.trim() || null,
        active,
        color,
        rules: {
          scale,
          passAverage: num(passAverage) ?? 6,
          lowAverage: num(lowAverage) ?? num(passAverage) ?? 6,
          minUnit: num(minUnit) ?? 0,
          minAttendance: Number(minAttendance) || 0,
        },
        required: { mandatory: num(mandatory), optional: num(optional), complementary: num(complementary) },
        complementaryDone: num(complementaryDone),
      })
      toast.success(enrollment ? 'Vínculo salvo.' : 'Vínculo criado.')
      onDone(saved)
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
          <div className="grid gap-4 sm:grid-cols-2">
            <Field>
              <FieldLabel htmlFor="enr-kind">Tipo</FieldLabel>
              <Select value={kind} onValueChange={(v) => changeKind(v as EnrollmentKind)}>
                <SelectTrigger id="enr-kind"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(KIND_LABEL).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
            <Field data-invalid={!!errors.startPeriod}>
              <FieldLabel htmlFor="enr-start">Semestre de entrada</FieldLabel>
              <Input id="enr-start" inputMode="decimal" placeholder="Ex.: 2023.1" maxLength={6} value={startPeriod}
                className="text-base md:text-sm" onChange={(e) => setStartPeriod(e.target.value)} />
              {err('startPeriod')}
            </Field>
          </div>
          <Field data-invalid={!!errors.course}>
            <FieldLabel htmlFor="enr-course">Curso</FieldLabel>
            <Input id="enr-course" placeholder="Ex.: Engenharia de Software" maxLength={160} value={course}
              className="text-base md:text-sm" onChange={(e) => setCourse(e.target.value)} />
            {err('course')}
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field data-invalid={!!errors.institution}>
              <FieldLabel htmlFor="enr-inst">Instituição</FieldLabel>
              <Input id="enr-inst" placeholder="Ex.: UFRN" maxLength={160} value={institution} className="text-base md:text-sm"
                onChange={(e) => setInstitution(e.target.value)} />
              {err('institution')}
            </Field>
            <Field data-invalid={!!errors.registration}>
              <FieldLabel htmlFor="enr-reg">Matrícula (opcional)</FieldLabel>
              <Input id="enr-reg" inputMode="numeric" autoComplete="off" maxLength={40} value={registration}
                className="text-base md:text-sm" onChange={(e) => setRegistration(e.target.value)} />
              {err('registration')}
            </Field>
          </div>
          <Field orientation="horizontal">
            <Switch id="enr-active" checked={active} onCheckedChange={setActive} />
            <Label htmlFor="enr-active">Cursando agora (avisos de nota e falta ligados)</Label>
          </Field>
          <Field>
            <FieldLabel>Cor</FieldLabel>
            <ColorPicker value={color} onChange={setColor} />
          </Field>

          <FieldSet>
            <FieldLegend>Regra de aprovação</FieldLegend>
            <FieldDescription>
              Padrão da graduação da UFRN: média 6; entre 5 e 6 aprova se toda unidade tiver pelo menos 3; reposição troca a menor
              nota; frequência mínima de 75%.
            </FieldDescription>
            <Field>
              <FieldLabel htmlFor="enr-scale">Avaliação por</FieldLabel>
              <Select value={scale} onValueChange={(v) => setScale(v as Scale)}>
                <SelectTrigger id="enr-scale"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="NUMERIC">Nota de 0 a 10</SelectItem>
                  <SelectItem value="CONCEPT">Conceito (A, B, C aprovam)</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <div className="grid grid-cols-2 gap-4">
              {scale === 'NUMERIC' && (
                <>
                  <Field>
                    <FieldLabel htmlFor="enr-pass">Média para aprovar</FieldLabel>
                    <Input id="enr-pass" inputMode="decimal" value={passAverage} className="text-base md:text-sm"
                      onChange={(e) => setPassAverage(e.target.value)} />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="enr-low">Média mínima</FieldLabel>
                    <Input id="enr-low" inputMode="decimal" value={lowAverage} className="text-base md:text-sm"
                      onChange={(e) => setLowAverage(e.target.value)} />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="enr-unit">Nota mínima por unidade</FieldLabel>
                    <Input id="enr-unit" inputMode="decimal" value={minUnit} className="text-base md:text-sm"
                      onChange={(e) => setMinUnit(e.target.value)} />
                  </Field>
                </>
              )}
              <Field>
                <FieldLabel htmlFor="enr-att">Frequência mínima (%)</FieldLabel>
                <Input id="enr-att" inputMode="numeric" value={minAttendance} className="text-base md:text-sm"
                  onChange={(e) => setMinAttendance(e.target.value)} />
              </Field>
            </div>
            {err('rules')}
          </FieldSet>

          <FieldSet>
            <FieldLegend>Carga horária exigida (horas)</FieldLegend>
            <FieldDescription>Está no histórico do SIGAA; a importação preenche sozinha.</FieldDescription>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <Field>
                <FieldLabel htmlFor="enr-mand">Obrigatórias</FieldLabel>
                <Input id="enr-mand" inputMode="numeric" value={mandatory} className="text-base md:text-sm"
                  onChange={(e) => setMandatory(e.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="enr-opt">Optativas</FieldLabel>
                <Input id="enr-opt" inputMode="numeric" value={optional} className="text-base md:text-sm"
                  onChange={(e) => setOptional(e.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="enr-comp">Complementares</FieldLabel>
                <Input id="enr-comp" inputMode="numeric" value={complementary} className="text-base md:text-sm"
                  onChange={(e) => setComplementary(e.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="enr-comp-done">Complementares feitas</FieldLabel>
                <Input id="enr-comp-done" inputMode="numeric" value={complementaryDone} className="text-base md:text-sm"
                  onChange={(e) => setComplementaryDone(e.target.value)} />
              </Field>
            </div>
          </FieldSet>
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={() => onDone()}>Cancelar</Button>
        <Button type="submit" disabled={save.isPending || !course.trim()}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}
