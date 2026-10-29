import { CalendarCheck2Icon } from 'lucide-react'
import type { CSSProperties } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { absenceTone, endOf, remainingLabel, STATUS_LABEL, TONE_TEXT } from '../absences'
import { useMarkDay } from '../api'
import type { AttendanceAgendaItem, Mark } from '../types'
import { MarkButtons } from './MarkButtons'

type Props = {
  item: AttendanceAgendaItem
  today: string
  /** Posição na grade (a agenda calcula). */
  style: CSSProperties
  /** Bloco baixo ou estreito: só o nome numa linha. */
  short: boolean
}

/** Compromisso da Frequência na agenda: borda tracejada; tocar abre Presente / Falta / Sem aula. */
export function CommitmentBlock({ item, today, style, short }: Props) {
  const mark = useMarkDay()
  const tone = absenceTone(item.absences, item.maxAbsences)
  const end = endOf(item.time, item.durationMinutes)
  const canceled = item.status === 'CANCELED'

  async function onMark(status: Mark | null) {
    try {
      await mark.mutateAsync({ id: item.commitmentId, date: item.date, status })
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível marcar.')
    }
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button type="button" onPointerDown={(e) => e.stopPropagation()}
          aria-label={`${item.name}, ${item.time} às ${end}, ${STATUS_LABEL[item.status]}. Faltas ${item.absences} de ${item.maxAbsences}. Marcar`}
          className={cn('absolute z-[5] overflow-hidden rounded-md border border-dashed px-1.5 text-left text-xs shadow-xs outline-none',
            'focus-visible:ring-[3px] focus-visible:ring-ring/50', short ? 'flex items-center py-0' : 'flex flex-col justify-start py-1', canceled && 'opacity-60')}
          style={{
            ...style,
            background: `color-mix(in oklab, ${item.color} 10%, var(--card))`,
            borderColor: `color-mix(in oklab, ${item.color} 70%, transparent)`,
          }}>
          <span className={cn('flex min-w-0 items-center gap-1 font-medium leading-tight', canceled && 'line-through')}>
            <CalendarCheck2Icon className="size-3 shrink-0" style={{ color: item.color }} aria-hidden />
            <span className="truncate">{item.name}</span>
          </span>
          {!short && (
            <span className="mt-0.5 block truncate text-[11px] text-muted-foreground tabular-nums">
              {canceled ? 'Sem aula' : `Faltas ${item.absences}/${item.maxAbsences}`}
              {item.status === 'PRESENT' || item.status === 'ABSENT' ? ` · ${STATUS_LABEL[item.status]}` : ''}
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-72 space-y-3">
        <div className="space-y-0.5">
          <p className="font-medium">{item.name}</p>
          <p className="text-xs text-muted-foreground tabular-nums">
            {item.time} às {end} · {STATUS_LABEL[item.status]}{item.fromTask ? ' (pela tarefa concluída)' : ''}
          </p>
          <p className={cn('text-xs', TONE_TEXT[tone])}>
            Faltas {item.absences} de {item.maxAbsences}. {remainingLabel(item.absences, item.maxAbsences)}.
          </p>
        </div>
        <MarkButtons status={item.status} fromTask={item.fromTask} future={item.date > today} disabled={mark.isPending}
          onMark={(status) => void onMark(status)} />
        <Link to="/frequencia" className="block text-xs text-muted-foreground underline-offset-2 hover:underline">
          Abrir a Frequência
        </Link>
      </PopoverContent>
    </Popover>
  )
}
