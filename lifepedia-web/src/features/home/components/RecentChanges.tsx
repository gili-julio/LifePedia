import { ChevronDownIcon } from 'lucide-react'
import { useId, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { type Change, CHANGES } from '../changelog'

const NEW_DAYS = 7
const dayLabel = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long' })
const dayLabelWithYear = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' })

/** Linha do tempo das mudanças, agrupadas por dia; `limit` mostra só as mais recentes. */
export function RecentChanges({ limit }: { limit?: number }) {
  const today = todayIso()
  const shown = limit ? CHANGES.slice(0, limit) : CHANGES

  return (
    <ol className="space-y-6">
      {byDay(shown).map(({ date, changes }) => (
        <li key={date} className="relative pl-6 before:absolute before:top-2 before:bottom-[-1.5rem] before:left-[5px] before:w-px before:bg-border last:before:hidden">
          <span aria-hidden className="absolute top-1.5 left-0 size-[11px] rounded-full border-2 border-background bg-foreground ring-1 ring-border" />
          <h3 className="text-sm font-medium text-muted-foreground">{label(date, today)}</h3>
          <ul className="mt-2 space-y-2">
            {changes.map((change) => (
              <li key={change.id}><ChangeCard change={change} isNew={daysBetween(change.date, today) < NEW_DAYS} /></li>
            ))}
          </ul>
        </li>
      ))}
    </ol>
  )
}

function ChangeCard({ change, isNew }: { change: Change; isNew: boolean }) {
  const [open, setOpen] = useState(false)
  const detailsId = useId()

  return (
    <div className="rounded-xl border bg-card">
      <button type="button" aria-expanded={open} aria-controls={detailsId} onClick={() => setOpen((o) => !o)}
        className="flex w-full items-start gap-3 rounded-xl p-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring">
        <span className="min-w-0 flex-1 space-y-2">
          <span className="flex flex-wrap items-center gap-2">
            <span className="font-medium leading-snug">{change.title}</span>
            {isNew && <Badge className="bg-data/15 text-data">Novo</Badge>}
          </span>
          <span className="flex flex-wrap gap-1.5">
            {change.modules.map((module) => <Badge key={module} variant="secondary">{module}</Badge>)}
          </span>
        </span>
        <ChevronDownIcon aria-hidden className={cn('mt-0.5 size-4 shrink-0 text-muted-foreground transition-transform motion-reduce:transition-none', open && 'rotate-180')} />
      </button>
      {open && (
        <ul id={detailsId} className="mx-4 list-disc space-y-1.5 border-t py-3 pl-5 text-sm leading-relaxed text-muted-foreground">
          {change.details.map((detail) => <li key={detail}>{detail}</li>)}
        </ul>
      )}
    </div>
  )
}

/** Junta as seguidas do mesmo dia (a lista já vem da mais nova para a mais antiga). */
function byDay(changes: Change[]) {
  const days: { date: string; changes: Change[] }[] = []
  for (const change of changes) {
    const last = days.at(-1)
    if (last?.date === change.date) last.changes.push(change)
    else days.push({ date: change.date, changes: [change] })
  }
  return days
}

function label(date: string, today: string) {
  const day = new Date(`${date}T12:00:00`)
  if (date === today) return `Hoje, ${dayLabel.format(day)}`
  return (date.slice(0, 4) === today.slice(0, 4) ? dayLabel : dayLabelWithYear).format(day)
}

function daysBetween(from: string, to: string) {
  return Math.round((Date.parse(`${to}T12:00:00Z`) - Date.parse(`${from}T12:00:00Z`)) / 86_400_000)
}
