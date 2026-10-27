import { CheckIcon, Loader2Icon, SearchIcon, UserPlusIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { UserAvatar } from '@/components/UserAvatar'
import { ApiError } from '@/lib/api'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useFriendCandidates, useSendFriendRequest } from '../api'
import type { FriendCandidate } from '../types'

type Props = { open: boolean; onOpenChange: (open: boolean) => void }

const STATUS_LABEL = { FRIEND: 'Amigos', SENT: 'Pedido enviado' } as const

/** Busca por nome ou @username e envia o pedido de amizade. */
export function AddFriendDialog({ open, onOpenChange }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>Adicionar amigo</DialogTitle>
          <DialogDescription>A pessoa recebe uma notificação e aceita em Amigos → Pedidos.</DialogDescription>
        </DialogHeader>
        <SearchBody />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Fechar</Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}

/** Conteúdo; montado só com o dialog aberto (a busca recomeça a cada abertura). */
function SearchBody() {
  const [query, setQuery] = useState('')
  const debounced = useDebouncedValue(query.trim(), 300)
  const { data, isFetching } = useFriendCandidates(debounced)
  const send = useSendFriendRequest()
  const tooShort = debounced.replace(/^@/, '').length < 2

  async function add(user: FriendCandidate) {
    try {
      const result = await send.mutateAsync(user.id)
      toast.success(result.status === 'ACCEPTED' ? `Agora você e ${user.name} são amigos.` : 'Pedido enviado.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível enviar o pedido.')
    }
  }

  return (
    <DialogBody className="space-y-2">
      <div className="relative">
        <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input value={query} onChange={(e) => setQuery(e.target.value)} className="pl-8 text-base md:text-sm" autoFocus
          placeholder="Buscar por nome ou @username" aria-label="Buscar pessoas" autoComplete="off" />
        {isFetching && <Loader2Icon className="absolute top-1/2 right-2.5 size-4 -translate-y-1/2 animate-spin text-muted-foreground" />}
      </div>
      {tooShort ? (
        <p className="px-1 text-xs text-muted-foreground">Digite pelo menos 2 letras.</p>
      ) : (
        <ul className="divide-y rounded-lg border">
          {!data?.length ? (
            <li className="px-3 py-4 text-center text-sm text-muted-foreground">{isFetching ? 'Buscando…' : 'Ninguém encontrado.'}</li>
          ) : data.map((user) => (
            <li key={user.id} className="flex items-center gap-3 px-3 py-2">
              <UserAvatar user={user} size="sm" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{user.name}</span>
                <span className="block truncate text-xs text-muted-foreground">@{user.username}</span>
              </span>
              {user.status === 'FRIEND' || user.status === 'SENT' ? (
                <span className="flex shrink-0 items-center gap-1 text-xs text-muted-foreground">
                  <CheckIcon className="size-3.5" /> {STATUS_LABEL[user.status]}
                </span>
              ) : (
                <Button size="sm" variant={user.status === 'RECEIVED' ? 'default' : 'outline'} className="shrink-0"
                  disabled={send.isPending} onClick={() => add(user)}>
                  {user.status === 'RECEIVED' ? <><CheckIcon /> Aceitar</> : <><UserPlusIcon /> Adicionar</>}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </DialogBody>
  )
}
