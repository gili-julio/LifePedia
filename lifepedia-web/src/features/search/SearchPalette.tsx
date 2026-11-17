import { ClockIcon, CornerDownLeftIcon, HistoryIcon, Loader2Icon, SearchIcon, UsersIcon, type LucideIcon } from 'lucide-react'
import { useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { Highlight } from '@/components/Highlight'
import { Dialog as DialogPrimitive } from 'radix-ui'
import { Dialog, DialogDescription, DialogOverlay, DialogPortal, DialogTitle } from '@/components/ui/dialog'
import { useDynamicLinks } from '@/components/layout/use-dynamic-links'
import { useAuth } from '@/features/auth/auth-context'
import { Money } from '@/features/finance/components/Money'
import { setHideValues, useHideValues } from '@/lib/hide-values'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { cn } from '@/lib/utils'
import { findActions, type SearchAction } from './actions'
import { useGlobalSearch } from './api'
import { KINDS } from './kinds'
import { parseQuery, periodRange } from './query'
import { readRecent, rememberItem, rememberQuery, type RecentItem } from './recent'
import { SearchFiltersBar } from './SearchFiltersBar'
import { SearchPreview } from './SearchPreview'
import type { SearchFilters, SearchHit, SearchResponse, SearchTotals } from './types'

type Entry =
  | { key: string; type: 'action'; action: SearchAction }
  | { key: string; type: 'hit'; hit: SearchHit }
  | { key: string; type: 'recent'; item: RecentItem }
  | { key: string; type: 'query'; query: string }

type Section = { key: string; title: string; icon?: LucideIcon; aside?: ReactNode; entries: Entry[] }

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialText: string
  /** Grupo da página aberta: a busca começa só nele. */
  groupId?: string
}

