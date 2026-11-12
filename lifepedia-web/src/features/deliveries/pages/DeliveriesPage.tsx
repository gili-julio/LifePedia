import { PackagePlusIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsTrigger } from '@/components/ui/tabs'
import { useDeliveries } from '../api'
import { AddressList } from '../components/AddressList'
import { DeliveryCard } from '../components/DeliveryCard'
import { DeliveryDialog } from '../components/DeliveryDialog'

const TABS = ['andamento', 'entregues', 'enderecos'] as const
type Tab = (typeof TABS)[number]

/** Entregas em andamento (suas e dos seus grupos), histórico das entregues e endereços salvos. */
export function DeliveriesPage() {
  const [params, setParams] = useSearchParams()
  const tab: Tab = TABS.includes(params.get('aba') as Tab) ? (params.get('aba') as Tab) : 'andamento'
  const [creating, setCreating] = useState(params.get('nova') === '1')
  const active = useDeliveries(false)
  const archived = useDeliveries(true)

  return (
    <div className="space-y-6">
      <PageHeader title="Entregas" description="Cole o código de rastreio das suas compras e acompanhe o caminho até você."
        actions={[{ label: 'Nova entrega', icon: PackagePlusIcon, primary: true, onSelect: () => setCreating(true) }]} />

      <Tabs value={tab} onValueChange={(v) => setParams((p) => { p.set('aba', v); return p }, { replace: true })}>
        <ScrollTabsList value={tab}>
          <TabsTrigger value="andamento">Em andamento{active.data ? ` (${active.data.length})` : ''}</TabsTrigger>
          <TabsTrigger value="entregues">Histórico</TabsTrigger>
          <TabsTrigger value="enderecos">Endereços</TabsTrigger>
        </ScrollTabsList>

        <TabsContent value="andamento" className="pt-2">
          {active.isError ? <p className="text-sm text-destructive">Não foi possível carregar as entregas.</p>
            : !active.data ? <Grid>{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-52" />)}</Grid>
            : !active.data.length ? (
              <Card>
                <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
                  <p className="max-w-sm text-sm text-muted-foreground">
                    Nenhuma entrega a caminho. Cole o código que a loja mandou para ver as etapas, o prazo e os avisos.
                  </p>
                  <Button onClick={() => setCreating(true)}><PackagePlusIcon /> Nova entrega</Button>
                </CardContent>
              </Card>
            ) : <Grid>{active.data.map((d) => <DeliveryCard key={d.id} delivery={d} />)}</Grid>}
        </TabsContent>

        <TabsContent value="entregues" className="space-y-3 pt-2">
          <p className="text-sm text-muted-foreground">Entregues saem de "Em andamento" uma semana depois. Você também pode arquivar à mão.</p>
          {!archived.data ? <Skeleton className="h-40" />
            : !archived.data.length ? <p className="text-sm text-muted-foreground">Nada no histórico ainda.</p>
            : <Grid>{archived.data.map((d) => <DeliveryCard key={d.id} delivery={d} />)}</Grid>}
        </TabsContent>

        <TabsContent value="enderecos" className="pt-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Endereços de entrega</CardTitle>
              <CardDescription>Salve uma vez e escolha nas próximas compras. O CEP preenche o resto.</CardDescription>
            </CardHeader>
            <CardContent><AddressList /></CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <DeliveryDialog open={creating} onOpenChange={(open) => {
        setCreating(open)
        if (!open && params.get('nova')) setParams((p) => { p.delete('nova'); return p }, { replace: true })
      }} />
    </div>
  )
}

function Grid({ children }: { children: ReactNode }) {
  return <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">{children}</div>
}
