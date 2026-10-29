import { BanIcon, CheckIcon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { DayStatus, Mark } from '../types'

const OPTIONS: { mark: Mark; label: string; icon: typeof CheckIcon; on: string }[] = [
  { mark: 'PRESENT', label: 'Presente', icon: CheckIcon, on: 'border-income bg-income text-background hover:bg-income/90' },
  { mark: 'ABSENT', label: 'Falta', icon: XIcon, on: 'border-destructive bg-destructive text-white hover:bg-destructive/90' },
  { mark: 'CANCELED', label: 'Sem aula', icon: BanIcon, on: 'border-foreground bg-foreground text-background hover:bg-foreground/90' },
]

type Props = {
  status: DayStatus
  /** Presença vinda da tarefa ligada (sem marcação: não há o que limpar). */
  fromTask: boolean
  /** Dia depois de hoje: só dá para marcar "Sem aula" (como na API). */
  future: boolean
  disabled?: boolean
  onMark: (mark: Mark | null) => void
}

/** Presente / Falta / Sem aula; tocar no já marcado tira a marcação. */
export function MarkButtons({ status, fromTask, future, disabled, onMark }: Props) {
  const marked = !fromTask && (status === 'PRESENT' || status === 'ABSENT' || status === 'CANCELED') ? status : null
  return (
    <div className={cn('grid gap-1.5', future ? 'grid-cols-1 sm:w-32' : 'grid-cols-3')} role="group" aria-label="Marcar o dia">
      {OPTIONS.filter((o) => !future || o.mark === 'CANCELED').map(({ mark, label, icon: Icon, on }) => {
        const active = marked === mark || (fromTask && mark === 'PRESENT')
        return (
          <Button key={mark} type="button" size="sm" variant="outline" aria-pressed={active} disabled={disabled}
            className={cn('h-8 min-w-0 px-2 text-xs', active && on)} onClick={() => onMark(marked === mark ? null : mark)}>
            <Icon /> {label}
          </Button>
        )
      })}
    </div>
  )
}
