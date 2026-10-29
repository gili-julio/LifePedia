import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { MonthField } from '@/components/MonthField'
import { Button } from '@/components/ui/button'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldContent, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { amountToInput, currentYearMonth, parseAmount } from '@/lib/format'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { useSaveRecurringEntry, useTransactionsSearch } from '../api'
import type { EntryType, RecurringEntry, RecurringEntryRequest, Transaction } from '../types'
import { ShareFields } from '../group/GroupShareSection'
import { useCategoryLookup } from '../use-category-lookup'
import { CategorySelect } from './CategorySelect'
import { FixedAdjustmentsList } from './FixedAdjustmentsList'
import { ReplacedOccasionals } from './ReplacedOccasionals'
import { TypeToggle } from './TypeToggle'

const schema = z
  .object({
    description: z.string().trim().min(1, 'Informe a descrição.').max(120, 'Máximo de 120 caracteres.'),
    type: z.enum(['INCOME', 'EXPENSE']),
    categoryId: z.string().min(1, 'Escolha uma categoria.'),
    amount: z.string().refine((v) => parseAmount(v) > 0, 'Informe um valor maior que zero.'),
    dayOfMonth: z
      .string()
      .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 31), 'Dia entre 1 e 31.'),
    startMonth: z.string().regex(/^\d{4}-\d{2}$/, 'Informe o mês de início.'),
    endMonth: z.string(),
    active: z.boolean(),
    notes: z.string().max(10000, 'Máximo de 10000 caracteres.'),
    /** Ocasionais desmarcados (não serão removidos) */
    keep: z.array(z.string()),
    groupIds: z.array(z.string()),
    shareMode: z.enum(['SYNC', 'COPY']),
  })
  .refine((d) => !d.endMonth || d.endMonth >= d.startMonth, {
    path: ['endMonth'],
    message: 'O fim não pode ser antes do início.',
  })

type FormValues = z.infer<typeof schema>

function toForm(entry: RecurringEntry | undefined, type: EntryType, from?: Transaction): FormValues {
  if (!entry && from) {
    return {
      description: from.description,
      type: from.type,
      categoryId: from.categoryId,
      amount: amountToInput(from.amount),
      dayOfMonth: String(Number(from.date.slice(8, 10))),
      startMonth: from.month,
      endMonth: '',
      active: true,
      notes: from.notes ?? '',
      keep: [],
      groupIds: [],
      shareMode: 'SYNC',
    }
  }
  return {
    description: entry?.description ?? '',
    type: entry?.type ?? type,
    categoryId: entry?.categoryId ?? '',
    amount: entry ? amountToInput(entry.amount) : '',
    dayOfMonth: entry?.dayOfMonth ? String(entry.dayOfMonth) : '',
    startMonth: entry?.startMonth ?? currentYearMonth(),
    endMonth: entry?.endMonth ?? '',
    active: entry?.active ?? true,
    notes: entry?.notes ?? '',
    keep: [],
    groupIds: [],
    shareMode: 'SYNC',
  }
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Se informado, edita; senão, cria. */
  entry?: RecurringEntry
  defaultType?: EntryType
  /** Tornar fixo: preenche com o ocasional e oferece remover os de mesmo nome. */
  fromTransaction?: Transaction
  /** Salva em outro lugar (ex.: cópia num grupo) em vez do fixo pessoal. */
  onSave?: (data: RecurringEntryRequest) => Promise<unknown>
}

