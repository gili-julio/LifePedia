import { MapPinIcon, PencilIcon, PlusIcon, StarIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { useAddresses, useDeleteAddress } from '../api'
import { addressLine } from '../labels'
import type { DeliveryAddress } from '../types'
import { AddressDialog } from './AddressDialog'

/** Endereços salvos: criar, editar, padrão e excluir (página de Entregas e Meu perfil). */
export function AddressList() {
  const { data, isLoading } = useAddresses()
  const remove = useDeleteAddress()
  const [editing, setEditing] = useState<DeliveryAddress | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState<DeliveryAddress | null>(null)

  async function confirmDelete(address: DeliveryAddress) {
    try {
      await remove.mutateAsync(address.id)
      toast.success('Endereço excluído.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  if (isLoading) return <Skeleton className="h-24" />
  return (
    <div className="space-y-3">
      {!data?.length ? (
        <p className="text-sm text-muted-foreground">Nenhum endereço salvo. Salve um para usar nas entregas.</p>
      ) : (
        <ul className="divide-y rounded-xl border">
          {data.map((a) => (
            <li key={a.id} className="flex items-start gap-3 p-3">
              <MapPinIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-1.5 font-medium">
                  {a.label}
                  {a.defaultAddress && <Badge variant="secondary"><StarIcon aria-hidden /> Padrão</Badge>}
                </p>
                <p className="text-sm text-muted-foreground">{addressLine(a)}</p>
                <p className="text-xs text-muted-foreground tabular-nums">CEP {a.cep.slice(0, 5)}-{a.cep.slice(5)}{a.recipient ? ` · ${a.recipient}` : ''}</p>
              </div>
              <Button size="icon" variant="ghost" aria-label={`Editar ${a.label}`} onClick={() => setEditing(a)}><PencilIcon /></Button>
              <Button size="icon" variant="ghost" aria-label={`Excluir ${a.label}`} onClick={() => setDeleting(a)}><Trash2Icon /></Button>
            </li>
          ))}
        </ul>
      )}
      <Button variant="outline" size="sm" onClick={() => setCreating(true)}><PlusIcon /> Novo endereço</Button>

      <AddressDialog open={creating || editing !== null} address={editing}
        onOpenChange={(open) => { if (!open) { setCreating(false); setEditing(null) } }} />
      <ConfirmDelete open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)} title={`Excluir ${deleting?.label ?? ''}?`}
        description="As entregas que usavam este endereço ficam sem endereço."
        onConfirm={() => { if (deleting) void confirmDelete(deleting); setDeleting(null) }} />
    </div>
  )
}
