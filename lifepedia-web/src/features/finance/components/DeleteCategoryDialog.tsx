import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Field, FieldLabel } from '@/components/ui/field'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { useDeleteCategory } from '../api'
import type { Category } from '../types'
import { useCategoryLookup } from '../use-category-lookup'
import { ColorDot } from './CategoryBadge'

type Props = {
  category: Category | null
  onOpenChange: (open: boolean) => void
}

/** Exclui categoria; se em uso, pede o destino dos lançamentos. */
export function DeleteCategoryDialog({ category, onOpenChange }: Props) {
  const remove = useDeleteCategory()
  const { ofType } = useCategoryLookup()
  const [replaceWith, setReplaceWith] = useState('')

  const alternatives = category ? ofType(category.type).filter((c) => c.id !== category.id) : []
  const inUse = (category?.usageCount ?? 0) > 0
  const isLast = !!category && alternatives.length === 0

  function close(open: boolean) {
    if (!open) setReplaceWith('')
    onOpenChange(open)
  }

  async function confirm() {
    if (!category) return
    try {
      await remove.mutateAsync({ id: category.id, replaceWith: inUse ? replaceWith : undefined })
      toast.success('Categoria excluída.')
      close(false)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <AlertDialog open={!!category} onOpenChange={close}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Excluir "{category?.name}"?</AlertDialogTitle>
          <AlertDialogDescription>
            {isLast
              ? 'Esta é a única categoria deste tipo. Crie outra antes de excluir.'
              : inUse
                ? `Ela está em ${category?.usageCount} lançamento(s). Escolha para qual categoria eles serão movidos.`
                : 'Nenhum lançamento usa esta categoria.'}
          </AlertDialogDescription>
        </AlertDialogHeader>

        {inUse && !isLast && (
          <Field>
            <FieldLabel htmlFor="replace-with">Mover lançamentos para</FieldLabel>
            <Select value={replaceWith} onValueChange={setReplaceWith}>
              <SelectTrigger id="replace-with" className="w-full">
                <SelectValue placeholder="Selecione a categoria" />
              </SelectTrigger>
              <SelectContent>
                {alternatives.map((c) => (
                  <SelectItem key={c.id} value={c.id}>
                    <ColorDot color={c.color} />
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        )}

        <AlertDialogFooter>
          <AlertDialogCancel>Cancelar</AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={confirm}
            disabled={isLast || (inUse && !replaceWith) || remove.isPending}
          >
            {remove.isPending && <Loader2Icon className="animate-spin" />}
            {inUse ? 'Mover e excluir' : 'Excluir'}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
