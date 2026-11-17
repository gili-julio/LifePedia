import { createContext, useContext } from 'react'

export type SearchContextValue = {
  /** Abre a busca geral, opcionalmente já com um texto. */
  openSearch: (text?: string) => void
}

export const SearchContext = createContext<SearchContextValue>({ openSearch: () => undefined })

export function useSearch() {
  return useContext(SearchContext)
}
