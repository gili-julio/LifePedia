import { CheckIcon, Loader2Icon, SearchIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { cn } from '@/lib/utils'
import { useInviteCandidates } from '../api'
import type { InviteCandidate, UserSummary } from '../types'

type Props = {
  groupId: string
  selected: UserSummary[]
  onChange: (selected: UserSummary[]) => void
}

const STATUS_LABEL = { MEMBER: 'Já participa', INVITED: 'Já convidado' } as const

/** Seleção múltipla de usuários buscando por nome ou @username; cada linha mostra nome e @username. */
export function UserPicker({ groupId, selected, onChange }: Props) {
  const [query, setQuery] = useState('')
  const debounced = useDebouncedValue(query.trim(), 300)
  const { data, isFetching } = useInviteCandidates(groupId, debounced)
  const selectedIds = new Set(selected.map((u) => u.id))
  const tooShort = debounced.replace(/^@/, '').length < 2

  function toggle(user: InviteCandidate) {
    if (user.status) return
    onChange(selectedIds.has(user.id) ? selected.filter((u) => u.id !== user.id) : [...selected, user])
  }

  return (
    <div className="space-y-2">
      {selected.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label="Selecionados">
          {selected.map((u) => (
            <li key={u.id} className="flex items-center gap-1 rounded-full bg-muted py-0.5 pr-1 pl-2.5 text-xs">
              {u.name}
              <button type="button" aria-label={`Remover ${u.name}`} onClick={() => onChange(selected.filter((s) => s.id !== u.id))}
                className="rounded-full p-0.5 hover:bg-foreground/10">
                <XIcon className="size-3" />
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="relative">
        <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input value={query} onChange={(e) => setQuery(e.target.value)} className="pl-8"
          placeholder="Buscar por nome ou @username" aria-label="Buscar usuários" autoComplete="off" />
        {isFetching && <Loader2Icon className="absolute top-1/2 right-2.5 size-4 -translate-y-1/2 animate-spin text-muted-foreground" />}
      </div>
      {!tooShort && (
        <ul className="max-h-60 divide-y overflow-y-auto rounded-lg border" role="listbox" aria-multiselectable>
          {!data?.length ? (
            <li className="px-3 py-4 text-center text-sm text-muted-foreground">{isFetching ? 'Buscando…' : 'Ninguém encontrado.'}</li>
          ) : data.map((user) => {
            const checked = selectedIds.has(user.id)
            return (
              <li key={user.id} role="option" aria-selected={checked} aria-disabled={!!user.status}>
                <button type="button" disabled={!!user.status} onClick={() => toggle(user)}
                  className={cn('flex w-full items-center gap-3 px-3 py-2 text-left transition-colors hover:bg-muted/60 disabled:cursor-not-allowed disabled:opacity-60',
                    checked && 'bg-primary/5')}>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{user.name}</span>
                    <span className="block truncate text-xs text-muted-foreground">@{user.username}</span>
                  </span>
                  {user.status ? (
                    <span className="text-xs text-muted-foreground">{STATUS_LABEL[user.status]}</span>
                  ) : (
                    <span className={cn('flex size-5 items-center justify-center rounded-full border', checked && 'border-primary bg-primary text-primary-foreground')}>
                      {checked && <CheckIcon className="size-3.5" />}
                    </span>
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      )}
      {tooShort && query.trim().length > 0 && (
        <p className="text-xs text-muted-foreground">Digite ao menos 2 caracteres.</p>
      )}
    </div>
  )
}