/** Busca geral: resultados por tipo, prévia do item em foco, recentes, ações rápidas e filtros. */
export function SearchPalette({ open, onOpenChange, initialText, groupId }: Props) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const userId = user?.id ?? ''
  const hidden = useMemo(() => new Set(user?.preferences.hiddenModules ?? []), [user])
  const dynamic = useDynamicLinks()
  const hideValues = useHideValues()
  const [text, setText] = useState(initialText)
  const [filters, setFilters] = useState<SearchFilters>({ modules: [], period: 'all', groupId })
  const [active, setActive] = useState(0)
  const listRef = useRef<HTMLDivElement>(null)
  const query = useDebouncedValue(text, 200)
  const parsed = parseQuery(query)
  const search = useGlobalSearch(query, filters, filters.kinds?.length ? 20 : 5)
  const recent = useMemo(() => readRecent(userId), [userId])

  const typed = parseQuery(text).text
  const sections = useMemo(
    () => buildSections(typed, search.enabled ? search.data : undefined, findActions(typed, hidden, dynamic), recent),
    [typed, search.enabled, search.data, hidden, dynamic, recent],
  )
  const entries = sections.flatMap((s) => s.entries)
  const current = entries[Math.min(active, entries.length - 1)]
  const totals = search.enabled ? search.data?.totals : undefined

  const close = () => onOpenChange(false)
  const go = (to: string) => {
    close()
    navigate(to)
  }
  const choose = (entry: Entry | undefined) => {
    if (!entry) return
    if (entry.type === 'query') {
      setText(entry.query)
      setActive(0)
      return
    }
    rememberQuery(userId, text)
    if (entry.type === 'hit') {
      rememberItem(userId, entry.hit)
      go(entry.hit.link)
    } else if (entry.type === 'recent') {
      rememberItem(userId, entry.item)
      go(entry.item.link)
    } else if (entry.action.run === 'toggle-values') {
      setHideValues(!hideValues)
      close()
    } else if (entry.action.to) {
      go(entry.action.to)
    }
  }
  const move = (delta: number) => {
    if (!entries.length) return
    const next = (Math.min(active, entries.length - 1) + delta + entries.length) % entries.length
    setActive(next)
    listRef.current?.querySelector(`[data-index="${next}"]`)?.scrollIntoView({ block: 'nearest' })
  }
  const changeFilters = (next: SearchFilters) => {
    setFilters(next)
    setActive(0)
  }

  const position = new Map(entries.map((entry, i) => [entry.key, i]))
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogPortal>
        <DialogOverlay />
        <DialogPrimitive.Content
          className="fixed inset-x-0 top-0 z-50 flex h-dvh flex-col overflow-hidden bg-popover text-sm text-popover-foreground ring-1 ring-foreground/5 outline-none duration-100 data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0 sm:inset-x-4 sm:top-[10vh] sm:mx-auto sm:h-[min(36rem,80vh)] sm:max-w-3xl sm:rounded-3xl">
          <DialogTitle className="sr-only">Buscar</DialogTitle>
          <DialogDescription className="sr-only">Busca em lançamentos, tarefas, notas e tudo o mais.</DialogDescription>
          <div className="flex items-center gap-2 border-b px-4">
            {search.isFetching ? <Loader2Icon className="size-4 shrink-0 animate-spin text-muted-foreground" aria-hidden />
              : <SearchIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />}
            <input
              autoFocus
              role="combobox"
              aria-expanded
              aria-controls="search-results"
              aria-activedescendant={current ? `search-${current.key}` : undefined}
              aria-label="Buscar"
              value={text}
              maxLength={100}
              onChange={(e) => {
                setText(e.target.value)
                setActive(0)
              }}
              onKeyDown={(e) => {
                if (e.key === 'ArrowDown') {
                  e.preventDefault()
                  move(1)
                } else if (e.key === 'ArrowUp') {
                  e.preventDefault()
                  move(-1)
                } else if (e.key === 'Enter') {
                  e.preventDefault()
                  choose(current)
                }
              }}
              placeholder="Buscar lançamentos, tarefas, notas, telas..."
              className="h-14 min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-muted-foreground"
            />
            <button type="button" onClick={close} className="rounded-md border px-1.5 py-0.5 text-[11px] text-muted-foreground hover:bg-muted">
              Esc
            </button>
          </div>
          <div className="border-b">
            <SearchFiltersBar filters={filters} onChange={changeFilters} parsed={parsed} hidden={hidden} routeGroupId={groupId} />
          </div>

          <div className="flex min-h-0 flex-1">
            <div ref={listRef} id="search-results" role="listbox" aria-label="Resultados" className="min-w-0 flex-1 overflow-y-auto p-2">
              {search.enabled && !search.isFetching && !search.data?.groups.length && !sections.some((s) => s.key === 'actions') && (
                <p className="px-3 py-10 text-center text-sm text-muted-foreground">Nada encontrado. Tente outra palavra ou tire um filtro.</p>
              )}
              {!search.enabled && !entries.length && (
                <p className="px-3 py-10 text-center text-sm text-muted-foreground">Digite pelo menos 2 letras ou escolha um filtro.</p>
              )}
              {sections.map((section) => (
                <div key={section.key} role="group" aria-label={section.title} className="mb-2">
                  <div className="flex items-center justify-between gap-2 px-3 pt-2 pb-1">
                    <p className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                      {section.icon && <section.icon className="size-3.5" aria-hidden />} {section.title}
                    </p>
                    {section.aside}
                  </div>
                  {section.key === 'TRANSACTION' && totals && (
                    <TotalsLine totals={totals} to={transactionsLink(parsed.text, filters, parsed)} onGo={go} />
                  )}
                  {section.entries.map((entry) => (
                    <EntryRow key={entry.key} entry={entry} index={position.get(entry.key)!} selected={current?.key === entry.key}
                      query={parsed.text} onHover={() => setActive(position.get(entry.key)!)} onChoose={() => choose(entry)} />
                  ))}
                  {section.key !== 'actions' && section.key !== 'recent' && section.key !== 'queries' && search.data?.groups
                    .find((g) => g.kind === section.key)?.more && !filters.kinds && (
                    <button type="button" className="ml-3 text-xs font-medium text-primary hover:underline"
                      onClick={() => changeFilters({ ...filters, kinds: [section.key as SearchHit['kind']] })}>
                      Ver mais em {KINDS[section.key as SearchHit['kind']].label.toLowerCase()}
                    </button>
                  )}
                </div>
              ))}
            </div>
            {current?.type === 'hit' && (
              <aside className="hidden w-72 shrink-0 border-l md:block" aria-label="Prévia">
                <SearchPreview hit={current.hit} query={parsed.text} onOpen={() => choose(current)} />
              </aside>
            )}
          </div>

          <div className="hidden items-center gap-4 border-t px-4 py-2 text-[11px] text-muted-foreground sm:flex">
            <span><Key>↑</Key> <Key>↓</Key> navegar</span>
            <span><Key><CornerDownLeftIcon className="size-3" /></Key> abrir</span>
            <span><Key>Esc</Key> fechar</span>
            <span className="ml-auto">Ctrl K ou / abrem a busca</span>
          </div>
        </DialogPrimitive.Content>
      </DialogPortal>
    </Dialog>
  )
}

