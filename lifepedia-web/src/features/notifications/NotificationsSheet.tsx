import { CheckCheckIcon, Loader2Icon } from 'lucide-react'
import { useNavigate } from 'react-router'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useMarkAllNotificationsRead, useMarkNotificationRead, useNotifications } from './api'
import { notificationContent } from './notification-content'
import type { AppNotification } from './types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Lista de notificações (aberta pelo menu do perfil). */
export function NotificationsSheet({ open, onOpenChange }: Props) {
  const navigate = useNavigate()
  const { data, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useNotifications(open)
  const markRead = useMarkNotificationRead()
  const markAll = useMarkAllNotificationsRead()
  const items = data?.pages.flatMap((p) => p.items) ?? []
  const hasUnread = items.some((n) => !n.read)

  function view(n: AppNotification) {
    if (!n.read) markRead.mutate(n.id)
    onOpenChange(false)
    navigate(notificationContent(n).link)
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full gap-0 sm:max-w-md">
        <SheetHeader className="border-b">
          <SheetTitle>Notificações</SheetTitle>
          <SheetDescription className="flex items-center justify-between gap-2">
            Convites e avisos para você.
            {hasUnread && (
              <Button variant="ghost" size="sm" className="-my-1" disabled={markAll.isPending} onClick={() => markAll.mutate()}>
                <CheckCheckIcon /> Marcar todas como lidas
              </Button>
            )}
          </SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto">
          {isLoading ? (
            <div className="space-y-3 p-4">
              {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-14" />)}
            </div>
          ) : !items.length ? (
            <p className="px-4 py-12 text-center text-sm text-muted-foreground">Nenhuma notificação por aqui.</p>
          ) : (
            <ul className="divide-y">
              {items.map((n) => {
                const content = notificationContent(n)
                return (
                  <li key={n.id} className={cn('flex items-start gap-3 px-4 py-3', !n.read && 'bg-primary/5')}>
                    <Avatar size="lg" className="mt-0.5">
                      {n.imageUrl && <AvatarImage src={n.imageUrl} alt="" />}
                      <AvatarFallback><content.icon className="size-4" /></AvatarFallback>
                    </Avatar>
                    <div className="min-w-0 flex-1 space-y-1">
                      <p className="text-sm leading-snug">{content.text}</p>
                      <p className="text-xs text-muted-foreground">{formatRelative(n.createdAt)}</p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      {!n.read && <span className="size-2 rounded-full bg-primary" aria-label="Não lida" />}
                      <Button size="sm" variant="outline" onClick={() => view(n)}>Ver</Button>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
          {hasNextPage && (
            <div className="p-4 text-center">
              <Button variant="ghost" size="sm" disabled={isFetchingNextPage} onClick={() => fetchNextPage()}>
                {isFetchingNextPage && <Loader2Icon className="animate-spin" />} Carregar mais
              </Button>
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
