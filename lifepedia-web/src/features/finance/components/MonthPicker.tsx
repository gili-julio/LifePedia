import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { addMonths, currentYearMonth, formatMonth, type YearMonth } from '@/lib/format'

type Props = {
  value: YearMonth
  onChange: (month: YearMonth) => void
}

export function MonthPicker({ value, onChange }: Props) {
  const isCurrent = value === currentYearMonth()
  return (
    <div className="flex w-full items-center gap-1 sm:w-auto">
      <Button variant="outline" size="icon" aria-label="Mês anterior" onClick={() => onChange(addMonths(value, -1))}>
        <ChevronLeftIcon />
      </Button>
      <span className="min-w-36 flex-1 text-center text-sm font-medium tabular-nums sm:flex-none">{formatMonth(value)}</span>
      <Button variant="outline" size="icon" aria-label="Próximo mês" onClick={() => onChange(addMonths(value, 1))}>
        <ChevronRightIcon />
      </Button>
      {!isCurrent && (
        <Button variant="ghost" size="sm" onClick={() => onChange(currentYearMonth())}>
          Hoje
        </Button>
      )}
    </div>
  )
}
