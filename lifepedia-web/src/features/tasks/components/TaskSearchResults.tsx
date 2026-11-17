import { ListTodoIcon } from 'lucide-react'
import { Highlight } from '@/components/Highlight'
import { NoMatches } from '@/components/ListSearch'
import { Skeleton } from '@/components/ui/skeleton'
import { useGlobalSearch } from '@/features/search/api'

type Props = {
  query: string
  /** Nulo = tarefas pessoais. */
  groupId: string | null
  onOpen: (taskId: string) => void
  onClear: () => void
}

/** Tarefas achadas pela busca (título, lista e anotações; sem acento e com erro de digitação), em todas as datas. */
export function TaskSearchResults({ query, groupId, onOpen, onClear }: Props) {
  const search = useGlobalSearch(query, { modules: [], period: 'all', kinds: ['TASK'], groupId: groupId ?? undefined }, 20)
  if (!search.enabled) return <p className="py-10 text-center text-sm text-muted-foreground">Digite pelo menos 2 letras.</p>
  if (!search.data) return <Skeleton className="h-40" />
  const hits = (search.data.groups.find((g) => g.kind === 'TASK')?.items ?? []).filter((hit) => !!groupId || !hit.groupName)
  if (!hits.length) return <NoMatches onClear={onClear} />
  return (
    <ul className="divide-y rounded-xl border">
      {hits.map((hit) => (
        <li key={hit.id}>
          <button type="button" onClick={() => onOpen(hit.id)}
            className="flex w-full items-start gap-3 px-3 py-2.5 text-left transition-colors hover:bg-muted/50 focus-visible:bg-muted focus-visible:outline-none">
            <ListTodoIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="block truncate font-medium"><Highlight text={hit.title} query={query} /></span>
              {hit.subtitle && <span className="block truncate text-xs text-muted-foreground">{hit.subtitle}</span>}
              {hit.snippet && <span className="block truncate text-xs text-muted-foreground"><Highlight text={hit.snippet} query={query} /></span>}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}
