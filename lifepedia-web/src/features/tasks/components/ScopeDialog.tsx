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
import type { Scope } from '../types'

export type ScopeOption = { scope: Scope; label: string; hint?: string; disabled?: boolean }

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  options: ScopeOption[]
  onChoose: (scope: Scope) => void
  destructive?: boolean
}

/** Pergunta de tarefa repetida: só esta, esta e as próximas ou todas. */
export function ScopeDialog({ open, onOpenChange, title, description, options, onChoose, destructive }: Props) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          {description && <AlertDialogDescription>{description}</AlertDialogDescription>}
        </AlertDialogHeader>
        <div className="grid gap-2">
          {options.map((option) => (
            <Button key={option.scope} variant={destructive && option.scope === 'ALL' ? 'destructive' : 'outline'}
              disabled={option.disabled} className="h-auto flex-col items-start gap-0.5 py-2 text-left whitespace-normal"
              onClick={() => {
                onOpenChange(false)
                onChoose(option.scope)
              }}>
              <span>{option.label}</span>
              {option.hint && <span className="text-xs font-normal opacity-80">{option.hint}</span>}
            </Button>
          ))}
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancelar</AlertDialogCancel>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
