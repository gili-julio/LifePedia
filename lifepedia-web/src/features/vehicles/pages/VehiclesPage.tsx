import { PlusIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useVehicles } from '../api'
import { VehicleCard } from '../components/VehicleCard'
import { VehicleDialog } from '../components/VehicleDialog'

/** Seus veículos e os compartilhados com você (com o nome do dono). */
export function VehiclesPage() {
  const [params, setParams] = useSearchParams()
  const [creating, setCreating] = useState(params.get('novo') === '1')
  const { data, isError } = useVehicles()
  const active = data?.owned.filter((v) => !v.archivedAt) ?? []
  const archived = data?.owned.filter((v) => v.archivedAt) ?? []

  return (
    <div className="space-y-6">
      <PageHeader title="Veículos" description="Km, abastecimentos, manutenção e documentos do carro, da moto ou da bike." hideValues
        actions={[{ label: 'Novo veículo', icon: PlusIcon, primary: true, onSelect: () => setCreating(true) }]} />

      {isError ? <p className="text-sm text-destructive">Não foi possível carregar os veículos.</p>
        : !data ? <Grid>{Array.from({ length: 2 }, (_, i) => <Skeleton key={i} className="h-56" />)}</Grid>
        : (
          <>
            {active.length ? <Grid>{active.map((v) => <VehicleCard key={v.id} vehicle={v} />)}</Grid> : (
              <Card>
                <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
                  <p className="max-w-sm text-sm text-muted-foreground">
                    Cadastre seu veículo para anotar o km, lançar abastecimentos direto no financeiro e receber aviso da troca de
                    óleo, das revisões e do IPVA.
                  </p>
                  <Button onClick={() => setCreating(true)}><PlusIcon /> Novo veículo</Button>
                </CardContent>
              </Card>
            )}
            {data.shared.length > 0 && (
              <Section title="Compartilhados com você">
                <Grid>{data.shared.map((v) => <VehicleCard key={v.id} vehicle={v} />)}</Grid>
              </Section>
            )}
            {archived.length > 0 && (
              <Section title="Arquivados (vendidos ou parados)">
                <Grid>{archived.map((v) => <VehicleCard key={v.id} vehicle={v} />)}</Grid>
              </Section>
            )}
          </>
        )}

      <VehicleDialog open={creating} onOpenChange={(open) => {
        setCreating(open)
        if (!open && params.get('novo')) setParams((p) => { p.delete('novo'); return p }, { replace: true })
      }} />
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-sm font-semibold text-muted-foreground">{title}</h2>
      {children}
    </section>
  )
}

function Grid({ children }: { children: ReactNode }) {
  return <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">{children}</div>
}
