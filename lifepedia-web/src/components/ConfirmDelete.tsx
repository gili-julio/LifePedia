import { Trash2Icon } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'

type Props = {
  title: string
  description: string
  onConfirm: () => void
  /** Texto do botão de confirmar (padrão: Excluir) */
  confirmLabel?: string
  /** Controlado (sem botão próprio), ex.: aberto por um ActionSheet. */
  open?: boolean
  onOpenChange?: (open: boolean) => void
}

export function ConfirmDelete({ title, description, onConfirm, open, onOpenChange, confirmLabel = 'Excluir' }: Props) {
  const controlled = open !== undefined
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      {!controlled && (
        <AlertDialogTrigger asChild>
          <Button variant="ghost" size="icon-sm" aria-label="Excluir">
            <Trash2Icon />
          </Button>
        </AlertDialogTrigger>
      )}
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancelar</AlertDialogCancel>
          <AlertDialogAction variant="destructive" onClick={onConfirm}>
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
