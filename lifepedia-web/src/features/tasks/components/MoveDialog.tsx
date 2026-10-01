import { useState } from 'react'
import { DatePicker } from '@/components/DatePicker'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { formatDate } from '@/lib/format'
import { addDays, dayLabel, nextMonday } from '../dates'
import type { TaskItem } from '../types'

type Props = {
  item: TaskItem | null
  today: string
  open: boolean
  onOpenChange: (open: boolean) => void
  /** `null` = sem data (só tarefa sem repetição). */
  onMove: (to: string | null) => void
}

/** Adiar/mover uma ocorrência: atalhos (hoje, amanhã, segunda) ou um dia qualquer. */
export function MoveDialog({ item, today, open, onOpenChange, onMove }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Mover para</DialogTitle>
          <DialogDescription>
            {item?.repeat ? 'Só esta ocorrência muda de dia; a repetição continua igual.' : item?.title}
          </DialogDescription>
        </DialogHeader>
        {open && item && <MoveOptions item={item} today={today} onMove={(to) => {
          onOpenChange(false)
          onMove(to)
        }} />}
      </DialogContent>
    </Dialog>
  )
}

function MoveOptions({ item, today, onMove }: { item: TaskItem; today: string; onMove: (to: string | null) => void }) {
  const [custom, setCustom] = useState(item.scheduledDate ?? '')
  const current = item.scheduledDate
  const shortcuts = [today, addDays(today, 1), nextMonday(today)]
    .filter((d, i, all) => d !== current && all.indexOf(d) === i)

  return (
    <>
      <div className="grid gap-2">
        {shortcuts.map((date) => (
          <Button key={date} variant="outline" className="justify-between" onClick={() => onMove(date)}>
            {date === today ? 'Hoje' : date === addDays(today, 1) ? 'Amanhã' : 'Próxima segunda'}
            <span className="text-xs text-muted-foreground">{formatDate(date).slice(0, 5)}</span>
          </Button>
        ))}
        {!item.repeat && current && (
          <Button variant="outline" onClick={() => onMove(null)}>Sem data</Button>
        )}
      </div>
      <div className="flex gap-2">
        <DatePicker value={custom} onChange={setCustom} className="min-w-0 flex-1" />
        <Button disabled={!custom || custom === current} onClick={() => onMove(custom)}>Mover</Button>
      </div>
      <DialogFooter className="sm:justify-start">
        <p className="text-xs text-muted-foreground">Dia atual: {current ? dayLabel(current, today) : 'sem data'}</p>
      </DialogFooter>
    </>
  )
}
