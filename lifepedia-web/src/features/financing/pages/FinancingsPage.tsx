import { CalculatorIcon, FilePlus2Icon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { financingUrl, simulatorUrl, useFinancingOverview } from '../api'
import { FinancingDialog } from '../components/FinancingDialog'
import { FinancingGrid, SavedSimulations } from '../components/FinancingLists'

/** Financiamentos em andamento (pessoais e dos grupos) e simulações salvas. */
export function FinancingsPage() {
  const navigate = useNavigate()
  const { data, isLoading } = useFinancingOverview()
  const [creating, setCreating] = useState(false)
  return (
    <div className="space-y-6">
      <PageHeader
        title="Financiamentos"
        description="Simule antes de comprar e acompanhe os que estão em andamento."
        hideValues
        actions={[
          { label: 'Simular', icon: CalculatorIcon, primary: true, to: simulatorUrl(null) },
          { label: 'Cadastrar financiamento', icon: FilePlus2Icon, onSelect: () => setCreating(true) },
        ]}
      />
      <section aria-labelledby="financings-active" className="space-y-3">
        <h2 id="financings-active" className="text-lg font-semibold tracking-tight">Em andamento</h2>
        <FinancingGrid items={data} isLoading={isLoading} groupId={null} onCreate={() => setCreating(true)} />
      </section>
      <SavedSimulations groupId={null} />
      <FinancingDialog open={creating} onOpenChange={setCreating} groupId={null}
        onSaved={(f) => navigate(financingUrl(f.groupId, f.id))} />
    </div>
  )
}
