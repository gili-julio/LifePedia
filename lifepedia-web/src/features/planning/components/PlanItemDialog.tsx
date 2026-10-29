import { zodResolver } from '@hookform/resolvers/zod'
import { InfoIcon, Loader2Icon, SearchIcon, ShoppingBagIcon, TrendingUpIcon, XIcon } from 'lucide-react'
import { useRef, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { ImagePicker } from '@/components/image/ImagePicker'
import { MonthField } from '@/components/MonthField'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { addMonths, amountToInput, currentYearMonth, formatCurrency, formatMonth, parseAmount } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useLinkPreview, usePriceReference, useSavePlanItem } from '../api'
import type { PlanItem, PlanItemRequest, PlanKind } from '../types'
import { ItemThumb } from './ItemThumb'
import { LinkChip } from './LinkChip'

const schema = z.object({
  kind: z.enum(['PURCHASE', 'INVESTMENT']),
  name: z.string().trim().min(1, 'Informe o nome.').max(120, 'Máximo de 120 caracteres.'),
  amount: z.string().refine((v) => parseAmount(v) > 0, 'Informe um valor maior que zero.'),
  targetMonth: z.string().regex(/^\d{4}-\d{2}$/, 'Informe o prazo.'),
  payment: z.enum(['CASH', 'INSTALLMENTS']),
  installments: z.string(),
  notes: z.string().max(10000, 'Máximo de 10000 caracteres.'),
  url: z.string().max(2048, 'Link muito longo.'),
}).refine((v) => v.payment === 'CASH' || (Number(v.installments) >= 2 && Number(v.installments) <= 120), {
  path: ['installments'], message: 'De 2 a 120 parcelas.',
})

type FormValues = z.infer<typeof schema>

/** Foto: a atual (URL da API), uma nova (data URL) ou nenhuma. */
type ImageState = { kind: 'current'; url: string } | { kind: 'new'; data: string } | { kind: 'none' }

/** Dados lidos do link que vão junto com o item; `blocked` = a loja não liberou a página. */
type LinkData = { title: string | null; site: string | null; price: number | null; blocked?: boolean }

/** Produto parecido de outro site; `fromLink` = veio da busca do link (sai se o link mudar). */
type ReferenceData = {
  site: string
  title: string | null
  url: string | null
  price: number | null
  fromLink: boolean
  stores?: number | null
}

const KINDS: { value: PlanKind; label: string; icon: typeof ShoppingBagIcon }[] = [
  { value: 'PURCHASE', label: 'Compra', icon: ShoppingBagIcon },
  { value: 'INVESTMENT', label: 'Investimento', icon: TrendingUpIcon },
]

function toForm(item?: PlanItem): FormValues {
  return {
    kind: item?.kind ?? 'PURCHASE',
    name: item?.name ?? '',
    amount: item ? amountToInput(item.amount) : '',
    targetMonth: item?.targetMonth ?? addMonths(currentYearMonth(), 6),
    payment: item && item.installments > 1 ? 'INSTALLMENTS' : 'CASH',
    installments: item && item.installments > 1 ? String(item.installments) : '10',
    notes: item?.notes ?? '',
    url: item?.url ?? '',
  }
}

function readAsDataUrl(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('Falha ao ler a imagem.'))
    reader.readAsDataURL(blob)
  })
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupId: string
  item?: PlanItem
}

