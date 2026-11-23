import {
  BookOpenCheckIcon,
  ClipboardPasteIcon,
  FileUpIcon,
  GaugeIcon,
  GraduationCapIcon,
  HourglassIcon,
  PencilIcon,
  PlusIcon,
  Trash2Icon,
  TriangleAlertIcon,
} from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader, type PageAction } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { StatCard } from '@/components/StatCard'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { useDeleteEnrollment, useEnrollment, useSchool } from '../api'
import { PeriodsChart } from '../components/charts'
import { CourseDialog } from '../components/CourseDialog'
import { EnrollmentDialog } from '../components/EnrollmentDialog'
import { ImportDialog } from '../components/ImportDialog'
import { CourseCard, SharedCourseCard, StatusBadge } from '../components/parts'
import { PasteGradesDialog } from '../components/PasteGradesDialog'
import { formatGrade, isOngoing, KIND_LABEL } from '../labels'
import type { Course, EnrollmentDetail, SharedCourse } from '../types'

type DialogName = 'enrollment' | 'edit' | 'course' | 'import' | 'paste' | 'delete' | null

/** Escola: vínculos (graduação, mestrado...) com o semestre atual, índice, progresso e histórico; e matérias dos grupos. */
export function SchoolPage() {
  const [params, setParams] = useSearchParams()
  const overview = useSchool()
  const enrollments = overview.data?.enrollments ?? []
  const selected = enrollments.find((e) => e.id === params.get('vinculo')) ?? enrollments.find((e) => e.active) ?? enrollments[0] ?? null
  const detail = useEnrollment(selected?.id ?? null)
  const remove = useDeleteEnrollment()
  const [dialog, setDialog] = useState<DialogName>(null)
  const close = (open: boolean) => { if (!open) setDialog(null) }
  const select = (id: string) => setParams((p) => { p.set('vinculo', id); return p }, { replace: true })

  const actions: PageAction[] = selected ? [
    { label: 'Importar histórico', icon: FileUpIcon, primary: true, onSelect: () => setDialog('import') },
    { label: 'Nova matéria', icon: PlusIcon, onSelect: () => setDialog('course') },
    { label: 'Colar notas do SIGAA', icon: ClipboardPasteIcon, onSelect: () => setDialog('paste') },
    { label: 'Novo vínculo', icon: GraduationCapIcon, secondary: true, onSelect: () => setDialog('enrollment') },
    { label: 'Editar vínculo', icon: PencilIcon, secondary: true, onSelect: () => setDialog('edit') },
    { label: 'Excluir vínculo', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDialog('delete') },
  ] : [{ label: 'Novo vínculo', icon: GraduationCapIcon, primary: true, onSelect: () => setDialog('enrollment') }]

  async function deleteEnrollment() {
    if (!selected) return
    try {
      await remove.mutateAsync(selected.id)
      setParams((p) => { p.delete('vinculo'); return p }, { replace: true })
      toast.success('Vínculo excluído.')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível excluir.')
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Escola" description="Matérias, notas, faltas e provas de cada curso, com o histórico do SIGAA."
        actions={actions} />

      {overview.isError ? <p className="text-sm text-destructive">Não foi possível carregar a Escola.</p>
        : !overview.data ? <Skeleton className="h-64" />
        : !selected ? <Empty onCreate={() => setDialog('enrollment')} />
        : (
          <>
            {enrollments.length > 1 && (
              <Tabs value={selected.id} onValueChange={select}>
                <ScrollTabsList value={selected.id}>
                  {enrollments.map((e) => (
                    <TabsTrigger key={e.id} value={e.id}>
                      <span className="size-2 rounded-full" style={{ background: e.color }} aria-hidden />
                      {e.course}
                    </TabsTrigger>
                  ))}
                </ScrollTabsList>
              </Tabs>
            )}
            {detail.isError ? <p className="text-sm text-destructive">Não foi possível carregar o vínculo.</p>
              : !detail.data ? <Skeleton className="h-96" />
              : <EnrollmentView detail={detail.data} onImport={() => setDialog('import')} onCourse={() => setDialog('course')} />}
          </>
        )}

      {!!overview.data?.shared.length && <Shared courses={overview.data.shared} />}

      <EnrollmentDialog open={dialog === 'enrollment'} onOpenChange={close} onSaved={(e) => select(e.id)} />
      {selected && (
        <>
          <EnrollmentDialog open={dialog === 'edit'} onOpenChange={close} enrollment={selected} />
          <CourseDialog open={dialog === 'course'} onOpenChange={close} enrollmentId={selected.id} />
          <ImportDialog open={dialog === 'import'} onOpenChange={close} enrollment={selected} aiEnabled={overview.data?.aiEnabled ?? false} />
          <PasteGradesDialog open={dialog === 'paste'} onOpenChange={close} registration={selected.registration}
            courses={(detail.data?.courses ?? []).filter((c) => isOngoing(c.status))} />
          <ConfirmDelete open={dialog === 'delete'} onOpenChange={close} onConfirm={() => void deleteEnrollment()}
            title={`Excluir ${selected.course}?`}
            description="Apaga o vínculo com todas as matérias, notas e avaliações. Tarefas, notas e compromissos ligados continuam nos módulos deles." />
        </>
      )}
    </div>
  )
}

function Empty({ onCreate }: { onCreate: () => void }) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
        <GraduationCapIcon className="size-8 text-muted-foreground" aria-hidden />
        <p className="max-w-md text-sm text-muted-foreground">
          Crie um vínculo para o seu curso (graduação, mestrado...) e importe o histórico do SIGAA: matérias, notas, índice e
          quanto falta para formar aparecem aqui.
        </p>
        <Button onClick={onCreate}><GraduationCapIcon /> Novo vínculo</Button>
      </CardContent>
    </Card>
  )
}

