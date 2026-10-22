import { Loader2Icon, SendIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { UserAvatar } from '@/components/UserAvatar'
import { ApiError } from '@/lib/api'
import { useAddComment, useComments, useDeleteComment } from '../api'
import type { TaskComment } from '../types'

const MAX = 2000
const when = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

/** Conversa da tarefa de grupo: mais antigos em cima, "ver anteriores" carrega mais. */
export function TaskComments({ groupId, taskId }: { groupId: string; taskId: string }) {
  const { data, isLoading, isError, hasNextPage, fetchNextPage, isFetchingNextPage } = useComments(groupId, taskId)
  const add = useAddComment()
  const remove = useDeleteComment()
  const [text, setText] = useState('')
  const [removing, setRemoving] = useState<{ open: boolean; comment: TaskComment | null }>({ open: false, comment: null })
  const comments = (data?.pages.flatMap((p) => p.items) ?? []).toReversed()
  const total = data?.pages[0]?.totalItems ?? 0

  async function submit() {
    const body = text.trim()
    if (!body) return
    try {
      await add.mutateAsync({ groupId, taskId, body })
      setText('')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível comentar.')
    }
  }

  async function onDelete(comment: TaskComment) {
    try {
      await remove.mutateAsync({ groupId, taskId, id: comment.id })
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível apagar.')
    }
  }

  return (
    <section aria-labelledby="task-comments" className="mt-5 space-y-3 border-t pt-4">
      <h3 id="task-comments" className="text-sm font-medium">
        Comentários{total > 0 && <span className="ml-1.5 text-muted-foreground tabular-nums">{total}</span>}
      </h3>
      {isLoading && <Skeleton className="h-12 w-full" />}
      {isError && <p className="text-sm text-destructive">Não foi possível carregar os comentários.</p>}
      {hasNextPage && (
        <Button type="button" variant="link" size="sm" className="h-auto p-0" disabled={isFetchingNextPage}
          onClick={() => void fetchNextPage()}>
          {isFetchingNextPage && <Loader2Icon className="animate-spin" />} Ver anteriores
        </Button>
      )}
      {comments.length > 0 && (
        <ul className="space-y-3">
          {comments.map((c) => (
            <li key={c.id} className="space-y-0.5">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <UserAvatar user={c.author} size="xs" />
                <span className="font-medium text-foreground">{c.author?.name ?? 'Conta excluída'}</span>
                <time dateTime={c.createdAt}>{when.format(new Date(c.createdAt))}</time>
                {c.canDelete && (
                  <Button type="button" variant="ghost" size="icon-xs" className="ml-auto" aria-label="Apagar comentário"
                    onClick={() => setRemoving({ open: true, comment: c })}>
                    <Trash2Icon />
                  </Button>
                )}
              </div>
              <p className="text-sm break-words whitespace-pre-wrap">{c.body}</p>
            </li>
          ))}
        </ul>
      )}
      <form className="flex items-end gap-2" onSubmit={(e) => {
        e.preventDefault()
        void submit()
      }}>
        <Textarea value={text} onChange={(e) => setText(e.target.value)} rows={2} maxLength={MAX}
          placeholder="Escreva um comentário" aria-label="Novo comentário" className="min-h-0 flex-1"
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
              e.preventDefault()
              void submit()
            }
          }} />
        <Button type="submit" size="icon" aria-label="Enviar comentário" disabled={!text.trim() || add.isPending}>
          {add.isPending ? <Loader2Icon className="animate-spin" /> : <SendIcon />}
        </Button>
      </form>
      <ConfirmDelete open={removing.open} onOpenChange={(open) => setRemoving((r) => ({ ...r, open }))}
        title="Apagar comentário?" description="O comentário sai para todos do grupo." confirmLabel="Apagar"
        onConfirm={() => removing.comment && void onDelete(removing.comment)} />
    </section>
  )
}
