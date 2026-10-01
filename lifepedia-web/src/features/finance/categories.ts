import type { Category, EntryType } from './types'

export const TYPE_LABEL: Record<EntryType, string> = {
  INCOME: 'Ganho',
  EXPENSE: 'Despesa',
}

/** Cor para categoria inexistente. */
export const FALLBACK_COLOR = '#94a3b8'

export function sortCategories(categories: Category[]) {
  return [...categories].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))
}

/** Minúsculas, sem acentos e espaços extras. */
export function normalizeName(name: string) {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}
