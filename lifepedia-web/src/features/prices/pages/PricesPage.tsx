import { PlusIcon, ShoppingBagIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { ListSearch, NoMatches } from '@/components/ListSearch'
import { PageHeader } from '@/components/PageHeader'
import { useListSearch } from '@/components/use-list-search'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { matchesSearch } from '@/lib/search-text'
import { usePriceWatches } from '../api'
import { BookmarkletCard } from '../components/BookmarkletCard'
import { WatchCard } from '../components/WatchCard'
import { WatchDialog } from '../components/WatchDialog'
import type { WatchSummary } from '../types'

/** Lista de desejos pessoal e itens dos grupos com o preço acompanhado. */
export function PricesPage() {
  const { data, isError } = usePriceWatches()
  const [creating, setCreating] = useState(false)
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useListSearch()
  const shown = data?.filter((w) => matchesSearch(search, w.name, w.bestStore, w.groupName)) ?? []
  const personal = shown.filter((w) => !w.groupId)
  const byGroup = new Map<string, WatchSummary[]>()
  shown.filter((w) => w.groupId).forEach((w) => byGroup.set(w.groupName ?? '', [...(byGroup.get(w.groupName ?? '') ?? []), w]))

  return (
    <div className="space-y-6">
      <PageHeader title="Preços" description="Acompanhe o preço do que você quer comprar e receba aviso quando baixar." hideValues
        toolbar={data?.length ? <ListSearch value={search} onChange={setSearch} placeholder="Buscar item" /> : undefined}
        actions={[{ label: 'Acompanhar item', icon: PlusIcon, primary: true, onSelect: () => setCreating(true) }]} />

      {isError ? <p className="text-sm text-destructive">Não foi possível carregar os itens.</p>
        : !data ? <Grid>{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-24" />)}</Grid>
        : !data.length ? (
          <Card>
            <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
              <ShoppingBagIcon className="size-8 text-muted-foreground" aria-hidden />
              <p className="max-w-md text-sm text-muted-foreground">
                Diga o produto e, se quiser, o link da loja. Todo dia o LifePedia confere os preços das lojas, monta o histórico e
                avisa quando chega no seu preço.
              </p>
              <Button onClick={() => setCreating(true)}><PlusIcon /> Acompanhar item</Button>
            </CardContent>
          </Card>
        ) : !shown.length ? <NoMatches onClear={() => setSearch('')} /> : (
          <>
            {personal.length > 0 && (
              <Section title="Minha lista de desejos">
                <Grid>{personal.map((w) => <WatchCard key={w.id} watch={w} query={search} />)}</Grid>
              </Section>
            )}
            {[...byGroup.entries()].map(([name, items]) => (
              <Section key={name} title={`Grupo ${name}`}>
                <Grid>{items.map((w) => <WatchCard key={w.id} watch={w} query={search} />)}</Grid>
              </Section>
            ))}
          </>
        )}

      <BookmarkletCard />
      <WatchDialog open={creating || params.get('novo') === '1'} onOpenChange={(open) => {
        setCreating(open)
        if (!open && params.get('novo')) setParams((p) => { p.delete('novo'); return p }, { replace: true })
      }} />
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      {children}
    </section>
  )
}

function Grid({ children }: { children: ReactNode }) {
  return <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">{children}</div>
}
