import { ArrowRightIcon, UsersIcon } from 'lucide-react'
import { Highlight } from '@/components/Highlight'
import { Button } from '@/components/ui/button'
import { Money } from '@/features/finance/components/Money'
import { KINDS } from './kinds'
import type { SearchHit } from './types'

/** Detalhes do resultado em foco, sem sair da busca. */
export function SearchPreview({ hit, query, onOpen }: { hit: SearchHit; query: string; onOpen: () => void }) {
  const { label, icon: Icon } = KINDS[hit.kind]
  return (
    <div className="flex h-full flex-col gap-4 p-4">
      <div className="space-y-2">
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground"><Icon className="size-3.5" aria-hidden /> {label}</p>
        <h3 className="text-base leading-snug font-semibold break-words"><Highlight text={hit.title} query={query} /></h3>
        {hit.amount !== null && <Money value={hit.amount} type={hit.entryType ?? undefined} className="text-lg font-semibold" />}
        {hit.groupName && (
          <p className="flex items-center gap-1.5 text-xs text-muted-foreground"><UsersIcon className="size-3.5" aria-hidden /> {hit.groupName}</p>
        )}
      </div>
      {hit.snippet && (
        <p className="rounded-xl bg-muted/60 p-3 text-xs leading-relaxed break-words text-muted-foreground">
          <Highlight text={hit.snippet} query={query} />
        </p>
      )}
      {hit.fields.length > 0 && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-xs">
          {hit.fields.map((f) => (
            <div key={f.label} className="contents">
              <dt className="text-muted-foreground">{f.label}</dt>
              <dd className="min-w-0 break-words"><Highlight text={f.value} query={query} /></dd>
            </div>
          ))}
        </dl>
      )}
      <Button size="sm" variant="outline" className="mt-auto self-start" onClick={onOpen}>
        Abrir <ArrowRightIcon />
      </Button>
    </div>
  )
}
