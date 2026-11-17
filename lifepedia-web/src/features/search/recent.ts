import type { SearchHit } from './types'

/** Buscas e itens abertos recentemente, só neste aparelho e só da sessão atual (apagados ao sair). */
const KEY = 'lifepedia.search.recent'
const MAX = 6

export type RecentItem = Pick<SearchHit, 'kind' | 'id' | 'title' | 'subtitle' | 'link'>

type Stored = { userId: string; queries: string[]; items: RecentItem[] }

export function readRecent(userId: string): Stored {
  try {
    const stored = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null
    if (stored?.userId === userId) return stored
  } catch {
    // Armazenamento indisponível ou corrompido: começa vazio.
  }
  return { userId, queries: [], items: [] }
}

export function rememberQuery(userId: string, query: string) {
  const text = query.trim()
  if (text.length < 2) return
  const recent = readRecent(userId)
  write({ ...recent, queries: [text, ...recent.queries.filter((q) => q !== text)].slice(0, MAX) })
}

export function rememberItem(userId: string, item: RecentItem) {
  const recent = readRecent(userId)
  const entry = { kind: item.kind, id: item.id, title: item.title, subtitle: item.subtitle, link: item.link }
  write({ ...recent, items: [entry, ...recent.items.filter((i) => i.id !== item.id)].slice(0, MAX) })
}

export function clearRecent() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // Nada a limpar.
  }
}

function write(value: Stored) {
  try {
    localStorage.setItem(KEY, JSON.stringify(value))
  } catch {
    // Sem armazenamento: só não lembra.
  }
}
