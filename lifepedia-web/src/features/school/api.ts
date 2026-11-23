import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  AssessmentInput,
  CourseDetail,
  CourseInput,
  Enrollment,
  EnrollmentDetail,
  EnrollmentInput,
  GradesInput,
  HistoryRead,
  ImportCourse,
  ImportResult,
  Hours,
  Overview,
} from './types'

const BASE = '/api/school'

/** Chaves sob 'school'; mutações invalidam o módulo, as tarefas (provas viram tarefa) e as notas (pasta da matéria). */
const keys = {
  all: ['school'] as const,
  overview: ['school', 'overview'] as const,
  enrollment: (id: string) => ['school', 'enrollment', id] as const,
  course: (id: string) => ['school', 'course', id] as const,
}

function useSchoolMutation<TVars, TResult = void>(fn: (vars: TVars) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      client.invalidateQueries({ queryKey: keys.all })
      client.invalidateQueries({ queryKey: ['tasks'] })
      client.invalidateQueries({ queryKey: ['notes'] })
    },
  })
}

export function useSchool(enabled = true) {
  return useQuery({ queryKey: keys.overview, queryFn: () => api<Overview>(BASE), enabled })
}

export function useEnrollment(id: string | null) {
  return useQuery({
    queryKey: keys.enrollment(id ?? ''),
    queryFn: () => api<EnrollmentDetail>(`${BASE}/enrollments/${id}`),
    enabled: !!id,
  })
}

export function useCourse(id: string) {
  return useQuery({ queryKey: keys.course(id), queryFn: () => api<CourseDetail>(`${BASE}/courses/${id}`), enabled: !!id })
}

export function useSaveEnrollment() {
  return useSchoolMutation(({ id, ...body }: EnrollmentInput & { id?: string }) =>
    api<Enrollment>(id ? `${BASE}/enrollments/${id}` : `${BASE}/enrollments`, { method: id ? 'PUT' : 'POST', body }))
}

export function useDeleteEnrollment() {
  return useSchoolMutation((id: string) => api<void>(`${BASE}/enrollments/${id}`, { method: 'DELETE' }))
}

type ImportVars = { enrollmentId: string; courses: ImportCourse[]; required: Hours | null }

const importCourses = ({ enrollmentId, ...body }: ImportVars & { dryRun: boolean }) =>
  api<ImportResult>(`${BASE}/enrollments/${enrollmentId}/import`, { method: 'POST', body })

/** Prévia: só conta o que seria criado e atualizado (nada é gravado). */
export function usePreviewImport(vars: ImportVars) {
  return useQuery({
    queryKey: ['school', 'import-preview', vars],
    queryFn: () => importCourses({ ...vars, dryRun: true }),
    enabled: vars.courses.length > 0,
    retry: false,
    staleTime: Infinity,
  })
}

export function useImportCourses() {
  return useSchoolMutation((vars: ImportVars) => importCourses({ ...vars, dryRun: false }))
}

/** Lê o histórico com a IA (nada é gravado). */
export function useReadWithAi() {
  return useMutation({
    mutationFn: (files: File[]) => {
      const form = new FormData()
      files.forEach((f) => form.append('files', f))
      return api<HistoryRead>(`${BASE}/import/ai`, { method: 'POST', body: form })
    },
  })
}

export function useSaveCourse() {
  return useSchoolMutation(({ id, enrollmentId, ...body }: CourseInput & { id?: string; enrollmentId: string }) =>
    api<CourseDetail>(id ? `${BASE}/courses/${id}` : `${BASE}/enrollments/${enrollmentId}/courses`, { method: id ? 'PUT' : 'POST', body }))
}

export function useDeleteCourse() {
  return useSchoolMutation((id: string) => api<void>(`${BASE}/courses/${id}`, { method: 'DELETE' }))
}

export function useSaveGrades() {
  return useSchoolMutation(({ courseId, ...body }: GradesInput & { courseId: string }) =>
    api<CourseDetail>(`${BASE}/courses/${courseId}/grades`, { method: 'PUT', body }))
}

export function useNoteFolder() {
  return useSchoolMutation((courseId: string) => api<CourseDetail>(`${BASE}/courses/${courseId}/notes-folder`, { method: 'POST' }))
}

export function useSaveAssessment() {
  return useSchoolMutation(({ courseId, id, ...body }: AssessmentInput & { courseId: string; id?: string }) =>
    api<CourseDetail>(id ? `${BASE}/courses/${courseId}/assessments/${id}` : `${BASE}/courses/${courseId}/assessments`,
      { method: id ? 'PUT' : 'POST', body }))
}

export function useDeleteAssessment() {
  return useSchoolMutation(({ courseId, id }: { courseId: string; id: string }) =>
    api<CourseDetail>(`${BASE}/courses/${courseId}/assessments/${id}`, { method: 'DELETE' }))
}
