import { Loader2Icon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useCommitments } from '@/features/attendance/api'
import type { Commitment } from '@/features/attendance/types'
import { useGroups } from '@/features/groups/api'
import type { GroupSummary } from '@/features/groups/types'
import { ApiError } from '@/lib/api'
import { useSaveCourse } from '../api'
import { CATEGORY_LABEL, currentPeriod, guessLinkKind, LINK_LABEL, STATUS_LABEL, STATUSES } from '../labels'
import type { Category, Course, CourseStatus, LinkKind, SchoolLink } from '../types'

const NONE = 'none'
const MAX_LINKS = 12

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  enrollmentId: string
  /** Editar; sem ela, cadastra */
  course?: Course | null
}

/** Cadastrar ou editar matéria: dados do SIGAA, professor, sala, links, compromisso da Frequência e grupo. */
export function CourseDialog({ open, onOpenChange, enrollmentId, course }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{course ? 'Editar matéria' : 'Nova matéria'}</DialogTitle>
          <DialogDescription>Ligue ao compromisso da Frequência para ver as faltas aqui.</DialogDescription>
        </DialogHeader>
        {open && <LoadedForm enrollmentId={enrollmentId} course={course ?? null} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

/** Abre só com compromissos e grupos carregados (o Select não mostra valor que chega depois). */
function LoadedForm({ enrollmentId, course, onDone }: { enrollmentId: string; course: Course | null; onDone: () => void }) {
  const commitments = useCommitments()
  const groups = useGroups()
  if (commitments.isLoading || groups.isLoading) return <Skeleton className="h-96" />
  return <CourseForm enrollmentId={enrollmentId} course={course} commitments={commitments.data ?? []}
    groups={(groups.data ?? []).filter((g) => g.role)} onDone={onDone} />
}

function CourseForm({ enrollmentId, course, commitments, groups, onDone }: {
  enrollmentId: string
  course: Course | null
  commitments: Commitment[]
  groups: GroupSummary[]
  onDone: () => void
}) {
  const navigate = useNavigate()
  const save = useSaveCourse()
  const [period, setPeriod] = useState(course ? course.period ?? '' : currentPeriod())
  const [code, setCode] = useState(course?.code ?? '')
  const [name, setName] = useState(course?.name ?? '')
  const [hours, setHours] = useState(course?.hours ? String(course.hours) : '')
  const [classCode, setClassCode] = useState(course?.classCode ?? '')
  const [category, setCategory] = useState<Category>(course?.category ?? 'MANDATORY')
  const [status, setStatus] = useState<CourseStatus>(course?.status ?? 'MATR')
  const [teacher, setTeacher] = useState(course?.teacher ?? '')
  const [teacherEmail, setTeacherEmail] = useState(course?.teacherEmail ?? '')
  const [room, setRoom] = useState(course?.room ?? '')
  const [schedule, setSchedule] = useState(course?.schedule ?? '')
  const [links, setLinks] = useState<SchoolLink[]>(course?.links ?? [])
  const [commitmentId, setCommitmentId] = useState<string | null>(course?.attendance?.commitmentId ?? null)
  const [groupId, setGroupId] = useState<string | null>(course?.group?.id ?? null)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const setLink = (index: number, patch: Partial<SchoolLink>) =>
    setLinks((list) => list.map((l, i) => (i === index ? { ...l, ...patch } : l)))

  async function submit() {
    try {
      const saved = await save.mutateAsync({
        id: course?.id,
        enrollmentId,
        period: period.trim() || null,
        code: code.trim().toUpperCase() || null,
        name: name.trim(),
        hours: hours.trim() ? Number(hours) : null,
        classCode: classCode.trim() || null,
        category,
        status,
        teacher: teacher.trim() || null,
        teacherEmail: teacherEmail.trim() || null,
        room: room.trim() || null,
        schedule: schedule.trim() || null,
        links: links.filter((l) => l.url.trim()).map((l) => ({ ...l, label: l.label.trim() || LINK_LABEL[l.kind], url: l.url.trim() })),
        commitmentId,
        groupId,
      })
      toast.success(course ? 'Matéria salva.' : 'Matéria criada.')
      onDone()
      if (!course) navigate(`/escola/materias/${saved.course.id}`)
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        toast.error(error.message)
      } else toast.error('Não foi possível salvar.')
    }
  }

  const err = (key: string) => errors[key] && <p className="text-sm text-destructive">{errors[key]}</p>
  const linkError = Object.entries(errors).find(([k]) => k.startsWith('links'))?.[1]

  return (
    <form className="contents" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <Field data-invalid={!!errors.name}>
            <FieldLabel htmlFor="crs-name">Nome</FieldLabel>
            <Input id="crs-name" placeholder="Ex.: Processos de Software" maxLength={200} value={name} className="text-base md:text-sm"
              onChange={(e) => setName(e.target.value)} />
            {err('name')}
          </Field>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Field data-invalid={!!errors.code}>
              <FieldLabel htmlFor="crs-code">Código</FieldLabel>
              <Input id="crs-code" placeholder="DIM0510" maxLength={20} value={code} autoCapitalize="characters"
                className="text-base uppercase md:text-sm" onChange={(e) => setCode(e.target.value)} />
              {err('code')}
            </Field>
            <Field data-invalid={!!errors.period}>
              <FieldLabel htmlFor="crs-period">Semestre</FieldLabel>
              <Input id="crs-period" inputMode="decimal" placeholder="2026.2" maxLength={6} value={period}
                className="text-base md:text-sm" onChange={(e) => setPeriod(e.target.value)} />
              {err('period')}
            </Field>
            <Field data-invalid={!!errors.hours}>
              <FieldLabel htmlFor="crs-hours">Carga (h)</FieldLabel>
              <Input id="crs-hours" inputMode="numeric" placeholder="60" value={hours} className="text-base md:text-sm"
                onChange={(e) => setHours(e.target.value.replace(/\D/g, ''))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="crs-class">Turma</FieldLabel>
              <Input id="crs-class" placeholder="01" maxLength={20} value={classCode} className="text-base md:text-sm"
                onChange={(e) => setClassCode(e.target.value)} />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field>
              <FieldLabel htmlFor="crs-cat">Tipo</FieldLabel>
              <Select value={category} onValueChange={(v) => setCategory(v as Category)}>
                <SelectTrigger id="crs-cat"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(CATEGORY_LABEL).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="crs-status">Situação</FieldLabel>
              <Select value={status} onValueChange={(v) => setStatus(v as CourseStatus)}>
                <SelectTrigger id="crs-status"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {STATUSES.map((s) => <SelectItem key={s} value={s}>{STATUS_LABEL[s]} ({s})</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          </div>

          <FieldSet>
            <FieldLegend>Professor e sala</FieldLegend>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="crs-teacher">Professor</FieldLabel>
                <Input id="crs-teacher" maxLength={160} value={teacher} className="text-base md:text-sm"
                  onChange={(e) => setTeacher(e.target.value)} />
              </Field>
              <Field data-invalid={!!errors.teacherEmail}>
                <FieldLabel htmlFor="crs-email">E-mail do professor</FieldLabel>
                <Input id="crs-email" type="email" maxLength={160} value={teacherEmail} className="text-base md:text-sm"
                  onChange={(e) => setTeacherEmail(e.target.value)} />
                {err('teacherEmail')}
              </Field>
              <Field>
                <FieldLabel htmlFor="crs-room">Sala</FieldLabel>
                <Input id="crs-room" placeholder="Ex.: A308 (IMD)" maxLength={80} value={room} className="text-base md:text-sm"
                  onChange={(e) => setRoom(e.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="crs-schedule">Horário</FieldLabel>
                <Input id="crs-schedule" placeholder="Ex.: 35T12" maxLength={80} value={schedule} className="text-base md:text-sm"
                  onChange={(e) => setSchedule(e.target.value)} />
              </Field>
            </div>
          </FieldSet>

          <FieldSet>
            <FieldLegend>Links de acesso rápido</FieldLegend>
            <FieldDescription>Grupo do Discord ou WhatsApp, Classroom, pasta do Drive, sala do Meet...</FieldDescription>
            {links.map((link, index) => (
              <div key={index} className="grid grid-cols-[1fr_auto] gap-2 rounded-xl border p-2 sm:grid-cols-[8rem_1fr_auto]">
                <Select value={link.kind} onValueChange={(v) => setLink(index, { kind: v as LinkKind })}>
                  <SelectTrigger aria-label="Tipo do link" className="max-sm:col-span-2"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(LINK_LABEL).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}
                  </SelectContent>
                </Select>
                <div className="grid min-w-0 gap-2 sm:grid-cols-[8rem_1fr]">
                  <Input aria-label="Nome do link" placeholder={LINK_LABEL[link.kind]} maxLength={40} value={link.label}
                    className="text-base md:text-sm" onChange={(e) => setLink(index, { label: e.target.value })} />
                  <Input aria-label="Endereço" placeholder="https://..." maxLength={500} value={link.url} inputMode="url"
                    className="text-base md:text-sm" onChange={(e) => setLink(index, {
                      url: e.target.value,
                      kind: link.kind === 'SITE' || !link.url ? guessLinkKind(e.target.value) : link.kind,
                    })} />
                </div>
                <Button type="button" variant="ghost" size="icon" aria-label="Tirar link"
                  onClick={() => setLinks((list) => list.filter((_, i) => i !== index))}>
                  <Trash2Icon />
                </Button>
              </div>
            ))}
            {linkError && <p className="text-sm text-destructive">{linkError}</p>}
            {links.length < MAX_LINKS && (
              <Button type="button" variant="outline" className="w-fit"
                onClick={() => setLinks((list) => [...list, { kind: 'SITE', label: '', url: '' }])}>
                <PlusIcon /> Adicionar link
              </Button>
            )}
          </FieldSet>

          <Field>
            <FieldLabel htmlFor="crs-commitment">Compromisso da Frequência</FieldLabel>
            <Select value={commitmentId ?? NONE} onValueChange={(v) => setCommitmentId(v === NONE ? null : v)}>
              <SelectTrigger id="crs-commitment"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>Nenhum</SelectItem>
                {commitments.map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <FieldDescription>
              {commitments.length ? 'Presenças e faltas marcadas na Frequência aparecem na matéria.'
                : 'Você ainda não tem compromissos na Frequência. Crie lá (ou importe do SIGAA) e ligue aqui.'}
            </FieldDescription>
          </Field>
          {groups.length > 0 && (
            <Field>
              <FieldLabel htmlFor="crs-group">Compartilhar com um grupo</FieldLabel>
              <Select value={groupId ?? NONE} onValueChange={(v) => setGroupId(v === NONE ? null : v)}>
                <SelectTrigger id="crs-group"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Só eu</SelectItem>
                  {groups.map((g) => <SelectItem key={g.id} value={g.id}>{g.name}</SelectItem>)}
                </SelectContent>
              </Select>
              <FieldDescription>Quem está no grupo vê links, professor, sala e próximas avaliações. Nunca as notas.</FieldDescription>
            </Field>
          )}
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" disabled={save.isPending || !name.trim()}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}
