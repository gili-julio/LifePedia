import { MapPinIcon, VideoIcon } from 'lucide-react'
import type { CalendarEvent } from '@/features/integrations/types'
import { formatTime } from '@/lib/format'

/** Eventos do Google Agenda (hora, conta e link da chamada). */
export function EventList({ events, showDay }: { events: CalendarEvent[]; showDay?: boolean }) {
  return (
    <ul className="divide-y">
      {events.map((event) => (
        <li key={event.id} className="flex items-start gap-3 py-2.5">
          <span className="w-14 shrink-0 text-xs text-muted-foreground tabular-nums">
            {showDay && <span className="block">{dayOf(event.start)}</span>}
            {event.allDay ? 'Dia todo' : formatTime(event.start)}
          </span>
          <div className="min-w-0 flex-1 space-y-0.5">
            <p className="flex items-center gap-1.5 text-sm font-medium">
              <span className="size-2 shrink-0 rounded-full" style={{ background: event.color }} aria-hidden />
              <span className="min-w-0 break-words">{event.title}</span>
            </p>
            <p className="flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
              <span>{event.accountName}</span>
              {!event.allDay && <span>até {formatTime(event.end)}</span>}
              {event.location && <span className="flex items-center gap-1"><MapPinIcon className="size-3" aria-hidden />{event.location}</span>}
            </p>
          </div>
          {event.meetUrl && (
            <a href={event.meetUrl} target="_blank" rel="noopener noreferrer"
              className="flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
              <VideoIcon className="size-3.5" aria-hidden />Entrar
            </a>
          )}
        </li>
      ))}
    </ul>
  )
}

function dayOf(iso: string) {
  return new Date(iso).toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit' })
}
