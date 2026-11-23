import type { AssessmentKind, Category, CourseStatus, EnrollmentKind, LinkKind, Rules } from './types'

export const KIND_LABEL: Record<EnrollmentKind, string> = {
  UNDERGRADUATE: 'Graduação',
  SECOND_DEGREE: 'Segunda graduação',
  MASTERS: 'Mestrado',
  DOCTORATE: 'Doutorado',
  SPECIALIZATION: 'Especialização',
  TECHNICAL: 'Técnico',
  OTHER: 'Outro',
}

export const CATEGORY_LABEL: Record<Category, string> = {
  MANDATORY: 'Obrigatória',
  OPTIONAL: 'Optativa',
  ELECTIVE: 'Eletiva',
  COMPLEMENTARY: 'Complementar',
}

/** Siglas do SIGAA com o texto para quem lê. */
export const STATUS_LABEL: Record<CourseStatus, string> = {
  MATR: 'Cursando',
  APR: 'Aprovado',
  APRN: 'Aprovado por nota',
  REC: 'Em recuperação',
  REP: 'Reprovado',
  REPF: 'Reprovado por falta',
  REPMF: 'Reprovado por média e falta',
  REPN: 'Reprovado por nota',
  REPNF: 'Reprovado por nota e falta',
  CANC: 'Cancelado',
  DISP: 'Dispensado',
  TRANC: 'Trancado',
  TRANS: 'Transferido',
  INCORP: 'Incorporado',
  CUMP: 'Cumprido',
}

export const STATUSES = Object.keys(STATUS_LABEL) as CourseStatus[]

/** Espelha `SchoolCourse.Status.ongoing`. */
export const isOngoing = (status: CourseStatus) => status === 'MATR' || status === 'REC'

/** Espelha `SchoolCourse.Status.passed`. */
export const isPassed = (status: CourseStatus) => ['APR', 'APRN', 'DISP', 'TRANS', 'INCORP', 'CUMP'].includes(status)

/** Tom do selo: verde aprovado, vermelho reprovado, azul cursando, cinza o resto. */
export function statusTone(status: CourseStatus): 'income' | 'expense' | 'data' | 'muted' {
  if (isPassed(status)) return 'income'
  if (status.startsWith('REP')) return 'expense'
  if (isOngoing(status)) return 'data'
  return 'muted'
}

export const ASSESSMENT_LABEL: Record<AssessmentKind, string> = {
  EXAM: 'Prova',
  ASSIGNMENT: 'Trabalho',
  PRESENTATION: 'Apresentação',
  QUIZ: 'Exercício',
  OTHER: 'Outra',
}

export const LINK_LABEL: Record<LinkKind, string> = {
  DISCORD: 'Discord',
  WHATSAPP: 'WhatsApp',
  TELEGRAM: 'Telegram',
  CLASSROOM: 'Classroom',
  DRIVE: 'Drive',
  MEET: 'Meet',
  SITE: 'Site',
  OTHER: 'Outro',
}

/** Tipo do link pelo endereço colado. */
export function guessLinkKind(url: string): LinkKind {
  const host = url.toLowerCase()
  if (host.includes('discord')) return 'DISCORD'
  if (host.includes('whatsapp') || host.includes('wa.me')) return 'WHATSAPP'
  if (host.includes('t.me') || host.includes('telegram')) return 'TELEGRAM'
  if (host.includes('classroom.google')) return 'CLASSROOM'
  if (host.includes('drive.google') || host.includes('docs.google')) return 'DRIVE'
  if (host.includes('meet.google') || host.includes('zoom.us') || host.includes('teams.microsoft')) return 'MEET'
  return 'SITE'
}

/** Regra padrão de cada tipo de vínculo (graduação da UFRN; pós por conceito). */
export function defaultRules(kind: EnrollmentKind): Rules {
  if (kind === 'MASTERS' || kind === 'DOCTORATE') {
    return { scale: 'CONCEPT', passAverage: 6, lowAverage: 5, minUnit: 3, minAttendance: 75 }
  }
  return { scale: 'NUMERIC', passAverage: 6, lowAverage: 5, minUnit: 3, minAttendance: 75 }
}

const number = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 2 })

/** Nota com vírgula ("8,5"); traço sem nota. */
export function formatGrade(value: number | null | undefined) {
  return value == null ? '-' : number.format(value)
}

/** "8,5" ou "8.5" → 8.5; vazio → null; inválido → NaN. */
export function parseGrade(raw: string): number | null {
  const text = raw.trim().replace(',', '.')
  if (!text) return null
  const value = Number(text)
  return Number.isFinite(value) ? value : Number.NaN
}

/** Semestre como a API aceita (2026.1). */
export const PERIOD_PATTERN = /^\d{4}\.[1-4]$/

/** Semestre do dia de hoje (jan a jun = .1). */
export function currentPeriod() {
  const now = new Date()
  return `${now.getFullYear()}.${now.getMonth() < 6 ? 1 : 2}`
}
