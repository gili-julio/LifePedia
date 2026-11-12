import { useState } from 'react'
import { formatDate } from '@/lib/format'
import type { DayActivity } from '../types'

/** Tons do mais claro ao mais forte (uma cor só, a principal do tema). */
const LEVELS = ['var(--muted)', 'color-mix(in oklab, var(--primary) 30%, var(--card))', 'color-mix(in oklab, var(--primary) 55%, var(--card))',
  'color-mix(in oklab, var(--primary) 80%, var(--card))', 'var(--primary)']
const WEEKDAYS = ['S', 'T', 'Q', 'Q', 'S', 'S', 'D']

const total = (d: DayActivity) => d.commits + d.mergeRequests + d.merged + d.reviews

/** Atividade por dia em semanas (colunas) de segunda a domingo; passar o mouse ou focar mostra o dia. */
export function ActivityHeatmap({ days }: { days: DayActivity[] }) {
  const [focused, setFocused] = useState<DayActivity | null>(null)
  if (days.length === 0) return null
  const max = Math.max(1, ...days.map(total))
  const firstWeekday = (new Date(`${days[0].day}T12:00:00`).getDay() + 6) % 7
  const cells: (DayActivity | null)[] = [...Array(firstWeekday).fill(null), ...days]
  const weeks: (DayActivity | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  const sums = days.reduce((acc, d) => ({ commits: acc.commits + d.commits, mrs: acc.mrs + d.mergeRequests, merged: acc.merged + d.merged,
    reviews: acc.reviews + d.reviews }), { commits: 0, mrs: 0, merged: 0, reviews: 0 })

  return (
    <div className="space-y-3">
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[['Commits', sums.commits], ['MRs abertos', sums.mrs], ['MRs aceitos', sums.merged], ['Revisões', sums.reviews]].map(([label, value]) => (
          <div key={label} className="rounded-xl border px-3 py-2">
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="text-lg font-semibold tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="overflow-x-auto pb-1">
        <div className="flex w-max gap-[3px]">
          <div className="flex flex-col gap-[3px] pr-1 text-[10px] text-muted-foreground" aria-hidden>
            {WEEKDAYS.map((w, i) => <span key={i} className="flex h-3.5 items-center">{i % 2 === 0 ? w : ''}</span>)}
          </div>
          {weeks.map((week, w) => (
            <div key={w} className="flex flex-col gap-[3px]">
              {week.map((day, i) => day ? (
                <button key={day.day} type="button"
                  className="size-3.5 rounded-[3px] outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  style={{ background: LEVELS[level(total(day), max)] }}
                  aria-label={describe(day)} onMouseEnter={() => setFocused(day)} onFocus={() => setFocused(day)}
                  onMouseLeave={() => setFocused(null)} onBlur={() => setFocused(null)} />
              ) : <span key={`e${i}`} className="size-3.5" aria-hidden />)}
            </div>
          ))}
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
        <p aria-live="polite" className="min-h-4">{focused ? describe(focused) : 'Passe o mouse ou toque num dia para ver os números.'}</p>
        <span className="flex items-center gap-1" aria-hidden>
          Menos {LEVELS.map((color) => <span key={color} className="size-3 rounded-[3px]" style={{ background: color }} />)} Mais
        </span>
      </div>
    </div>
  )
}

function level(value: number, max: number) {
  if (value === 0) return 0
  return Math.min(4, Math.max(1, Math.ceil((value / max) * 4)))
}

function describe(day: DayActivity) {
  return `${formatDate(day.day)}: ${day.commits} commits, ${day.mergeRequests} MRs abertos, ${day.merged} aceitos, ${day.reviews} revisões`
}
