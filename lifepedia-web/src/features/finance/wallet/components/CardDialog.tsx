import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { BankSelect } from '@/components/bank/BankSelect'
import { ColorPicker } from '@/components/ColorPicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useGroups } from '@/features/groups/api'
import { ApiError } from '@/lib/api'
import { OTHER_BANK } from '@/lib/banks'
import { amountToInput, parseAmount } from '@/lib/format'
import { useSaveCard, useWallet } from '../api'
import { DEFAULT_CARD_COLOR } from '../labels'
import type { CardView } from '../types'

const SAME_BANK = 'SAME'
const dayField = z.string().refine((v) => /^\d{1,2}$/.test(v) && +v >= 1 && +v <= 31, 'Dia de 1 a 31.')
const optionalAmount = z.string().refine((v) => !v.trim() || parseAmount(v) > 0, 'Valor inválido.')

const schema = z.object({
  bankCode: z.string().min(1, 'Escolha o banco.'),
  bankName: z.string().max(60),
  nickname: z.string().max(40, 'Máximo de 40 caracteres.'),
  lastDigits: z.string().refine((v) => !v || /^\d{4}$/.test(v), 'Informe os 4 últimos números.'),
  color: z.string(),
  closingDay: dayField,
  dueDay: dayField,
  creditLimit: optionalAmount,
  budget: optionalAmount,
  reminders: z.boolean(),
  accountId: z.string(),
  groupIds: z.array(z.string()),
}).refine((v) => v.bankCode !== OTHER_BANK || v.bankName.trim(), { path: ['bankName'], message: 'Informe o nome do banco.' })

type FormValues = z.infer<typeof schema>

function toForm(card?: CardView): FormValues {
  return {
    bankCode: card?.bank.code ?? '',
    bankName: card?.bank.name ?? '',
    nickname: card?.nickname ?? '',
    lastDigits: card?.lastDigits ?? '',
    color: card?.color ?? DEFAULT_CARD_COLOR,
    closingDay: card ? String(card.closingDay) : '',
    dueDay: card ? String(card.dueDay) : '',
    creditLimit: card?.creditLimit ? amountToInput(card.creditLimit) : '',
    budget: card?.budget ? amountToInput(card.budget) : '',
    reminders: card?.reminders ?? true,
    accountId: card?.accountId ?? SAME_BANK,
    groupIds: card?.groups.map((g) => g.id) ?? [],
  }
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  card?: CardView
  onSaved?: (id: string) => void
}

