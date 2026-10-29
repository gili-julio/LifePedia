import { zodResolver } from '@hookform/resolvers/zod'
import { FlagIcon, Loader2Icon, PlusIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/features/auth/auth-context'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { useSaveTask, useTask, useUpdateOccurrence } from '../api'
import { describeRepeat, isoWeekday, PRIORITY_COLOR, PRIORITY_LABEL, REMINDERS } from '../dates'
import { useLabelLookup } from '../labels'
import type { Priority, Scope, Task, TaskItem } from '../types'
import { AssignmentFields } from './AssignmentFields'
import { ConflictWarning } from './ConflictWarning'
import { FixedField } from './FixedField'
import { RepeatFields } from './RepeatFields'
import { ScopeDialog, type ScopeOption } from './ScopeDialog'
import { TaskComments } from './TaskComments'
import { changesSeries, taskSchema, toForm, toRequest, toRule, type TaskFormInput, type TaskFormValues } from './task-form'

/** Nova tarefa (com dia/lista sugeridos) ou edição a partir de uma ocorrência. `groupId` nulo = pessoal. */
export type TaskTarget =
  | { mode: 'new'; groupId: string | null; dueDate: string | null; listId: string | null; dueTime?: string | null }
  | { mode: 'edit'; groupId: string | null; taskId: string; occurrence: TaskItem | null }

const NONE = '__none'
const DURATIONS = [15, 30, 45, 60, 90, 120, 180, 240]
const PRIORITIES: Priority[] = ['NONE', 'LOW', 'MEDIUM', 'HIGH']

type Props = {
  target: TaskTarget | null
  onOpenChange: (open: boolean) => void
}

/** Cadastro/edição de tarefa. Repetida (regra fixa): pergunta se vale só nesta, nesta e nas próximas ou em todas. */
export function TaskDialog({ target, onOpenChange }: Props) {
  const open = target !== null
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{target?.mode === 'edit' ? 'Editar tarefa' : 'Nova tarefa'}</DialogTitle>
          <DialogDescription>Dia, horário e repetição são opcionais.</DialogDescription>
        </DialogHeader>
        {target?.mode === 'new' && <TaskForm target={target} onDone={() => onOpenChange(false)} />}
        {target?.mode === 'edit' && <EditLoader target={target} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function EditLoader({ target, onDone }: { target: Extract<TaskTarget, { mode: 'edit' }>; onDone: () => void }) {
  const { data: task, isError } = useTask(target.groupId, target.taskId)
  if (isError) return <DialogBody><p className="text-sm text-destructive">Não foi possível carregar a tarefa.</p></DialogBody>
  if (!task) return <DialogBody><Skeleton className="h-64 w-full" /></DialogBody>
  return <TaskForm target={target} task={task} onDone={onDone} />
}

function TaskForm({ target, task, onDone }: { target: TaskTarget; task?: Task; onDone: () => void }) {
  const save = useSaveTask()
  const saveOccurrence = useUpdateOccurrence()
  const groupId = target.groupId
  const labels = useLabelLookup(groupId)
  const occurrence = target.mode === 'edit' ? target.occurrence : null
  const meId = useAuth().user?.id ?? null
  const form = useForm<TaskFormInput, unknown, TaskFormValues>({
    resolver: zodResolver(taskSchema),
    defaultValues: toForm(task, occurrence,
      target.mode === 'new' ? { ...target, assignees: groupId && meId ? [meId] : [] } : undefined),
  })
  const { errors, isSubmitting } = form.formState
  const subtasks = useFieldArray({ control: form.control, name: 'subtasks' })
  const [scopeFor, setScopeFor] = useState<TaskFormValues | null>(null)
  const [dueDate, dueTime, repeatOn, priority, fromCompletion] =
    useWatch({ control: form.control, name: ['dueDate', 'dueTime', 'repeatOn', 'priority', 'fromCompletion'] })
  const values = useWatch({ control: form.control })
  const fixedRule = !!task?.repeat && !task.repeat.fromCompletion && !!occurrence?.date
  const preview = repeatOn && dueDate && /^\d+$/.test(values.interval ?? '') ? toRule(values as TaskFormValues) : null

  async function run(action: () => Promise<unknown>, message: string) {
    try {
      await action()
      toast.success(message)
      onDone()
    } catch (error) {
      if (error instanceof ApiError) {
        for (const [field, text] of Object.entries(error.fieldErrors)) form.setError(field as keyof TaskFormInput, { message: text })
      }
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  function onSubmit(v: TaskFormValues) {
    if (!task) return run(() => save.mutateAsync({ groupId, data: toRequest(v) }), 'Tarefa criada.')
    if (fixedRule) {
      setScopeFor(v)
      return
    }
    return run(() => save.mutateAsync({ groupId, id: task.id, data: toRequest(v) }), 'Tarefa atualizada.')
  }

  function onScope(scope: Scope) {
    const v = scopeFor
    if (!v || !task || !occurrence?.date) return
    const date = occurrence.date
    if (scope === 'THIS') {
      return run(() => saveOccurrence.mutateAsync({
        groupId,
        id: task.id,
        data: { date, moveTo: v.dueDate, title: v.title, notes: v.notes.trim() || null, dueTime: v.dueTime || null, priority: v.priority },
      }), 'Só esta ocorrência foi alterada.')
    }
    if (scope === 'FOLLOWING') {
      return run(() => save.mutateAsync({ groupId, id: task.id, from: date, data: toRequest(v) }), 'Esta e as próximas foram alteradas.')
    }
    // Todas: o início da série só muda se o dia foi alterado.
    const start = v.dueDate === (occurrence.scheduledDate ?? date) ? task.dueDate ?? v.dueDate : v.dueDate
    return run(() => save.mutateAsync({ groupId, id: task.id, data: toRequest(v, start) }), 'Todas as ocorrências foram alteradas.')
  }

  const scopeOptions: ScopeOption[] = scopeFor && task ? [
    {
      scope: 'THIS', label: 'Só esta', disabled: changesSeries(scopeFor, task),
      hint: changesSeries(scopeFor, task) ? 'Repetição, lista, etiquetas, duração, subtarefas ou responsável mudam a série toda.' : undefined,
    },
    ...(occurrence?.date && task.dueDate && occurrence.date > task.dueDate
      ? [{ scope: 'FOLLOWING' as const, label: 'Esta e as próximas' }] : []),
    { scope: 'ALL', label: 'Todas' },
  ] : []

  return (
    <>
      <DialogBody>
        <form id="task-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup className="gap-4">
            <Field data-invalid={!!errors.title}>
              <FieldLabel htmlFor="task-title">Tarefa</FieldLabel>
              <Input id="task-title" autoFocus={!task} autoComplete="off" aria-invalid={!!errors.title} {...form.register('title')} />
              <FieldError errors={[errors.title]} />
            </Field>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={!!errors.dueDate}>
                <FieldLabel htmlFor="task-date">{repeatOn && !occurrence ? 'Começa em' : 'Dia'}</FieldLabel>
                <div className="flex gap-1">
                  <Controller control={form.control} name="dueDate" render={({ field }) => (
                    <DatePicker id="task-date" value={field.value} placeholder="Sem data" invalid={!!errors.dueDate}
                      className="min-w-0 flex-1"
                      onChange={(value) => {
                        field.onChange(value)
                        if (!form.getValues('repeatOn')) form.setValue('weekdays', [isoWeekday(value)])
                      }} />
                  )} />
                  {dueDate && !repeatOn && (
                    <Button type="button" variant="ghost" size="icon" aria-label="Tirar a data"
                      onClick={() => form.setValue('dueDate', '')}>
                      <XIcon />
                    </Button>
                  )}
                </div>
                <FieldError errors={[errors.dueDate]} />
              </Field>
              <Field data-invalid={!!errors.dueTime}>
                <FieldLabel htmlFor="task-time">Horário</FieldLabel>
                <div className="flex gap-1">
                  <Input id="task-time" type="time" disabled={!dueDate} className="min-w-0 flex-1" aria-invalid={!!errors.dueTime}
                    {...form.register('dueTime')} />
                  <Controller control={form.control} name="duration" render={({ field }) => (
                    <Select value={field.value || NONE} onValueChange={(v) => field.onChange(v === NONE ? '' : v)} disabled={!dueTime}>
                      <SelectTrigger className="w-28" aria-label="Duração"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>Duração</SelectItem>
                        {DURATIONS.map((m) => (
                          <SelectItem key={m} value={String(m)}>{m < 60 ? `${m} min` : `${m / 60}h`.replace('.5h', 'h30')}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )} />
                </div>
                <FieldError errors={[errors.dueTime]} />
              </Field>
            </div>

            <ConflictWarning control={form.control} groupId={groupId} taskId={task?.id ?? null} />

            {dueTime && (
              <Field>
                <FieldLabel htmlFor="task-reminder">Lembrete</FieldLabel>
                <Controller control={form.control} name="reminder" render={({ field }) => (
                  <Select value={field.value || NONE} onValueChange={(v) => field.onChange(v === NONE ? '' : v)}>
                    <SelectTrigger id="task-reminder" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE}>Sem lembrete</SelectItem>
                      {REMINDERS.map((r) => <SelectItem key={r.minutes} value={String(r.minutes)}>{r.label}</SelectItem>)}
                    </SelectContent>
                  </Select>
                )} />
                <FieldDescription>
                  Aviso no sino{groupId ? ' para os responsáveis (sem responsável: para todos do grupo)' : ''}.
                </FieldDescription>
              </Field>
            )}

            <Field>
              <FieldLabel>Prioridade</FieldLabel>
              <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="Prioridade">
                {PRIORITIES.map((p) => (
                  <button key={p} type="button" role="radio" aria-checked={priority === p}
                    onClick={() => form.setValue('priority', p)}
                    className={cn('flex h-8 items-center gap-1.5 rounded-full border px-3 text-sm outline-none transition-colors motion-reduce:transition-none',
                      'focus-visible:ring-[3px] focus-visible:ring-ring/50',
                      priority === p ? 'border-foreground bg-muted font-medium' : 'hover:bg-muted')}>
                    {PRIORITY_COLOR[p] && <FlagIcon className="size-3.5" style={{ color: PRIORITY_COLOR[p] }} aria-hidden />}
                    {PRIORITY_LABEL[p]}
                  </button>
                ))}
              </div>
            </Field>

            <Field>
              <div className="flex items-center justify-between gap-2">
                <Label htmlFor="task-repeat">Repetir</Label>
                <Controller control={form.control} name="repeatOn" render={({ field }) => (
                  <Switch id="task-repeat" checked={field.value} onCheckedChange={field.onChange} />
                )} />
              </div>
              {repeatOn && <RepeatFields control={form.control} register={form.register} />}
              {preview && <FieldDescription>{describeRepeat(preview, dueDate)}</FieldDescription>}
              <FieldError errors={[errors.interval, errors.until, errors.count]} />
              {repeatOn && (
                <div className="flex items-start justify-between gap-3 pt-1">
                  <Label htmlFor="task-habit" className="flex-col items-start gap-0.5 font-normal">
                    Acompanhar como hábito
                    <span className="text-xs text-muted-foreground">
                      {fromCompletion ? 'Só para repetição em dias fixos.' : 'Mostra a sequência e o % do mês. Pular não quebra a sequência.'}
                    </span>
                  </Label>
                  <Controller control={form.control} name="habit" render={({ field }) => (
                    <Switch id="task-habit" checked={field.value && !fromCompletion} disabled={fromCompletion}
                      onCheckedChange={field.onChange} />
                  )} />
                </div>
              )}
            </Field>

            {groupId && (
              <AssignmentFields control={form.control} errors={errors} groupId={groupId} repeatOn={repeatOn}
                fromCompletion={fromCompletion} />
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="task-list">Lista</FieldLabel>
                <Controller control={form.control} name="listId" render={({ field }) => (
                  <Select value={field.value || NONE} onValueChange={(v) => field.onChange(v === NONE ? '' : v)}>
                    <SelectTrigger id="task-list" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE}>Sem lista</SelectItem>
                      {labels.lists.map((l) => (
                        <SelectItem key={l.id} value={l.id}>
                          <span className="size-2 rounded-full" style={{ background: l.color }} aria-hidden />{l.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )} />
              </Field>
              <Field>
                <FieldLabel>Etiquetas</FieldLabel>
                <Controller control={form.control} name="tagIds" render={({ field }) => (
                  labels.tags.length === 0 ? (
                    <p className="text-sm text-muted-foreground">Crie etiquetas em “Listas e etiquetas”.</p>
                  ) : (
                    <div className="flex flex-wrap gap-1.5" role="group" aria-label="Etiquetas">
                      {labels.tags.map((tag) => {
                        const on = field.value.includes(tag.id)
                        return (
                          <button key={tag.id} type="button" aria-pressed={on}
                            onClick={() => field.onChange(on ? field.value.filter((id) => id !== tag.id) : [...field.value, tag.id])}
                            className={cn('flex h-7 items-center gap-1 rounded-full border px-2.5 text-xs outline-none transition-colors motion-reduce:transition-none',
                              'focus-visible:ring-[3px] focus-visible:ring-ring/50',
                              on ? 'border-foreground bg-muted font-medium' : 'hover:bg-muted')}>
                            <span className="size-2 rounded-full" style={{ background: tag.color }} aria-hidden />{tag.name}
                          </button>
                        )
                      })}
                    </div>
                  )
                )} />
                <FieldError errors={[errors.tagIds]} />
              </Field>
            </div>

            {!groupId && <FixedField control={form.control} />}

            <Field>
              <FieldLabel>Subtarefas</FieldLabel>
              {subtasks.fields.map((row, index) => (
                <div key={row.id} className="flex gap-1">
                  <Input aria-label={`Subtarefa ${index + 1}`} autoComplete="off" {...form.register(`subtasks.${index}.title`)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault()
                        subtasks.insert(index + 1, { id: null, title: '' })
                      }
                    }} />
                  <Button type="button" variant="ghost" size="icon" aria-label={`Remover subtarefa ${index + 1}`}
                    onClick={() => subtasks.remove(index)}>
                    <XIcon />
                  </Button>
                </div>
              ))}
              {subtasks.fields.length < 50 && (
                <Button type="button" variant="outline" size="sm" className="self-start"
                  onClick={() => subtasks.append({ id: null, title: '' })}>
                  <PlusIcon /> Adicionar subtarefa
                </Button>
              )}
              {task?.repeat && subtasks.fields.length > 0 && (
                <FieldDescription>Em tarefa repetida, cada ocorrência tem o próprio checklist.</FieldDescription>
              )}
            </Field>

            <Field data-invalid={!!errors.notes}>
              <FieldLabel htmlFor="task-notes">Notas</FieldLabel>
              <Textarea id="task-notes" rows={3} aria-invalid={!!errors.notes} {...form.register('notes')} />
              <FieldError errors={[errors.notes]} />
            </Field>
          </FieldGroup>
        </form>
        {groupId && task && <TaskComments groupId={groupId} taskId={task.id} />}
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" form="task-form" disabled={isSubmitting || save.isPending || saveOccurrence.isPending}>
          {(save.isPending || saveOccurrence.isPending) && <Loader2Icon className="animate-spin" />}
          Salvar
        </Button>
      </DialogFooter>
      <ScopeDialog open={scopeFor !== null} onOpenChange={(o) => !o && setScopeFor(null)}
        title="Tarefa repetida" description="Aplicar a mudança em quais ocorrências?" options={scopeOptions} onChoose={onScope} />
    </>
  )
}
