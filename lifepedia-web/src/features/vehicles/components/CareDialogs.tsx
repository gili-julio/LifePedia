import { Loader2Icon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { useCategoryLookup } from '@/features/finance/use-category-lookup'
import { ApiError } from '@/lib/api'
import { amountToInput, formatDate, parseAmount, todayIso } from '@/lib/format'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useAddReading, useCare, type CareAction } from '../api'
import { DOCUMENT_KINDS, DOCUMENT_LABEL, formatKm } from '../labels'
import type { DocumentKind, VehicleCard, VehicleDocument, VehicleItem, VehicleRevision } from '../types'
import { toFinanceLink, useFinanceState } from '../finance-link'
import { FinanceFields } from './FinanceFields'

const digits = (value: string) => value.replace(/\D/g, '')
const toInt = (value: string) => (value ? Number(value) : null)

type Base = { open: boolean; onOpenChange: (open: boolean) => void; vehicle: VehicleCard }

/** Moldura dos formulários pequenos: cabeçalho, corpo com rolagem e "Salvar". */
function FormDialog({ open, onOpenChange, title, description, children }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        {open && children}
      </DialogScrollContent>
    </Dialog>
  )
}

function Footer({ busy, disabled }: { busy: boolean; disabled?: boolean }) {
  return (
    <DialogFooter>
      <Button type="submit" disabled={busy || disabled}>{busy && <Loader2Icon className="animate-spin" />} Salvar</Button>
    </DialogFooter>
  )
}

/** Roda a ação, avisa e fecha; erro de validação volta por campo. */
function useRun(onDone: () => void) {
  const care = useCare()
  const [errors, setErrors] = useState<Record<string, string>>({})
  async function run(action: CareAction, done: string) {
    try {
      await care.mutateAsync(action)
      toast.success(done)
      onDone()
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        toast.error(error.message)
      } else toast.error('Não foi possível salvar.')
    }
  }
  return { run, busy: care.isPending, errors }
}

export function ReadingDialog({ open, onOpenChange, vehicle }: Base) {
  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Atualizar km" description={`Último: ${formatKm(vehicle.odometer)}`}>
      <ReadingForm vehicle={vehicle} onDone={() => onOpenChange(false)} />
    </FormDialog>
  )
}

