import {
  ArrowLeftIcon,
  CalendarCheck2Icon,
  ClipboardPasteIcon,
  FolderOpenIcon,
  ListChecksIcon,
  PencilIcon,
  PlusIcon,
  SquarePenIcon,
  Trash2Icon,
  TriangleAlertIcon,
  UsersIcon,
} from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useCourse, useDeleteAssessment, useDeleteCourse, useNoteFolder, useSchool } from '../api'
import { AssessmentDialog } from '../components/AssessmentDialog'
import { CourseDialog } from '../components/CourseDialog'
import { GradesDialog } from '../components/GradesDialog'
import { LinkButtons, StatusBadge, TeacherInfo } from '../components/parts'
import { PasteGradesDialog } from '../components/PasteGradesDialog'
import { ASSESSMENT_LABEL, CATEGORY_LABEL, formatGrade, isOngoing, STATUS_LABEL } from '../labels'
import type { Assessment, CourseDetail } from '../types'

type DialogName = 'edit' | 'grades' | 'paste' | 'assessment' | 'delete' | null

/** Matéria: notas e situação, avaliações, frequência, links, professor e sala, pasta de notas e grupo. */
export function CoursePage() {
  const { courseId = '' } = useParams()
  const query = useCourse(courseId)

  if (query.isError) {
    const notFound = query.error instanceof ApiError && query.error.status === 404
    return (
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">{notFound ? 'Matéria não encontrada.' : 'Não foi possível carregar a matéria.'}</p>
        <Button variant="outline" asChild><Link to="/escola"><ArrowLeftIcon /> Voltar para a Escola</Link></Button>
      </div>
    )
  }
  if (!query.data) return <Skeleton className="h-96" />
  return <CourseView detail={query.data} />
}

