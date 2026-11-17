import { BadgeCheckIcon, PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useCare } from '../api'
import { DOCUMENT_LABEL, canAct, dueText } from '../labels'
import type { VehicleDetail, VehicleDocument } from '../types'
import { DocumentDialog, RenewDialog } from './CareDialogs'
import { RowMenu } from './RowMenu'
import { StageBadge } from './VehicleCard'

/** IPVA, licenciamento, seguro, CNH: vencimento, aviso e "pago/renovado". */
export function DocumentsTab({ detail }: { detail: VehicleDetail }) {
  const { formatCurrency } = useMoney()
  const vehicle = detail.vehicle
  const canLog = canAct(vehicle.role, 'LOG')
  const canEdit = canAct(vehicle.role, 'EDIT')
  const care = useCare()
  const [editing, setEditing] = useState<{ open: boolean; document: VehicleDocument | null }>({ open: false, document: null })
  const [renewing, setRenewing] = useState<VehicleDocument | null>(null)
  const [deleting, setDeleting] = useState<VehicleDocument | null>(null)

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2">
        <div className="space-y-1">
          <CardTitle className="text-base">Documentos e vencimentos</CardTitle>
          <CardDescription>Avisamos 30 dias antes e no dia. Ao pagar, marque como renovado e o próximo já fica agendado.</CardDescription>
        </div>
        {canEdit && <Button size="sm" onClick={() => setEditing({ open: true, document: null })}><PlusIcon /> Documento</Button>}
      </CardHeader>
      <CardContent>
        {!detail.documents.length ? <p className="text-sm text-muted-foreground">Nenhum documento cadastrado.</p> : (
          <ul className="divide-y">
            {detail.documents.map((d) => (
              <li key={d.id} className="flex items-center gap-3 py-2.5">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="min-w-0 truncate text-sm font-medium">{d.name}</p>
                    <StageBadge due={d.due} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {[DOCUMENT_LABEL[d.kind], `vence ${formatDate(d.dueOn)}`, d.amount != null ? formatCurrency(d.amount) : null,
                      d.due.stage !== 'OK' ? dueText(d.due) : null].filter(Boolean).join(' · ')}
                  </p>
                </div>
                {canLog && (
                  <Button size="sm" variant="outline" onClick={() => setRenewing(d)}>
                    <BadgeCheckIcon /> <span className="max-sm:sr-only">Pago</span>
                  </Button>
                )}
                {canEdit && (
                  <RowMenu label={d.name} actions={[
                    { label: 'Editar', icon: PencilIcon, onSelect: () => setEditing({ open: true, document: d }) },
                    { label: 'Excluir', icon: Trash2Icon, destructive: true, onSelect: () => setDeleting(d) },
                  ]} />
                )}
              </li>
            ))}
          </ul>
        )}
      </CardContent>

      <DocumentDialog open={editing.open} onOpenChange={(open) => setEditing((s) => ({ ...s, open }))} vehicle={vehicle} document={editing.document} />
      <RenewDialog open={!!renewing} onOpenChange={(open) => { if (!open) setRenewing(null) }} vehicle={vehicle} document={renewing} />
      <ConfirmDelete open={!!deleting} onOpenChange={(open) => { if (!open) setDeleting(null) }} title={`Excluir ${deleting?.name ?? ''}?`}
        description="Os gastos já lançados continuam."
        onConfirm={async () => {
          if (!deleting) return
          try {
            await care.mutateAsync({ vehicleId: vehicle.id, kind: 'deleteDocument', documentId: deleting.id })
            toast.success('Documento excluído.')
          } catch (error) {
            toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
          }
          setDeleting(null)
        }} />
    </Card>
  )
}
