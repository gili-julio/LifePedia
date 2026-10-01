import { CalendarIcon } from 'lucide-react'
import { useState } from 'react'
import { ptBR } from 'react-day-picker/locale'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'

type Props = {
  id?: string
  /** yyyy-MM-dd ('' = vazio) */
  value: string
  onChange: (value: string) => void
  invalid?: boolean
  size?: 'default' | 'sm'
  placeholder?: string
  className?: string
}

function toDate(value: string) {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  return match ? new Date(+match[1], +match[2] - 1, +match[3]) : undefined
}

function toIso(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const THIS_YEAR = new Date().getFullYear()

/** Campo de data do sistema: botão que abre o calendário. */
export function DatePicker({ id, value, onChange, invalid, size = 'default', placeholder = 'Escolha a data', className }: Props) {
  const [open, setOpen] = useState(false)
  const selected = toDate(value)
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button id={id} type="button" variant="outline" size={size} aria-invalid={invalid}
          className={cn('w-full justify-start font-normal tabular-nums', !selected && 'text-muted-foreground', className)}>
          <CalendarIcon />
          {selected ? formatDate(value) : placeholder}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          locale={ptBR}
          captionLayout="dropdown"
          startMonth={new Date(THIS_YEAR - 10, 0)}
          endMonth={new Date(THIS_YEAR + 5, 11)}
          defaultMonth={selected}
          selected={selected}
          onSelect={(date) => {
            if (date) onChange(toIso(date))
            setOpen(false)
          }}
        />
      </PopoverContent>
    </Popover>
  )
}
