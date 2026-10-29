import { ArrowDownIcon, ArrowUpIcon, CheckIcon, PlusIcon, XIcon } from 'lucide-react'
import { type Control, Controller, type FieldErrors, useWatch } from 'react-hook-form'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { useGroup } from '@/features/groups/api'
import type { GroupMember } from '@/features/groups/types'
import { cn } from '@/lib/utils'
import type { TaskFormInput } from './task-form'

type Props = {
  control: Control<TaskFormInput>
  errors: FieldErrors<TaskFormInput>
  groupId: string
  repeatOn: boolean
  fromCompletion: boolean
}

/** Responsáveis da tarefa de grupo: ninguém, pessoas escolhidas, todos do grupo ou rodízio em ordem fixa. */
export function AssignmentFields({ control, errors, groupId, repeatOn, fromCompletion }: Props) {
  const { data: group } = useGroup(groupId)
  const { user } = useAuth()
  const [mode, assignees] = useWatch({ control, name: ['assignMode', 'assignees'] })
  if (!group) return <Skeleton className="h-9 w-full" />
  const name = (id: string) => {
    const member = group.members.find((m) => m.user.id === id)
    if (!member) return 'Ex-membro'
    return member.user.id === user?.id ? `${member.user.name} (você)` : member.user.name
  }
  const several = mode === 'ALL' || (mode === 'PEOPLE' && assignees.length > 1)

  return (
    <Field data-invalid={!!errors.assignees || !!errors.rotation}>
      <FieldLabel htmlFor="task-assign">Responsáveis</FieldLabel>
      <Controller control={control} name="assignMode" render={({ field }) => (
        <Select value={field.value} onValueChange={field.onChange}>
          <SelectTrigger id="task-assign" className="w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="NONE">Ninguém (qualquer um faz)</SelectItem>
            <SelectItem value="PEOPLE">Escolher pessoas</SelectItem>
            <SelectItem value="ALL">Todos do grupo</SelectItem>
            <SelectItem value="ROTATION" disabled={!repeatOn}>Rodízio (revezar a cada vez)</SelectItem>
          </SelectContent>
        </Select>
      )} />
      {mode === 'PEOPLE' && (
        <Controller control={control} name="assignees" render={({ field }) => (
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Pessoas responsáveis">
            {group.members.map((m) => {
              const on = field.value.includes(m.user.id)
              return (
                <button key={m.user.id} type="button" aria-pressed={on}
                  onClick={() => field.onChange(on ? field.value.filter((id) => id !== m.user.id) : [...field.value, m.user.id])}
                  className={cn('flex h-8 items-center gap-1.5 rounded-full border py-1 pr-3 pl-1 text-sm outline-none transition-colors motion-reduce:transition-none',
                    'focus-visible:ring-[3px] focus-visible:ring-ring/50',
                    on ? 'border-foreground bg-muted font-medium' : 'hover:bg-muted')}>
                  <UserAvatar user={m.user} size="xs" />
                  {name(m.user.id)}
                  {on && <CheckIcon className="size-3.5" aria-hidden />}
                </button>
              )
            })}
          </div>
        )} />
      )}
      {mode === 'ALL' && <FieldDescription>Vale para todo mundo do grupo, inclusive quem entrar depois.</FieldDescription>}
      {several && (
        <Controller control={control} name="completion" render={({ field }) => (
          <RadioGroup value={field.value} onValueChange={field.onChange} className="gap-2 pt-1" aria-label="Como concluir">
            <Label className="items-start gap-2 font-normal">
              <RadioGroupItem value="ANY" className="mt-0.5" />
              <span>Basta um concluir<span className="block text-xs text-muted-foreground">Ex.: lavar a louça.</span></span>
            </Label>
            <Label className="items-start gap-2 font-normal">
              <RadioGroupItem value="EACH" className="mt-0.5" />
              <span>Cada um conclui a sua<span className="block text-xs text-muted-foreground">Ex.: academia juntos. Fica feita quando todos marcarem.</span></span>
            </Label>
          </RadioGroup>
        )} />
      )}
      {mode === 'ROTATION' && (
        <Controller control={control} name="rotation" render={({ field }) => (
          <RotationPicker value={field.value} onChange={field.onChange} members={group.members} name={name} />
        )} />
      )}
      {mode === 'ROTATION' && (
        <FieldDescription>
          {fromCompletion
            ? 'A vez passa para a próxima pessoa quando a tarefa é concluída ou pulada.'
            : 'Cada ocorrência é de uma pessoa, nesta ordem (pular também passa a vez).'}
        </FieldDescription>
      )}
      {mode === 'NONE' && !repeatOn && <FieldDescription>Para revezar, ligue a repetição.</FieldDescription>}
      <FieldError errors={[errors.assignees, errors.rotation]} />
    </Field>
  )
}

function RotationPicker({ value, onChange, members, name }: {
  value: string[]
  onChange: (value: string[]) => void
  members: GroupMember[]
  name: (id: string) => string
}) {
  const others = members.filter((m) => !value.includes(m.user.id))
  function move(index: number, delta: number) {
    const next = [...value]
    const [item] = next.splice(index, 1)
    next.splice(index + delta, 0, item)
    onChange(next)
  }

  return (
    <div className="space-y-2">
      {value.length > 0 && (
        <ol className="divide-y rounded-lg border" aria-label="Ordem do rodízio">
          {value.map((id, i) => (
            <li key={id} className="flex items-center gap-1 py-1 pr-1 pl-3 text-sm">
              <span className="w-5 text-muted-foreground tabular-nums" aria-hidden>{i + 1}.</span>
              <span className="min-w-0 flex-1 truncate">{name(id)}</span>
              <Button type="button" variant="ghost" size="icon-sm" aria-label={`Subir ${name(id)}`} disabled={i === 0}
                onClick={() => move(i, -1)}>
                <ArrowUpIcon />
              </Button>
              <Button type="button" variant="ghost" size="icon-sm" aria-label={`Descer ${name(id)}`} disabled={i === value.length - 1}
                onClick={() => move(i, 1)}>
                <ArrowDownIcon />
              </Button>
              <Button type="button" variant="ghost" size="icon-sm" aria-label={`Tirar ${name(id)} do rodízio`}
                onClick={() => onChange(value.filter((v) => v !== id))}>
                <XIcon />
              </Button>
            </li>
          ))}
        </ol>
      )}
      {others.length > 0 && (
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Adicionar ao rodízio">
          {others.map((m) => (
            <Button key={m.user.id} type="button" variant="outline" size="sm" className="h-7 rounded-full text-xs"
              onClick={() => onChange([...value, m.user.id])}>
              <PlusIcon /> {name(m.user.id)}
            </Button>
          ))}
        </div>
      )}
    </div>
  )
}