export function RecurringEntryDialog({ open, onOpenChange, entry, defaultType = 'EXPENSE', fromTransaction, onSave }: Props) {
  const save = useSaveRecurringEntry()
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toForm(entry, defaultType, fromTransaction) })
  const { errors, isSubmitting } = form.formState
  const { byId } = useCategoryLookup()
  const [type, description, startMonth, endMonth, keep] = useWatch({
    control: form.control,
    name: ['type', 'description', 'startMonth', 'endMonth', 'keep'],
  })
  const excluded = new Set(keep)

  const replacing = !entry && !!fromTransaction
  const canShare = !entry && !onSave
  const search = { from: startMonth, to: endMonth || undefined, type, description: useDebouncedValue(description.trim()) }
  const validSearch = !!search.description && /^\d{4}-\d{2}$/.test(search.from) && (!search.to || search.to >= search.from)
  const matches = useTransactionsSearch(search, open && replacing && validSearch)
  const toRemove = replacing && validSearch ? (matches.data ?? []).filter((t) => !excluded.has(t.id)) : []

  useEffect(() => {
    if (open) form.reset(toForm(entry, defaultType, fromTransaction))
  }, [open, entry, defaultType, fromTransaction, form])

  async function onSubmit(values: FormValues) {
    const data: RecurringEntryRequest = {
      description: values.description,
      type: values.type,
      categoryId: values.categoryId,
      amount: parseAmount(values.amount),
      dayOfMonth: values.dayOfMonth ? Number(values.dayOfMonth) : null,
      startMonth: values.startMonth,
      endMonth: values.endMonth || null,
      active: values.active,
      notes: values.notes.trim() || null,
      ...(toRemove.length && { removeTransactionIds: toRemove.map((t) => t.id) }),
      ...(canShare && values.groupIds.length && { groupIds: values.groupIds, shareMode: values.shareMode }),
    }
    try {
      if (onSave) await onSave(data)
      else await save.mutateAsync({ id: entry?.id, data })
      const extras = [
        toRemove.length ? `${toRemove.length} ocasional(is) removido(s)` : '',
        data.groupIds?.length ? `levado para ${data.groupIds.length} grupo(s)` : '',
      ].filter(Boolean).join(' · ')
      toast.success(entry ? 'Lançamento fixo atualizado.' : `Lançamento fixo cadastrado${extras ? ` · ${extras}` : ''}.`)
      onOpenChange(false)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  const isIncome = type === 'INCOME'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {replacing ? 'Tornar fixo' : entry ? `Editar ${isIncome ? 'ganho fixo' : 'despesa fixa'}` : isIncome ? 'Novo ganho fixo' : 'Nova despesa fixa'}
          </DialogTitle>
          <DialogDescription>
            {replacing
              ? 'Preenchido com o lançamento escolhido. Revise antes de salvar.'
              : `Valores que se repetem todo mês${isIncome ? ', como salário ou aluguel recebido.' : ', como aluguel, internet ou academia.'}`}
          </DialogDescription>
        </DialogHeader>
        <DialogBody>
          <form id="recurring-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
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
                <FieldLabel htmlFor="rec-description">Descrição</FieldLabel>
                <Input id="rec-description" placeholder={isIncome ? 'Ex.: Salário' : 'Ex.: Aluguel'}
                  aria-invalid={!!errors.description} {...form.register('description')} />
                <FieldError errors={[errors.description]} />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field data-invalid={!!errors.categoryId}>
                  <FieldLabel htmlFor="rec-category">Categoria</FieldLabel>
                  <Controller
                    control={form.control}
                    name="categoryId"
                    render={({ field }) => (
                      <CategorySelect id="rec-category" type={type} value={field.value}
                        onChange={field.onChange} invalid={!!errors.categoryId} />
                    )}
                  />
                  <FieldError errors={[errors.categoryId]} />
                </Field>
                <Field data-invalid={!!errors.amount}>
                  <FieldLabel htmlFor="rec-amount">Valor mensal (R$)</FieldLabel>
                  <Input id="rec-amount" inputMode="decimal" placeholder="0,00"
                    aria-invalid={!!errors.amount} {...form.register('amount')} />
                  <FieldError errors={[errors.amount]} />
                </Field>
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field data-invalid={!!errors.dayOfMonth}>
                  <FieldLabel htmlFor="rec-day">Dia</FieldLabel>
                  <Input id="rec-day" inputMode="numeric" placeholder="Ex.: 5"
                    aria-invalid={!!errors.dayOfMonth} {...form.register('dayOfMonth')} />
                  <FieldError errors={[errors.dayOfMonth]} />
                </Field>
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field data-invalid={!!errors.startMonth}>
                  <FieldLabel htmlFor="rec-start">Início</FieldLabel>
                  <Controller control={form.control} name="startMonth" render={({ field }) => (
                    <MonthField id="rec-start" value={field.value} onChange={field.onChange} invalid={!!errors.startMonth} />
                  )} />
                  <FieldError errors={[errors.startMonth]} />
                </Field>
                <Field data-invalid={!!errors.endMonth}>
                  <FieldLabel htmlFor="rec-end">Fim (opcional)</FieldLabel>
                  <Controller control={form.control} name="endMonth" render={({ field }) => (
                    <MonthField id="rec-end" value={field.value} onChange={field.onChange} invalid={!!errors.endMonth}
                      clearable placeholder="Sem fim" />
                  )} />
                  <FieldError errors={[errors.endMonth]} />
                </Field>
              </div>
              <Field data-invalid={!!errors.notes}>
                <FieldLabel htmlFor="rec-notes">Observações</FieldLabel>
                <Textarea id="rec-notes" rows={2} {...form.register('notes')} />
                <FieldError errors={[errors.notes]} />
              </Field>
              <Controller
                control={form.control}
                name="active"
                render={({ field }) => (
                  <Field orientation="horizontal">
                    <FieldContent>
                      <FieldLabel htmlFor="rec-active">Ativo</FieldLabel>
                      <FieldDescription>Desative para pausar sem apagar o histórico.</FieldDescription>
                    </FieldContent>
                    <Switch id="rec-active" checked={field.value} onCheckedChange={field.onChange} />
                  </Field>
                )}
              />
              {canShare && <ShareFields control={form.control} />}
            </FieldGroup>
          </form>
          {entry && !onSave && <FixedAdjustmentsList entryId={entry.id} />}
          {replacing && (
            <ReplacedOccasionals
              items={validSearch ? matches.data : []}
              loading={validSearch && matches.isLoading}
              excluded={excluded}
              onToggle={(id, checked) => form.setValue('keep', checked ? keep.filter((k) => k !== id) : [...keep, id])}
            />
          )}
        </DialogBody>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button type="submit" form="recurring-form" disabled={isSubmitting}>
            {isSubmitting && <Loader2Icon className="animate-spin" />}
            Salvar
          </Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}
