import { CalendarIcon, ChevronLeftIcon, ChevronRightIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { currentYearMonth, formatMonth, type YearMonth } from '@/lib/format'
import { cn } from '@/lib/utils'

type Props = {
  id?: string
  /** yyyy-MM ('' = vazio) */
  value: string
  onChange: (value: YearMonth | '') => void
  invalid?: boolean
  /** Mostra botão para limpar */
  clearable?: boolean
  placeholder?: string
}

const MONTHS = Array.from({ length: 12 }, (_, i) =>
  new Date(2000, i, 1).toLocaleDateString('pt-BR', { month: 'short' }).replace('.', ''),
)

/** Campo de mês do sistema: botão que abre a grade de meses. */
export function MonthField({ id, value, onChange, invalid, clearable, placeholder = 'Escolha o mês' }: Props) {
  const [open, setOpen] = useState(false)
  const [year, setYear] = useState(() => Number((value || currentYearMonth()).slice(0, 4)))

  return (
    <Popover open={open} onOpenChange={(next) => {
      if (next) setYear(Number((value || currentYearMonth()).slice(0, 4)))
      setOpen(next)
    }}>
      <div className="relative">
        <PopoverTrigger asChild>
          <Button id={id} type="button" variant="outline" aria-invalid={invalid}
            className={cn('w-full justify-start font-normal', !value && 'text-muted-foreground', clearable && value && 'pr-8')}>
            <CalendarIcon />
            {value ? formatMonth(value) : placeholder}
          </Button>
        </PopoverTrigger>
        {clearable && value && (
          <Button type="button" variant="ghost" size="icon-xs" aria-label="Limpar mês"
            className="absolute top-1/2 right-1.5 -translate-y-1/2" onClick={() => onChange('')}>
            <XIcon />
          </Button>
        )}
      </div>
      <PopoverContent className="w-64 p-2" align="start">
        <div className="mb-2 flex items-center justify-between">
          <Button type="button" variant="ghost" size="icon-sm" aria-label="Ano anterior" onClick={() => setYear((y) => y - 1)}>
            <ChevronLeftIcon />
          </Button>
          <span className="text-sm font-medium tabular-nums">{year}</span>
          <Button type="button" variant="ghost" size="icon-sm" aria-label="Próximo ano" onClick={() => setYear((y) => y + 1)}>
            <ChevronRightIcon />
          </Button>
        </div>
        <div className="grid grid-cols-3 gap-1">
          {MONTHS.map((label, i) => {
            const month = `${year}-${String(i + 1).padStart(2, '0')}`
            return (
              <Button key={month} type="button" size="sm" variant={month === value ? 'default' : 'ghost'}
                className={cn('capitalize', month === currentYearMonth() && month !== value && 'ring-1 ring-border')}
                onClick={() => {
                  onChange(month)
                  setOpen(false)
                }}>
                {label}
              </Button>
            )
          })}
        </div>
      </PopoverContent>
    </Popover>
  )
}
