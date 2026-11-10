import { InfoIcon, TriangleAlertIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useAnnouncements } from './api'

const STORAGE_KEY = 'lifepedia:dismissed-announcements'

/** Ids dos avisos que a pessoa fechou neste navegador (só preferência de tela, nada sensível). */
function readDismissed(): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(value) ? value.filter((v) => typeof v === 'string') : []
  } catch {
    return []
  }
}

/** Faixa com os avisos da administração, no topo do app; cada um pode ser fechado. */
export function AnnouncementBanner() {
  const { data } = useAnnouncements()
  const [dismissed, setDismissed] = useState(readDismissed)
  const visible = data?.filter((a) => !dismissed.includes(a.id)) ?? []
  if (!visible.length) return null

  function dismiss(id: string) {
    // Guarda só os ids ainda no ar, para a lista não crescer para sempre.
    const next = [...dismissed, id].filter((d) => data?.some((a) => a.id === d))
    setDismissed(next)
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    } catch {
      // sem armazenamento: some só até recarregar
    }
  }

  return (
    <div className="space-y-2 px-4 pt-4">
      {visible.map((a) => {
        const warning = a.level === 'WARNING'
        const Icon = warning ? TriangleAlertIcon : InfoIcon
        return (
          <div key={a.id} role={warning ? 'alert' : 'status'}
            className={cn('mx-auto flex max-w-6xl items-start gap-3 rounded-lg border px-3 py-2 text-sm',
              warning ? 'border-destructive/30 bg-destructive/10' : 'border-data/30 bg-data/10')}>
            <Icon className={cn('mt-0.5 size-4 shrink-0', warning ? 'text-destructive' : 'text-data')} aria-hidden />
            <p className="min-w-0 flex-1 break-words whitespace-pre-line">
              <span className="sr-only">{warning ? 'Atenção: ' : 'Aviso: '}</span>{a.message}
            </p>
            <Button variant="ghost" size="icon-xs" className="-my-0.5 shrink-0" aria-label="Fechar aviso" onClick={() => dismiss(a.id)}>
              <XIcon />
            </Button>
          </div>
        )
      })}
    </div>
  )
}
