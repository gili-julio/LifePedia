import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { todayIso } from '@/lib/format'
import { useAddDeliveryStep } from '../api'
import { MANUAL_STEPS } from '../labels'
import type { ManualStatus } from '../types'

type Props = {
  deliveryId: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Passo lançado à mão: vale até o rastreio trazer algo mais à frente. */
export function StepDialog({ deliveryId, open, onOpenChange }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        {open && <StepForm deliveryId={deliveryId} onClose={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function StepForm({ deliveryId, onClose }: { deliveryId: string; onClose: () => void }) {
  const add = useAddDeliveryStep()
  const [status, setStatus] = useState<ManualStatus>('IN_TRANSIT')
  const [date, setDate] = useState(todayIso())
  const [time, setTime] = useState(() => new Date().toTimeString().slice(0, 5))
  const [text, setText] = useState('')
  const [location, setLocation] = useState('')

  async function save() {
    try {
      await add.mutateAsync({
        id: deliveryId,
        status,
        at: new Date(`${date}T${time || '12:00'}`).toISOString(),
        text: text.trim() || undefined,
        location: location.trim() || undefined,
      })
      toast.success('Passo adicionado.')
      onClose()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível adicionar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Adicionar passo</DialogTitle>
        <DialogDescription>
          Anote o que você viu no app da loja ou da transportadora. Quando o rastreio trouxer algo mais à frente, ele assume.
        </DialogDescription>
      </DialogHeader>
      <DialogBody>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="step-status">Etapa</FieldLabel>
            <Select value={status} onValueChange={(v) => setStatus(v as ManualStatus)}>
              <SelectTrigger id="step-status" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {MANUAL_STEPS.map((s) => <SelectItem key={s.status} value={s.status}>{s.label}</SelectItem>)}
              </SelectContent>
            </Select>
          </Field>
          <div className="grid grid-cols-[minmax(0,1fr)_7rem] gap-3">
            <Field>
              <FieldLabel htmlFor="step-date">Dia</FieldLabel>
              <DatePicker id="step-date" value={date} onChange={setDate} />
            </Field>
            <Field>
              <FieldLabel htmlFor="step-time">Hora</FieldLabel>
              <Input id="step-time" type="time" value={time} onChange={(e) => setTime(e.target.value)} className="text-base md:text-sm" />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="step-text">Descrição (opcional)</FieldLabel>
            <Input id="step-text" value={text} maxLength={200} placeholder="Ex.: Chegou no centro de distribuição"
              onChange={(e) => setText(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="step-location">Local (opcional)</FieldLabel>
            <Input id="step-location" value={location} maxLength={120} placeholder="Ex.: Natal - RN"
              onChange={(e) => setLocation(e.target.value)} />
          </Field>
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancelar</Button>
        <Button onClick={() => void save()} disabled={!date || add.isPending}>
          {add.isPending && <Loader2Icon className="animate-spin" />}
          Adicionar
        </Button>
      </DialogFooter>
    </>
  )
}
