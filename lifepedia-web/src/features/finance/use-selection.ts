import { useCallback, useMemo, useState } from 'react'

/** Itens marcados numa lista (mantidos ao trocar de página da lista). */
export function useSelection() {
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())

  const set = useCallback((ids: string[], checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current)
      ids.forEach((id) => (checked ? next.add(id) : next.delete(id)))
      return next
    })
  }, [])
  const clear = useCallback(() => setSelected(new Set()), [])

  return useMemo(() => ({ selected, ids: [...selected], set, clear }), [selected, set, clear])
}