function ReadingForm({ vehicle, onDone }: { vehicle: VehicleCard; onDone: () => void }) {
  const add = useAddReading()
  const [km, setKm] = useState('')
  const [date, setDate] = useState(todayIso())
  async function submit() {
    try {
      await add.mutateAsync({ id: vehicle.id, km: Number(km), date })
      toast.success('Km atualizado.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }
  return (
    <form className="contents" onSubmit={(e) => { e.preventDefault(); void submit() }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <Field>
            <FieldLabel htmlFor="rd-km">Km no painel</FieldLabel>
            <Input id="rd-km" inputMode="numeric" autoComplete="off" value={km} className="text-base md:text-sm"
              onChange={(e) => setKm(digits(e.target.value))} />
            <FieldDescription>Com leituras de tempos em tempos, o app calcula o ritmo de uso e prevê as trocas.</FieldDescription>
          </Field>
          <Field>
            <FieldLabel htmlFor="rd-date">Data</FieldLabel>
            <DatePicker id="rd-date" value={date} onChange={setDate} />
          </Field>
        </FieldGroup>
      </DialogBody>
      <Footer busy={add.isPending} disabled={!km} />
    </form>
  )
}

export function ItemDialog({ open, onOpenChange, vehicle, item, preset }: Base & {
  item?: VehicleItem | null
  preset?: { name: string; intervalKm: number | null; intervalMonths: number | null } | null
}) {
  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={item ? 'Editar item' : 'Novo item de manutenção'}
      description="Vence no km ou na data, o que vier primeiro.">
      <ItemForm vehicle={vehicle} item={item ?? null} preset={preset ?? null} onDone={() => onOpenChange(false)} />
    </FormDialog>
  )
}

function ItemForm({ vehicle, item, preset, onDone }: {
  vehicle: VehicleCard
  item: VehicleItem | null
  preset: { name: string; intervalKm: number | null; intervalMonths: number | null } | null
  onDone: () => void
}) {
  const { run, busy, errors } = useRun(onDone)
  const [name, setName] = useState(item?.name ?? preset?.name ?? '')
  const [everyKm, setEveryKm] = useState(String(item?.intervalKm ?? preset?.intervalKm ?? ''))
  const [everyMonths, setEveryMonths] = useState(String(item?.intervalMonths ?? preset?.intervalMonths ?? ''))
  const [lastKm, setLastKm] = useState(item?.lastKm != null ? String(item.lastKm) : '')
  const [lastOn, setLastOn] = useState(item?.lastOn ?? '')
  const [alert, setAlert] = useState(item?.alert ?? true)
  return (
    <form className="contents" onSubmit={(e) => {
      e.preventDefault()
      void run({ vehicleId: vehicle.id, kind: 'saveItem', itemId: item?.id, body: {
        name: name.trim(), intervalKm: toInt(everyKm), intervalMonths: toInt(everyMonths), lastKm: toInt(lastKm), lastOn: lastOn || null, alert,
      } }, 'Item salvo.')
    }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <Field data-invalid={!!errors.name}>
            <FieldLabel htmlFor="it-name">Nome</FieldLabel>
            <Input id="it-name" maxLength={60} placeholder="Ex.: Lubrificar a corrente" value={name} className="text-base md:text-sm"
              onChange={(e) => setName(e.target.value)} />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="it-km">A cada (km)</FieldLabel>
              <Input id="it-km" inputMode="numeric" value={everyKm} className="text-base md:text-sm" onChange={(e) => setEveryKm(digits(e.target.value))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="it-months">Ou a cada (meses)</FieldLabel>
              <Input id="it-months" inputMode="numeric" value={everyMonths} className="text-base md:text-sm"
                onChange={(e) => setEveryMonths(digits(e.target.value))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="it-last-km">Última vez (km)</FieldLabel>
              <Input id="it-last-km" inputMode="numeric" value={lastKm} className="text-base md:text-sm" onChange={(e) => setLastKm(digits(e.target.value))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="it-last-on">Última vez (data)</FieldLabel>
              <DatePicker id="it-last-on" value={lastOn} onChange={setLastOn} placeholder="Quando?" />
            </Field>
          </div>
          <Field orientation="horizontal">
            <Switch id="it-alert" checked={alert} onCheckedChange={setAlert} />
            <FieldLabel htmlFor="it-alert">Avisar quando chegar perto</FieldLabel>
          </Field>
        </FieldGroup>
      </DialogBody>
      <Footer busy={busy} disabled={!name.trim() || (!everyKm && !everyMonths)} />
    </form>
  )
}

type DoneTarget = { kind: 'item'; item: VehicleItem } | { kind: 'revision'; revision: VehicleRevision; items: VehicleItem[] }

/** Feito: data, km, valor (vira gasto de manutenção) e, na revisão, os itens trocados nela. */
export function DoneDialog({ open, onOpenChange, vehicle, target }: Base & { target: DoneTarget | null }) {
  const name = target?.kind === 'item' ? target.item.name : target?.revision.name ?? ''
  return (
    <FormDialog open={open && !!target} onOpenChange={onOpenChange} title={`Marcar feito: ${name}`}>
      {target && <LoadedDone vehicle={vehicle} target={target} onDone={() => onOpenChange(false)} />}
    </FormDialog>
  )
}

function LoadedDone(props: { vehicle: VehicleCard; target: DoneTarget; onDone: () => void }) {
  const { isLoading } = useCategoryLookup()
  if (isLoading) return <Skeleton className="m-4 h-56" />
  return <DoneForm {...props} />
}

function DoneForm({ vehicle, target, onDone }: { vehicle: VehicleCard; target: DoneTarget; onDone: () => void }) {
  const { run, busy, errors } = useRun(onDone)
  const [date, setDate] = useState(todayIso())
  const [km, setKm] = useState(String(vehicle.estimatedKm || ''))
  const [amount, setAmount] = useState('')
  const [shop, setShop] = useState('')
  const [notes, setNotes] = useState('')
  const [itemIds, setItemIds] = useState<string[]>([])
  const [finance, setFinance] = useFinanceState()
  const value = amount ? parseAmount(amount) : 0
  return (
    <form className="contents" onSubmit={(e) => {
      e.preventDefault()
      const body = {
        date, km: toInt(km), notes: notes.trim() || null, amount: value > 0 ? value : null, shop: shop.trim() || null,
        finance: value > 0 ? toFinanceLink(finance) : null,
      }
      void run(target.kind === 'item'
        ? { vehicleId: vehicle.id, kind: 'itemDone', itemId: target.item.id, body }
        : { vehicleId: vehicle.id, kind: 'revisionDone', revisionId: target.revision.id, body: { ...body, itemIds } }, 'Feito.')
    }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="dn-date">Data</FieldLabel>
              <DatePicker id="dn-date" value={date} onChange={setDate} />
            </Field>
            <Field data-invalid={!!errors.km}>
              <FieldLabel htmlFor="dn-km">Km</FieldLabel>
              <Input id="dn-km" inputMode="numeric" value={km} className="text-base md:text-sm" onChange={(e) => setKm(digits(e.target.value))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="dn-amount">Quanto custou</FieldLabel>
              <Input id="dn-amount" inputMode="decimal" placeholder="Opcional" value={amount} className="text-base md:text-sm"
                onChange={(e) => setAmount(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="dn-shop">Onde</FieldLabel>
              <Input id="dn-shop" maxLength={60} placeholder="Oficina, loja..." value={shop} className="text-base md:text-sm"
                onChange={(e) => setShop(e.target.value)} />
            </Field>
          </div>
          {target.kind === 'revision' && target.items.length > 0 && (
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">O que foi trocado nela</legend>
              <div className="grid gap-2 sm:grid-cols-2">
                {target.items.map((i) => (
                  <label key={i.id} className="flex items-center gap-2 text-sm">
                    <Checkbox checked={itemIds.includes(i.id)}
                      onCheckedChange={(c) => setItemIds(c ? [...itemIds, i.id] : itemIds.filter((id) => id !== i.id))} />
                    <span className="min-w-0 truncate">{i.name}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}
          <Field>
            <FieldLabel htmlFor="dn-notes">Observações</FieldLabel>
            <Textarea id="dn-notes" rows={2} maxLength={500} value={notes} className="text-base md:text-sm" onChange={(e) => setNotes(e.target.value)} />
          </Field>
          {value > 0 && <FinanceFields state={finance} onChange={setFinance} creating idPrefix="dn" />}
        </FieldGroup>
      </DialogBody>
      <Footer busy={busy} />
    </form>
  )
}

export function RevisionDialog({ open, onOpenChange, vehicle, revision }: Base & { revision?: VehicleRevision | null }) {
  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={revision ? 'Editar revisão' : 'Nova revisão'}
      description="Vale o que vier primeiro: o km ou a data.">
      <RevisionForm vehicle={vehicle} revision={revision ?? null} onDone={() => onOpenChange(false)} />
    </FormDialog>
  )
}

function RevisionForm({ vehicle, revision, onDone }: { vehicle: VehicleCard; revision: VehicleRevision | null; onDone: () => void }) {
  const { run, busy, errors } = useRun(onDone)
  const [name, setName] = useState(revision?.name ?? '')
  const [dueKm, setDueKm] = useState(revision?.dueKm != null ? String(revision.dueKm) : '')
  const [dueOn, setDueOn] = useState(revision?.dueOn ?? '')
  const [notes, setNotes] = useState(revision?.notes ?? '')
  return (
    <form className="contents" onSubmit={(e) => {
      e.preventDefault()
      void run({ vehicleId: vehicle.id, kind: 'saveRevision', revisionId: revision?.id,
        body: { name: name.trim(), dueKm: toInt(dueKm), dueOn: dueOn || null, notes: notes.trim() || null } }, 'Revisão salva.')
    }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <Field data-invalid={!!errors.name}>
            <FieldLabel htmlFor="rv-name">Nome</FieldLabel>
            <Input id="rv-name" maxLength={60} placeholder="Ex.: Revisão de 20.000 km" value={name} className="text-base md:text-sm"
              onChange={(e) => setName(e.target.value)} />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="rv-km">Com quantos km</FieldLabel>
              <Input id="rv-km" inputMode="numeric" value={dueKm} className="text-base md:text-sm" onChange={(e) => setDueKm(digits(e.target.value))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="rv-on">Até quando</FieldLabel>
              <DatePicker id="rv-on" value={dueOn} onChange={setDueOn} placeholder="Opcional" />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="rv-notes">O que inclui</FieldLabel>
            <Textarea id="rv-notes" rows={3} maxLength={2000} value={notes} className="text-base md:text-sm" onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </FieldGroup>
      </DialogBody>
      <Footer busy={busy} disabled={!name.trim() || (!dueKm && !dueOn)} />
    </form>
  )
}

export function PlanDialog({ open, onOpenChange, vehicle }: Base) {
  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Montar plano de revisões"
      description="Como no manual: a cada tantos km ou meses. As que já passaram do km atual ficam de fora.">
      <PlanForm vehicle={vehicle} onDone={() => onOpenChange(false)} />
    </FormDialog>
  )
}

function PlanForm({ vehicle, onDone }: { vehicle: VehicleCard; onDone: () => void }) {
  const { run, busy } = useRun(onDone)
  const [everyKm, setEveryKm] = useState(vehicle.type === 'MOTORCYCLE' ? '6000' : '10000')
  const [everyMonths, setEveryMonths] = useState(vehicle.type === 'MOTORCYCLE' ? '6' : '12')
  const [since, setSince] = useState('')
  const [count, setCount] = useState('5')
  return (
    <form className="contents" onSubmit={(e) => {
      e.preventDefault()
      void run({ vehicleId: vehicle.id, kind: 'plan',
        body: { everyKm: toInt(everyKm), everyMonths: toInt(everyMonths), since: since || null, count: Number(count) || 1 } }, 'Plano criado.')
    }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="pl-km">A cada (km)</FieldLabel>
              <Input id="pl-km" inputMode="numeric" value={everyKm} className="text-base md:text-sm" onChange={(e) => setEveryKm(digits(e.target.value))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="pl-months">Ou a cada (meses)</FieldLabel>
              <Input id="pl-months" inputMode="numeric" value={everyMonths} className="text-base md:text-sm"
                onChange={(e) => setEveryMonths(digits(e.target.value))} />
            </Field>
            <Field>
              <FieldLabel htmlFor="pl-since">Contar meses desde</FieldLabel>
              <DatePicker id="pl-since" value={since} onChange={setSince} placeholder="Hoje" />
              <FieldDescription>Ex.: data da compra.</FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="pl-count">Quantas</FieldLabel>
              <Input id="pl-count" inputMode="numeric" maxLength={2} value={count} className="text-base md:text-sm"
                onChange={(e) => setCount(digits(e.target.value))} />
            </Field>
          </div>
        </FieldGroup>
      </DialogBody>
      <Footer busy={busy} disabled={!everyKm && !everyMonths} />
    </form>
  )
}

export function DocumentDialog({ open, onOpenChange, vehicle, document }: Base & { document?: VehicleDocument | null }) {
  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={document ? 'Editar documento' : 'Novo documento'}
      description="Avisamos 30 dias antes e no dia do vencimento.">
      <DocumentForm vehicle={vehicle} document={document ?? null} onDone={() => onOpenChange(false)} />
    </FormDialog>
  )
}

function DocumentForm({ vehicle, document, onDone }: { vehicle: VehicleCard; document: VehicleDocument | null; onDone: () => void }) {
  const { run, busy, errors } = useRun(onDone)
  const [kind, setKind] = useState<DocumentKind>(document?.kind ?? 'IPVA')
  const [name, setName] = useState(document?.name ?? '')
  const [dueOn, setDueOn] = useState(document?.dueOn ?? '')
  const [amount, setAmount] = useState(document?.amount != null ? amountToInput(document.amount) : '')
  const [notes, setNotes] = useState(document?.notes ?? '')
  return (
    <form className="contents" onSubmit={(e) => {
      e.preventDefault()
      void run({ vehicleId: vehicle.id, kind: 'saveDocument', documentId: document?.id, body: {
        kind, name: name.trim() || DOCUMENT_LABEL[kind], dueOn, amount: amount ? parseAmount(amount) : null, notes: notes.trim() || null,
      } }, 'Documento salvo.')
    }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="dc-kind">Tipo</FieldLabel>
              <Select value={kind} onValueChange={(k) => { if (k) setKind(k as DocumentKind) }}>
                <SelectTrigger id="dc-kind" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{DOCUMENT_KINDS.map((k) => <SelectItem key={k} value={k}>{DOCUMENT_LABEL[k]}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
            <Field data-invalid={!!errors.dueOn}>
              <FieldLabel htmlFor="dc-due">Vencimento</FieldLabel>
              <DatePicker id="dc-due" value={dueOn} onChange={setDueOn} />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="dc-name">Nome</FieldLabel>
            <Input id="dc-name" maxLength={60} placeholder={kind === 'CNH' ? 'Ex.: CNH da Maria' : `Ex.: ${DOCUMENT_LABEL[kind]} 2027`} value={name}
              className="text-base md:text-sm" onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="dc-amount">Valor previsto</FieldLabel>
            <Input id="dc-amount" inputMode="decimal" placeholder="Opcional" value={amount} className="text-base md:text-sm"
              onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="dc-notes">Observações</FieldLabel>
            <Textarea id="dc-notes" rows={2} maxLength={2000} value={notes} className="text-base md:text-sm" onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </FieldGroup>
      </DialogBody>
      <Footer busy={busy} disabled={!dueOn} />
    </form>
  )
}

/** Pago/renovado: próximo vencimento (padrão: um ano depois) e o gasto, se informar o valor. */
export function RenewDialog({ open, onOpenChange, vehicle, document }: Base & { document: VehicleDocument | null }) {
  return (
    <FormDialog open={open && !!document} onOpenChange={onOpenChange} title={`Pago/renovado: ${document?.name ?? ''}`}
      description={document ? `Vencia em ${formatDate(document.dueOn)}.` : undefined}>
      {document && <LoadedRenew vehicle={vehicle} document={document} onDone={() => onOpenChange(false)} />}
    </FormDialog>
  )
}

function LoadedRenew(props: { vehicle: VehicleCard; document: VehicleDocument; onDone: () => void }) {
  const { isLoading } = useCategoryLookup()
  if (isLoading) return <Skeleton className="m-4 h-48" />
  return <RenewForm {...props} />
}

function nextYear(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(y + 1, m - 1, d)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function RenewForm({ vehicle, document, onDone }: { vehicle: VehicleCard; document: VehicleDocument; onDone: () => void }) {
  const { run, busy } = useRun(onDone)
  const [nextDue, setNextDue] = useState(nextYear(document.dueOn))
  const [paidOn, setPaidOn] = useState(todayIso())
  const [amount, setAmount] = useState(document.amount != null ? amountToInput(document.amount) : '')
  const [finance, setFinance] = useFinanceState()
  const value = amount ? parseAmount(amount) : 0
  return (
    <form className="contents" onSubmit={(e) => {
      e.preventDefault()
      void run({ vehicleId: vehicle.id, kind: 'renew', documentId: document.id, body: {
        nextDue, paidOn, amount: value > 0 ? value : null, finance: value > 0 ? toFinanceLink(finance) : null,
      } }, 'Documento renovado.')
    }}>
      <DialogBody>
        <FieldGroup className="gap-4">
          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="rn-next">Próximo vencimento</FieldLabel>
              <DatePicker id="rn-next" value={nextDue} onChange={setNextDue} />
            </Field>
            <Field>
              <FieldLabel htmlFor="rn-paid">Pago em</FieldLabel>
              <DatePicker id="rn-paid" value={paidOn} onChange={setPaidOn} />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="rn-amount">Valor pago</FieldLabel>
            <Input id="rn-amount" inputMode="decimal" placeholder="Opcional" value={amount} className="text-base md:text-sm"
              onChange={(e) => setAmount(e.target.value)} />
            <FieldDescription>Com valor, entra nos gastos do veículo.</FieldDescription>
          </Field>
          {value > 0 && <FinanceFields state={finance} onChange={setFinance} creating idPrefix="rn" />}
        </FieldGroup>
      </DialogBody>
      <Footer busy={busy} disabled={!nextDue} />
    </form>
  )
}
