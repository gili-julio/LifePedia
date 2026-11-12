import { Loader2Icon, PlusIcon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { ImagePicker } from '@/components/image/ImagePicker'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useGroups } from '@/features/groups/api'
import type { GroupSummary } from '@/features/groups/types'
import { ApiError } from '@/lib/api'
import { useAddresses, useDeliveryImage, useSaveDelivery } from '../api'
import type { Delivery, DeliveryAddress } from '../types'
import { AddressDialog } from './AddressDialog'

const NONE = 'none'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Editar; sem ela, cadastra */
  delivery?: Delivery | null
}

/** Cadastrar ou editar entrega: código, nome, foto, endereço e grupo. */
export function DeliveryDialog({ open, onOpenChange, delivery }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{delivery ? 'Editar entrega' : 'Nova entrega'}</DialogTitle>
          <DialogDescription>
            {delivery ? 'O código não muda; para outro código, cadastre uma entrega nova.'
              : 'Cole o código que a loja mandou (Correios, AliExpress, Shopee, Mercado Livre, Jadlog...).'}
          </DialogDescription>
        </DialogHeader>
        {open && <LoadedForm delivery={delivery ?? null} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

/** Abre o formulário só com endereços e grupos carregados (o Select não mostra valor que chega depois). */
function LoadedForm({ delivery, onDone }: { delivery: Delivery | null; onDone: () => void }) {
  const addresses = useAddresses()
  const groups = useGroups()
  if (!addresses.data || groups.isLoading) return <Skeleton className="h-72" />
  return <DeliveryForm delivery={delivery} addresses={addresses.data} groups={groups.data ?? []} onDone={onDone} />
}

type Photo = { kind: 'keep' } | { kind: 'new'; blob: Blob; url: string } | { kind: 'none' }

function DeliveryForm({ delivery, addresses, groups, onDone }: {
  delivery: Delivery | null
  addresses: DeliveryAddress[]
  groups: GroupSummary[]
  onDone: () => void
}) {
  const navigate = useNavigate()
  const save = useSaveDelivery()
  const image = useDeliveryImage()
  const [code, setCode] = useState(delivery?.trackingCode ?? '')
  const [name, setName] = useState(delivery?.name ?? '')
  const [addressId, setAddressId] = useState<string | null>(
    delivery ? delivery.address?.id ?? null : addresses.find((a) => a.defaultAddress)?.id ?? null)
  const [groupId, setGroupId] = useState<string | null>(delivery?.group?.id ?? null)
  const [photo, setPhoto] = useState<Photo>({ kind: 'keep' })
  const [newAddress, setNewAddress] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const chosenAddress = addressId
  const photoUrl = photo.kind === 'new' ? photo.url : photo.kind === 'none' ? null : delivery?.imageUrl ?? null
  const busy = save.isPending || image.isPending

  async function submit() {
    try {
      const saved = await save.mutateAsync({ id: delivery?.id, trackingCode: code, name: name.trim(), addressId: chosenAddress, groupId })
      if (photo.kind === 'new') await image.mutateAsync({ id: saved.id, image: photo.blob })
      if (photo.kind === 'none' && delivery?.imageUrl) await image.mutateAsync({ id: saved.id, image: null })
      toast.success(delivery ? 'Entrega salva.' : 'Entrega cadastrada.')
      onDone()
      if (!delivery) navigate(`/entregas/${saved.id}`)
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        toast.error(error.message)
      } else toast.error('Não foi possível salvar.')
    }
  }

  return (
    <>
    <form className="contents" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <Field data-invalid={!!errors.trackingCode}>
            <FieldLabel htmlFor="dlv-code">Código de rastreio</FieldLabel>
            <Input id="dlv-code" autoComplete="off" autoCapitalize="characters" spellCheck={false} placeholder="Ex.: AA123456789BR"
              maxLength={40} disabled={!!delivery} value={code} className="font-mono text-base uppercase md:text-sm"
              onChange={(e) => setCode(e.target.value)} />
            {errors.trackingCode && <p className="text-sm text-destructive">{errors.trackingCode}</p>}
          </Field>
          <Field data-invalid={!!errors.name}>
            <FieldLabel htmlFor="dlv-name">Nome do item</FieldLabel>
            <Input id="dlv-name" placeholder="Ex.: Fone de ouvido" maxLength={80} value={name} className="text-base md:text-sm"
              onChange={(e) => setName(e.target.value)} />
            {errors.name && <p className="text-sm text-destructive">{errors.name}</p>}
          </Field>
          <Field>
            <FieldLabel>Foto (opcional)</FieldLabel>
            <ImagePicker shape="rect"
              preview={photoUrl
                ? <img src={photoUrl} alt="" className="size-20 rounded-xl object-cover" />
                : <span className="flex size-20 items-center justify-center rounded-xl bg-muted text-xs text-muted-foreground">Sem foto</span>}
              onChange={(blob) => setPhoto({ kind: 'new', blob, url: URL.createObjectURL(blob) })}
              onRemove={photoUrl ? () => setPhoto({ kind: 'none' }) : undefined} />
          </Field>
          <Field>
            <FieldLabel htmlFor="dlv-address">Endereço de entrega</FieldLabel>
            <div className="flex gap-2">
              <Select key={addresses.length} value={chosenAddress ?? NONE}
                onValueChange={(v) => { if (v) setAddressId(v === NONE ? null : v) }}>
                <SelectTrigger id="dlv-address" className="min-w-0 flex-1"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Sem endereço</SelectItem>
                  {addresses.map((a) => <SelectItem key={a.id} value={a.id}>{a.label} ({a.city}/{a.state})</SelectItem>)}
                </SelectContent>
              </Select>
              <Button type="button" variant="outline" onClick={() => setNewAddress(true)}>
                <PlusIcon /> <span className="max-sm:sr-only">Novo</span>
              </Button>
            </div>
            <FieldDescription>Só você vê o endereço completo; no grupo aparece o apelido e a cidade.</FieldDescription>
          </Field>
          {groups.some((g) => g.role) && (
            <Field>
              <FieldLabel htmlFor="dlv-group">Compartilhar com um grupo</FieldLabel>
              <Select value={groupId ?? NONE} onValueChange={(v) => setGroupId(v === NONE ? null : v)}>
                <SelectTrigger id="dlv-group"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Só eu</SelectItem>
                  {groups.filter((g) => g.role).map((g) => <SelectItem key={g.id} value={g.id}>{g.name}</SelectItem>)}
                </SelectContent>
              </Select>
              <FieldDescription>Quem está no grupo vê o andamento e recebe os avisos (ex.: quem mora junto).</FieldDescription>
            </Field>
          )}
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" disabled={busy || !code.trim() || !name.trim()}>
          {busy && <Loader2Icon className="animate-spin" />} {delivery ? 'Salvar' : 'Acompanhar'}
        </Button>
      </DialogFooter>
    </form>
    <AddressDialog open={newAddress} onOpenChange={setNewAddress} onSaved={(a) => setAddressId(a.id)} />
    </>
  )
}
