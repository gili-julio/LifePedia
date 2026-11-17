import { Loader2Icon, PaperclipIcon, Trash2Icon } from 'lucide-react'
import { useRef, useState } from 'react'
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
import { Textarea } from '@/components/ui/textarea'
import { useCategoryLookup } from '@/features/finance/use-category-lookup'
import { ApiError } from '@/lib/api'
import { amountToInput, parseAmount, todayIso } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { shrinkPhoto } from '@/lib/images'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useExpenseImage, useSaveExpense } from '../api'
import { EXPENSE_KINDS, EXPENSE_LABEL, FUEL_LABEL, FUELS, formatNumber } from '../labels'
import type { Expense, ExpenseKind, VehicleCard, VehicleFuel } from '../types'
import { toFinanceLink, useFinanceState } from '../finance-link'
import { FinanceFields } from './FinanceFields'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  vehicle: VehicleCard
  /** Editar; sem ele, lança */
  expense?: Expense | null
  /** Tipo já escolhido (ex.: "Abastecer") */
  kind?: ExpenseKind
}

/** Abastecimento ou outro gasto, com km, comprovante e o lançamento no financeiro de quem lança. */
export function ExpenseDialog({ open, onOpenChange, vehicle, expense, kind }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>{expense ? 'Editar gasto' : kind === 'FUEL' ? 'Abastecer' : 'Lançar gasto'}</DialogTitle>
          <DialogDescription>{vehicle.name}</DialogDescription>
        </DialogHeader>
        {open && <Loaded vehicle={vehicle} expense={expense ?? null} kind={kind} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function Loaded(props: { vehicle: VehicleCard; expense: Expense | null; kind?: ExpenseKind; onDone: () => void }) {
  const { isLoading } = useCategoryLookup()
  if (isLoading) return <Skeleton className="m-4 h-72" />
  return <ExpenseForm {...props} />
}

type Receipt = { kind: 'keep' } | { kind: 'new'; blob: Blob; url: string } | { kind: 'none' }

function ExpenseForm({ vehicle, expense, kind: initialKind, onDone }: { vehicle: VehicleCard; expense: Expense | null; kind?: ExpenseKind; onDone: () => void }) {
  const { formatCurrency } = useMoney()
  const save = useSaveExpense()
  const image = useExpenseImage()
  const file = useRef<HTMLInputElement>(null)
  const [kind, setKind] = useState<ExpenseKind>(expense?.kind ?? initialKind ?? 'FUEL')
  const [date, setDate] = useState(expense?.date ?? todayIso())
  const [amount, setAmount] = useState(expense ? amountToInput(expense.amount) : '')
  const [odometer, setOdometer] = useState(expense?.odometer != null ? String(expense.odometer) : '')
  const [liters, setLiters] = useState(expense?.liters != null ? amountToInput(expense.liters) : '')
  const [fuel, setFuel] = useState<VehicleFuel | null>(expense?.fuel ?? (vehicle.fuel === 'FLEX' ? null : vehicle.fuel))
  const [fullTank, setFullTank] = useState(expense ? expense.fullTank : true)
  const [station, setStation] = useState(expense?.station ?? '')
  const [description, setDescription] = useState(expense?.description ?? '')
  const [notes, setNotes] = useState(expense?.notes ?? '')
  const [receipt, setReceipt] = useState<Receipt>({ kind: 'keep' })
  const [finance, setFinance] = useFinanceState(expense?.finance, !!expense)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const isFuel = kind === 'FUEL'
  const value = amount ? parseAmount(amount) : 0
  const litersValue = liters ? parseAmount(liters) : 0
  const receiptUrl = receipt.kind === 'new' ? receipt.url : receipt.kind === 'none' ? null : expense?.imageUrl ?? null
  const busy = save.isPending || image.isPending

  async function pickReceipt(selected: File | undefined) {
    if (file.current) file.current.value = ''
    if (!selected) return
    try {
      const blob = await shrinkPhoto(selected, 1600, 1_000_000)
      setReceipt({ kind: 'new', blob, url: URL.createObjectURL(blob) })
    } catch {
      toast.error('Não foi possível ler a imagem. Use JPEG, PNG ou WebP.')
    }
  }

  async function submit() {
    try {
      const saved = await save.mutateAsync({
        id: vehicle.id,
        expenseId: expense?.id,
        kind,
        date,
        amount: value,
        description: description.trim() || null,
        odometer: odometer ? Number(odometer) : null,
        liters: isFuel && litersValue ? litersValue : null,
        fuel: isFuel ? fuel : null,
        fullTank: isFuel && fullTank,
        station: isFuel ? station.trim() || null : null,
        notes: notes.trim() || null,
        finance: toFinanceLink(finance),
      })
      if (receipt.kind === 'new') await image.mutateAsync({ id: vehicle.id, expenseId: saved.id, image: receipt.blob })
      if (receipt.kind === 'none' && expense?.imageUrl) await image.mutateAsync({ id: vehicle.id, expenseId: saved.id, image: null })
      toast.success(expense ? 'Gasto salvo.' : isFuel ? 'Abastecimento lançado.' : 'Gasto lançado.')
      onDone()
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
              <FieldLabel htmlFor="exp-kind">Tipo</FieldLabel>
              <Select value={kind} onValueChange={(k) => { if (k) setKind(k as ExpenseKind) }}>
                <SelectTrigger id="exp-kind" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{EXPENSE_KINDS.map((k) => <SelectItem key={k} value={k}>{EXPENSE_LABEL[k]}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
            <Field data-invalid={!!errors.date}>
              <FieldLabel htmlFor="exp-date">Data</FieldLabel>
              <DatePicker id="exp-date" value={date} onChange={setDate} />
            </Field>
            <Field data-invalid={!!errors.amount}>
              <FieldLabel htmlFor="exp-amount">Valor pago</FieldLabel>
              <Input id="exp-amount" inputMode="decimal" placeholder="0,00" value={amount} className="text-base md:text-sm"
                onChange={(e) => setAmount(e.target.value)} />
              {errors.amount && <p className="text-sm text-destructive">{errors.amount}</p>}
            </Field>
            <Field data-invalid={!!errors.odometer}>
              <FieldLabel htmlFor="exp-km">Km no painel</FieldLabel>
              <Input id="exp-km" inputMode="numeric" placeholder={`Último: ${vehicle.odometer}`} value={odometer}
                className="text-base md:text-sm" onChange={(e) => setOdometer(e.target.value.replace(/\D/g, ''))} />
            </Field>
            {isFuel && (
              <>
                <Field data-invalid={!!errors.liters}>
                  <FieldLabel htmlFor="exp-liters">Litros</FieldLabel>
                  <Input id="exp-liters" inputMode="decimal" placeholder="0,00" value={liters} className="text-base md:text-sm"
                    onChange={(e) => setLiters(e.target.value)} />
                  {value > 0 && litersValue > 0 && (
                    <FieldDescription>{formatCurrency(value / litersValue)} o litro ({formatNumber(litersValue)} L)</FieldDescription>
                  )}
                </Field>
                <Field>
                  <FieldLabel htmlFor="exp-fuel">Combustível</FieldLabel>
                  <Select value={fuel ?? ''} onValueChange={(f) => { if (f) setFuel(f as VehicleFuel) }}>
                    <SelectTrigger id="exp-fuel" className="w-full"><SelectValue placeholder="Qual foi?" /></SelectTrigger>
                    <SelectContent>{FUELS.filter((f) => f !== 'FLEX' && f !== 'HYBRID').map((f) => <SelectItem key={f} value={f}>{FUEL_LABEL[f]}</SelectItem>)}</SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel htmlFor="exp-station">Posto</FieldLabel>
                  <Input id="exp-station" maxLength={60} value={station} className="text-base md:text-sm" onChange={(e) => setStation(e.target.value)} />
                </Field>
                <Field orientation="horizontal" className="self-end">
                  <Checkbox id="exp-full" checked={fullTank} onCheckedChange={(c) => setFullTank(c === true)} />
                  <FieldLabel htmlFor="exp-full">Completei o tanque</FieldLabel>
                </Field>
              </>
            )}
          </div>
          {isFuel && (
            <p className="text-xs text-muted-foreground">
              O consumo (km/l) sai de dois tanques cheios seguidos com o km anotado; abastecimentos no meio entram na conta.
            </p>
          )}
          <Field>
            <FieldLabel htmlFor="exp-desc">Descrição (opcional)</FieldLabel>
            <Input id="exp-desc" maxLength={120} placeholder={EXPENSE_LABEL[kind]} value={description} className="text-base md:text-sm"
              onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="exp-notes">Observações</FieldLabel>
            <Textarea id="exp-notes" rows={2} maxLength={2000} value={notes} className="text-base md:text-sm" onChange={(e) => setNotes(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel>Comprovante (opcional)</FieldLabel>
            <div className="flex items-center gap-3">
              {receiptUrl
                ? <img src={receiptUrl} alt="Comprovante" className="size-16 rounded-xl object-cover" />
                : <span className="flex size-16 items-center justify-center rounded-xl bg-muted text-xs text-muted-foreground">Sem foto</span>}
              <Button type="button" variant="outline" size="sm" onClick={() => file.current?.click()}><PaperclipIcon /> Escolher</Button>
              {receiptUrl && (
                <Button type="button" variant="ghost" size="sm" onClick={() => setReceipt({ kind: 'none' })}><Trash2Icon /> Tirar</Button>
              )}
              <input ref={file} type="file" accept="image/jpeg,image/png,image/webp" className="hidden"
                onChange={(e) => void pickReceipt(e.target.files?.[0])} />
            </div>
          </Field>
          <FinanceFields state={finance} onChange={setFinance} creating={!expense} idPrefix="exp" />
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="submit" disabled={busy || value <= 0}>{busy && <Loader2Icon className="animate-spin" />} Salvar</Button>
      </DialogFooter>
    </form>
  )
}
