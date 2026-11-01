import type { QueryClient } from '@tanstack/react-query'
import { fetchDraftContent, fetchTransactions } from '../api'
import type { Category, RecurringEntry } from '../types'
import { linkFixed, markDuplicates, monthsOf, parseAiOutput, type ImportRow } from './parse'

export type ImportLookups = {
  categories: Category[]
  fixedEntries: RecurringEntry[]
  knownNames: string[]
}

/** Linhas da prévia a partir da resposta da IA: casa categorias e fixos e marca duplicados. */
export async function rowsFromText(text: string, lookups: ImportLookups, queryClient: QueryClient): Promise<ImportRow[]> {
  const { categories, fixedEntries, knownNames } = lookups
  const parsed = linkFixed(parseAiOutput(text, categories, fixedEntries, knownNames), fixedEntries)
  const existing = (await Promise.all(monthsOf(parsed).map((m) => fetchTransactions(queryClient, m)))).flat()
  return markDuplicates(parsed, existing)
}

/** Prévia gravada; a primeira abertura de uma leitura pela IA monta as linhas pelo resultado. */
export async function rowsOfDraft(id: string, lookups: ImportLookups, queryClient: QueryClient): Promise<ImportRow[]> {
  const { rows, result } = await fetchDraftContent(id)
  return rows ? (JSON.parse(rows) as ImportRow[]) : rowsFromText(result ?? '', lookups, queryClient)
}
