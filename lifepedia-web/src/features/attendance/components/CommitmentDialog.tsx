import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { ColorPicker } from '@/components/ColorPicker'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { COLOR_SWATCHES } from '@/lib/colors'
import { todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useLinkableTasks, useSaveCommitment } from '../api'
import type { Commitment, CommitmentRequest } from '../types'

const WEEKDAYS = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom']
const NO_TASK = '__none'

const whole = (min: number, max: number, message: string) =>
  z.string().trim().regex(/^\d+$/, message).refine((v) => Number(v) >= min && Number(v) <= max, message)

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome.').max(120, 'Máximo de 120 caracteres.'),
  color: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Escolha uma cor.'),
  weekdays: z.array(z.number()).min(1, 'Escolha pelo menos um dia.'),
  time: z.string().regex(/^\d{2}:\d{2}$/, 'Informe o horário.'),
  durationMinutes: whole(5, 720, 'De 5 a 720 minutos.'),
  startDate: z.string().min(1, 'Informe o início.'),
  endDate: z.string(),
  maxAbsences: whole(0, 999, 'De 0 a 999 faltas.'),
  taskId: z.string(),
}).refine((v) => !v.endDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'O fim precisa ser depois do início.' })

type FormValues = z.infer<typeof schema>

function toForm(commitment: Commitment | undefined, defaults: Partial<CommitmentRequest> = {}): FormValues {
  const base = { ...commitment, ...defaults }
  return {
    name: base.name ?? '',
    color: base.color ?? COLOR_SWATCHES[0],
    weekdays: base.weekdays ?? [],
    time: base.time ?? '08:00',
    durationMinutes: String(base.durationMinutes ?? 60),
    startDate: base.startDate ?? todayIso(),
    endDate: base.endDate ?? '',
    maxAbsences: String(base.maxAbsences ?? 8),
    taskId: defaults.taskId ?? commitment?.task?.id ?? NO_TASK,
  }
}

type Props = { open: boolean; onOpenChange: (open: boolean) => void; commitment?: Commitment }

/** Criar ou editar compromisso (o formulário recomeça a cada abertura). */
export function CommitmentDialog({ open, onOpenChange, commitment }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{commitment ? 'Editar compromisso' : 'Novo compromisso'}</DialogTitle>
          <DialogDescription>Ex.: uma matéria da faculdade, com os dias, o horário e quantas faltas você pode ter.</DialogDescription>
        </DialogHeader>
        <CommitmentForm commitment={commitment} onDone={() => onOpenChange(false)} />
      </DialogScrollContent>
    </Dialog>
  )
}

type FormProps = {
  commitment?: Commitment
  /** Valores iniciais de um compromisso novo (ex.: lidos do SIGAA). */
  defaults?: Partial<CommitmentRequest>
  /** Troca o salvar padrão (a importação manda junto as marcações); devolve false se não deu certo. */
  onSubmit?: (data: CommitmentRequest) => Promise<boolean>
  submitLabel?: string
  /** Conteúdo antes dos campos (ex.: resumo da importação). */
  children?: ReactNode
  onDone: () => void
  onBack?: () => void
}

