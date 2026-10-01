import { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatCurrency } from '@/lib/format'
import { isActiveIn } from '../fixed'
import type { EntryType, RecurringEntry } from '../types'

const OCCASIONAL = 'none'

type Props = {
  id?: string
  type: EntryType
  /** yyyy-MM do item; '' mostra todos os fixos do tipo */
  month: string
  value: string
  onChange: (fixedEntryId: string) => void
  fixedEntries: RecurringEntry[]
  invalid?: boolean
  size?: 'sm' | 'default'
}

/** "Lançar como": ocasional novo ou um fixo existente. */
export function FixedSelect({ id, type, month, value, onChange, fixedEntries, invalid, size }: Props) {
  const options = fixedEntries.filter((f) => f.type === type && (!month || isActiveIn(f, month)))
  return (
    <Select value={value || OCCASIONAL} onValueChange={(v) => onChange(v === OCCASIONAL ? '' : v)}>
      <SelectTrigger id={id} size={size} className="w-full" aria-invalid={invalid}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={OCCASIONAL}>Ocasional (novo)</SelectItem>
        {options.length > 0 && <SelectSeparator />}
        {options.map((f) => (
          <SelectItem key={f.id} value={f.id}>
            Fixo: {f.description} ({formatCurrency(f.amount)})
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
