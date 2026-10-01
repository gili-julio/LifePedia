import { ChevronDownIcon, UsersIcon } from 'lucide-react'
import { useState } from 'react'
import { Controller, type Control } from 'react-hook-form'
import { Link } from 'react-router'
import { Checkbox } from '@/components/ui/checkbox'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'
import { useGroups } from '@/features/groups/api'
import { GroupAvatar } from '@/features/groups/components/GroupAvatar'
import { cn } from '@/lib/utils'
import type { ShareMode } from './types'

type Props = {
  groupIds: string[]
  onGroupIdsChange: (ids: string[]) => void
  mode: ShareMode
  onModeChange: (mode: ShareMode) => void
}

const SHARE_MODES: { value: ShareMode; label: string; description: string }[] = [
  { value: 'SYNC', label: 'Manter sincronizado', description: 'Editar ou excluir aqui reflete nos grupos.' },
  { value: 'COPY', label: 'Criar cópia', description: 'O grupo recebe uma cópia independente.' },
]

/** Escolha do modo (sincronizado ou cópia). */
export function ShareModeRadio({ value, onChange, idPrefix }: { value: ShareMode; onChange: (m: ShareMode) => void; idPrefix: string }) {
  return (
    <RadioGroup value={value} onValueChange={(v) => onChange(v as ShareMode)} className="gap-2">
      {SHARE_MODES.map((m) => (
        <Label key={m.value} htmlFor={`${idPrefix}-${m.value}`}
          className="flex cursor-pointer items-start gap-2.5 rounded-lg border p-2.5 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-primary/5">
          <RadioGroupItem id={`${idPrefix}-${m.value}`} value={m.value} className="mt-0.5" />
          <span className="space-y-0.5">
            <span className="block text-sm font-medium">{m.label}</span>
            <span className="block text-xs text-muted-foreground">{m.description}</span>
          </span>
        </Label>
      ))}
    </RadioGroup>
  )
}

type ShareValues = { groupIds: string[]; shareMode: ShareMode }

/** Liga `groupIds` + `shareMode` de um formulário (React Hook Form) à seção de grupos. */
export function ShareFields<T extends ShareValues>({ control }: { control: Control<T> }) {
  const c = control as unknown as Control<ShareValues>
  return (
    <Controller control={c} name="groupIds" render={({ field: ids }) => (
      <Controller control={c} name="shareMode" render={({ field: mode }) => (
        <GroupShareSection groupIds={ids.value} onGroupIdsChange={ids.onChange} mode={mode.value} onModeChange={mode.onChange} />
      )} />
    )} />
  )
}

/** Seção expansível do cadastro: levar o item também para nenhum, um ou vários grupos. */
export function GroupShareSection({ groupIds, onGroupIdsChange, mode, onModeChange }: Props) {
  const [open, setOpen] = useState(false)
  const { data: groups, isLoading } = useGroups()
  const selected = new Set(groupIds)

  function toggle(id: string, checked: boolean) {
    onGroupIdsChange(checked ? [...groupIds, id] : groupIds.filter((g) => g !== id))
  }

  return (
    <Collapsible open={open} onOpenChange={setOpen} className="rounded-lg border">
      <CollapsibleTrigger className="flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm font-medium focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">
        <UsersIcon className="size-4 text-muted-foreground" />
        <span className="flex-1">
          Levar para grupos
          {groupIds.length > 0 && <span className="ml-1.5 text-xs font-normal text-muted-foreground">({groupIds.length} selecionado{groupIds.length > 1 ? 's' : ''})</span>}
        </span>
        <ChevronDownIcon className={cn('size-4 text-muted-foreground transition-transform motion-reduce:transition-none', open && 'rotate-180')} />
      </CollapsibleTrigger>
      <CollapsibleContent className="space-y-3 border-t px-3 py-3">
        {isLoading ? (
          <Skeleton className="h-12" />
        ) : !groups?.length ? (
          <p className="text-sm text-muted-foreground">
            Você ainda não participa de grupos. <Link to="/grupos" className="font-medium text-foreground underline underline-offset-4">Criar um grupo</Link>
          </p>
        ) : (
          <>
            <ul className="space-y-1">
              {groups.map((g) => (
                <li key={g.id}>
                  <Label htmlFor={`share-${g.id}`} className="flex cursor-pointer items-center gap-3 rounded-md px-1.5 py-1.5 font-normal hover:bg-muted/60">
                    <Checkbox id={`share-${g.id}`} checked={selected.has(g.id)} onCheckedChange={(v) => toggle(g.id, v === true)} />
                    <GroupAvatar name={g.name} imageUrl={g.imageUrl} size="sm" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{g.name}</span>
                      <span className="block text-xs text-muted-foreground">
                        {g.memberCount} participante{g.memberCount === 1 ? '' : 's'}
                      </span>
                    </span>
                  </Label>
                </li>
              ))}
            </ul>
            {groupIds.length > 0 && <ShareModeRadio value={mode} onChange={onModeChange} idPrefix="share-mode" />}
          </>
        )}
      </CollapsibleContent>
    </Collapsible>
  )
}
