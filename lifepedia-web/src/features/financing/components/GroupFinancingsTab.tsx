import { CalculatorIcon, FilePlus2Icon } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import { financingUrl, simulatorUrl, useFinancings } from '../api'
import { FinancingDialog } from './FinancingDialog'
import { FinancingGrid, SavedSimulations } from './FinancingLists'

/** Aba Financiamentos do grupo: dividido entre os membros; cada um leva a sua parte para as finanças. */
export function GroupFinancingsTab({ groupId }: { groupId: string }) {
  const navigate = useNavigate()
  const { data, isLoading } = useFinancings(groupId)
  const [creating, setCreating] = useState(false)
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">Financiamentos do grupo, com a parte de cada um.</p>
        <div className="flex gap-2">
          <Button asChild variant="outline" size="sm"><Link to={simulatorUrl(groupId)}><CalculatorIcon /> Simular</Link></Button>
          <Button size="sm" onClick={() => setCreating(true)}><FilePlus2Icon /> Cadastrar</Button>
        </div>
      </div>
      <FinancingGrid items={data} isLoading={isLoading} groupId={groupId} onCreate={() => setCreating(true)} />
      <SavedSimulations groupId={groupId} />
      <FinancingDialog open={creating} onOpenChange={setCreating} groupId={groupId}
        onSaved={(f) => navigate(financingUrl(f.groupId, f.id))} />
    </div>
  )
}