/** Cadastro do cartão: banco, ciclo (fechamento e vencimento), limite, meta, avisos e grupos que veem a fatura. */
export function CardDialog({ open, onOpenChange, card, onSaved }: Props) {
  const save = useSaveCard()
  const accounts = useWallet().data?.accounts ?? []
  const { data: groups } = useGroups()
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toForm(card) })
  const { errors, isSubmitting } = form.formState
  const bankCode = useWatch({ control: form.control, name: 'bankCode' })

  useEffect(() => {
    if (open) form.reset(toForm(card))
  }, [open, card, form])

  async function onSubmit(values: FormValues) {
    try {
      const saved = await save.mutateAsync({
        id: card?.id,
        data: {
          bank: { code: values.bankCode, name: values.bankCode === OTHER_BANK ? values.bankName.trim() : null, kind: 'CARD' },
          nickname: values.nickname.trim() || null,
          lastDigits: values.lastDigits || null,
          color: values.color,
          closingDay: Number(values.closingDay),
          dueDay: Number(values.dueDay),
          creditLimit: values.creditLimit.trim() ? parseAmount(values.creditLimit) : null,
          budget: values.budget.trim() ? parseAmount(values.budget) : null,
          reminders: values.reminders,
          accountId: values.accountId === SAME_BANK ? null : values.accountId,
          groupIds: values.groupIds,
        },
      })
      toast.success(card ? 'Cartão atualizado.' : 'Cartão cadastrado.')
      onOpenChange(false)
      onSaved?.(saved.card.id)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{card ? 'Editar cartão' : 'Novo cartão'}</DialogTitle>
          <DialogDescription>Com o dia do fechamento e do vencimento, cada compra vai para a fatura certa.</DialogDescription>
        </DialogHeader>
        <DialogBody>
          <form id="card-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
            <FieldGroup className="gap-4">
              <Field data-invalid={!!errors.bankCode}>
                <FieldLabel htmlFor="card-bank">Banco</FieldLabel>
                <Controller control={form.control} name="bankCode" render={({ field }) => (
                  <BankSelect id="card-bank" value={field.value} onChange={field.onChange} noneLabel="Escolha o banco" invalid={!!errors.bankCode} />
                )} />
                <FieldDescription>Os lançamentos com esse banco como cartão entram nas faturas.</FieldDescription>
                <FieldError errors={[errors.bankCode]} />
              </Field>
              {bankCode === OTHER_BANK && (
                <Field data-invalid={!!errors.bankName}>
                  <FieldLabel htmlFor="card-bank-name">Nome do banco</FieldLabel>
                  <Input id="card-bank-name" maxLength={60} className="text-base md:text-sm" {...form.register('bankName')} />
                  <FieldError errors={[errors.bankName]} />
                </Field>
              )}
              <div className="grid gap-4 sm:grid-cols-2">
                <Field data-invalid={!!errors.nickname}>
                  <FieldLabel htmlFor="card-nickname">Apelido (opcional)</FieldLabel>
                  <Input id="card-nickname" placeholder="Ex.: Roxinho" maxLength={40} className="text-base md:text-sm" {...form.register('nickname')} />
                  <FieldError errors={[errors.nickname]} />
                </Field>
                <Field data-invalid={!!errors.lastDigits}>
                  <FieldLabel htmlFor="card-digits">Final (opcional)</FieldLabel>
                  <Input id="card-digits" inputMode="numeric" placeholder="1234" maxLength={4} className="text-base md:text-sm"
                    aria-invalid={!!errors.lastDigits} {...form.register('lastDigits')} />
                  <FieldError errors={[errors.lastDigits]} />
                </Field>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <Field data-invalid={!!errors.closingDay}>
                  <FieldLabel htmlFor="card-closing">Dia do fechamento</FieldLabel>
                  <Input id="card-closing" inputMode="numeric" placeholder="3" maxLength={2} className="text-base md:text-sm"
                    aria-invalid={!!errors.closingDay} {...form.register('closingDay')} />
                  <FieldError errors={[errors.closingDay]} />
                </Field>
                <Field data-invalid={!!errors.dueDay}>
                  <FieldLabel htmlFor="card-due">Dia do vencimento</FieldLabel>
                  <Input id="card-due" inputMode="numeric" placeholder="10" maxLength={2} className="text-base md:text-sm"
                    aria-invalid={!!errors.dueDay} {...form.register('dueDay')} />
                  <FieldError errors={[errors.dueDay]} />
                </Field>
              </div>
              <p className="-mt-2 text-xs text-muted-foreground">
                Compra feita no dia do fechamento ou depois já vai para a fatura seguinte. Confira as datas no app do banco.
              </p>
              <div className="grid grid-cols-2 gap-4">
                <Field data-invalid={!!errors.creditLimit}>
                  <FieldLabel htmlFor="card-limit">Limite (opcional)</FieldLabel>
                  <Input id="card-limit" inputMode="decimal" placeholder="0,00" className="text-base md:text-sm"
                    aria-invalid={!!errors.creditLimit} {...form.register('creditLimit')} />
                  <FieldError errors={[errors.creditLimit]} />
                </Field>
                <Field data-invalid={!!errors.budget}>
                  <FieldLabel htmlFor="card-budget">Meta da fatura (opcional)</FieldLabel>
                  <Input id="card-budget" inputMode="decimal" placeholder="0,00" className="text-base md:text-sm"
                    aria-invalid={!!errors.budget} {...form.register('budget')} />
                  <FieldError errors={[errors.budget]} />
                </Field>
              </div>
              <Field>
                <FieldLabel htmlFor="card-account">Conta que paga a fatura</FieldLabel>
                <Controller control={form.control} name="accountId" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="card-account" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value={SAME_BANK}>A conta do mesmo banco, se houver</SelectItem>
                      {accounts.map((a) => <SelectItem key={a.id} value={a.id}>{a.label}</SelectItem>)}
                    </SelectContent>
                  </Select>
                )} />
                <FieldDescription>Usada para prever o saldo da conta no fim do mês.</FieldDescription>
              </Field>
              <Field>
                <FieldLabel>Cor</FieldLabel>
                <Controller control={form.control} name="color" render={({ field }) => (
                  <ColorPicker value={field.value} onChange={field.onChange} />
                )} />
              </Field>
              <Controller control={form.control} name="reminders" render={({ field }) => (
                <Label htmlFor="card-reminders" className="flex items-start gap-3 rounded-lg border p-3 font-normal">
                  <Switch id="card-reminders" checked={field.value} onCheckedChange={field.onChange} className="mt-0.5" />
                  <span className="space-y-0.5">
                    <span className="block text-sm font-medium">Avisar no celular</span>
                    <span className="block text-xs text-muted-foreground">3 dias antes de fechar e de vencer, e quando a fatura passar de 80% e 100% da meta.</span>
                  </span>
                </Label>
              )} />
              {!!groups?.length && (
                <Field>
                  <FieldLabel>Mostrar nos grupos</FieldLabel>
                  <FieldDescription>Quem está no grupo vê a fatura aberta deste cartão e quanto do gasto do grupo está nele.</FieldDescription>
                  <Controller control={form.control} name="groupIds" render={({ field }) => (
                    <ul className="space-y-1">
                      {groups.map((g) => (
                        <li key={g.id}>
                          <Label htmlFor={`card-group-${g.id}`} className="flex cursor-pointer items-center gap-3 rounded-md px-1.5 py-1.5 font-normal hover:bg-muted/60">
                            <Checkbox id={`card-group-${g.id}`} checked={field.value.includes(g.id)}
                              onCheckedChange={(v) => field.onChange(v === true ? [...field.value, g.id] : field.value.filter((id) => id !== g.id))} />
                            <span className="truncate text-sm">{g.name}</span>
                          </Label>
                        </li>
                      ))}
                    </ul>
                  )} />
                </Field>
              )}
            </FieldGroup>
          </form>
        </DialogBody>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
          <Button type="submit" form="card-form" disabled={isSubmitting}>
            {isSubmitting && <Loader2Icon className="animate-spin" />}
            Salvar
          </Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}