/** Campos do compromisso (também usados na importação do SIGAA). */
export function CommitmentForm({ commitment, defaults, onSubmit: submit, submitLabel = 'Salvar', children, onDone, onBack }: FormProps) {
  const save = useSaveCommitment()
  const tasks = useLinkableTasks(true)
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toForm(commitment, defaults) })
  const { errors, isSubmitting } = form.formState
  const endDate = useWatch({ control: form.control, name: 'endDate' })
  const options = [...(tasks.data ?? [])]
  if (commitment?.task && !options.some((t) => t.id === commitment.task?.id)) options.unshift(commitment.task)

  async function onSubmit(values: FormValues) {
    const data: CommitmentRequest = {
      name: values.name,
      color: values.color,
      weekdays: values.weekdays,
      time: values.time,
      durationMinutes: Number(values.durationMinutes),
      startDate: values.startDate,
      endDate: values.endDate || null,
      maxAbsences: Number(values.maxAbsences),
      taskId: values.taskId === NO_TASK ? null : values.taskId,
    }
    if (submit) {
      if (await submit(data)) onDone()
      return
    }
    try {
      await save.mutateAsync({ id: commitment?.id, data })
      toast.success(commitment ? 'Compromisso atualizado.' : 'Compromisso criado.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <>
      <DialogBody>
        {children}
        <form id="commitment-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup className="gap-4">
            <Field data-invalid={!!errors.name}>
              <FieldLabel htmlFor="commitment-name">Nome</FieldLabel>
              <Input id="commitment-name" placeholder="Ex.: Cálculo I" className="text-base md:text-sm"
                aria-invalid={!!errors.name} {...form.register('name')} />
              <FieldError errors={[errors.name]} />
            </Field>

            <Field data-invalid={!!errors.weekdays}>
              <FieldLabel>Dias</FieldLabel>
              <Controller control={form.control} name="weekdays" render={({ field }) => (
                <div className="flex flex-wrap gap-1.5" role="group" aria-label="Dias da semana">
                  {WEEKDAYS.map((label, index) => {
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
              <FieldError errors={[errors.weekdays]} />
            </Field>

            <div className="grid grid-cols-2 gap-3">
              <Field data-invalid={!!errors.time}>
                <FieldLabel htmlFor="commitment-time">Horário</FieldLabel>
                <Input id="commitment-time" type="time" className="text-base md:text-sm" aria-invalid={!!errors.time}
                  {...form.register('time')} />
                <FieldError errors={[errors.time]} />
              </Field>
              <Field data-invalid={!!errors.durationMinutes}>
                <FieldLabel htmlFor="commitment-duration">Duração (min)</FieldLabel>
                <Input id="commitment-duration" inputMode="numeric" className="text-base md:text-sm"
                  aria-invalid={!!errors.durationMinutes} {...form.register('durationMinutes')} />
                <FieldError errors={[errors.durationMinutes]} />
              </Field>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Field data-invalid={!!errors.startDate}>
                <FieldLabel htmlFor="commitment-start">Começa em</FieldLabel>
                <Controller control={form.control} name="startDate" render={({ field }) => (
                  <DatePicker id="commitment-start" value={field.value} onChange={field.onChange} invalid={!!errors.startDate} />
                )} />
                <FieldError errors={[errors.startDate]} />
              </Field>
              <Field data-invalid={!!errors.endDate}>
                <FieldLabel htmlFor="commitment-end">Termina em</FieldLabel>
                <Controller control={form.control} name="endDate" render={({ field }) => (
                  <DatePicker id="commitment-end" value={field.value} onChange={field.onChange} invalid={!!errors.endDate}
                    placeholder="Sem fim" />
                )} />
                {endDate && (
                  <Button type="button" variant="link" size="sm" className="h-auto justify-start p-0"
                    onClick={() => form.setValue('endDate', '', { shouldValidate: true })}>
                    Tirar o fim
                  </Button>
                )}
                <FieldError errors={[errors.endDate]} />
              </Field>
            </div>

            <Field data-invalid={!!errors.maxAbsences}>
              <FieldLabel htmlFor="commitment-max">Limite de faltas</FieldLabel>
              <Input id="commitment-max" inputMode="numeric" className="text-base md:text-sm"
                aria-invalid={!!errors.maxAbsences} {...form.register('maxAbsences')} />
              <FieldDescription>Quantas vezes você pode faltar no período inteiro.</FieldDescription>
              <FieldError errors={[errors.maxAbsences]} />
            </Field>

            <Field>
              <FieldLabel htmlFor="commitment-task">Tarefa ligada (opcional)</FieldLabel>
              <Controller control={form.control} name="taskId" render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="commitment-task" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NO_TASK}>Nenhuma</SelectItem>
                    {options.map((t) => (
                      <SelectItem key={t.id} value={t.id}>{t.title}{t.dueTime ? ` (${t.dueTime})` : ''}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )} />
              <FieldDescription>Concluir a tarefa num dia do compromisso já marca presença nesse dia.</FieldDescription>
            </Field>

            <Field data-invalid={!!errors.color}>
              <FieldLabel>Cor</FieldLabel>
              <Controller control={form.control} name="color"
                render={({ field }) => <ColorPicker value={field.value} onChange={field.onChange} />} />
              <FieldError errors={[errors.color]} />
            </Field>
          </FieldGroup>
        </form>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onBack ?? onDone}>{onBack ? 'Voltar' : 'Cancelar'}</Button>
        <Button type="submit" form="commitment-form" disabled={isSubmitting}>
          {isSubmitting && <Loader2Icon className="animate-spin" />} {submitLabel}
        </Button>
      </DialogFooter>
    </>
  )
}
