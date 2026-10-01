import { useMemo } from 'react'
import { useCategories } from './api'
import { sortCategories } from './categories'
import type { Category, EntryType } from './types'

/** Categorias ordenadas, por id e por tipo. */
export function useCategoryLookup() {
  const { data, isLoading } = useCategories()
  return useMemo(() => {
    const list = sortCategories(data ?? [])
    const byId = new Map<string, Category>(list.map((c) => [c.id, c]))
    return {
      list,
      byId,
      isLoading,
      ofType: (type: EntryType) => list.filter((c) => c.type === type),
    }
  }, [data, isLoading])
}
