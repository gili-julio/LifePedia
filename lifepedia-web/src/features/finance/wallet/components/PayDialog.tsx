import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { amountToInput, parseAmount, todayIso } from '@/lib/format'
import { usePayInvoice, useWallet } from '../api'
import { invoiceName } from '../labels'
import type { CardView, InvoiceSummary } from '../types'

const NONE = 'NONE'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  card: CardView
  invoice: InvoiceSummary
}

/** "Paguei": marca a fatura como paga e tira o valor da conta escolhida, sem virar gasto de novo. */
export function PayDialog({ open, onOpenChange, card, invoice }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Paguei a {invoiceName(invoice.month).toLowerCase()}</DialogTitle>
          <DialogDescription>
            Os gastos já estão nas compras: o pagamento só marca a fatura como paga e sai do saldo da conta.
          </DialogDescription>
        </DialogHeader>
        <PayForm card={card} invoice={invoice} onDone={() => onOpenChange(false)} />
      </DialogScrollContent>
    </Dialog>
  )
}

function PayForm({ card, invoice, onDone }: { card: CardView; invoice: InvoiceSummary; onDone: () => void }) {
  const pay = usePayInvoice()
  const accounts = useWallet().data?.accounts ?? []
  const sameBank = accounts.find((a) => a.bank.code === card.bank.code && a.bank.name === card.bank.name)
  const [amount, setAmount] = useState(amountToInput(Math.max(0, invoice.total)))
  const [paidOn, setPaidOn] = useState(todayIso())
  const [accountId, setAccountId] = useState(card.accountId ?? sameBank?.id ?? NONE)
  const invalidAmount = !(parseAmount(amount) >= 0)
  const invalidDay = paidOn > todayIso()

  async function submit() {
    if (invalidAmount || invalidDay) return
    try {
      await pay.mutateAsync({ cardId: card.id, month: invoice.month, paid: true, paidOn, amount: parseAmount(amount),
        accountId: accountId === NONE ? null : accountId })
      toast.success('Fatura marcada como paga.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível marcar.')
    }
  }

  return (
    <>
      <DialogBody>
        <FieldGroup className="gap-4">
          <div className="grid grid-cols-2 gap-4">
            <Field data-invalid={invalidAmount}>
              <FieldLabel htmlFor="pay-amount">Valor pago (R$)</FieldLabel>
              <Input id="pay-amount" inputMode="decimal" className="text-base md:text-sm" value={amount}
                aria-invalid={invalidAmount} onChange={(e) => setAmount(e.target.value)} />
              {invalidAmount && <FieldError>Valor inválido.</FieldError>}
            </Field>
            <Field data-invalid={invalidDay}>
              <FieldLabel htmlFor="pay-day">Dia</FieldLabel>
              <DatePicker id="pay-day" value={paidOn} onChange={setPaidOn} invalid={invalidDay} />
              {invalidDay && <FieldError>Não pode ser no futuro.</FieldError>}
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="pay-account">Saiu da conta</FieldLabel>
            <Select value={accountId} onValueChange={setAccountId}>
              <SelectTrigger id="pay-account" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>Nenhuma cadastrada</SelectItem>
                {accounts.map((a) => <SelectItem key={a.id} value={a.id}>{a.label}</SelectItem>)}
              </SelectContent>
            </Select>
            <FieldDescription>Desconta do saldo projetado dessa conta.</FieldDescription>
          </Field>
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button onClick={submit} disabled={pay.isPending}>
          {pay.isPending && <Loader2Icon className="animate-spin" />}
          Marcar como paga
        </Button>
      </DialogFooter>
    </>
  )
}
