import type { QueryClient } from '@tanstack/react-query'
import { fetchDraftContent, fetchTransactions } from '../api'
import type { Category, RecurringEntry } from '../types'
import { linkFixed, markDuplicates, matchLaunched, monthsOf, parseAiOutput, type ImportRow } from './parse'

export type ImportLookups = {
  categories: Category[]
  fixedEntries: RecurringEntry[]
  knownNames: string[]
}

/** Linhas da prévia a partir da resposta da IA: casa categorias e fixos e marca duplicados. */
export async function rowsFromText(text: string, lookups: ImportLookups, queryClient: QueryClient): Promise<ImportRow[]> {
  const { categories, fixedEntries, knownNames } = lookups
  const parsed = linkFixed(parseAiOutput(text, categories, fixedEntries, knownNames), fixedEntries)
  // Lançado à mão pode estar no mês da compra, não no da fatura: busca os dois.
  const months = new Set([...monthsOf(parsed), ...parsed.filter((r) => r.date).map((r) => r.date.slice(0, 7))])
  const existing = (await Promise.all([...months].map((m) => fetchTransactions(queryClient, m)))).flat()
  // Já importado = veio de documento ou foi conferido; o resto (feito à mão) é casado para conferir.
  const imported = existing.filter((t) => t.checked || t.originalDescription)
  return matchLaunched(markDuplicates(parsed, imported), existing)
}

/** Prévia gravada; a primeira abertura de uma leitura pela IA monta as linhas pelo resultado. */
export async function rowsOfDraft(id: string, lookups: ImportLookups, queryClient: QueryClient): Promise<ImportRow[]> {
  const { rows, result } = await fetchDraftContent(id)
  return rows ? (JSON.parse(rows) as ImportRow[]) : rowsFromText(result ?? '', lookups, queryClient)
}
