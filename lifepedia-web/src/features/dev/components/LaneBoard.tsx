import { ExternalLinkIcon, SettingsIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { itemNumber } from '../labels'
import type { DevItem, Lane } from '../types'
import { LaneBadge } from './LaneBadge'

type Props = {
  lanes: Lane[]
  /** Issues do projeto, já na ordem da API (mais parada primeiro) */
  issues: DevItem[]
  onConfigure: () => void
}

/** Quadro por raia: uma coluna por etiqueta acompanhada, com os dias de cada issue nela. */
export function LaneBoard({ lanes, issues, onConfigure }: Props) {
  if (lanes.length === 0) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-8 text-center">
          <p className="max-w-md text-sm text-muted-foreground">
            Escolha as raias que quer acompanhar (ex.: To Do, Doing, Waiting for merge request) nas regras de cada repositório. O
            quadro mostra as issues em que você participa e há quantos dias cada uma está na raia.
          </p>
          <Button onClick={onConfigure}><SettingsIcon /> Escolher raias</Button>
        </CardContent>
      </Card>
    )
  }
  const columns = lanes.map((lane) => ({
    lane,
    items: issues.filter((i) => i.lane?.toLowerCase() === lane.label.toLowerCase()),
  }))
  const rest = issues.filter((i) => !i.lane)

  return (
    <div className="-mx-1 flex snap-x gap-3 overflow-x-auto px-1 pb-2">
      {columns.map(({ lane, items }) => (
        <Column key={lane.label} title={lane.label} hint={lane.days ? `Aviso a partir de ${lane.days} dia${lane.days > 1 ? 's' : ''}` : null}
          items={items} />
      ))}
      {rest.length > 0 && <Column title="Sem raia" hint="Repositórios sem raias escolhidas" items={rest} />}
    </div>
  )
}

function Column({ title, hint, items }: { title: string; hint: string | null; items: DevItem[] }) {
  return (
    <section aria-label={title} className="flex w-72 shrink-0 snap-start flex-col gap-2 rounded-2xl bg-muted/50 p-2">
      <header className="flex items-center gap-2 px-1 pt-1">
        <h3 className="min-w-0 flex-1 truncate text-sm font-semibold" title={title}>{title}</h3>
        <Badge variant="secondary">{items.length}</Badge>
      </header>
      {hint && <p className="px-1 text-xs text-muted-foreground">{hint}</p>}
      {items.length === 0 ? <p className="px-1 py-3 text-xs text-muted-foreground">Nenhuma issue aqui.</p> : (
        <ul className="space-y-2">
          {items.map((item) => (
            <li key={item.id} className="space-y-1.5 rounded-xl bg-card p-3 shadow-xs ring-1 ring-foreground/10">
              <a href={item.webUrl} target="_blank" rel="noopener noreferrer"
                className="group flex items-start gap-1.5 text-sm font-medium underline-offset-2 hover:underline focus-visible:underline focus-visible:outline-none">
                <span className="min-w-0 break-words">
                  <span className="mr-1 text-muted-foreground tabular-nums">{itemNumber(null, 'ISSUE', item.number)}</span>{item.title}
                </span>
                <ExternalLinkIcon className="mt-0.5 size-3.5 shrink-0 opacity-50 group-hover:opacity-100" aria-label="abre em outra aba" />
              </a>
              <p className="truncate text-xs text-muted-foreground">{item.repoName}</p>
              <div className="flex flex-wrap items-center gap-1">
                <LaneBadge item={item} showLane={false} />
                {!item.mine && <Badge variant="outline">Participando</Badge>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
