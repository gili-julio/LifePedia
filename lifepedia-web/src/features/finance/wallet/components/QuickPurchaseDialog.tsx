import { zodResolver } from '@hookform/resolvers/zod'
import { ClipboardPasteIcon, Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { BankLogo } from '@/components/bank/BankLogo'
import { ApiError } from '@/lib/api'
import { amountToInput, formatDate, formatMonth, parseAmount, todayIso } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { CategorySelect } from '../../components/CategorySelect'
import { usePurchase, useWallet } from '../api'
import { dueOn, invoiceOf } from '../cycle'
import { parsePurchase } from '../notification'
import type { Wallet } from '../types'

const schema = z.object({
  target: z.string().min(1, 'Escolha o cartão ou a conta.'),
  description: z.string().trim().min(1, 'Informe a descrição.').max(100, 'Máximo de 100 caracteres.'),
  amount: z.string().refine((v) => parseAmount(v) > 0, 'Informe um valor maior que zero.'),
  installments: z.string().refine((v) => /^\d{1,2}$/.test(v) && +v >= 1 && +v <= 48, 'De 1 a 48.'),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe a data.'),
  categoryId: z.string(),
})

type FormValues = z.infer<typeof schema>

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Cartão já escolhido (tela do cartão). */
  cardId?: string
  /** Abre na aba de colar o aviso do banco. */
  paste?: boolean
}

/** Lançamento rápido: valor, descrição e onde; a fatura, as parcelas e a categoria saem sozinhas. */
export function QuickPurchaseDialog({ open, onOpenChange, cardId, paste }: Props) {
  const wallet = useWallet().data
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Lançamento rápido</DialogTitle>
          <DialogDescription>No cartão, a compra cai na fatura certa e as parcelas vão para as próximas.</DialogDescription>
        </DialogHeader>
        {wallet ? <PurchaseForm wallet={wallet} cardId={cardId} paste={paste} onDone={() => onOpenChange(false)} />
          : <DialogBody><Loader2Icon className="mx-auto animate-spin" /></DialogBody>}
      </DialogScrollContent>
    </Dialog>
  )
}

