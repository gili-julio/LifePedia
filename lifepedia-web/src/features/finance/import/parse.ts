import { amountToInput, currentYearMonth, parseAmount, type YearMonth } from '@/lib/format'
import { normalizeName } from '../categories'
import { isActiveIn, valueIn } from '../fixed'
import type { Category, EntryType, RecurringEntry, Transaction } from '../types'
import { SOURCE_LABEL, type ImportSource } from './prompt'

/** Limite do endpoint batch da API. */
export const MAX_IMPORT_ITEMS = 500

/** Linha editável da prévia. */
export type ImportRow = {
  key: string
  include: boolean
  date: string
  /** Nome padronizado (editável) */
  description: string
  /** Como veio no documento; não é editável ('' = não informado) */
  originalDescription: string
  type: EntryType
  categoryId: string
  /** Fixo que o item representa ('' = ocasional) */
  fixedEntryId: string
  amount: string
  notes: string
  source: ImportSource
  /** Mês em que conta ('' = o da data) */
  month: YearMonth
  /** Mês da fatura informado pelo agente ('' = não informado) */
  invoiceMonth: YearMonth
  /** Não impedem importar */
  warnings: string[]
}

export class ImportParseError extends Error {}

/** Extrai o JSON da resposta do agente. */
function extractJson(text: string): unknown {
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i)
  const candidate = (fenced ? fenced[1] : text).trim()
  const start = candidate.search(/[[{]/)
  const end = Math.max(candidate.lastIndexOf('}'), candidate.lastIndexOf(']'))
  if (start < 0 || end <= start) throw new ImportParseError('Não encontrei um JSON no texto colado.')
  try {
    return JSON.parse(candidate.slice(start, end + 1))
  } catch {
    throw new ImportParseError('O JSON colado está inválido. Peça ao agente para responder só com o bloco JSON.')
  }
}

function parseDate(raw: unknown, referenceMonth: YearMonth): string | null {
  if (typeof raw !== 'string') return null
  const value = raw.trim()
  let match = value.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (match) return isValidDate(+match[1], +match[2], +match[3]) ? value : null
  match = value.match(/^(\d{1,2})\/(\d{1,2})(?:\/(\d{2}|\d{4}))?$/)
  if (!match) return null
  const [refYear] = referenceMonth.split('-').map(Number)
  const day = +match[1]
  const month = +match[2]
  const year = match[3] ? (match[3].length === 2 ? 2000 + +match[3] : +match[3]) : refYear
  if (!isValidDate(year, month, day)) return null
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

/** `AAAA-MM` ou `MM/AAAA`. */
function parseMonth(raw: unknown): YearMonth | null {
  if (typeof raw !== 'string') return null
  const value = raw.trim()
  const iso = value.match(/^(\d{4})-(\d{1,2})$/)
  const br = value.match(/^(\d{1,2})\/(\d{4})$/)
  const year = iso ? +iso[1] : br ? +br[2] : 0
  const month = iso ? +iso[2] : br ? +br[1] : 0
  return month >= 1 && month <= 12 ? `${year}-${String(month).padStart(2, '0')}` : null
}

function isValidDate(year: number, month: number, day: number) {
  const date = new Date(year, month - 1, day)
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
}

function parseType(raw: unknown): EntryType | null {
  const value = normalizeName(String(raw ?? ''))
  if (['expense', 'despesa', 'gasto', 'debito'].includes(value)) return 'EXPENSE'
  if (['income', 'ganho', 'receita', 'credito', 'estorno'].includes(value)) return 'INCOME'
  return null
}

function parseNumber(raw: unknown): number {
  if (typeof raw === 'number') return raw
  if (typeof raw !== 'string') return Number.NaN
  const value = raw.trim()
  const negative = value.startsWith('-')
  const parsed = parseAmount(value.replace(/^-/, ''))
  return negative ? -parsed : parsed
}

/** Mesmas palavras em qualquer ordem = mesmo nome ("Natal IMD" = "IMD Natal"). */
export function nameKey(name: string) {
  return normalizeName(name).split(/[^a-z0-9]+/).filter(Boolean).sort().join(' ')
}

function parseSource(raw: unknown): ImportSource | null {
  const value = normalizeName(String(raw ?? ''))
  if (['statement', 'extrato', 'conta', 'bank', 'banco'].includes(value)) return 'statement'
  if (['card', 'fatura', 'cartao', 'credit card', 'cartao de credito'].includes(value)) return 'card'
  return null
}

/**
 * Resposta do agente → linhas da prévia. Aceita formatos BR e os formatos @1 (`description`) e @2 (sem
 * `source`/`invoiceMonth`). Cada item tem origem (extrato ou fatura); item de fatura conta no mês da fatura.
 * Valor negativo vira positivo e mantém o tipo informado (sem tipo, o sinal decide conforme a origem). Nomes com as
 * mesmas palavras viram a grafia de `knownNames` (ou a primeira da resposta).
 */
export function parseAiOutput(
  text: string,
  categories: Category[],
  fixedEntries: RecurringEntry[],
  knownNames: string[] = [],
): ImportRow[] {
  const data = extractJson(text)
  const items = Array.isArray(data) ? data : (data as { items?: unknown })?.items
  if (!Array.isArray(items)) throw new ImportParseError('O JSON não tem a lista "items".')
  if (!items.length) throw new ImportParseError('A lista "items" está vazia.')
  if (items.length > MAX_IMPORT_ITEMS) {
    throw new ImportParseError(`São ${items.length} lançamentos; importe no máximo ${MAX_IMPORT_ITEMS} por vez.`)
  }

  const byTypeAndName = new Map(categories.map((c) => [`${c.type}:${normalizeName(c.name)}`, c]))
  const fallback = (type: EntryType) => byTypeAndName.get(`${type}:outros`)
  const fixedByTypeAndName = new Map(fixedEntries.map((f) => [`${f.type}:${normalizeName(f.description)}`, f]))
  const spelling = new Map<string, string>()
  for (const name of knownNames) if (nameKey(name) && !spelling.has(nameKey(name))) spelling.set(nameKey(name), name)
  const asText = (value: unknown) => (typeof value === 'string' ? value.trim() : '')

  return items.map((raw, index): ImportRow => {
    const item = (raw ?? {}) as Record<string, unknown>
    const warnings: string[] = []

    let invoiceMonth = parseMonth(item.invoiceMonth) ?? ''
    if (item.invoiceMonth !== undefined && !invoiceMonth) warnings.push(`Mês da fatura "${String(item.invoiceMonth)}" não reconhecido.`)

    // Sem origem: com mês da fatura é fatura; senão, extrato.
    const source = parseSource(item.source) ?? (invoiceMonth ? 'card' : 'statement')
    if (!parseSource(item.source)) {
      const label = item.source === undefined ? 'Origem não informada' : `Origem "${String(item.source)}" desconhecida`
      warnings.push(`${label}; considerada ${SOURCE_LABEL[source].toLowerCase()}.`)
    }

    const informed = parseType(item.type)
    let amount = parseNumber(item.amount)
    const negative = amount < 0
    if (negative) amount = -amount
    // Sem tipo, o sinal decide: no extrato negativo é saída; na fatura, negativo é crédito.
    const type: EntryType = informed ?? ((source === 'statement') === negative ? 'EXPENSE' : 'INCOME')
    if (!informed) {
      const label = item.type === undefined ? 'Sem tipo' : `Tipo "${String(item.type)}" desconhecido`
      warnings.push(`${label}; considerado ${type === 'EXPENSE' ? 'despesa' : 'ganho'} pelo sinal do valor.`)
    } else if (negative) {
      warnings.push('Valor negativo; mantive o tipo informado.')
    }

    // Ano faltante sai do mês da fatura; nenhuma data passa do mês atual.
    const limit = currentYearMonth()
    let date = parseDate(item.date, invoiceMonth || limit)
    if (!date) warnings.push(`Data "${String(item.date ?? '')}" não reconhecida.`)
    else if (date.slice(0, 7) > limit) {
      const previousYear = `${+date.slice(0, 4) - 1}${date.slice(4)}`
      if (previousYear.slice(0, 7) <= limit && isValidDate(+previousYear.slice(0, 4), +date.slice(5, 7), +date.slice(8))) {
        date = previousYear
        warnings.push(`Data depois do mês atual; ano ajustado para ${date.slice(0, 4)}.`)
      }
    }
    if (date && invoiceMonth && invoiceMonth < date.slice(0, 7)) {
      warnings.push(`Mês da fatura (${invoiceMonth}) antes da compra; ignorado.`)
      invoiceMonth = ''
    }

    const categoryName = String(item.category ?? '').trim()
    let category = byTypeAndName.get(`${type}:${normalizeName(categoryName)}`)
    if (!category) {
      category = fallback(type)
      warnings.push(
        categoryName
          ? `Categoria "${categoryName}" não existe${category ? '; usei "Outros"' : ''}.`
          : `Sem categoria${category ? '; usei "Outros"' : ''}.`,
      )
    }

    let fixedEntryId = ''
    if (typeof item.fixed === 'string' && item.fixed.trim()) {
      const fixed = fixedByTypeAndName.get(`${type}:${normalizeName(item.fixed)}`)
      if (fixed) fixedEntryId = fixed.id
      else warnings.push(`Fixo "${item.fixed}" não encontrado; tratado como ocasional.`)
    }

    const original = (asText(item.descriptionOficial) || asText(item.description)).slice(0, 200)
    let description = (asText(item.descriptionNormal) || asText(item.description) || original).slice(0, 120)
    const key = nameKey(description)
    if (key) {
      description = spelling.get(key) ?? description
      spelling.set(key, description)
    }

    return {
      key: `${index}-${Math.random().toString(36).slice(2, 8)}`,
      fixedEntryId,
      include: true,
      date: date ?? '',
      description,
      originalDescription: original,
      type,
      categoryId: category?.id ?? '',
      amount: Number.isFinite(amount) && amount > 0 ? amountToInput(amount) : '',
      notes: typeof item.notes === 'string' ? item.notes.trim().slice(0, 10000) : '',
      source,
      month: source === 'card' ? invoiceMonth : '',
      invoiceMonth,
      warnings,
    }
  })
}

/** Troca a origem da linha e recalcula o mês em que ela conta (fatura: o da fatura; extrato: o da data). */
export function withSource(row: ImportRow, source: ImportSource): Pick<ImportRow, 'source' | 'month' | 'warnings'> {
  // Origem escolhida à mão: o aviso de origem deduzida não vale mais.
  return { source, month: source === 'card' ? row.invoiceMonth : '', warnings: row.warnings.filter((w) => !w.startsWith('Origem')) }
}

/** Erros que impedem importar a linha. */
export function rowErrors(
  row: ImportRow,
  categoriesById: Map<string, Category>,
  fixedById: Map<string, RecurringEntry>,
): string[] {
  const errors: string[] = []
  if (!/^\d{4}-\d{2}-\d{2}$/.test(row.date) || (row.month && row.date.slice(0, 7) > row.month)) errors.push('data')
  if (row.fixedEntryId) {
    const fixed = fixedById.get(row.fixedEntryId)
    if (!fixed || fixed.type !== row.type || (row.date && !isActiveIn(fixed, rowMonth(row)))) errors.push('fixo')
    if (!(parseAmount(row.amount) > 0)) errors.push('valor')
    return errors
  }
  if (!row.description.trim()) errors.push('descrição')
  const category = categoriesById.get(row.categoryId)
  if (!category || category.type !== row.type) errors.push('categoria')
  if (!(parseAmount(row.amount) > 0)) errors.push('valor')
  return errors
}

/** Mesma data e valor = possível duplicado; com mesma descrição (padronizada ou do documento), vem desmarcado. */
export function markDuplicates(rows: ImportRow[], existing: Transaction[]): ImportRow[] {
  const index = new Map<string, Transaction[]>()
  for (const t of existing) {
    const key = `${t.date}|${t.amount.toFixed(2)}`
    index.set(key, [...(index.get(key) ?? []), t])
  }
  return rows.map((row) => {
    if (row.fixedEntryId) return row
    const matches = index.get(`${row.date}|${parseAmount(row.amount).toFixed(2)}`)
    if (!matches?.length) return row
    const same = (a: string | null, b: string) => !!a && !!b && normalizeName(a) === normalizeName(b)
    const sameDescription = matches.some((t) =>
      same(t.description, row.description) || same(t.originalDescription, row.originalDescription))
    return {
      ...row,
      include: row.include && !sameDescription,
      warnings: [...row.warnings, sameDescription ? 'Já importado antes (desmarcado).' : 'Possível duplicado: mesmo valor e data.'],
    }
  })
}

/** Mês em que a linha conta. */
export function rowMonth(row: ImportRow): YearMonth {
  return row.month || row.date.slice(0, 7)
}

export function monthsOf(rows: ImportRow[]): YearMonth[] {
  return [...new Set(rows.filter((r) => r.date).map(rowMonth))]
}

function similarNames(a: string, b: string) {
  const na = normalizeName(a)
  const nb = normalizeName(b)
  if (na.includes(nb) || nb.includes(na)) return true
  const words = new Set(nb.split(/[^a-z0-9]+/).filter((w) => w.length >= 3))
  return na.split(/[^a-z0-9]+/).some((w) => w.length >= 3 && words.has(w))
}

/**
 * Liga ao fixo os itens que o agente não marcou: exige valor próximo (até 15%) e mais um sinal
 * (valor exato, mesma categoria, descrição parecida ou dia próximo).
 */
export function linkFixed(rows: ImportRow[], fixedEntries: RecurringEntry[]): ImportRow[] {
  const used = new Set(rows.map((r) => r.fixedEntryId).filter(Boolean))
  return rows.map((row) => {
    const amount = parseAmount(row.amount)
    if (row.fixedEntryId || !row.date || !(amount > 0)) return row
    const month = rowMonth(row)
    const day = Number(row.date.slice(8, 10))

    let best: { fixed: RecurringEntry; score: number; diff: number } | null = null
    for (const fixed of fixedEntries) {
      if (fixed.type !== row.type || used.has(fixed.id) || !isActiveIn(fixed, month)) continue
      const reference = valueIn(fixed, month)
      const diff = Math.abs(amount - reference)
      if (diff > Math.max(1, reference * 0.15)) continue
      const score = (diff < 0.005 ? 2 : 1)
        + (row.categoryId === fixed.categoryId ? 1 : 0)
        + (similarNames(row.description, fixed.description) ? 2 : 0)
        + (fixed.dayOfMonth && Math.abs(day - fixed.dayOfMonth) <= 5 ? 1 : 0)
      if (score >= 3 && (!best || score > best.score || (score === best.score && diff < best.diff))) {
        best = { fixed, score, diff }
      }
    }
    if (!best) return row
    used.add(best.fixed.id)
    return { ...row, fixedEntryId: best.fixed.id, warnings: [...row.warnings, `Parece o fixo "${best.fixed.description}"; confira.`] }
  })
}

/** O que acontece com um item ligado a fixo: nada (já conta) ou atualiza o valor real do mês. */
export function fixedOutcome(row: ImportRow, fixed: RecurringEntry) {
  const month = rowMonth(row)
  const current = valueIn(fixed, month)
  const amount = parseAmount(row.amount)
  return { month, current, amount, changes: amount > 0 && Math.abs(amount - current) >= 0.005 }
}
