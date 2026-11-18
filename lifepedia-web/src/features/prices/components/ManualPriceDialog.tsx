import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { parseAmount, todayIso } from '@/lib/format'
import { useAddWatchPrice } from '../api'

type Props = { watchId: string; open: boolean; onOpenChange: (open: boolean) => void }

/** Preço visto pela pessoa (na loja, no app, numa promoção); vale como oferta por 7 dias e entra no histórico. */
export function ManualPriceDialog({ watchId, open, onOpenChange }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        {open && <ManualForm watchId={watchId} onClose={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function ManualForm({ watchId, onClose }: { watchId: string; onClose: () => void }) {
  const add = useAddWatchPrice()
  const [store, setStore] = useState('')
  const [price, setPrice] = useState('')
  const [day, setDay] = useState(todayIso())
  const [url, setUrl] = useState('')
  const value = parseAmount(price)
  const ready = store.trim() && !Number.isNaN(value) && value > 0

  async function save() {
    try {
      await add.mutateAsync({ id: watchId, store: store.trim(), price: value, day, url: url.trim() || undefined })
      toast.success('Preço anotado.')
      onClose()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível anotar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Anotar preço</DialogTitle>
        <DialogDescription>Viu um preço numa loja ou no app? Ele entra no histórico e conta como oferta por 7 dias.</DialogDescription>
      </DialogHeader>
      <DialogBody>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="manual-store">Loja</FieldLabel>
            <Input id="manual-store" value={store} maxLength={80} placeholder="Ex.: Magazine Luiza" onChange={(e) => setStore(e.target.value)} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field>
              <FieldLabel htmlFor="manual-price">Preço à vista</FieldLabel>
              <Input id="manual-price" inputMode="decimal" value={price} placeholder="0,00" onChange={(e) => setPrice(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="manual-day">Dia</FieldLabel>
              <DatePicker id="manual-day" value={day} onChange={setDay} />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="manual-url">Link (opcional)</FieldLabel>
            <Input id="manual-url" type="url" value={url} maxLength={2048} placeholder="https://" onChange={(e) => setUrl(e.target.value)} />
          </Field>
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancelar</Button>
        <Button onClick={() => void save()} disabled={!ready || add.isPending}>
          {add.isPending && <Loader2Icon className="animate-spin" />}
          Anotar
        </Button>
      </DialogFooter>
    </>
  )
}
