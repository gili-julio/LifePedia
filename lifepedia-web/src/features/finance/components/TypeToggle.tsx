import { ArrowDownRightIcon, ArrowUpRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { EntryType } from '../types'

type Props = {
  value: EntryType
  onChange: (type: EntryType) => void
  disabled?: boolean
}

const options: { value: EntryType; label: string; icon: typeof ArrowUpRightIcon; active: string }[] = [
  { value: 'EXPENSE', label: 'Despesa', icon: ArrowDownRightIcon, active: 'border-expense bg-expense/10 text-foreground [&_svg]:text-expense' },
  { value: 'INCOME', label: 'Ganho', icon: ArrowUpRightIcon, active: 'border-income bg-income/10 text-foreground [&_svg]:text-income' },
]

export function TypeToggle({ value, onChange, disabled }: Props) {
  return (
    <div role="radiogroup" aria-label="Tipo" className="grid grid-cols-2 gap-2">
      {options.map(({ value: option, label, icon: Icon, active }) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={value === option}
          disabled={disabled}
          onClick={() => onChange(option)}
          className={cn(
            'flex h-9 items-center justify-center gap-2 rounded-lg border text-sm font-medium transition-colors',
            'focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none disabled:opacity-50',
            value === option ? active : 'text-muted-foreground hover:bg-muted',
          )}
        >
          <Icon className="size-4" />
          {label}
        </button>
      ))}
    </div>
  )
}
