import { STATUSES } from '../labels'
import type { CourseStatus, Unit } from '../types'

/** Notas da turma lidas do "Ver notas" do SIGAA. */
export type SigaaGrades = {
  code: string | null
  name: string | null
  period: string | null
  classCode: string | null
  units: Unit[]
  recovery: number | null
  average: number | null
  absences: number | null
  status: CourseStatus | null
}

const TITLE = /([A-Z]{2,6}\d{3,5}[A-Z]?)\s*-\s*(.+?)(?:\s*\(\d+\s*h\))?\s*-\s*Turma:?\s*(\S+)\s*\((\d{4}\.[1-4])\)/i

/**
 * Lê o texto colado da tela de notas da turma (colunas por TAB). `registration` escolhe a linha da pessoa quando vier mais
 * de uma. Erro com mensagem em pt-BR se não achar a tabela.
 */
export function parseSigaaGrades(text: string, registration?: string | null): SigaaGrades {
  const lines = text.split(/\r?\n/).map((l) => l.replace(/\s+$/, ''))
  const headerIndex = lines.findIndex((l) => /matr[íi]cula/i.test(l) && /unid/i.test(l))
  if (headerIndex < 0) throw new Error('Não encontrei a tabela de notas. Copie a tela "Ver notas" da turma inteira (Ctrl + A).')
  const header = lines[headerIndex].split('\t').map((c) => c.trim())
  const rows = lines.slice(headerIndex + 1).filter((l) => /^\s*\d{6,}\t/.test(l)).map((l) => l.split('\t').map((c) => c.trim()))
  if (!rows.length) throw new Error('Achei a tabela, mas nenhuma linha com notas. Confira se copiou a tela inteira.')
  const wanted = registration?.replace(/\D/g, '')
  const row = rows.find((r) => wanted && r[0] === wanted) ?? rows[0]
  const column = (pattern: RegExp) => header.findIndex((h) => pattern.test(h))
  const cell = (index: number) => (index >= 0 ? row[index] ?? '' : '')
  const units = header.flatMap((h, i) => (/^unid/i.test(h) ? [{ label: h.slice(0, 30), grade: grade(row[i]) }] : []))
  const status = cell(column(/^sit/i)).toUpperCase() as CourseStatus
  const absences = Number(cell(column(/^faltas/i)))
  const title = text.match(TITLE)
  return {
    code: title?.[1].toUpperCase() ?? null,
    name: title?.[2].trim() ?? null,
    classCode: title?.[3] ?? null,
    period: title?.[4] ?? null,
    units,
    recovery: grade(cell(column(/reposi|recupera|^rec\b/i))),
    average: grade(cell(column(/resultado|m[ée]dia/i))),
    absences: cell(column(/^faltas/i)) && Number.isInteger(absences) ? absences : null,
    status: STATUSES.includes(status) ? status : null,
  }
}

function grade(raw: string | undefined): number | null {
  if (!raw || raw === '--' || raw === '-') return null
  const value = Number(raw.replace(',', '.'))
  return Number.isFinite(value) && value >= 0 && value <= 10 ? value : null
}
