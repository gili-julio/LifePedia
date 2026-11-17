import { useSearchParams } from 'react-router'

const PARAM = 'busca'

/** Busca da lista, guardada em `?busca=` (a busca geral abre a lista já filtrada). */
export function useListSearch() {
  const [params, setParams] = useSearchParams()
  const value = params.get(PARAM) ?? ''
  const set = (next: string) =>
    setParams((prev) => {
      const updated = new URLSearchParams(prev)
      if (next.trim()) updated.set(PARAM, next)
      else updated.delete(PARAM)
      return updated
    }, { replace: true })
  return [value, set] as const
}
