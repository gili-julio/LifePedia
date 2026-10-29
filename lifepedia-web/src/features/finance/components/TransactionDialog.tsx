import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { DatePicker } from '@/components/DatePicker'
import { MonthField } from '@/components/MonthField'
import { Button } from '@/components/ui/button'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { amountToInput, parseAmount, todayIso } from '@/lib/format'
import { useSaveTransaction } from '../api'
import { ShareFields } from '../group/GroupShareSection'
import type { EntryType, Transaction, TransactionRequest } from '../types'
import { useCategoryLookup } from '../use-category-lookup'
import { CategorySelect } from './CategorySelect'
import { TypeToggle } from './TypeToggle'

const schema = z.object({
  description: z.string().trim().min(1, 'Informe a descrição.').max(120, 'Máximo de 120 caracteres.'),
  type: z.enum(['INCOME', 'EXPENSE']),
  categoryId: z.string().min(1, 'Escolha uma categoria.'),
  amount: z.string().refine((v) => parseAmount(v) > 0, 'Informe um valor maior que zero.'),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe a data.'),
  month: z.string(),
  notes: z.string().max(10000, 'Máximo de 10000 caracteres.'),
  groupIds: z.array(z.string()),
  shareMode: z.enum(['SYNC', 'COPY']),
}).refine((v) => v.month >= v.date.slice(0, 7), { path: ['month'], message: 'Não pode ser antes do mês da data.' })

type FormValues = z.infer<typeof schema>

function toForm(transaction: Transaction | undefined, type: EntryType, date: string): FormValues {
  return {
    description: transaction?.description ?? '',
    type: transaction?.type ?? type,
    categoryId: transaction?.categoryId ?? '',
    amount: transaction ? amountToInput(transaction.amount) : '',
    date: transaction?.date ?? date,
    month: transaction?.month ?? date.slice(0, 7),
    notes: transaction?.notes ?? '',
    groupIds: [],
    shareMode: 'SYNC',
  }
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  transaction?: Transaction
  defaultType?: EntryType
  /** Data sugerida para novos lançamentos (yyyy-MM-dd). */
  defaultDate?: string
  /** Salva em outro lugar (ex.: cópia num grupo) em vez do lançamento pessoal. */
  onSave?: (data: TransactionRequest) => Promise<unknown>
}

export function TransactionDialog({ open, onOpenChange, transaction, defaultType = 'EXPENSE', defaultDate, onSave }: Props) {
  const save = useSaveTransaction()
  const initialDate = defaultDate ?? todayIso()
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: toForm(transaction, defaultType, initialDate),
  })
  const { errors, isSubmitting } = form.formState
  const { byId } = useCategoryLookup()
  const type = useWatch({ control: form.control, name: 'type' })
  const canShare = !transaction && !onSave

  useEffect(() => {
    if (open) form.reset(toForm(transaction, defaultType, initialDate))
  }, [open, transaction, defaultType, initialDate, form])

  async function onSubmit(values: FormValues) {
    const data: TransactionRequest = {
      description: values.description,
      type: values.type,
      categoryId: values.categoryId,
      amount: parseAmount(values.amount),
      date: values.date,
      month: values.month,
      notes: values.notes.trim() || null,
      ...(canShare && values.groupIds.length && { groupIds: values.groupIds, shareMode: values.shareMode }),
    }
    try {
      if (onSave) await onSave(data)
      else await save.mutateAsync({ id: transaction?.id, data })
      const shared = data.groupIds?.length ? ` · levado para ${data.groupIds.length} grupo(s)` : ''
      toast.success(transaction ? 'Lançamento atualizado.' : `Lançamento registrado${shared}.`)
      onOpenChange(false)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{transaction ? 'Editar lançamento' : 'Novo lançamento'}</DialogTitle>
          <DialogDescription>Um ganho ou gasto pontual, que não se repete todo mês.</DialogDescription>
        </DialogHeader>
        <DialogBody>
          <form id="transaction-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
            <FieldGroup className="gap-4">
              <Controller
                control={form.control}
                name="type"
                render={({ field }) => (
                  <TypeToggle
                    value={field.value}
                    onChange={(next) => {
                      field.onChange(next)
                      const current = byId.get(form.getValues('categoryId'))
                      if (current && current.type !== next) form.setValue('categoryId', '')
                    }}
                  />
                )}
              />
              <Field data-invalid={!!errors.description}>
                <FieldLabel htmlFor="tx-description">Descrição</FieldLabel>
                <Input id="tx-description" placeholder={type === 'INCOME' ? 'Ex.: Venda da bicicleta' : 'Ex.: Mercado'}
                  aria-invalid={!!errors.description} {...form.register('description')} />
                {transaction?.originalDescription && (
                  <FieldDescription className="break-words">No documento: {transaction.originalDescription}</FieldDescription>
                )}
                <FieldError errors={[errors.description]} />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field data-invalid={!!errors.categoryId}>
                  <FieldLabel htmlFor="tx-category">Categoria</FieldLabel>
                  <Controller
                    control={form.control}
                    name="categoryId"
                    render={({ field }) => (
                      <CategorySelect id="tx-category" type={type} value={field.value}
                        onChange={field.onChange} invalid={!!errors.categoryId} />
                    )}
                  />
                  <FieldError errors={[errors.categoryId]} />
                </Field>
                <Field data-invalid={!!errors.amount}>
                  <FieldLabel htmlFor="tx-amount">Valor (R$)</FieldLabel>
                  <Input id="tx-amount" inputMode="decimal" placeholder="0,00"
                    aria-invalid={!!errors.amount} {...form.register('amount')} />
                  <FieldError errors={[errors.amount]} />
                </Field>
              </div>
              <Field data-invalid={!!errors.date}>
                <FieldLabel htmlFor="tx-date">Data</FieldLabel>
                <Controller control={form.control} name="date" render={({ field }) => (
                  <DatePicker id="tx-date" value={field.value} invalid={!!errors.date}
                    onChange={(value) => {
                      const previous = field.value.slice(0, 7)
                      const next = value.slice(0, 7)
                      const month = form.getValues('month')
                      if (month === previous || month < next) form.setValue('month', next)
                      field.onChange(value)
                    }} />
                )} />
                <FieldError errors={[errors.date]} />
              </Field>
              <Field data-invalid={!!errors.month}>
                <FieldLabel htmlFor="tx-month">Conta no mês</FieldLabel>
                <Controller
                  control={form.control}
                  name="month"
                  render={({ field }) => (
                    <MonthField id="tx-month" value={field.value} onChange={field.onChange} invalid={!!errors.month} />
                  )}
                />
                <FieldDescription>Compra no cartão? Use o mês da fatura.</FieldDescription>
                <FieldError errors={[errors.month]} />
              </Field>
              <Field data-invalid={!!errors.notes}>
                <FieldLabel htmlFor="tx-notes">Observações</FieldLabel>
                <Textarea id="tx-notes" rows={2} {...form.register('notes')} />
                <FieldError errors={[errors.notes]} />
              </Field>
              {canShare && <ShareFields control={form.control} />}
            </FieldGroup>
          </form>
        </DialogBody>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button type="submit" form="transaction-form" disabled={isSubmitting}>
            {isSubmitting && <Loader2Icon className="animate-spin" />}
            Salvar
          </Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}
