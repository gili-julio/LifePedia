import { Loader2Icon, SearchIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { ApiError } from '@/lib/api'
import { lookupCep, useSaveAddress } from '../api'
import type { DeliveryAddress } from '../types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Editar; sem ele, cria */
  address?: DeliveryAddress | null
  onSaved?: (address: DeliveryAddress) => void
}

/** Endereço de entrega: CEP preenche rua, bairro, cidade e estado (ViaCEP pela API). */
export function AddressDialog({ open, onOpenChange, address, onSaved }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{address ? 'Editar endereço' : 'Novo endereço'}</DialogTitle>
          <DialogDescription>Fica salvo para usar nas próximas entregas.</DialogDescription>
        </DialogHeader>
        {open && <AddressForm address={address ?? null} onDone={(saved) => { if (saved) onSaved?.(saved); onOpenChange(false) }} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function AddressForm({ address, onDone }: { address: DeliveryAddress | null; onDone: (saved?: DeliveryAddress) => void }) {
  const save = useSaveAddress()
  const [form, setForm] = useState({
    label: address?.label ?? '',
    recipient: address?.recipient ?? '',
    cep: address ? `${address.cep.slice(0, 5)}-${address.cep.slice(5)}` : '',
    street: address?.street ?? '',
    number: address?.number ?? '',
    complement: address?.complement ?? '',
    district: address?.district ?? '',
    city: address?.city ?? '',
    state: address?.state ?? '',
    defaultAddress: address?.defaultAddress ?? false,
  })
  const [searching, setSearching] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const set = (patch: Partial<typeof form>) => setForm((f) => ({ ...f, ...patch }))

  async function searchCep(raw = form.cep) {
    const digits = raw.replace(/\D/g, '')
    if (digits.length !== 8) return
    setSearching(true)
    try {
      const found = await lookupCep(digits)
      set({ street: found.street || form.street, district: found.district || form.district, city: found.city, state: found.state })
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível buscar o CEP.')
    } finally {
      setSearching(false)
    }
  }

  function changeCep(value: string) {
    const digits = value.replace(/\D/g, '').slice(0, 8)
    const masked = digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits
    set({ cep: masked })
    if (digits.length === 8 && digits !== form.cep.replace(/\D/g, '')) void searchCep(digits)
  }

  async function submit() {
    try {
      const saved = await save.mutateAsync({
        id: address?.id,
        label: form.label.trim(),
        recipient: form.recipient.trim() || null,
        cep: form.cep,
        street: form.street.trim(),
        number: form.number.trim() || null,
        complement: form.complement.trim() || null,
        district: form.district.trim() || null,
        city: form.city.trim(),
        state: form.state.trim().toUpperCase(),
        defaultAddress: form.defaultAddress,
      })
      toast.success('Endereço salvo.')
      onDone(saved)
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors ?? {})
        toast.error(error.message)
      } else toast.error('Não foi possível salvar.')
    }
  }

  const err = (key: string) => errors[key] && <p className="text-sm text-destructive">{errors[key]}</p>
  return (
    <form className="contents" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field data-invalid={!!errors.label}>
              <FieldLabel htmlFor="addr-label">Apelido</FieldLabel>
              <Input id="addr-label" placeholder="Ex.: Casa" maxLength={40} value={form.label} className="text-base md:text-sm"
                onChange={(e) => set({ label: e.target.value })} />
              {err('label')}
            </Field>
            <Field>
              <FieldLabel htmlFor="addr-recipient">Quem recebe (opcional)</FieldLabel>
              <Input id="addr-recipient" maxLength={120} value={form.recipient} className="text-base md:text-sm"
                onChange={(e) => set({ recipient: e.target.value })} />
            </Field>
          </div>
          <Field data-invalid={!!errors.cep}>
            <FieldLabel htmlFor="addr-cep">CEP</FieldLabel>
            <div className="flex gap-2">
              <Input id="addr-cep" inputMode="numeric" autoComplete="postal-code" placeholder="00000-000" value={form.cep}
                className="text-base md:text-sm" onChange={(e) => changeCep(e.target.value)} />
              <Button type="button" variant="outline" disabled={searching || form.cep.replace(/\D/g, '').length !== 8}
                onClick={() => void searchCep()}>
                {searching ? <Loader2Icon className="animate-spin" /> : <SearchIcon />} <span className="max-sm:sr-only">Buscar</span>
              </Button>
            </div>
            {err('cep')}
          </Field>
          <Field data-invalid={!!errors.street}>
            <FieldLabel htmlFor="addr-street">Rua</FieldLabel>
            <Input id="addr-street" autoComplete="address-line1" maxLength={160} value={form.street} className="text-base md:text-sm"
              onChange={(e) => set({ street: e.target.value })} />
            {err('street')}
          </Field>
          <div className="grid grid-cols-[6rem_minmax(0,1fr)] gap-3">
            <Field>
              <FieldLabel htmlFor="addr-number">Número</FieldLabel>
              <Input id="addr-number" maxLength={20} value={form.number} className="text-base md:text-sm"
                onChange={(e) => set({ number: e.target.value })} />
            </Field>
            <Field>
              <FieldLabel htmlFor="addr-complement">Complemento</FieldLabel>
              <Input id="addr-complement" autoComplete="address-line2" maxLength={80} value={form.complement} className="text-base md:text-sm"
                onChange={(e) => set({ complement: e.target.value })} />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="addr-district">Bairro</FieldLabel>
            <Input id="addr-district" maxLength={80} value={form.district} className="text-base md:text-sm"
              onChange={(e) => set({ district: e.target.value })} />
          </Field>
          <div className="grid grid-cols-[minmax(0,1fr)_5rem] gap-3">
            <Field data-invalid={!!errors.city}>
              <FieldLabel htmlFor="addr-city">Cidade</FieldLabel>
              <Input id="addr-city" autoComplete="address-level2" maxLength={80} value={form.city} className="text-base md:text-sm"
                onChange={(e) => set({ city: e.target.value })} />
              {err('city')}
            </Field>
            <Field data-invalid={!!errors.state}>
              <FieldLabel htmlFor="addr-state">UF</FieldLabel>
              <Input id="addr-state" autoComplete="address-level1" maxLength={2} value={form.state} className="text-base uppercase md:text-sm"
                onChange={(e) => set({ state: e.target.value.replace(/[^a-zA-Z]/g, '') })} />
              {err('state')}
            </Field>
          </div>
          {!address?.defaultAddress && (
            <Field orientation="horizontal">
              <Switch id="addr-default" checked={form.defaultAddress} onCheckedChange={(on) => set({ defaultAddress: on })} />
              <FieldLabel htmlFor="addr-default" className="font-normal">Usar como padrão nas próximas entregas</FieldLabel>
            </Field>
          )}
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={() => onDone()}>Cancelar</Button>
        <Button type="submit" disabled={save.isPending}>{save.isPending && <Loader2Icon className="animate-spin" />} Salvar</Button>
      </DialogFooter>
    </form>
  )
}
