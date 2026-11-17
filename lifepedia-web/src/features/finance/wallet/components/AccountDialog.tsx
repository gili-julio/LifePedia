import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { BankSelect } from '@/components/bank/BankSelect'
import { DatePicker } from '@/components/DatePicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { OTHER_BANK } from '@/lib/banks'
import { amountToInput, parseAmount, todayIso } from '@/lib/format'
import { useSaveAccount, useSetBalance } from '../api'
import type { AccountView } from '../types'

/** Aceita saldo negativo ("-120,50"). */
function parseBalance(raw: string) {
  const value = raw.trim()
  return value.startsWith('-') ? -parseAmount(value.slice(1)) : parseAmount(value)
}

const schema = z.object({
  bankCode: z.string().min(1, 'Escolha o banco.'),
  bankName: z.string().max(60),
  nickname: z.string().max(40, 'Máximo de 40 caracteres.'),
  balance: z.string().refine((v) => /\d/.test(v) && Number.isFinite(parseBalance(v)), 'Informe o saldo.'),
  balanceOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe o dia.').refine((v) => v <= todayIso(), 'Não pode ser no futuro.'),
}).refine((v) => v.bankCode !== OTHER_BANK || v.bankName.trim(), { path: ['bankName'], message: 'Informe o nome do banco.' })

type FormValues = z.infer<typeof schema>

function toForm(account?: AccountView, onlyBalance?: boolean): FormValues {
  return {
    bankCode: account?.bank.code ?? '',
    bankName: account?.bank.name ?? '',
    nickname: account?.nickname ?? '',
    balance: account && !onlyBalance ? amountToInput(account.balance) : '',
    balanceOn: account && !onlyBalance ? account.balanceOn : todayIso(),
  }
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  account?: AccountView
  /** Só o saldo de hoje (atualização rápida). */
  onlyBalance?: boolean
}

/** Conta corrente: banco e o saldo num dia; o resto o app projeta pelos lançamentos. */
export function AccountDialog({ open, onOpenChange, account, onlyBalance }: Props) {
  const save = useSaveAccount()
  const setBalance = useSetBalance()
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toForm(account, onlyBalance) })
  const { errors, isSubmitting } = form.formState
  const bankCode = useWatch({ control: form.control, name: 'bankCode' })

  useEffect(() => {
    if (open) form.reset(toForm(account, onlyBalance))
  }, [open, account, onlyBalance, form])

  async function onSubmit(values: FormValues) {
    try {
      if (onlyBalance && account) {
        await setBalance.mutateAsync({ id: account.id, balance: parseBalance(values.balance), balanceOn: values.balanceOn })
      } else {
        await save.mutateAsync({
          id: account?.id,
          data: {
            bank: { code: values.bankCode, name: values.bankCode === OTHER_BANK ? values.bankName.trim() : null, kind: 'ACCOUNT' },
            nickname: values.nickname.trim() || null,
            balance: parseBalance(values.balance),
            balanceOn: values.balanceOn,
          },
        })
      }
      toast.success(onlyBalance ? 'Saldo atualizado.' : account ? 'Conta atualizada.' : 'Conta cadastrada.')
      onOpenChange(false)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{onlyBalance ? `Saldo de ${account?.label}` : account ? 'Editar conta' : 'Nova conta'}</DialogTitle>
          <DialogDescription>Informe o saldo que aparece no app do banco. Depois o LifePedia soma o que entra e sai.</DialogDescription>
        </DialogHeader>
        <DialogBody>
          <form id="account-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
            <FieldGroup className="gap-4">
              {!onlyBalance && (
                <>
                  <Field data-invalid={!!errors.bankCode}>
                    <FieldLabel htmlFor="account-bank">Banco</FieldLabel>
                    <Controller control={form.control} name="bankCode" render={({ field }) => (
                      <BankSelect id="account-bank" value={field.value} onChange={field.onChange} noneLabel="Escolha o banco" invalid={!!errors.bankCode} />
                    )} />
                    <FieldDescription>Os lançamentos com esse banco como conta mexem no saldo.</FieldDescription>
                    <FieldError errors={[errors.bankCode]} />
                  </Field>
                  {bankCode === OTHER_BANK && (
                    <Field data-invalid={!!errors.bankName}>
                      <FieldLabel htmlFor="account-bank-name">Nome do banco</FieldLabel>
                      <Input id="account-bank-name" maxLength={60} className="text-base md:text-sm" {...form.register('bankName')} />
                      <FieldError errors={[errors.bankName]} />
                    </Field>
                  )}
                  <Field data-invalid={!!errors.nickname}>
                    <FieldLabel htmlFor="account-nickname">Apelido (opcional)</FieldLabel>
                    <Input id="account-nickname" maxLength={40} className="text-base md:text-sm" {...form.register('nickname')} />
                    <FieldError errors={[errors.nickname]} />
                  </Field>
                </>
              )}
              <div className="grid grid-cols-2 gap-4">
                <Field data-invalid={!!errors.balance}>
                  <FieldLabel htmlFor="account-balance">Saldo (R$)</FieldLabel>
                  <Input id="account-balance" inputMode="decimal" placeholder="0,00" className="text-base md:text-sm"
                    aria-invalid={!!errors.balance} {...form.register('balance')} />
                  <FieldError errors={[errors.balance]} />
                </Field>
                <Field data-invalid={!!errors.balanceOn}>
                  <FieldLabel htmlFor="account-day">No dia</FieldLabel>
                  <Controller control={form.control} name="balanceOn" render={({ field }) => (
                    <DatePicker id="account-day" value={field.value} onChange={field.onChange} invalid={!!errors.balanceOn} />
                  )} />
                  <FieldError errors={[errors.balanceOn]} />
                </Field>
              </div>
            </FieldGroup>
          </form>
        </DialogBody>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
          <Button type="submit" form="account-form" disabled={isSubmitting}>
            {isSubmitting && <Loader2Icon className="animate-spin" />}
            Salvar
          </Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}