function CourseView({ detail }: { detail: CourseDetail }) {
  const { course, assessments, scale } = detail
  const navigate = useNavigate()
  const overview = useSchool()
  const registration = overview.data?.enrollments.find((e) => e.id === course.enrollmentId)?.registration ?? null
  const remove = useDeleteCourse()
  const folder = useNoteFolder()
  const [dialog, setDialog] = useState<DialogName>(null)
  const [editing, setEditing] = useState<Assessment | null>(null)
  const close = (open: boolean) => { if (!open) { setDialog(null); setEditing(null) } }
  const back = `/escola?vinculo=${course.enrollmentId}`

  async function openFolder() {
    try {
      const saved = course.noteFolderId ? detail : await folder.mutateAsync(course.id)
      navigate(`/notas?pasta=${saved.course.noteFolderId}`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível criar a pasta.')
    }
  }

  async function deleteCourse() {
    try {
      await remove.mutateAsync(course.id)
      toast.success('Matéria excluída.')
      navigate(back)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível excluir.')
    }
  }

  const subtitle = [course.code, course.period ?? 'Aproveitada', course.classCode && `Turma ${course.classCode}`, detail.enrollmentName]
    .filter(Boolean).join(' · ')

  return (
    <div className="space-y-6">
      <Button variant="ghost" size="sm" className="-ml-2" asChild>
        <Link to={back}><ArrowLeftIcon /> Escola</Link>
      </Button>
      <PageHeader title={course.name} description={subtitle}
        actions={[
          { label: 'Lançar notas', icon: SquarePenIcon, primary: true, onSelect: () => setDialog('grades') },
          { label: 'Nova avaliação', icon: PlusIcon, onSelect: () => setDialog('assessment') },
          { label: 'Colar notas do SIGAA', icon: ClipboardPasteIcon, onSelect: () => setDialog('paste') },
          { label: course.noteFolderId ? 'Abrir pasta de notas' : 'Criar pasta de notas', icon: FolderOpenIcon, secondary: true,
            onSelect: () => void openFolder() },
          { label: 'Editar matéria', icon: PencilIcon, secondary: true, onSelect: () => setDialog('edit') },
          { label: 'Excluir matéria', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDialog('delete') },
        ]} />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <div className="space-y-4">
          <GradesCard detail={detail} onEdit={() => setDialog('grades')} />
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Provas e trabalhos</CardTitle>
              <CardDescription>Com data viram tarefa com lembrete; a nota entra na unidade escolhida.</CardDescription>
              <CardAction>
                <Button size="sm" variant="outline" onClick={() => setDialog('assessment')}><PlusIcon /> Nova</Button>
              </CardAction>
            </CardHeader>
            <CardContent>
              {assessments.length ? (
                <ul className="divide-y">
                  {assessments.map((a) => (
                    <AssessmentRow key={a.id} assessment={a} courseId={course.id}
                      unitLabel={a.unit ? course.units[a.unit - 1]?.label ?? `Unidade ${a.unit}` : null}
                      onEdit={() => { setEditing(a); setDialog('assessment') }} />
                  ))}
                </ul>
              ) : (
                <div className="flex flex-col items-center gap-3 py-6 text-center">
                  <p className="text-sm text-muted-foreground">Nenhuma avaliação. Anote as provas para receber lembrete.</p>
                  <Button size="sm" onClick={() => setDialog('assessment')}><PlusIcon /> Nova avaliação</Button>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <AttendanceCard detail={detail} onEdit={() => setDialog('edit')} />
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Informações</CardTitle>
              <CardAction>
                <Button size="icon-sm" variant="ghost" aria-label="Editar informações" onClick={() => setDialog('edit')}><PencilIcon /></Button>
              </CardAction>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <StatusBadge status={course.status} />
                <span className="text-muted-foreground">{CATEGORY_LABEL[course.category]}{course.hours ? ` · ${course.hours} h` : ''}</span>
              </div>
              <TeacherInfo teacher={course.teacher} teacherEmail={course.teacherEmail} room={course.room} schedule={course.schedule} />
              {course.links.length ? <LinkButtons links={course.links} size="default" /> : (
                <Button variant="outline" size="sm" onClick={() => setDialog('edit')}><PlusIcon /> Adicionar links (Discord, WhatsApp...)</Button>
              )}
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" size="sm" disabled={folder.isPending} onClick={() => void openFolder()}>
                  <FolderOpenIcon /> {course.noteFolderId ? 'Pasta de notas' : 'Criar pasta de notas'}
                </Button>
              </div>
              <p className="flex items-start gap-2 text-sm text-muted-foreground">
                <UsersIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
                {course.group ? <>Compartilhada com {course.group.name} (sem as notas).</> : 'Só você vê esta matéria.'}
              </p>
            </CardContent>
          </Card>
        </div>
      </div>

      <CourseDialog open={dialog === 'edit'} onOpenChange={close} enrollmentId={course.enrollmentId} course={course} />
      <GradesDialog open={dialog === 'grades'} onOpenChange={close} course={course} scale={scale} />
      <PasteGradesDialog open={dialog === 'paste'} onOpenChange={close} courses={[course]} registration={registration} />
      <AssessmentDialog open={dialog === 'assessment'} onOpenChange={close} courseId={course.id} units={course.units} assessment={editing} />
      <ConfirmDelete open={dialog === 'delete'} onOpenChange={close} onConfirm={() => void deleteCourse()}
        title={`Excluir ${course.name}?`}
        description="Apaga a matéria com as notas e avaliações. Tarefas, pasta de notas e compromisso ligados continuam nos módulos deles." />
    </div>
  )
}

function GradesCard({ detail, onEdit }: { detail: CourseDetail; onEdit: () => void }) {
  const { course, scale } = detail
  const { result } = course
  const ongoing = isOngoing(course.status)
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Notas</CardTitle>
        <CardAction>
          <Button size="icon-sm" variant="ghost" aria-label="Lançar notas" onClick={onEdit}><PencilIcon /></Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-4">
        {scale === 'CONCEPT' ? (
          <p className="text-sm">Conceito: <strong className="text-lg">{course.concept ?? '-'}</strong></p>
        ) : (
          <dl className="grid grid-cols-3 gap-3 sm:grid-cols-4 lg:grid-cols-6">
            {course.units.map((u) => (
              <div key={u.label} className="rounded-xl bg-muted/50 p-2.5">
                <dt className="truncate text-xs text-muted-foreground">{u.label}</dt>
                <dd className="text-lg font-semibold tabular-nums">{formatGrade(u.grade)}</dd>
                {u.fromAssessments && <dd className="text-[0.7rem] text-muted-foreground">das avaliações</dd>}
              </div>
            ))}
            <div className="rounded-xl bg-muted/50 p-2.5">
              <dt className="text-xs text-muted-foreground">Reposição</dt>
              <dd className="text-lg font-semibold tabular-nums">{formatGrade(course.recovery)}</dd>
            </div>
            <div className="rounded-xl bg-data/10 p-2.5">
              <dt className="text-xs text-data">{ongoing ? (result.average != null ? 'Média' : 'Parcial') : 'Média final'}</dt>
              <dd className="text-lg font-semibold tabular-nums text-data">
                {formatGrade(ongoing ? result.average ?? result.partial : course.average ?? result.average)}
              </dd>
            </div>
          </dl>
        )}
        {ongoing && result.outcome && (
          <p className="text-sm">Pelas notas: <strong>{STATUS_LABEL[result.outcome]}</strong></p>
        )}
        {result.gradeRisk && (
          <p className="flex items-center gap-2 text-sm font-medium text-expense">
            <TriangleAlertIcon className="size-4 shrink-0" aria-hidden /> Média abaixo do necessário para aprovar.
          </p>
        )}
        {!course.units.length && scale === 'NUMERIC' && ongoing && (
          <Button variant="outline" size="sm" onClick={onEdit}><PlusIcon /> Adicionar unidades</Button>
        )}
      </CardContent>
    </Card>
  )
}

function AttendanceCard({ detail, onEdit }: { detail: CourseDetail; onEdit: () => void }) {
  const { attendance, absences, attendancePercent, result } = detail.course
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Frequência</CardTitle>
        {attendance && (
          <CardAction>
            <Button size="sm" variant="ghost" asChild><Link to="/frequencia"><CalendarCheck2Icon /> Abrir</Link></Button>
          </CardAction>
        )}
      </CardHeader>
      <CardContent className="space-y-2 text-sm">
        {attendance ? (
          <>
            <p>{attendance.presences} presença(s) e {attendance.absences} falta(s) em {attendance.name}.</p>
            <p className={cn(attendance.remaining < 0 ? 'text-expense' : result.absenceRisk ? 'font-medium text-amber-700 dark:text-amber-400'
              : 'text-muted-foreground')}>
              {attendance.remaining < 0 ? `Passou ${-attendance.remaining} falta(s) do limite.` : `Cabem mais ${attendance.remaining} falta(s).`}
            </p>
          </>
        ) : (
          <div className="space-y-2">
            <p className="text-muted-foreground">Ligue a um compromisso da Frequência para ver presenças e faltas aqui.</p>
            <Button variant="outline" size="sm" onClick={onEdit}><CalendarCheck2Icon /> Ligar compromisso</Button>
          </div>
        )}
        {(absences != null || attendancePercent != null) && (
          <p className="text-muted-foreground">
            No SIGAA: {[absences != null && `${absences} falta(s)`, attendancePercent != null && `${formatGrade(attendancePercent)}% de frequência`]
              .filter(Boolean).join(', ')}.
          </p>
        )}
      </CardContent>
    </Card>
  )
}

function AssessmentRow({ assessment, courseId, unitLabel, onEdit }: {
  assessment: Assessment
  courseId: string
  unitLabel: string | null
  onEdit: () => void
}) {
  const remove = useDeleteAssessment()
  const details = [
    ASSESSMENT_LABEL[assessment.kind],
    assessment.dueDate && `${formatDate(assessment.dueDate)}${assessment.dueTime ? ` ${assessment.dueTime}` : ''}`,
    unitLabel,
    assessment.weight !== 1 && `peso ${formatGrade(assessment.weight)}`,
  ].filter(Boolean).join(' · ')

  return (
    <li className="flex items-center gap-3 py-2.5">
      <button type="button" onClick={onEdit} className="min-w-0 flex-1 text-left">
        <span className="block truncate text-sm font-medium">{assessment.title}</span>
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          {details}
          {assessment.taskId && <ListChecksIcon className="size-3.5 shrink-0" aria-label="Virou tarefa" />}
        </span>
      </button>
      <span className="shrink-0 text-sm font-semibold tabular-nums">{formatGrade(assessment.grade)}</span>
      <ConfirmDelete title={`Excluir ${assessment.title}?`} description="A tarefa criada por ela continua nas Tarefas."
        onConfirm={() => remove.mutate({ courseId, id: assessment.id }, {
          onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Não foi possível excluir.'),
        })} />
    </li>
  )
}