/** Cadastro/edição de item do planejamento. O estado recomeça a cada abertura (formulário dentro do conteúdo). */
export function PlanItemDialog({ open, onOpenChange, groupId, item }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{item ? 'Editar item' : 'Novo item'}</DialogTitle>
          <DialogDescription>Algo que o grupo quer comprar ou investir até um prazo.</DialogDescription>
        </DialogHeader>
        {open && <PlanItemForm groupId={groupId} item={item} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function PlanItemForm({ groupId, item, onDone }: { groupId: string; item?: PlanItem; onDone: () => void }) {
  const save = useSavePlanItem()
  const preview = useLinkPreview()
  const lookup = usePriceReference()
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toForm(item) })
  const { errors, isSubmitting } = form.formState
  const [image, setImage] = useState<ImageState>(item?.imageUrl ? { kind: 'current', url: item.imageUrl } : { kind: 'none' })
  const [link, setLink] = useState<LinkData | null>(
    item?.url ? { title: item.linkTitle, site: item.linkSite, price: item.linkPrice } : null,
  )
  const [reference, setReference] = useState<ReferenceData | null>(item?.referenceSite
    ? { site: item.referenceSite, title: item.referenceTitle, url: item.referenceUrl, price: item.referencePrice, fromLink: !!item.url }
    : null)
  /** Valores preenchidos pela busca: só são trocados se o usuário não mexeu. */
  const autofilled = useRef<{ name?: string; amount?: string; image?: string }>({})
  const [kind, payment, installments, amount, targetMonth, url, name] = useWatch({
    control: form.control,
    name: ['kind', 'payment', 'installments', 'amount', 'targetMonth', 'url', 'name'],
  })

  /** Preenche valor e foto achados; `keepImage` não troca foto que já existe. */
  function fill(price: number | null, imageData: string | null, keepImage: boolean) {
    const currentAmount = form.getValues('amount')
    if (price && (!currentAmount.trim() || currentAmount === autofilled.current.amount)) {
      const next = amountToInput(price)
      form.setValue('amount', next, { shouldValidate: true })
      autofilled.current.amount = next
    }
    if (!imageData) return
    const replaceable = keepImage
      ? image.kind === 'none'
      : image.kind !== 'new' || image.data === autofilled.current.image
    if (replaceable) {
      setImage({ kind: 'new', data: imageData })
      autofilled.current.image = imageData
    }
  }

  function dropLinkReference() {
    setReference((current) => (current?.fromLink ? null : current))
  }

  async function fetchLink(raw = form.getValues('url')) {
    const value = raw.trim()
    if (!value) return
    try {
      const result = await preview.mutateAsync({ groupId, url: value })
      form.setValue('url', result.url)
      setLink({ title: result.title, site: result.site, price: result.price, blocked: result.blocked })
      const name = form.getValues('name')
      if (result.title && (!name.trim() || name === autofilled.current.name)) {
        const next = result.title.slice(0, 120)
        form.setValue('name', next, { shouldValidate: true })
        autofilled.current.name = next
      }
      const ref = result.reference
      if (ref) {
        setReference({ site: ref.site, title: ref.title, url: ref.url, price: ref.price, fromLink: true, stores: ref.stores })
      } else {
        dropLinkReference()
      }
      fill(result.price ?? ref?.price ?? null, result.imageData ?? ref?.imageData ?? null, false)
      if (ref) {
        const why = result.blocked ? `${result.site ?? 'A loja'} não liberou os dados` : 'Faltou preço ou foto na página'
        toast.info(`${why}. Trouxemos de um produto parecido no ${ref.site}; confira.`)
      } else if (result.blocked) {
        toast.info('A loja não liberou os dados da página. Confira o nome e informe o valor.')
      } else if (!result.price) {
        toast.info('Não achamos o preço na página. Informe o valor.')
      }
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível ler o link.')
    }
  }

  async function searchByName() {
    const value = form.getValues('name').trim()
    if (!value) return
    try {
      const ref = await lookup.mutateAsync({ groupId, name: value })
      if (!ref) {
        toast.info('Não achamos produto parecido. Informe o valor.')
        return
      }
      setReference({ site: ref.site, title: ref.title, url: ref.url, price: ref.price, fromLink: false, stores: ref.stores })
      fill(ref.price, ref.imageData, true)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível buscar o preço.')
    }
  }

  function clearLink() {
    form.setValue('url', '')
    setLink(null)
    dropLinkReference()
  }

  async function onSubmit(values: FormValues) {
    const data: PlanItemRequest = {
      name: values.name,
      kind: values.kind,
      amount: parseAmount(values.amount),
      targetMonth: values.targetMonth,
      installments: values.payment === 'CASH' ? 1 : Number(values.installments),
      notes: values.notes.trim() || null,
      url: values.url.trim() || null,
      linkTitle: link?.title ?? null,
      linkSite: link?.site ?? null,
      linkPrice: link?.price ?? null,
      referenceSite: reference?.site ?? null,
      referenceTitle: reference?.title ?? null,
      referenceUrl: reference?.url ?? null,
      referencePrice: reference?.price ?? null,
      ...(image.kind === 'new' && { imageData: image.data }),
      ...(image.kind === 'none' && item?.imageUrl && { removeImage: true }),
    }
    try {
      await save.mutateAsync({ groupId, id: item?.id, data })
      toast.success(item ? 'Item atualizado.' : 'Item adicionado ao planejamento.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  const imageSrc = image.kind === 'new' ? image.data : image.kind === 'current' ? image.url : null
  const count = Number(installments)
  const total = parseAmount(amount)
  const installmentHint = payment === 'INSTALLMENTS' && total > 0 && count >= 2 && count <= 120 && targetMonth
    ? `${count}× de ${formatCurrency(total / count)}, de ${formatMonth(targetMonth, 'short')} a ${formatMonth(addMonths(targetMonth, count - 1), 'short')}`
    : null
  const suggested = link?.price
    ? { price: link.price, from: 'da página' }
    : reference?.price ? { price: reference.price, from: `do ${reference.site}` } : null
  const priceDiffers = suggested && total > 0 && Math.abs(suggested.price - total) >= 0.01
  const busy = preview.isPending || lookup.isPending

  return (
    <>
      <DialogBody>
        <form id="plan-item-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup className="gap-4">
            <Field data-invalid={!!errors.url}>
              <FieldLabel htmlFor="plan-url">Link do produto (opcional)</FieldLabel>
              <div className="flex gap-2">
                <Input id="plan-url" inputMode="url" placeholder="Cole o link da loja" autoComplete="off"
                  aria-invalid={!!errors.url}
                  {...form.register('url', {
                    // Link trocado à mão: os dados lidos eram do anterior.
                    onChange: () => {
                      setLink(null)
                      dropLinkReference()
                    },
                  })}
                  onPaste={(e) => {
                    const pasted = e.clipboardData.getData('text')
                    if (pasted.trim()) setTimeout(() => void fetchLink(pasted), 0)
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      void fetchLink()
                    }
                  }} />
                <Button type="button" variant="outline" disabled={!url.trim() || busy} onClick={() => fetchLink()}>
                  {preview.isPending ? <Loader2Icon className="animate-spin" /> : <SearchIcon />}
                  <span className="max-sm:sr-only">Buscar</span>
                </Button>
              </div>
              <FieldDescription>Tentamos trazer nome, preço e foto. Se a loja não liberar (ex.: Mercado Livre, Shopee), buscamos um produto parecido no Buscapé.</FieldDescription>
              <FieldError errors={[errors.url]} />
            </Field>

            {(url.trim() || imageSrc) && (
              <div className="flex items-start gap-3 rounded-lg border p-3">
                <ImagePicker
                  shape="rect"
                  preview={<ItemThumb src={imageSrc} kind={kind} className="size-20" />}
                  onChange={async (blob) => setImage({ kind: 'new', data: await readAsDataUrl(blob) })}
                  onRemove={imageSrc ? () => setImage({ kind: 'none' }) : undefined}
                />
                {link && url.trim() && (
                  <Button type="button" variant="ghost" size="icon-sm" className="ml-auto shrink-0" aria-label="Tirar link" onClick={clearLink}>
                    <XIcon />
                  </Button>
                )}
              </div>
            )}
            {link && url.trim() && (
              <div className="-mt-2 space-y-0.5 text-xs text-muted-foreground">
                <LinkChip url={url} site={link.site} />
                {link.title && <p className="line-clamp-2">{link.title}</p>}
                {link.price != null && <p>Preço na página: {formatCurrency(link.price)}</p>}
              </div>
            )}
            {reference && (
              <div className="flex items-start gap-2 rounded-lg border border-dashed p-2.5 text-xs" role="status">
                <InfoIcon className="mt-px size-3.5 shrink-0 text-muted-foreground" aria-hidden />
                <div className="min-w-0 flex-1 space-y-0.5">
                  <p>
                    {reference.fromLink && link?.blocked ? `Não deu para ler ${link.site ?? 'a loja'}. ` : ''}
                    {reference.fromLink && link && !link.blocked ? 'Preço' : 'Preço e foto'} de referência: produto parecido no {reference.site}
                    {reference.stores ? ` (menor preço entre ${reference.stores} lojas)` : ''}.
                  </p>
                  {(reference.title || reference.price != null) && (
                    <p className="line-clamp-2 text-muted-foreground">
                      {reference.title}{reference.price != null && ` · ${formatCurrency(reference.price)}`}
                    </p>
                  )}
                  {reference.url && <LinkChip url={reference.url} site={`Ver no ${reference.site}`} />}
                </div>
                <Button type="button" variant="ghost" size="icon-xs" className="shrink-0" aria-label="Tirar referência"
                  onClick={() => setReference(null)}>
                  <XIcon />
                </Button>
              </div>
            )}

            <Controller control={form.control} name="kind" render={({ field }) => (
              <div role="radiogroup" aria-label="Tipo" className="grid grid-cols-2 gap-2">
                {KINDS.map(({ value, label, icon: Icon }) => (
                  <button key={value} type="button" role="radio" aria-checked={field.value === value}
                    onClick={() => field.onChange(value)}
                    className={cn(
                      'flex h-9 items-center justify-center gap-2 rounded-lg border text-sm font-medium transition-colors',
                      'focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none',
                      field.value === value ? 'border-primary bg-primary/5 text-foreground' : 'text-muted-foreground hover:bg-muted',
                    )}>
                    <Icon className="size-4" /> {label}
                  </button>
                ))}
              </div>
            )} />

            <Field data-invalid={!!errors.name}>
              <FieldLabel htmlFor="plan-name">Nome</FieldLabel>
              <Input id="plan-name" placeholder={kind === 'INVESTMENT' ? 'Ex.: Reserva de emergência' : 'Ex.: Geladeira nova'}
                aria-invalid={!!errors.name} {...form.register('name')} />
              {kind === 'PURCHASE' && !link?.price && name.trim().length >= 3 && (
                <FieldDescription>
                  <button type="button" className="inline-flex items-center gap-1 underline underline-offset-2 disabled:opacity-50"
                    disabled={busy} onClick={() => void searchByName()}>
                    {lookup.isPending ? <Loader2Icon className="size-3 animate-spin" /> : <SearchIcon className="size-3" />}
                    Buscar preço e foto pelo nome
                  </button>
                </FieldDescription>
              )}
              <FieldError errors={[errors.name]} />
            </Field>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={!!errors.amount}>
                <FieldLabel htmlFor="plan-amount">Valor (R$)</FieldLabel>
                <Input id="plan-amount" inputMode="decimal" placeholder="0,00" aria-invalid={!!errors.amount} {...form.register('amount')} />
                {priceDiffers && (
                  <FieldDescription>
                    <button type="button" className="underline underline-offset-2"
                      onClick={() => form.setValue('amount', amountToInput(suggested.price), { shouldValidate: true })}>
                      Usar {formatCurrency(suggested.price)} {suggested.from}
                    </button>
                  </FieldDescription>
                )}
                <FieldError errors={[errors.amount]} />
              </Field>
              <Field data-invalid={!!errors.targetMonth}>
                <FieldLabel htmlFor="plan-target">Quero até</FieldLabel>
                <Controller control={form.control} name="targetMonth" render={({ field }) => (
                  <MonthField id="plan-target" value={field.value} onChange={field.onChange} invalid={!!errors.targetMonth} />
                )} />
                <FieldError errors={[errors.targetMonth]} />
              </Field>
            </div>

            <Field data-invalid={!!errors.installments}>
              <FieldLabel>Pagamento</FieldLabel>
              <Controller control={form.control} name="payment" render={({ field }) => (
                <RadioGroup value={field.value} onValueChange={field.onChange} className="grid grid-cols-2 gap-2">
                  {([['CASH', 'À vista'], ['INSTALLMENTS', 'Parcelado']] as const).map(([value, label]) => (
                    <Label key={value} htmlFor={`plan-pay-${value}`}
                      className="flex cursor-pointer items-center gap-2 rounded-lg border p-2.5 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-primary/5">
                      <RadioGroupItem id={`plan-pay-${value}`} value={value} />
                      {label}
                    </Label>
                  ))}
                </RadioGroup>
              )} />
              {payment === 'INSTALLMENTS' && (
                <div className="flex items-center gap-2">
                  <Input id="plan-installments" type="number" min={2} max={120} className="w-24"
                    aria-label="Número de parcelas" aria-invalid={!!errors.installments} {...form.register('installments')} />
                  <span className="text-sm text-muted-foreground">parcelas</span>
                </div>
              )}
              <FieldDescription>
                {installmentHint ?? (payment === 'CASH' ? 'O valor todo sai no mês do prazo.' : 'As parcelas começam no mês do prazo.')}
              </FieldDescription>
              <FieldError errors={[errors.installments]} />
            </Field>

            <Field data-invalid={!!errors.notes}>
              <FieldLabel htmlFor="plan-notes">Observações</FieldLabel>
              <Textarea id="plan-notes" rows={2} {...form.register('notes')} />
              <FieldError errors={[errors.notes]} />
            </Field>
          </FieldGroup>
        </form>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" form="plan-item-form" disabled={isSubmitting || busy}>
          {isSubmitting && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </>
  )
}
