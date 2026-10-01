import { useEffect, useState } from 'react'

/** Página de uma listagem da API (`page` começa em 0). */
export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

export type SortDirection = 'asc' | 'desc'
export type SortState = { field: string; direction: SortDirection }

export const PAGE_SIZES = [10, 25, 50, 100] as const

/** Página, tamanho e ordenação de uma listagem; tudo vai para a API. */
export function useListState(defaultSort: SortState, defaultSize = 10) {
  const [page, setPage] = useState(0)
  const [size, setSizeState] = useState(defaultSize)
  const [sort, setSortState] = useState(defaultSort)

  return {
    page,
    size,
    sort,
    setPage,
    setSize: (next: number) => {
      setSizeState(next)
      setPage(0)
    },
    setSort: (next: SortState) => {
      setSortState(next)
      setPage(0)
    },
    /** Mesma coluna inverte; outra começa em `firstDirection`. */
    toggleSort: (field: string, firstDirection: SortDirection = 'asc') => {
      setSortState((current) =>
        current.field === field
          ? { field, direction: current.direction === 'asc' ? 'desc' : 'asc' }
          : { field, direction: firstDirection },
      )
      setPage(0)
    },
    resetPage: () => setPage(0),
    params: { page, size, sort: `${sort.field},${sort.direction}` },
  }
}

export type ListState = ReturnType<typeof useListState>

/** Volta para a última página se a atual ficou vazia (ex.: após excluir). */
export function useClampPage(data: Page<unknown> | undefined, setPage: (page: number) => void) {
  useEffect(() => {
    if (data && data.totalPages > 0 && data.page >= data.totalPages) setPage(data.totalPages - 1)
  }, [data, setPage])
}
