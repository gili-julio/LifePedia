import { type Control, Controller, useWatch } from 'react-hook-form'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useRecurringEntries } from '@/features/finance/api'
import { formatCurrency } from '@/lib/format'
import type { TaskFormInput } from './task-form'

const NONE = '__none'

/** Fixo do financeiro ligado à tarefa pessoal (some se a pessoa não tem fixos). */
export function FixedField({ control }: { control: Control<TaskFormInput> }) {
  const { data: entries, isLoading } = useRecurringEntries()
  const value = useWatch({ control, name: 'recurringEntryId' })
  const options = (entries ?? []).filter((e) => e.active || e.id === value)
  if (!isLoading && options.length === 0) return null

  return (
    <Field>
      <FieldLabel htmlFor="task-fixed">Fixo do financeiro</FieldLabel>
      <Controller control={control} name="recurringEntryId" render={({ field }) => (
        <Select value={field.value || NONE} onValueChange={(v) => field.onChange(v === NONE ? '' : v)} disabled={isLoading}>
          <SelectTrigger id="task-fixed" className="w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={NONE}>Nenhum</SelectItem>
            {options.map((e) => (
              <SelectItem key={e.id} value={e.id}>
                <span className="truncate">{e.description}</span>
                <span className="text-muted-foreground tabular-nums">{formatCurrency(e.amount)}</span>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )} />
      <FieldDescription>Ao concluir, dá para registrar o valor do fixo no mês (ex.: a conta de luz que chegou).</FieldDescription>
    </Field>
  )
}
