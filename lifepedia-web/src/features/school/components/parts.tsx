import { ExternalLinkIcon, MailIcon, MapPinIcon, TriangleAlertIcon, UserRoundIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import { ASSESSMENT_LABEL, formatGrade, STATUS_LABEL, statusTone } from '../labels'
import type { Course, CourseStatus, SchoolLink, SharedCourse } from '../types'

const TONE = {
  income: 'bg-income/15 text-income',
  expense: 'bg-expense/15 text-expense',
  data: 'bg-data/15 text-data',
  muted: 'bg-muted text-muted-foreground',
}

/** Situação da matéria com texto (nunca só cor). */
export function StatusBadge({ status, className }: { status: CourseStatus; className?: string }) {
  return <Badge variant="secondary" className={cn('shrink-0 border-0', TONE[statusTone(status)], className)}>{STATUS_LABEL[status]}</Badge>
}

const isSafe = (url: string) => /^https?:\/\//i.test(url)

/** Links de acesso rápido (só http/https, nova aba). */
export function LinkButtons({ links, size = 'sm' }: { links: SchoolLink[]; size?: 'sm' | 'default' }) {
  const safe = links.filter((l) => isSafe(l.url))
  if (!safe.length) return null
  return (
    <div className="flex flex-wrap gap-2">
      {safe.map((l, i) => (
        <Button key={i} variant="outline" size={size} asChild>
          <a href={l.url} target="_blank" rel="noopener noreferrer"><ExternalLinkIcon /> {l.label}</a>
        </Button>
      ))}
    </div>
  )
}

/** Professor, e-mail e sala em linhas curtas. */
export function TeacherInfo({ teacher, teacherEmail, room, schedule }: Pick<Course, 'teacher' | 'teacherEmail' | 'room' | 'schedule'>) {
  if (!teacher && !teacherEmail && !room && !schedule) return null
  return (
    <ul className="space-y-1 text-sm text-muted-foreground">
      {teacher && <li className="flex min-w-0 items-center gap-2"><UserRoundIcon className="size-4 shrink-0" aria-hidden /><span className="truncate">{teacher}</span></li>}
      {teacherEmail && (
        <li className="flex min-w-0 items-center gap-2">
          <MailIcon className="size-4 shrink-0" aria-hidden />
          <a href={`mailto:${teacherEmail}`} className="truncate underline-offset-4 hover:underline">{teacherEmail}</a>
        </li>
      )}
      {(room || schedule) && (
        <li className="flex min-w-0 items-center gap-2">
          <MapPinIcon className="size-4 shrink-0" aria-hidden />
          <span className="truncate">{[room, schedule].filter(Boolean).join(' · ')}</span>
        </li>
      )}
    </ul>
  )
}

/** Cartão da matéria do semestre atual: notas parciais, faltas, próxima avaliação e risco. */
export function CourseCard({ course }: { course: Course }) {
  const { result, attendance } = course
  const risk = result.gradeRisk || result.absenceRisk
  return (
    <Card size="sm" className={cn(risk && 'ring-expense/40')}>
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="min-w-0 text-base">
            <Link to={`/escola/materias/${course.id}`} className="line-clamp-2 underline-offset-4 hover:underline">{course.name}</Link>
          </CardTitle>
          <StatusBadge status={result.outcome && course.status === 'MATR' && result.average != null ? result.outcome : course.status} />
        </div>
        <p className="text-xs text-muted-foreground">{[course.code, course.classCode && `Turma ${course.classCode}`, course.hours && `${course.hours} h`].filter(Boolean).join(' · ')}</p>
      </CardHeader>
      <CardContent className="space-y-3">
        {course.units.length > 0 && (
          <dl className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
            {course.units.map((u) => (
              <div key={u.label}>
                <dt className="text-xs text-muted-foreground">{u.label}</dt>
                <dd className="font-medium tabular-nums">{formatGrade(u.grade)}</dd>
              </div>
            ))}
            <div>
              <dt className="text-xs text-muted-foreground">{result.average != null ? 'Média' : 'Parcial'}</dt>
              <dd className="font-semibold tabular-nums">{formatGrade(result.average ?? result.partial)}</dd>
            </div>
          </dl>
        )}
        {course.concept && <p className="text-sm">Conceito <strong>{course.concept}</strong></p>}
        {attendance && (
          <p className={cn('text-sm', attendance.remaining < 0 ? 'text-expense' : result.absenceRisk ? 'text-amber-700 dark:text-amber-400' : 'text-muted-foreground')}>
            {attendance.absences} falta(s) · {attendance.remaining < 0 ? `passou ${-attendance.remaining} do limite` : `cabem mais ${attendance.remaining}`}
          </p>
        )}
        {course.next && (
          <p className="text-sm text-muted-foreground">
            Próxima: <span className="text-foreground">{course.next.title}</span>
            {course.next.dueDate ? ` em ${formatDate(course.next.dueDate)}` : ''}
          </p>
        )}
        {risk && (
          <p className="flex items-center gap-1.5 text-sm font-medium text-expense">
            <TriangleAlertIcon className="size-4 shrink-0" aria-hidden />
            {result.gradeRisk ? 'Média abaixo do necessário' : 'Faltas perto do limite'}
          </p>
        )}
        <LinkButtons links={course.links} />
      </CardContent>
    </Card>
  )
}

/** Matéria que alguém do grupo compartilhou (sem notas). */
export function SharedCourseCard({ course }: { course: SharedCourse }) {
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle className="text-base">{course.name}</CardTitle>
        <p className="text-xs text-muted-foreground">
          {[course.code, course.period].filter(Boolean).join(' · ')}{course.code || course.period ? ' · ' : ''}
          {course.ownerName} em {course.group.name}
        </p>
      </CardHeader>
      <CardContent className="space-y-3">
        <TeacherInfo teacher={course.teacher} teacherEmail={course.teacherEmail} room={course.room} schedule={course.schedule} />
        {course.upcoming.length > 0 && (
          <ul className="space-y-1 text-sm">
            {course.upcoming.map((a, i) => (
              <li key={i} className="flex justify-between gap-2">
                <span className="min-w-0 truncate">{ASSESSMENT_LABEL[a.kind]}: {a.title}</span>
                <span className="shrink-0 text-muted-foreground">{formatDate(a.dueDate)}{a.dueTime ? ` ${a.dueTime}` : ''}</span>
              </li>
            ))}
          </ul>
        )}
        <LinkButtons links={course.links} />
      </CardContent>
    </Card>
  )
}
