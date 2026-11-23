import { STATUSES } from '../labels'
import type { Category, CourseStatus, HistoryRead, Hours, ImportCourse } from '../types'

const CODE = /^[A-Z]{2,6}\d{3,5}[A-Z]?$/
const PERIOD = /^\d{4}\.[1-4]$/
const INTEGER = /^\d{1,4}$/
const TEACHER = /\(\d+\s*h\)\s*$/i

/** Símbolo antes do código no histórico do SIGAA → tipo da matéria. */
const SYMBOLS: Record<string, Category> = {
  '*': 'OPTIONAL',
  e: 'MANDATORY',
  '&': 'OPTIONAL',
  '#': 'ELECTIVE',
  '@': 'MANDATORY',
  '§': 'OPTIONAL',
  '%': 'COMPLEMENTARY',
}

const SMALL_WORDS = new Set(['a', 'as', 'ao', 'aos', 'com', 'da', 'das', 'de', 'do', 'dos', 'e', 'em', 'na', 'nas', 'no', 'nos', 'o',
  'os', 'para', 'por'])

/**
 * Lê as linhas do histórico do SIGAA (PDF remontado por `pdfLines`): curso, instituição, carga exigida e matérias. Erro com
 * mensagem em pt-BR se não achar nenhuma matéria.
 */
export function parseSigaaHistory(lines: string[]): HistoryRead {
  const courses: ImportCourse[] = []
  lines.forEach((line, index) => {
    const course = courseLine(line, lines[index - 1], lines[index + 1])
    if (course) courses.push(course)
  })
  if (!courses.length) {
    throw new Error('Não encontrei matérias no PDF. Confira se é o histórico do SIGAA (Ensino > Emitir Histórico).')
  }
  return { course: courseName(lines), institution: institution(lines), required: required(lines), courses }
}

/** Primeira letra maiúscula em cada palavra, menos as curtas ("Engenharia de Software"). */
export function titleCase(text: string) {
  return text.toLowerCase().split(/\s+/).filter(Boolean)
    .map((word, i) => (i > 0 && SMALL_WORDS.has(word) ? word : /^[ivx]+$/.test(word) ? word.toUpperCase()
      : word.charAt(0).toUpperCase() + word.slice(1)))
    .join(' ')
}

function courseLine(line: string, previous: string | undefined, next: string | undefined): ImportCourse | null {
  const cells = line.split('\t')
  const status = cells.at(-1) as CourseStatus
  if (!STATUSES.includes(status)) return null
  if (!PERIOD.test(cells[0]) && cells[0] !== '--') return null
  let i = 1
  const category = SYMBOLS[cells[i]] ?? 'MANDATORY'
  if (SYMBOLS[cells[i]]) i++
  const code = cells[i]
  if (!CODE.test(code ?? '')) return null
  i++
  const start = i
  while (i < cells.length - 1 && !(INTEGER.test(cells[i]) && INTEGER.test(cells[i + 1] ?? ''))) i++
  if (i >= cells.length - 1) return null
  const name = cells.slice(start, i).join(' ') || (previous && !previous.includes('\t') && !TEACHER.test(previous) ? previous : '')
  if (!name.trim()) return null
  const hours = Number(cells[i + 1])
  const middle = cells.slice(i + 2, -1)
  const classCode = middle[0] && middle[0] !== '--' ? middle[0] : null
  const attendance = middle.length > 2 ? grade(middle[1], 100) : null
  const final = middle.length > 1 ? middle.at(-1) ?? '' : ''
  const concept = /^[A-E]$/.test(final) ? final : null
  return {
    period: PERIOD.test(cells[0]) ? cells[0] : null,
    code,
    name: titleCase(name).slice(0, 200),
    hours: Number.isFinite(hours) ? hours : null,
    classCode,
    category,
    status,
    attendance,
    average: concept ? null : grade(final, 10),
    concept,
    teacher: next && TEACHER.test(next) && !next.includes('\t') ? teacherName(next) : null,
  }
}

function grade(raw: string | undefined, max: number): number | null {
  if (!raw || raw === '--') return null
  const value = Number(raw.replace(',', '.'))
  return Number.isFinite(value) && value >= 0 && value <= max ? value : null
}

/** "Dr. FULANO DE TAL (60h)" → "Fulano de Tal"; vários professores ficam separados por vírgula. */
function teacherName(line: string) {
  return line.split(/,\s*(?=[^,]*\(\d+\s*h\))/i)
    .map((part) => titleCase(part.replace(TEACHER, '').replace(/^(dr|dra|me|ma|msc|prof|profa)\.?\s+/i, '').trim()))
    .filter(Boolean).join(', ').slice(0, 160) || null
}

function courseName(lines: string[]): string | null {
  for (const line of lines) {
    const match = line.replace(/^curso:?\s*/i, '').match(/^([A-ZÀ-Ü][A-ZÀ-Ü ]{3,})\/[A-ZÀ-Ü]+\s+-\s+/)
    if (match) return titleCase(match[1].trim())
  }
  return null
}

/** "UFRN - Universidade Federal do Rio Grande do Norte" → "UFRN"; sem sigla, o nome. */
function institution(lines: string[]): string | null {
  const pattern = /^(?:([A-Z]{2,12})\s+-\s+)?((?:universidade|instituto federal|faculdade|centro universit).*)$/i
  for (const line of lines) {
    const match = line.split('\t')[0].match(pattern)
    if (match) return (match[1] ?? titleCase(match[2])).slice(0, 160)
  }
  return null
}

/** Linha "Exigido  2870 h  540 h  190 h  3600 h" da tabela de carga horária. */
function required(lines: string[]): Hours | null {
  const line = lines.find((l) => /^exigid[oa]\b/i.test(l))
  if (!line) return null
  const values = [...line.matchAll(/(\d+)\s*h/gi)].map((m) => Number(m[1]))
  if (values.length < 3) return null
  return { mandatory: values[0], optional: values[1], complementary: values[2] }
}