function buildSections(query: string, data: SearchResponse | undefined, actions: SearchAction[],
                       recent: { queries: string[]; items: RecentItem[] }): Section[] {
  const sections: Section[] = []
  if (!data && !query.trim()) {
    if (recent.queries.length) {
      sections.push({ key: 'queries', title: 'Buscas recentes', icon: HistoryIcon,
        entries: recent.queries.map((q) => ({ key: `query:${q}`, type: 'query', query: q })) })
    }
    if (recent.items.length) {
      sections.push({ key: 'recent', title: 'Abertos recentemente', icon: ClockIcon,
        entries: recent.items.map((item) => ({ key: `recent:${item.kind}:${item.id}`, type: 'recent', item })) })
    }
  }
  if (actions.length) {
    sections.push({ key: 'actions', title: query.trim() ? 'Telas e ações' : 'Ações rápidas',
      entries: actions.map((action) => ({ key: `action:${action.id}`, type: 'action', action })) })
  }
  for (const group of data?.groups ?? []) {
    sections.push({ key: group.kind, title: KINDS[group.kind].label, icon: KINDS[group.kind].icon,
      entries: group.items.map((hit) => ({ key: `hit:${hit.kind}:${hit.id}`, type: 'hit', hit })) })
  }
  return sections
}

/** Abre a tela de lançamentos com a mesma busca (lá tem a soma e as ações em lote). */
function transactionsLink(text: string, filters: SearchFilters, parsed: { min?: number; max?: number }) {
  const params = new URLSearchParams()
  if (text) params.set('busca', text)
  const { from, to } = periodRange(filters.period)
  if (from) params.set('de', from)
  if (to) params.set('ate', to)
  const min = filters.min ?? parsed.min
  const max = filters.max ?? parsed.max
  if (min !== undefined) params.set('min', String(min))
  if (max !== undefined) params.set('max', String(max))
  if (filters.type) params.set('tipo', filters.type)
  return `/financeiro/lancamentos?${params}`
}

function TotalsLine({ totals, to, onGo }: { totals: SearchTotals; to: string; onGo: (to: string) => void }) {
  return (
    <div className="mx-3 mb-1 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-muted/60 px-2.5 py-1.5 text-xs">
      <span className="font-medium">{totals.count} {totals.count === 1 ? 'lançamento' : 'lançamentos'}</span>
      {totals.expense > 0 && <Money value={totals.expense} type="EXPENSE" />}
      {totals.income > 0 && <Money value={totals.income} type="INCOME" />}
      <Link to={to} onClick={(e) => { e.preventDefault(); onGo(to) }} className="ml-auto font-medium text-primary hover:underline">
        Ver todos e editar em lote
      </Link>
    </div>
  )
}

function EntryRow({ entry, index, selected, query, onHover, onChoose }: {
  entry: Entry
  index: number
  selected: boolean
  query: string
  onHover: () => void
  onChoose: () => void
}) {
  const { icon: Icon, title, subtitle, right } = describe(entry)
  return (
    <div
      id={`search-${entry.key}`}
      role="option"
      aria-selected={selected}
      data-index={index}
      onMouseMove={onHover}
      onClick={onChoose}
      className={cn('flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2 text-sm', selected && 'bg-muted')}
    >
      <Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium"><Highlight text={title} query={query} /></p>
        {subtitle && <p className="truncate text-xs text-muted-foreground"><Highlight text={subtitle} query={query} /></p>}
      </div>
      {right}
    </div>
  )
}

function describe(entry: Entry): { icon: LucideIcon; title: string; subtitle?: string | null; right?: ReactNode } {
  switch (entry.type) {
    case 'action':
      return { icon: entry.action.icon, title: entry.action.label, subtitle: entry.action.hint }
    case 'query':
      return { icon: HistoryIcon, title: entry.query }
    case 'recent':
      return { icon: KINDS[entry.item.kind].icon, title: entry.item.title, subtitle: entry.item.subtitle }
    case 'hit': {
      const { hit } = entry
      const subtitle = hit.snippet ?? hit.subtitle
      return {
        icon: KINDS[hit.kind].icon,
        title: hit.title,
        subtitle: hit.groupName && hit.kind !== 'GROUP' ? `${hit.groupName} · ${subtitle ?? ''}` : subtitle,
        right: (
          <span className="flex shrink-0 items-center gap-2">
            {hit.groupName && <UsersIcon className="size-3.5 text-muted-foreground" aria-label="Do grupo" />}
            {hit.amount !== null && <Money value={hit.amount} type={hit.entryType ?? undefined} className="text-xs" />}
          </span>
        ),
      }
    }
  }
}

function Key({ children }: { children: ReactNode }) {
  return <kbd className="inline-flex min-w-4 items-center justify-center rounded border px-1 font-sans">{children}</kbd>
}
