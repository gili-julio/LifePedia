import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate, todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { STATUS_LABEL } from '../absences'
import { useCommitmentDays, useCommitments, useMarkDay } from '../api'
import type { Commitment, CommitmentDay, Mark } from '../types'
import { AbsenceMeter } from './AbsenceMeter'
import { MarkButtons } from './MarkButtons'

const weekday = new Intl.DateTimeFormat('pt-BR', { weekday: 'short' })

const STATUS_STYLE: Record<CommitmentDay['status'], string> = {
  PRESENT: 'bg-income/15 text-income',
  ABSENT: 'bg-destructive/15 text-destructive',
  CANCELED: 'bg-muted text-muted-foreground',
  PENDING: 'bg-orange-500/15 text-orange-700 dark:text-orange-300',
  UPCOMING: 'bg-muted text-muted-foreground',
}

type Props = { commitment: Commitment | null; onOpenChange: (open: boolean) => void }

/** Dias que já chegaram, com Presente / Falta / Sem aula em cada um. */
export function DaysDialog({ commitment, onOpenChange }: Props) {
  return (
    <Dialog open={commitment !== null} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        {commitment && <Days commitment={commitment} onClose={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function Days({ commitment: initial, onClose }: { commitment: Commitment; onClose: () => void }) {
  // Contagem sempre da lista atual (muda a cada marcação).
  const commitment = useCommitments().data?.find((c) => c.id === initial.id) ?? initial
  const { data, isLoading } = useCommitmentDays(commitment.id)
  const mark = useMarkDay()
  const today = todayIso()
  const pending = data?.filter((d) => d.status === 'PENDING').length ?? 0

  async function onMark(date: string, status: Mark | null) {
    try {
      await mark.mutateAsync({ id: commitment.id, date, status })
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível marcar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2">
          <span className="size-3 shrink-0 rounded-full" style={{ background: commitment.color }} aria-hidden />
          {commitment.name}
        </DialogTitle>
        <DialogDescription>
          {pending > 0 ? `${pending} ${pending === 1 ? 'dia pendente' : 'dias pendentes'}: marque presença ou falta.`
            : 'Toque para marcar presença, falta ou sem aula.'}
        </DialogDescription>
      </DialogHeader>
      <DialogBody>
        <AbsenceMeter absences={commitment.stats.absences} max={commitment.maxAbsences} />
        {isLoading || !data ? <Skeleton className="h-40 w-full" /> : data.length === 0 ? (
          <p className="text-sm text-muted-foreground">Ainda não chegou nenhum dia deste compromisso.</p>
        ) : (
          <ul className="divide-y">
            {data.map((day) => (
              <li key={day.date} className="flex flex-wrap items-center gap-x-3 gap-y-2 py-3">
                <span className="w-24 shrink-0 text-sm tabular-nums">
                  <span className="capitalize text-muted-foreground">{weekday.format(new Date(`${day.date}T12:00:00`)).replace('.', '')}</span>{' '}
                  {formatDate(day.date).slice(0, 5)}
                </span>
                <Badge className={cn('shrink-0', STATUS_STYLE[day.status])}>
                  {STATUS_LABEL[day.status]}{day.fromTask ? ' (tarefa)' : ''}
                </Badge>
                <div className="w-full sm:ml-auto sm:w-72">
                  <MarkButtons status={day.status} fromTask={day.fromTask} future={day.date > today}
                    disabled={mark.isPending} onMark={(status) => void onMark(day.date, status)} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Fechar</Button>
      </DialogFooter>
    </>
  )
}
