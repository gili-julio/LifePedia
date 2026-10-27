import { LockIcon } from 'lucide-react'
import { formatInstantDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import { ACHIEVEMENTS } from '../achievements'
import type { Achievement } from '../types'

/** Cartões de conquista: nível, barra até a próxima meta e o que falta. */
export function AchievementGrid({ achievements }: { achievements: Achievement[] }) {
  return (
    <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {achievements.map((a) => <AchievementCard key={a.code} achievement={a} />)}
    </ul>
  )
}

function AchievementCard({ achievement: a }: { achievement: Achievement }) {
  const info = ACHIEVEMENTS[a.code]
  const Icon = info.icon
  const unlocked = a.level > 0
  const percent = a.target ? Math.min(100, Math.round((a.value / a.target) * 100)) : 100
  return (
    <li className="flex gap-3 rounded-xl border bg-card p-3">
      <span className={cn('flex size-11 shrink-0 items-center justify-center rounded-full',
        unlocked ? 'bg-amber-500/15 text-amber-600 dark:text-amber-400' : 'bg-muted text-muted-foreground')}>
        <Icon className="size-5" aria-hidden />
      </span>
      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex items-baseline justify-between gap-2">
          <h3 className={cn('truncate text-sm font-medium', !unlocked && 'text-muted-foreground')}>{info.title}</h3>
          <span className="shrink-0 text-xs text-muted-foreground tabular-nums">Nível {a.level}/{a.maxLevel}</span>
        </div>
        <div role="progressbar" aria-label={`Progresso de ${info.title}`} aria-valuemin={0} aria-valuemax={100}
          aria-valuenow={percent} className="h-1.5 overflow-hidden rounded-full bg-muted">
          <div className={cn('h-full rounded-full transition-[width] motion-reduce:transition-none', unlocked ? 'bg-amber-500' : 'bg-muted-foreground/40')}
            style={{ width: `${percent}%` }} />
        </div>
        <p className="text-xs text-muted-foreground">
          {a.target === null ? <>Nível máximo: {info.goal(a.value)}</> : <>{a.value} de {info.goal(a.target)}</>}
        </p>
        {a.unlockedAt && (
          <p className="text-[11px] text-muted-foreground">Nível {a.level} conquistado em {formatInstantDate(a.unlockedAt)}</p>
        )}
        {a.ownerOnly && (
          <p className="flex items-center gap-1 text-[11px] text-muted-foreground"><LockIcon className="size-3" aria-hidden /> Só você vê</p>
        )}
      </div>
    </li>
  )
}
