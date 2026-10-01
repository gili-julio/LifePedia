import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { ColorPicker } from '@/components/ColorPicker'
import { Button } from '@/components/ui/button'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { COLOR_SWATCHES } from '@/lib/colors'
import { useSaveCategory } from '../api'
import type { Category, EntryType } from '../types'
import { ColorDot } from './CategoryBadge'
import { TypeToggle } from './TypeToggle'

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome.').max(60, 'Máximo de 60 caracteres.'),
  type: z.enum(['INCOME', 'EXPENSE']),
  color: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Escolha uma cor.'),
})

type FormValues = z.infer<typeof schema>

function toForm(category: Category | undefined, type: EntryType): FormValues {
  return {
    name: category?.name ?? '',
    type: category?.type ?? type,
    color: category?.color ?? COLOR_SWATCHES[0],
  }
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  category?: Category
  defaultType?: EntryType
}

export function CategoryDialog({ open, onOpenChange, category, defaultType = 'EXPENSE' }: Props) {
  const save = useSaveCategory()
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toForm(category, defaultType) })
  const { errors, isSubmitting } = form.formState
  const [name, color] = useWatch({ control: form.control, name: ['name', 'color'] })
  const typeLocked = !!category && category.usageCount > 0

  useEffect(() => {
    if (open) form.reset(toForm(category, defaultType))
  }, [open, category, defaultType, form])

  async function onSubmit(values: FormValues) {
    try {
      await save.mutateAsync({ id: category?.id, data: values })
      toast.success(category ? 'Categoria atualizada.' : 'Categoria criada.')
      onOpenChange(false)
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) form.setError('name', { message: error.message })
      else toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{category ? 'Editar categoria' : 'Nova categoria'}</DialogTitle>
          <DialogDescription>A cor aparece nos gráficos e nas listas.</DialogDescription>
        </DialogHeader>
        <DialogBody>
          <form id="category-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
            <FieldGroup className="gap-4">
              <Controller
                control={form.control}
                name="type"
                render={({ field }) => (
                  <Field>
                    <TypeToggle value={field.value} onChange={field.onChange} disabled={typeLocked} />
                    {typeLocked && (
                      <FieldDescription>O tipo não pode mudar porque já há lançamentos nesta categoria.</FieldDescription>
                    )}
                  </Field>
                )}
              />
              <Field data-invalid={!!errors.name}>
                <FieldLabel htmlFor="cat-name">Nome</FieldLabel>
                <Input id="cat-name" placeholder="Ex.: Pets" aria-invalid={!!errors.name} {...form.register('name')} />
                <FieldError errors={[errors.name]} />
              </Field>
              <Field data-invalid={!!errors.color}>
                <FieldLabel>Cor</FieldLabel>
                <Controller
                  control={form.control}
                  name="color"
                  render={({ field }) => <ColorPicker value={field.value} onChange={field.onChange} />}
                />
                <FieldError errors={[errors.color]} />
              </Field>
              <div className="flex items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-sm">
                <span className="text-muted-foreground">Prévia:</span>
                <ColorDot color={color} />
                <span className="font-medium">{name || 'Nome da categoria'}</span>
                <span className="ml-auto h-2 w-16 rounded-[3px]" style={{ background: color }} />
              </div>
            </FieldGroup>
          </form>
        </DialogBody>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button type="submit" form="category-form" disabled={isSubmitting}>
            {isSubmitting && <Loader2Icon className="animate-spin" />}
            Salvar
          </Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}