function PurchaseForm({ wallet, cardId, paste, onDone }: { wallet: Wallet; cardId?: string; paste?: boolean; onDone: () => void }) {
  const purchase = usePurchase()
  const { formatCurrency } = useMoney()
  const [text, setText] = useState('')
  const [showPaste, setShowPaste] = useState(!!paste)
  const first = cardId ? `card:${cardId}` : wallet.cards[0] ? `card:${wallet.cards[0].id}` : wallet.accounts[0] ? `account:${wallet.accounts[0].id}` : ''
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { target: first, description: '', amount: '', installments: '1', date: todayIso(), categoryId: '' },
  })
  const { errors, isSubmitting } = form.formState
  const [target, amount, installments, date] = useWatch({ control: form.control, name: ['target', 'amount', 'installments', 'date'] })
  const card = target.startsWith('card:') ? wallet.cards.find((c) => `card:${c.id}` === target) : undefined
  const count = Number(installments) || 1
  const invoice = card && /^\d{4}-\d{2}-\d{2}$/.test(date) ? invoiceOf(card, date) : null
  const total = parseAmount(amount)

  function readNotice() {
    const parsed = parsePurchase(text)
    if (!parsed.amount && !parsed.description) {
      toast.error('Não achei valor nem estabelecimento no texto. Preencha à mão.')
      return
    }
    if (parsed.amount) form.setValue('amount', amountToInput(parsed.amount))
    if (parsed.description) form.setValue('description', parsed.description)
    if (parsed.date) form.setValue('date', parsed.date)
    form.setValue('installments', String(parsed.installments))
    const match = wallet.cards.find((c) => parsed.lastDigits && c.lastDigits === parsed.lastDigits)
      ?? wallet.cards.find((c) => parsed.bankCode && c.bank.code === parsed.bankCode)
    if (match) form.setValue('target', `card:${match.id}`)
    setShowPaste(false)
    toast.success('Aviso lido. Confira os campos antes de salvar.')
  }

  async function onSubmit(values: FormValues) {
    const [kind, id] = values.target.split(':')
    try {
      const result = await purchase.mutateAsync({
        ...(kind === 'card' ? { cardId: id } : { accountId: id }),
        description: values.description.trim(),
        amount: parseAmount(values.amount),
        date: values.date,
        installments: kind === 'card' ? Number(values.installments) : 1,
        ...(values.categoryId && { categoryId: values.categoryId }),
      })
      toast.success(result.created > 1
        ? `${result.created} parcelas lançadas a partir de ${formatMonth(result.firstMonth).toLowerCase()}.`
        : `Lançado em ${formatMonth(result.firstMonth).toLowerCase()}.`)
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível lançar.')
    }
  }

  if (!wallet.cards.length && !wallet.accounts.length) {
    return <DialogBody><p className="text-sm text-muted-foreground">Cadastre um cartão ou uma conta na Carteira para usar o lançamento rápido.</p></DialogBody>
  }

  return (
    <>
      <DialogBody>
        <form id="purchase-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup className="gap-4">
            {showPaste ? (
              <Field>
                <FieldLabel htmlFor="purchase-paste">Aviso de compra do banco</FieldLabel>
                <Textarea id="purchase-paste" rows={4} className="text-base md:text-sm" value={text} onChange={(e) => setText(e.target.value)}
                  placeholder="Ex.: Compra de R$ 45,90 aprovada em PADARIA para o cartão final 1234." />
                <FieldDescription>O texto fica só no seu aparelho: o LifePedia lê aqui e preenche os campos.</FieldDescription>
                <div className="flex gap-2">
                  <Button type="button" size="sm" onClick={readNotice} disabled={!text.trim()}>Ler aviso</Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => setShowPaste(false)}>Digitar</Button>
                </div>
              </Field>
            ) : (
              <Button type="button" variant="outline" size="sm" className="self-start" onClick={() => setShowPaste(true)}>
                <ClipboardPasteIcon /> Colar aviso do banco
              </Button>
            )}
            <Field data-invalid={!!errors.target}>
              <FieldLabel htmlFor="purchase-target">Onde</FieldLabel>
              <Controller control={form.control} name="target" render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="purchase-target" className="w-full" aria-invalid={!!errors.target}><SelectValue placeholder="Escolha" /></SelectTrigger>
                  <SelectContent>
                    {wallet.cards.map((c) => (
                      <SelectItem key={c.id} value={`card:${c.id}`}>
                        <BankLogo code={c.bank.code} name={c.bank.name} size="xs" /> {c.label} (cartão{c.lastDigits ? ` ${c.lastDigits}` : ''})
                      </SelectItem>
                    ))}
                    {wallet.accounts.map((a) => (
                      <SelectItem key={a.id} value={`account:${a.id}`}>
                        <BankLogo code={a.bank.code} name={a.bank.name} size="xs" /> {a.label} (conta, débito ou Pix)
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )} />
              {invoice && card && (
                <FieldDescription>
                  Vai para a fatura de {formatMonth(invoice).toLowerCase()}, que vence {formatDate(dueOn(card, invoice))}.
                </FieldDescription>
              )}
              <FieldError errors={[errors.target]} />
            </Field>
            <Field data-invalid={!!errors.description}>
              <FieldLabel htmlFor="purchase-description">Descrição</FieldLabel>
              <Input id="purchase-description" placeholder="Ex.: Padaria" maxLength={100} className="text-base md:text-sm"
                aria-invalid={!!errors.description} {...form.register('description')} />
              <FieldError errors={[errors.description]} />
            </Field>
            <div className="grid grid-cols-2 gap-4">
              <Field data-invalid={!!errors.amount}>
                <FieldLabel htmlFor="purchase-amount">Valor total (R$)</FieldLabel>
                <Input id="purchase-amount" inputMode="decimal" placeholder="0,00" className="text-base md:text-sm"
                  aria-invalid={!!errors.amount} {...form.register('amount')} />
                <FieldError errors={[errors.amount]} />
              </Field>
              {card ? (
                <Field data-invalid={!!errors.installments}>
                  <FieldLabel htmlFor="purchase-installments">Parcelas</FieldLabel>
                  <Input id="purchase-installments" inputMode="numeric" maxLength={2} className="text-base md:text-sm"
                    aria-invalid={!!errors.installments} {...form.register('installments')} />
                  {count > 1 && total > 0 && <FieldDescription>{count}x de {formatCurrency(total / count)}</FieldDescription>}
                  <FieldError errors={[errors.installments]} />
                </Field>
              ) : <div />}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={!!errors.date}>
                <FieldLabel htmlFor="purchase-date">Data da compra</FieldLabel>
                <Controller control={form.control} name="date" render={({ field }) => (
                  <DatePicker id="purchase-date" value={field.value} onChange={field.onChange} invalid={!!errors.date} />
                )} />
                <FieldError errors={[errors.date]} />
              </Field>
              <Field>
                <FieldLabel htmlFor="purchase-category">Categoria</FieldLabel>
                <Controller control={form.control} name="categoryId" render={({ field }) => (
                  <CategorySelect id="purchase-category" type="EXPENSE" value={field.value} onChange={field.onChange} />
                )} />
                <FieldDescription>Sem escolher, usa a do último lançamento com esse nome (ou Outros).</FieldDescription>
              </Field>
            </div>
          </FieldGroup>
        </form>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" form="purchase-form" disabled={isSubmitting}>
          {isSubmitting && <Loader2Icon className="animate-spin" />}
          Lançar
        </Button>
      </DialogFooter>
    </>
  )
}
