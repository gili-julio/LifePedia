import { EllipsisVerticalIcon, Loader2Icon, PencilIcon, ReplyIcon, SendHorizontalIcon, Trash2Icon, XIcon } from 'lucide-react'
import { Fragment, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { UserAvatar } from '@/components/UserAvatar'
import { useAuth } from '@/features/auth/auth-context'
import { ApiError } from '@/lib/api'
import { formatDayLabel, formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useChatMessages, useDeleteMessage, useEditMessage, useMarkChatRead, useSendMessage } from '../api'
import { type ChatMessage, MAX_MESSAGE } from '../types'

/** Perto do fim da conversa: mensagem nova rola junto. */
const NEAR_BOTTOM = 120

/** Chat do grupo, estilo WhatsApp: foto, nome e @ de quem escreve, responder, editar e apagar a própria mensagem. */
export function GroupChat({ groupId }: { groupId: string }) {
  const { user } = useAuth()
  const { data, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage } = useChatMessages(groupId)
  const markRead = useMarkChatRead(groupId)
  const remove = useDeleteMessage(groupId)
  const [replyTo, setReplyTo] = useState<ChatMessage | null>(null)
  const [editing, setEditing] = useState<ChatMessage | null>(null)
  const [deleting, setDeleting] = useState<ChatMessage | null>(null)
  const scroller = useRef<HTMLDivElement>(null)
  const restore = useRef<number | null>(null)
  const stick = useRef(true)

  const messages = data ? [...data.pages].reverse().flatMap((p) => p.items) : []
  const last = messages.at(-1)

  // Mensagem nova: rola para o fim se já estava lá (ou se fui eu); carregar antigas mantém a posição.
  useLayoutEffect(() => {
    const el = scroller.current
    if (!el) return
    if (restore.current !== null) {
      el.scrollTop = el.scrollHeight - restore.current
      restore.current = null
    } else if (stick.current || last?.author.id === user?.id) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages.length, last?.id, last?.author.id, user?.id])

  const { mutate: read } = markRead
  useEffect(() => {
    if (last && last.author.id !== user?.id && document.visibilityState === 'visible') read()
  }, [last, user?.id, read])
  // Ao abrir e ao voltar para a aba do navegador (mensagem que chegou com a aba escondida fica não lida até lá).
  useEffect(() => {
    read()
    const onVisible = () => document.visibilityState === 'visible' && read()
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [read])

  function loadOlder() {
    const el = scroller.current
    if (el) restore.current = el.scrollHeight - el.scrollTop
    fetchNextPage()
  }

  async function confirmDelete(message: ChatMessage) {
    try {
      await remove.mutateAsync(message)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível apagar.')
    }
  }

  function jumpTo(id: string) {
    document.getElementById(`msg-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  return (
    <Card className="flex h-[calc(100dvh-15rem)] min-h-96 flex-col gap-0 overflow-hidden py-0">
      <div ref={scroller} role="log" aria-live="polite" aria-label="Mensagens do grupo"
        onScroll={(e) => {
          const el = e.currentTarget
          stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM
        }}
        className="flex-1 space-y-1 overflow-y-auto bg-muted/30 px-3 py-4 sm:px-4">
        {isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className={cn('h-12 w-2/3 rounded-2xl', i % 2 && 'ml-auto')} />)}
          </div>
        ) : isError ? (
          <p className="py-12 text-center text-sm text-destructive">Não foi possível carregar as mensagens.</p>
        ) : messages.length === 0 ? (
          <p className="py-12 text-center text-sm text-muted-foreground">Nenhuma mensagem ainda. Diga oi para o grupo!</p>
        ) : (
          <>
            {hasNextPage && (
              <div className="flex justify-center pb-2">
                <Button variant="outline" size="sm" disabled={isFetchingNextPage} onClick={loadOlder}>
                  {isFetchingNextPage && <Loader2Icon className="animate-spin" />} Mensagens anteriores
                </Button>
              </div>
            )}
            {messages.map((m, i) => {
              const previous = messages[i - 1]
              const newDay = !previous || formatDayLabel(previous.createdAt) !== formatDayLabel(m.createdAt)
              const grouped = !newDay && previous.author.id === m.author.id
              return (
                <Fragment key={m.id}>
                  {newDay && (
                    <div className="flex justify-center py-2">
                      <span className="rounded-full bg-background px-3 py-0.5 text-xs text-muted-foreground shadow-xs">
                        {formatDayLabel(m.createdAt)}
                      </span>
                    </div>
                  )}
                  <MessageBubble message={m} mine={m.author.id === user?.id} grouped={grouped}
                    onReply={() => {
                      setEditing(null)
                      setReplyTo(m)
                    }}
                    onEdit={() => {
                      setReplyTo(null)
                      setEditing(m)
                    }}
                    onDelete={() => setDeleting(m)}
                    onJump={jumpTo} />
                </Fragment>
              )
            })}
          </>
        )}
      </div>
      <Composer key={editing?.id ?? 'new'} groupId={groupId} replyTo={replyTo} editing={editing}
        onDone={() => {
          setReplyTo(null)
          setEditing(null)
          stick.current = true
        }} />
      <ConfirmDelete open={!!deleting} onOpenChange={(open) => !open && setDeleting(null)}
        title="Apagar mensagem?" description="Ela some para todos do grupo." confirmLabel="Apagar"
        onConfirm={() => deleting && confirmDelete(deleting)} />
    </Card>
  )
}

type BubbleProps = {
  message: ChatMessage
  mine: boolean
  /** Mesma pessoa da mensagem anterior: sem foto e nome */
  grouped: boolean
  onReply: () => void
  onEdit: () => void
  onDelete: () => void
  onJump: (id: string) => void
}

function MessageBubble({ message: m, mine, grouped, onReply, onEdit, onDelete, onJump }: BubbleProps) {
  return (
    <div id={`msg-${m.id}`} className={cn('group/msg flex items-end gap-2', mine && 'flex-row-reverse', !grouped && 'pt-2')}>
      {!mine && (grouped ? <span className="w-8 shrink-0" /> : <UserAvatar user={m.author} size="sm" className="shrink-0" />)}
      <div className={cn('max-w-[85%] min-w-0 rounded-2xl px-3 py-1.5 shadow-xs sm:max-w-[70%]',
        mine ? 'rounded-br-md bg-primary text-primary-foreground' : 'rounded-bl-md bg-background')}>
        {!mine && !grouped && (
          <p className="truncate text-xs font-semibold">
            {m.author.name} <span className="font-normal text-muted-foreground">@{m.author.username}</span>
          </p>
        )}
        {m.replyTo && (
          <button type="button" onClick={() => onJump(m.replyTo!.id)}
            className={cn('mt-1 block w-full rounded-md border-l-2 px-2 py-1 text-left text-xs',
              mine ? 'border-primary-foreground/60 bg-primary-foreground/10' : 'border-primary/60 bg-muted')}>
            <span className="block truncate font-semibold">{m.replyTo.author.name}</span>
            <span className={cn('line-clamp-2 break-words', !m.replyTo.body && 'italic')}>
              {m.replyTo.body ?? 'Mensagem apagada'}
            </span>
          </button>
        )}
        <p className={cn('text-sm break-words whitespace-pre-wrap', m.deleted && 'italic opacity-70')}>
          {m.deleted ? 'Mensagem apagada' : m.body}
        </p>
        <p className={cn('text-right text-[10px] tabular-nums', mine ? 'text-primary-foreground/70' : 'text-muted-foreground')}>
          {m.editedAt && !m.deleted && 'editada · '}{formatTime(m.createdAt)}
        </p>
      </div>
      {!m.deleted && (
        <DropdownMenu modal={false}>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label="Ações da mensagem"
              className="shrink-0 text-muted-foreground opacity-100 transition-opacity focus-visible:opacity-100 motion-reduce:transition-none md:opacity-0 md:group-hover/msg:opacity-100 data-[state=open]:opacity-100">
              <EllipsisVerticalIcon />
            </Button>
          </DropdownMenuTrigger>
          {/* O foco vai para o campo de mensagem (responder/editar), não volta para o botão. */}
          <DropdownMenuContent align={mine ? 'end' : 'start'} onCloseAutoFocus={(e) => e.preventDefault()}>
            <DropdownMenuItem onSelect={onReply}><ReplyIcon /> Responder</DropdownMenuItem>
            {mine && <DropdownMenuItem onSelect={onEdit}><PencilIcon /> Editar</DropdownMenuItem>}
            {mine && <DropdownMenuItem variant="destructive" onSelect={onDelete}><Trash2Icon /> Apagar</DropdownMenuItem>}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  )
}

type ComposerProps = { groupId: string; replyTo: ChatMessage | null; editing: ChatMessage | null; onDone: () => void }

/** Enter envia, Shift+Enter quebra a linha. Editando: o texto da mensagem já vem no campo. */
function Composer({ groupId, replyTo, editing, onDone }: ComposerProps) {
  const send = useSendMessage(groupId)
  const edit = useEditMessage(groupId)
  const [text, setText] = useState(editing?.body ?? '')
  const input = useRef<HTMLTextAreaElement>(null)
  const busy = send.isPending || edit.isPending
  const body = text.trim()

  useEffect(() => {
    const el = input.current
    if (!el || !(replyTo || editing)) return
    el.focus()
    el.setSelectionRange(el.value.length, el.value.length)
  }, [replyTo, editing])

  async function submit() {
    if (!body || busy || body.length > MAX_MESSAGE) return
    try {
      if (editing) await edit.mutateAsync({ id: editing.id, body })
      else await send.mutateAsync({ body, replyToId: replyTo?.id })
      setText('')
      onDone()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível enviar.')
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
    if (e.key === 'Escape' && (replyTo || editing)) onDone()
  }

  const context = editing ? { label: 'Editando mensagem', text: editing.body } : replyTo
    ? { label: `Respondendo ${replyTo.author.name}`, text: replyTo.body ?? 'Mensagem apagada' } : null

  return (
    <div className="border-t bg-background p-2 sm:p-3">
      {context && (
        <div className="mb-2 flex items-start gap-2 rounded-lg border-l-2 border-primary bg-muted px-2 py-1.5 text-xs">
          <span className="min-w-0 flex-1">
            <span className="block font-semibold">{context.label}</span>
            <span className="line-clamp-1 break-words text-muted-foreground">{context.text}</span>
          </span>
          <Button variant="ghost" size="icon-xs" aria-label="Cancelar" onClick={onDone}><XIcon /></Button>
        </div>
      )}
      <div className="flex items-end gap-2">
        <Textarea ref={input} rows={1} value={text} maxLength={MAX_MESSAGE} placeholder="Mensagem"
          aria-label="Mensagem" onChange={(e) => setText(e.target.value)} onKeyDown={onKeyDown}
          className="max-h-32 min-h-10 resize-none py-2 text-base md:text-sm" />
        <Button size="icon" aria-label={editing ? 'Salvar edição' : 'Enviar'} disabled={!body || busy} onClick={submit}>
          {busy ? <Loader2Icon className="animate-spin" /> : <SendHorizontalIcon />}
        </Button>
      </div>
      {text.length > MAX_MESSAGE - 200 && (
        <p className="mt-1 text-right text-xs text-muted-foreground tabular-nums">{text.length}/{MAX_MESSAGE}</p>
      )}
    </div>
  )
}