function EnrollmentView({ detail, onImport, onCourse }: { detail: EnrollmentDetail; onImport: () => void; onCourse: () => void }) {
  const { enrollment, courses, periods, progress } = detail
  const current = courses.filter((c) => isOngoing(c.status))
  const past = courses.filter((c) => !isOngoing(c.status))
  const percent = progress.totalRequired ? Math.min(100, Math.round((progress.totalDone / progress.totalRequired) * 100)) : null

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        {KIND_LABEL[enrollment.kind]}{enrollment.institution ? ` · ${enrollment.institution}` : ''}
        {enrollment.startPeriod ? ` · desde ${enrollment.startPeriod}` : ''}{!enrollment.active ? ' · encerrado' : ''}
      </p>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label={enrollment.rules.scale === 'CONCEPT' ? 'Média' : 'Índice (IRA)'} tone="data" icon={GaugeIcon}
          hint="Média final ponderada pela carga horária">
          {formatGrade(enrollment.index)}
        </StatCard>
        <StatCard label="Carga cumprida" tone="data" icon={BookOpenCheckIcon}
          hint={progress.totalRequired ? `de ${progress.totalRequired} h (${percent}%)` : 'Informe a carga exigida no vínculo'}>
          {progress.totalDone} h
        </StatCard>
        <StatCard label="Semestres que faltam" tone="data" icon={HourglassIcon}
          hint={progress.ongoing ? `${progress.ongoing} h em andamento` : 'Pelo ritmo dos semestres cursados'}>
          {progress.termsLeft ?? '-'}
        </StatCard>
        <StatCard label="Matérias em risco" tone={enrollment.atRisk ? 'expense' : 'income'} icon={TriangleAlertIcon}
          hint="Média abaixo do necessário ou faltas no limite">
          {enrollment.atRisk}
        </StatCard>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Semestre atual{enrollment.currentPeriod ? ` (${enrollment.currentPeriod})` : ''}</h2>
        {current.length ? (
          <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">{current.map((c) => <CourseCard key={c.id} course={c} />)}</div>
        ) : (
          <Card>
            <CardContent className="flex flex-col items-center gap-3 py-8 text-center">
              <p className="max-w-sm text-sm text-muted-foreground">Nenhuma matéria em andamento. Importe o histórico ou cadastre à mão.</p>
              <div className="flex flex-wrap justify-center gap-2">
                <Button onClick={onImport}><FileUpIcon /> Importar histórico</Button>
                <Button variant="outline" onClick={onCourse}><PlusIcon /> Nova matéria</Button>
              </div>
            </CardContent>
          </Card>
        )}
      </section>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Média por semestre</CardTitle>
            <CardDescription>Ponderada pela carga horária; a linha tracejada é a média para aprovar.</CardDescription>
          </CardHeader>
          <CardContent>
            {periods.some((p) => p.average != null)
              ? <PeriodsChart periods={periods} passAverage={enrollment.rules.scale === 'NUMERIC' ? enrollment.rules.passAverage : null} />
              : <p className="text-sm text-muted-foreground">Aparece quando houver matérias com média final.</p>}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Progresso do curso</CardTitle>
            <CardDescription>Horas cumpridas (aprovadas e aproveitadas) contra as exigidas.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <ProgressRow label="Obrigatórias" done={progress.mandatoryDone} required={enrollment.required.mandatory} />
            <ProgressRow label="Optativas" done={progress.optionalDone} required={enrollment.required.optional} />
            <ProgressRow label="Complementares" done={progress.complementaryDone} required={enrollment.required.complementary} />
          </CardContent>
        </Card>
      </div>

      {past.length > 0 && <History courses={past} />}
    </div>
  )
}

