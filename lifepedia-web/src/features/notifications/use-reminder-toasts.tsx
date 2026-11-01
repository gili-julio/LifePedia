import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import type { Page } from '@/components/data-table/list-state'
import { api } from '@/lib/api'
import { useMarkNotificationRead } from './api'
import { notificationContent } from './notification-content'
import type { AppNotification, NotificationType } from './types'

const recent = () => api<Page<AppNotification>>('/api/notifications', { query: { page: 0, size: 10 } })

/** Avisos que merecem aparecer na hora, com o rótulo do botão. */
const TOASTED: Partial<Record<NotificationType, string>> = {
  TASK_REMINDER: 'Ver',
  IMPORT_READY: 'Revisar',
  IMPORT_FAILED: 'Ver',
}

/** Mostra na tela lembretes e o fim da importação quando chegam com o app aberto (confere quando as não lidas sobem). */
export function useReminderToasts(unread: number | undefined) {
  const navigate = useNavigate()
  const { mutate: markRead } = useMarkNotificationRead()
  const previous = useRef<number | undefined>(undefined)
  const seen = useRef<Set<string> | null>(null)

  useEffect(() => {
    const before = previous.current
    previous.current = unread
    if (unread === undefined) return
    const first = seen.current === null
    if (!first && (before === undefined || unread <= before)) return
    if (first && unread === 0) {
      seen.current = new Set()
      return
    }
    let cancelled = false
    recent().then(({ items }) => {
      if (cancelled) return
      // Na primeira carga só registra o que já existia.
      if (seen.current === null) {
        seen.current = new Set(items.map((n) => n.id))
        return
      }
      for (const n of items) {
        const label = TOASTED[n.type]
        if (!label || n.read || seen.current.has(n.id)) continue
        seen.current.add(n.id)
        const content = notificationContent(n)
        const Icon = content.icon
        toast(content.text, {
          icon: <Icon className="size-4" />,
          duration: 15_000,
          action: {
            label,
            onClick: () => {
              markRead(n.id)
              navigate(content.link)
            },
          },
        })
      }
    }).catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [unread, navigate, markRead])
}
