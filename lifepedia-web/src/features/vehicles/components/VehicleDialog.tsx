import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { ImagePicker } from '@/components/image/ImagePicker'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { parseAmount, amountToInput } from '@/lib/format'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useSaveVehicle, useVehicleImage } from '../api'
import { FIPE_KIND, FUEL_LABEL, FUELS, TYPE_LABEL, TYPES } from '../labels'
import type { FipeRef, VehicleDetail, VehicleFuel, VehicleType } from '../types'
import { FipePicker } from './FipePicker'

const NONE = 'none'

type Props = { open: boolean; onOpenChange: (open: boolean) => void; detail?: VehicleDetail | null }

/** Cadastrar ou editar veículo: tipo, nome, FIPE, dados, foto e (no cadastro) km inicial e tabela sugerida. */
export function VehicleDialog({ open, onOpenChange, detail }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>{detail ? 'Editar veículo' : 'Novo veículo'}</DialogTitle>
          <DialogDescription>Carro, moto, bicicleta... O km e os gastos ficam no próprio veículo.</DialogDescription>
        </DialogHeader>
        {open && <VehicleForm detail={detail ?? null} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

type Photo = { kind: 'keep' } | { kind: 'new'; blob: Blob; url: string } | { kind: 'none' }

function VehicleForm({ detail, onDone }: { detail: VehicleDetail | null; onDone: () => void }) {
  const navigate = useNavigate()
  const save = useSaveVehicle()
  const image = useVehicleImage()
  const v = detail?.vehicle
  const [type, setType] = useState<VehicleType>(v?.type ?? 'CAR')
  const [name, setName] = useState(v?.name ?? '')
  const [brand, setBrand] = useState(v?.brand ?? '')
  const [model, setModel] = useState(v?.model ?? '')
  const [year, setYear] = useState(v?.modelYear ? String(v.modelYear) : '')
  const [plate, setPlate] = useState(v?.plate ?? '')
  const [color, setColor] = useState(v?.color ?? '')
  const [fuel, setFuel] = useState<VehicleFuel | null>(v?.fuel ?? null)
  const [tank, setTank] = useState(detail?.tankLiters ? amountToInput(detail.tankLiters) : '')
  const [odometer, setOdometer] = useState('')
  const [template, setTemplate] = useState(true)
  const [notes, setNotes] = useState(detail?.notes ?? '')
  const [fipe, setFipe] = useState<FipeRef | null>(detail?.fipe ?? null)
  const [photo, setPhoto] = useState<Photo>({ kind: 'keep' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const fipeKind = FIPE_KIND[type]
  const photoUrl = photo.kind === 'new' ? photo.url : photo.kind === 'none' ? null : v?.imageUrl ?? null
  const busy = save.isPending || image.isPending

  async function submit() {
    try {
      const saved = await save.mutateAsync({
        id: v?.id,
        type,
        name: name.trim(),
        brand: brand.trim() || null,
        model: model.trim() || null,
        modelYear: year ? Number(year) : null,
        plate: plate.trim() || null,
        color: color.trim() || null,
        fuel,
        tankLiters: tank ? parseAmount(tank) : null,
        odometer: v ? undefined : odometer ? Number(odometer.replace(/\D/g, '')) : null,
        notes: notes.trim() || null,
        fipe: fipeKind && fipe?.kind === fipeKind ? fipe : null,
        template: v ? undefined : template,
      })
      if (photo.kind === 'new') await image.mutateAsync({ id: saved.vehicle.id, image: photo.blob })
      if (photo.kind === 'none' && v?.imageUrl) await image.mutateAsync({ id: saved.vehicle.id, image: null })
      toast.success(v ? 'Veículo salvo.' : 'Veículo cadastrado.')
      onDone()
      if (!v) navigate(`/veiculos/${saved.vehicle.id}`)
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        toast.error(error.message)
      } else toast.error('Não foi possível salvar.')
    }
  }

  return (
    <form className="contents" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field>
              <FieldLabel htmlFor="veh-type">Tipo</FieldLabel>
              <Select value={type} onValueChange={(t) => { if (t) setType(t as VehicleType) }}>
                <SelectTrigger id="veh-type" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {TYPES.map((t) => {
                    const Icon = TYPE_LABEL[t].icon
                    return <SelectItem key={t} value={t}><Icon /> {TYPE_LABEL[t].label}</SelectItem>
                  })}
                </SelectContent>
              </Select>
            </Field>
            <Field data-invalid={!!errors.name}>
              <FieldLabel htmlFor="veh-name">Apelido</FieldLabel>
              <Input id="veh-name" placeholder="Ex.: Gol da família" maxLength={60} value={name} className="text-base md:text-sm"
                onChange={(e) => setName(e.target.value)} />
              {errors.name && <p className="text-sm text-destructive">{errors.name}</p>}
            </Field>
          </div>
          {fipeKind && (
            <FipePicker key={fipeKind} kind={fipeKind} value={fipe} onChange={(ref, names) => {
              setFipe(ref)
              if (names) {
                setBrand(names.brand)
                setModel(names.model)
                if (names.year) setYear(String(names.year))
              }
            }} />
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field>
              <FieldLabel htmlFor="veh-brand">Marca</FieldLabel>
              <Input id="veh-brand" maxLength={60} value={brand} className="text-base md:text-sm" onChange={(e) => setBrand(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="veh-model">Modelo</FieldLabel>
              <Input id="veh-model" maxLength={120} value={model} className="text-base md:text-sm" onChange={(e) => setModel(e.target.value)} />
            </Field>
            <Field data-invalid={!!errors.modelYear}>
              <FieldLabel htmlFor="veh-year">Ano</FieldLabel>
              <Input id="veh-year" inputMode="numeric" maxLength={4} value={year} className="text-base md:text-sm"
                onChange={(e) => setYear(e.target.value.replace(/\D/g, ''))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="veh-plate">Placa</FieldLabel>
              <Input id="veh-plate" maxLength={10} autoCapitalize="characters" value={plate} className="text-base uppercase md:text-sm"
                onChange={(e) => setPlate(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="veh-color">Cor</FieldLabel>
              <Input id="veh-color" maxLength={30} value={color} className="text-base md:text-sm" onChange={(e) => setColor(e.target.value)} />
            </Field>
            {type !== 'BICYCLE' && (
              <Field>
                <FieldLabel htmlFor="veh-fuel">Combustível</FieldLabel>
                <Select value={fuel ?? NONE} onValueChange={(f) => { if (f) setFuel(f === NONE ? null : f as VehicleFuel) }}>
                  <SelectTrigger id="veh-fuel" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE}>Não informar</SelectItem>
                    {FUELS.map((f) => <SelectItem key={f} value={f}>{FUEL_LABEL[f]}</SelectItem>)}
                  </SelectContent>
                </Select>
              </Field>
            )}
            {type !== 'BICYCLE' && (
              <Field data-invalid={!!errors.tankLiters}>
                <FieldLabel htmlFor="veh-tank">Tanque (litros)</FieldLabel>
                <Input id="veh-tank" inputMode="decimal" value={tank} className="text-base md:text-sm" onChange={(e) => setTank(e.target.value)} />
              </Field>
            )}
            {!v && (
              <Field data-invalid={!!errors.odometer}>
                <FieldLabel htmlFor="veh-km">Km atual</FieldLabel>
                <Input id="veh-km" inputMode="numeric" placeholder="Ex.: 45000" value={odometer} className="text-base md:text-sm"
                  onChange={(e) => setOdometer(e.target.value.replace(/\D/g, ''))} />
              </Field>
            )}
          </div>
          {!v && (
            <Field orientation="horizontal">
              <Checkbox id="veh-template" checked={template} onCheckedChange={(c) => setTemplate(c === true)} />
              <div>
                <FieldLabel htmlFor="veh-template">Usar a tabela sugerida de manutenção</FieldLabel>
                <FieldDescription>Óleo, filtros, corrente, freios... com intervalos comuns. Dá para ajustar depois pelo manual.</FieldDescription>
              </div>
            </Field>
          )}
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
            <FieldLabel htmlFor="veh-notes">Observações</FieldLabel>
            <Textarea id="veh-notes" maxLength={2000} rows={2} value={notes} className="text-base md:text-sm"
              onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="submit" disabled={busy || !name.trim()}>{busy && <Loader2Icon className="animate-spin" />} Salvar</Button>
      </DialogFooter>
    </form>
  )
}
