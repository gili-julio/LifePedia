import { Controller, useWatch, type Control, type UseFormRegister } from 'react-hook-form'
import { DatePicker } from '@/components/DatePicker'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import { nthWeekday, UNIT_LABEL, WEEKDAYS_SHORT } from '../dates'
import type { RepeatUnit } from '../types'
import type { TaskFormInput } from './task-form'

type Props = {
  control: Control<TaskFormInput>
  register: UseFormRegister<TaskFormInput>
}

const UNITS: RepeatUnit[] = ['DAY', 'WEEK', 'MONTH', 'YEAR']

/** Regra de repetição: intervalo, dias da semana, modo do mês, "a partir da conclusão" e fim. */
export function RepeatFields({ control, register }: Props) {
  const [unit, interval, fromCompletion, end, dueDate] = useWatch({
    control,
    name: ['unit', 'interval', 'fromCompletion', 'end', 'dueDate'],
  })
  const plural = Number(interval) !== 1
  const day = dueDate ? Number(dueDate.slice(8)) : null

  return (
    <div className="space-y-4 rounded-lg border p-3">
      <div className="flex items-center gap-2">
        <Label htmlFor="task-interval" className="shrink-0 font-normal">A cada</Label>
        <Input id="task-interval" inputMode="numeric" className="w-16" aria-label="Intervalo" {...register('interval')} />
        <Controller control={control} name="unit" render={({ field }) => (
          <Select value={field.value} onValueChange={field.onChange}>
            <SelectTrigger className="min-w-0 flex-1" aria-label="Frequência"><SelectValue /></SelectTrigger>
            <SelectContent>
              {UNITS.map((u) => <SelectItem key={u} value={u}>{UNIT_LABEL[u][plural ? 1 : 0]}</SelectItem>)}
            </SelectContent>
          </Select>
        )} />
      </div>

      <Controller control={control} name="fromCompletion" render={({ field }) => (
        <div className="flex items-start gap-2">
          <Checkbox id="task-from-completion" checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} />
          <Label htmlFor="task-from-completion" className="flex-col items-start gap-0.5 font-normal">
            Contar a partir de quando concluir
            <span className="text-xs text-muted-foreground">Ex.: regar a planta 3 dias depois da última vez.</span>
          </Label>
        </div>
      )} />

      {unit === 'WEEK' && !fromCompletion && (
        <Controller control={control} name="weekdays" render={({ field }) => (
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Dias da semana">
            {WEEKDAYS_SHORT.map((label, index) => {
              const value = index + 1
              const on = field.value.includes(value)
              return (
                <button key={label} type="button" aria-pressed={on}
                  onClick={() => field.onChange(on ? field.value.filter((d) => d !== value) : [...field.value, value].sort())}
                  className={cn('h-8 min-w-10 rounded-full border px-2 text-sm outline-none transition-colors motion-reduce:transition-none',
                    'focus-visible:ring-[3px] focus-visible:ring-ring/50',
                    on ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-muted')}>
                  {label}
                </button>
              )
            })}
          </div>
        )} />
      )}

      {unit === 'MONTH' && !fromCompletion && dueDate && (
        <Controller control={control} name="monthMode" render={({ field }) => (
          <RadioGroup value={field.value} onValueChange={field.onChange} aria-label="Dia do mês">
            <div className="flex items-center gap-2">
              <RadioGroupItem id="month-day" value="DAY_OF_MONTH" />
              <Label htmlFor="month-day" className="font-normal">No dia {day}{day && day > 28 ? ' (ou o último, em meses mais curtos)' : ''}</Label>
            </div>
            <div className="flex items-center gap-2">
              <RadioGroupItem id="month-weekday" value="WEEKDAY" />
              <Label htmlFor="month-weekday" className="font-normal">Na {nthWeekday(dueDate)}</Label>
            </div>
            <div className="flex items-center gap-2">
              <RadioGroupItem id="month-last" value="LAST_DAY" />
              <Label htmlFor="month-last" className="font-normal">No último dia</Label>
            </div>
          </RadioGroup>
        )} />
      )}

      <Field>
        <FieldLabel>Termina</FieldLabel>
        <Controller control={control} name="end" render={({ field }) => (
          <RadioGroup value={field.value} onValueChange={field.onChange} className="gap-2">
            <div className="flex items-center gap-2">
              <RadioGroupItem id="end-never" value="NEVER" />
              <Label htmlFor="end-never" className="font-normal">Nunca</Label>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <RadioGroupItem id="end-until" value="UNTIL" />
              <Label htmlFor="end-until" className="font-normal">Em</Label>
              {end === 'UNTIL' && (
                <Controller control={control} name="until" render={({ field: until }) => (
                  <DatePicker value={until.value} onChange={until.onChange} size="sm" className="w-40" />
                )} />
              )}
            </div>
            <div className="flex items-center gap-2">
              <RadioGroupItem id="end-count" value="COUNT" />
              <Label htmlFor="end-count" className="font-normal">Após</Label>
              {end === 'COUNT' && (
                <>
                  <Input inputMode="numeric" className="h-8 w-16" aria-label="Quantas vezes" {...register('count')} />
                  <span className="text-sm">vezes</span>
                </>
              )}
            </div>
          </RadioGroup>
        )} />
      </Field>
    </div>
  )
}
