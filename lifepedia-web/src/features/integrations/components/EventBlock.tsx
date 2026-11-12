import { CalendarDaysIcon, ExternalLinkIcon, MapPinIcon, VideoIcon } from 'lucide-react'
import type { CSSProperties } from 'react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { formatTime } from '@/lib/format'
import type { CalendarEvent } from '../types'

type Props = {
  event: CalendarEvent
  /** Posição na grade (a agenda calcula) */
  style: CSSProperties
  short: boolean
}

/** Evento do Google Agenda na agenda: só leitura, com a cor da conta; tocar mostra detalhes e o link da chamada. */
export function EventBlock({ event, style, short }: Props) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button type="button" onPointerDown={(e) => e.stopPropagation()}
          aria-label={`${event.title}, ${formatTime(event.start)} às ${formatTime(event.end)}, agenda ${event.accountName}. Detalhes`}
          className={`absolute z-[4] overflow-hidden rounded-md border px-1.5 text-left text-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 ${short ? 'flex items-center py-0' : 'flex flex-col justify-start py-1'}`}
          style={{
            ...style,
            background: `repeating-linear-gradient(135deg, color-mix(in oklab, ${event.color} 14%, var(--card)) 0 6px, color-mix(in oklab, ${event.color} 8%, var(--card)) 6px 12px)`,
            borderColor: `color-mix(in oklab, ${event.color} 60%, transparent)`,
          }}>
          <span className="flex min-w-0 items-center gap-1 font-medium leading-tight">
            <CalendarDaysIcon className="size-3 shrink-0" style={{ color: event.color }} aria-hidden />
            <span className="truncate">{event.title}</span>
          </span>
          {!short && <span className="mt-0.5 block truncate text-[11px] text-muted-foreground tabular-nums">{formatTime(event.start)} · {event.accountName}</span>}
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-72 space-y-2">
        <p className="font-medium">{event.title}</p>
        <p className="text-xs text-muted-foreground tabular-nums">
          {formatTime(event.start)} às {formatTime(event.end)} · {event.accountName}
        </p>
        {event.location && (
          <p className="flex items-start gap-1.5 text-xs"><MapPinIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />{event.location}</p>
        )}
        <div className="flex flex-wrap gap-2 pt-1">
          {event.meetUrl && (
            <a href={event.meetUrl} target="_blank" rel="noopener noreferrer"
              className="flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium hover:bg-muted">
              <VideoIcon className="size-3.5" aria-hidden />Entrar na chamada
            </a>
          )}
          {event.htmlUrl && (
            <a href={event.htmlUrl} target="_blank" rel="noopener noreferrer"
              className="flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs hover:bg-muted">
              <ExternalLinkIcon className="size-3.5" aria-hidden />Abrir no Google Agenda
            </a>
          )}
        </div>
        <p className="text-[11px] text-muted-foreground">Vem do Google Agenda: edite por lá.</p>
      </PopoverContent>
    </Popover>
  )
}