function ProgressRow({ label, done, required }: { label: string; done: number; required: number | null }) {
  const percent = required ? Math.min(100, Math.round((done / required) * 100)) : null
  return (
    <div className="space-y-1.5">
      <div className="flex justify-between gap-2 text-sm">
        <span>{label}</span>
        <span className="tabular-nums text-muted-foreground">{done} h{required ? ` de ${required} h (${percent}%)` : ''}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={label}
        aria-valuenow={percent ?? undefined} aria-valuemin={0} aria-valuemax={100}>
        <div className="h-full rounded-full bg-data transition-all motion-reduce:transition-none" style={{ width: `${percent ?? 0}%` }} />
      </div>
    </div>
  )
}

/** Semestres passados, do mais novo; aproveitadas (sem semestre) por último. */
function History({ courses }: { courses: Course[] }) {
  const groups = new Map<string, Course[]>()
  for (const c of courses) {
    const key = c.period ?? ''
    groups.set(key, [...(groups.get(key) ?? []), c])
  }
  const keys = [...groups.keys()].sort((a, b) => (a === '' ? 1 : b === '' ? -1 : b.localeCompare(a)))
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">Histórico</h2>
      <Card className="py-0">
        <CardContent className="divide-y px-0">
          {keys.map((key, index) => (
            <Collapsible key={key || 'none'} defaultOpen={index === 0}>
              <CollapsibleTrigger className="flex w-full items-center justify-between gap-2 px-4 py-3 text-left text-sm font-medium hover:bg-muted/50">
                <span>{key || 'Aproveitadas e dispensadas'}</span>
                <span className="text-xs font-normal text-muted-foreground">{groups.get(key)!.length} matéria(s)</span>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <ul className="divide-y border-t">
                  {groups.get(key)!.map((c) => (
                    <li key={c.id}>
                      <Link to={`/escola/materias/${c.id}`} className="flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-muted/50">
                        <span className="min-w-0 flex-1">
                          <span className="block truncate">{c.name}</span>
                          <span className="block text-xs text-muted-foreground">{[c.code, c.hours && `${c.hours} h`].filter(Boolean).join(' · ')}</span>
                        </span>
                        <span className="w-10 shrink-0 text-right font-medium tabular-nums">{c.concept ?? formatGrade(c.average)}</span>
                        <StatusBadge status={c.status} />
                      </Link>
                    </li>
                  ))}
                </ul>
              </CollapsibleContent>
            </Collapsible>
          ))}
        </CardContent>
      </Card>
    </section>
  )
}

function Shared({ courses }: { courses: SharedCourse[] }) {
  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-lg font-semibold">Dos seus grupos</h2>
        <p className="text-sm text-muted-foreground">Matérias que colegas compartilharam: links, professor, sala e próximas avaliações.</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">{courses.map((c) => <SharedCourseCard key={c.id} course={c} />)}</div>
    </section>
  )
}

