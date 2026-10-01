import { useCallback } from 'react'
import { useSearchParams } from 'react-router'
import { currentYearMonth, isYearMonth, type YearMonth } from '@/lib/format'

/** Mês selecionado na URL (?mes=2026-09). */
export function useMonthParam(): [YearMonth, (month: YearMonth) => void] {
  const [params, setParams] = useSearchParams()
  const raw = params.get('mes')
  const month = isYearMonth(raw) ? raw : currentYearMonth()

  const setMonth = useCallback(
    (next: YearMonth) =>
      setParams(
        (prev) => {
          const updated = new URLSearchParams(prev)
          if (next === currentYearMonth()) updated.delete('mes')
          else updated.set('mes', next)
          return updated
        },
        { replace: true },
      ),
    [setParams],
  )

  return [month, setMonth]
}
