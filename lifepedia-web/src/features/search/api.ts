import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { hasCriteria, searchParams } from './query'
import type { SearchFilters, SearchResponse } from './types'

/** Busca geral; só chama a API com 2 letras ou um filtro. */
export function useGlobalSearch(text: string, filters: SearchFilters, limit?: number) {
  const params = searchParams(text, filters, limit)
  const enabled = hasCriteria(params)
  return {
    enabled,
    ...useQuery({
      queryKey: ['search', params],
      queryFn: () => api<SearchResponse>('/api/search', { query: params }),
      enabled,
      placeholderData: keepPreviousData,
      staleTime: 15_000,
    }),
  }
}
