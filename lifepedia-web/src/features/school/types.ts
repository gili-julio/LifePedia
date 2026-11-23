/** Espelha `SchoolEnrollment.Kind`. */
export type EnrollmentKind = 'UNDERGRADUATE' | 'SECOND_DEGREE' | 'MASTERS' | 'DOCTORATE' | 'SPECIALIZATION' | 'TECHNICAL' | 'OTHER'

/** Espelha `SchoolEnrollment.Scale`. */
export type Scale = 'NUMERIC' | 'CONCEPT'

/** Espelha `SchoolCourse.Category`. */
export type Category = 'MANDATORY' | 'OPTIONAL' | 'ELECTIVE' | 'COMPLEMENTARY'

/** Espelha `SchoolCourse.Status` (siglas do SIGAA). */
export type CourseStatus =
  | 'MATR' | 'APR' | 'APRN' | 'REC' | 'REP' | 'REPF' | 'REPMF' | 'REPN' | 'REPNF' | 'CANC' | 'DISP' | 'TRANC' | 'TRANS'
  | 'INCORP' | 'CUMP'

/** Espelha `SchoolAssessment.Kind`. */
export type AssessmentKind = 'EXAM' | 'ASSIGNMENT' | 'PRESENTATION' | 'QUIZ' | 'OTHER'

/** Espelha `SchoolDtos.LinkKind`. */
export type LinkKind = 'DISCORD' | 'WHATSAPP' | 'TELEGRAM' | 'CLASSROOM' | 'DRIVE' | 'MEET' | 'SITE' | 'OTHER'

export type Rules = { scale: Scale; passAverage: number; lowAverage: number; minUnit: number; minAttendance: number }

export type Hours = { mandatory: number | null; optional: number | null; complementary: number | null }

export type SchoolLink = { label: string; url: string; kind: LinkKind }

export type Unit = { label: string; grade: number | null }

/** `fromAssessments` = nota veio das provas com nota daquela unidade. */
export type UnitView = { label: string; grade: number | null; typed: number | null; fromAssessments: boolean }

/** `remaining` = faltas que ainda cabem (negativo = passou). */
export type AttendanceView = { commitmentId: string; name: string; presences: number; absences: number; remaining: number }

export type ResultView = { partial: number | null; average: number | null; outcome: CourseStatus | null; gradeRisk: boolean; absenceRisk: boolean }

export type GroupRef = { id: string; name: string }

export type Assessment = {
  id: string
  title: string
  kind: AssessmentKind
  dueDate: string | null
  dueTime: string | null
  unit: number | null
  weight: number
  grade: number | null
  taskId: string | null
}

export type Course = {
  id: string
  enrollmentId: string
  period: string | null
  code: string | null
  name: string
  hours: number | null
  classCode: string | null
  category: Category
  status: CourseStatus
  attendancePercent: number | null
  average: number | null
  concept: string | null
  units: UnitView[]
  recovery: number | null
  absences: number | null
  teacher: string | null
  teacherEmail: string | null
  room: string | null
  schedule: string | null
  links: SchoolLink[]
  group: GroupRef | null
  noteFolderId: string | null
  attendance: AttendanceView | null
  result: ResultView
  next: Assessment | null
}

export type CourseDetail = { course: Course; assessments: Assessment[]; enrollmentName: string; scale: Scale }

export type Enrollment = {
  id: string
  kind: EnrollmentKind
  course: string
  institution: string | null
  registration: string | null
  startPeriod: string | null
  active: boolean
  color: string
  rules: Rules
  required: Hours
  complementaryDone: number
  /** Média final ponderada pela carga horária (calculada pela API) */
  index: number | null
  currentPeriod: string | null
  courses: number
  atRisk: number
}

export type PeriodView = { period: string; average: number | null; hours: number; courses: number }

export type ProgressView = {
  mandatoryDone: number
  optionalDone: number
  complementaryDone: number
  ongoing: number
  totalDone: number
  totalRequired: number | null
  termsLeft: number | null
}

export type EnrollmentDetail = { enrollment: Enrollment; courses: Course[]; periods: PeriodView[]; progress: ProgressView }

export type SharedCourse = {
  id: string
  name: string
  code: string | null
  period: string | null
  ownerName: string
  group: GroupRef
  teacher: string | null
  teacherEmail: string | null
  room: string | null
  schedule: string | null
  links: SchoolLink[]
  upcoming: { title: string; kind: AssessmentKind; dueDate: string; dueTime: string | null }[]
}

export type Overview = { enrollments: Enrollment[]; shared: SharedCourse[]; aiEnabled: boolean }

export type EnrollmentInput = {
  kind: EnrollmentKind
  course: string
  institution: string | null
  registration: string | null
  startPeriod: string | null
  active: boolean
  color: string
  rules: Rules
  required: Hours | null
  complementaryDone: number | null
}

export type CourseInput = {
  period: string | null
  code: string | null
  name: string
  hours: number | null
  classCode: string | null
  category: Category
  status: CourseStatus
  teacher: string | null
  teacherEmail: string | null
  room: string | null
  schedule: string | null
  links: SchoolLink[]
  commitmentId: string | null
  groupId: string | null
}

export type GradesInput = {
  units: Unit[]
  recovery: number | null
  absences: number | null
  average?: number | null
  attendance?: number | null
  concept?: string | null
  status?: CourseStatus | null
}

/** Matéria lida do histórico (PDF do SIGAA no navegador ou IA). */
export type ImportCourse = {
  period: string | null
  code: string | null
  name: string
  hours: number | null
  classCode: string | null
  category: Category
  status: CourseStatus
  attendance: number | null
  average: number | null
  concept: string | null
  teacher: string | null
}

export type HistoryRead = { course: string | null; institution: string | null; required: Hours | null; courses: ImportCourse[] }

export type ImportResult = { created: number; updated: number }

export type AssessmentInput = {
  title: string
  kind: AssessmentKind
  dueDate: string | null
  dueTime: string | null
  unit: number | null
  weight: number
  grade: number | null
  createTask: boolean
  reminderMinutes: number | null
}
