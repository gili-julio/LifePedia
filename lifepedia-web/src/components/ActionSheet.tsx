import type { LucideIcon } from 'lucide-react'
import { useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Drawer, DrawerClose, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from '@/components/ui/drawer'
import { cn } from '@/lib/utils'

export type SheetAction = {
  label: string
  icon?: LucideIcon
  onSelect: () => void
  destructive?: boolean
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  actions: SheetAction[]
}

/** Folha de opções que sobe da base da tela (mobile). A ação roda depois que a folha fecha. */
export function ActionSheet({ open, onOpenChange, title, description, actions }: Props) {
  const pending = useRef<(() => void) | null>(null)

  function runPending() {
    const run = pending.current
    pending.current = null
    run?.()
  }

  function select(action: SheetAction) {
    pending.current = action.onSelect
    onOpenChange(false)
    // Fallback caso o fim da animação não seja notificado (ex.: movimento reduzido).
    setTimeout(runPending, 600)
  }

  return (
    <Drawer open={open} onOpenChange={onOpenChange} onAnimationEnd={(isOpen) => !isOpen && runPending()}>
      <DrawerContent>
        <DrawerHeader className="text-left">
          <DrawerTitle className="truncate">{title}</DrawerTitle>
          {description && <DrawerDescription>{description}</DrawerDescription>}
        </DrawerHeader>
        <div className="grid gap-1 px-2 pb-[max(1rem,env(safe-area-inset-bottom))]">
          {actions.map(({ label, icon: Icon, destructive, ...action }) => (
            <button
              key={label}
              type="button"
              onClick={() => select({ label, destructive, ...action })}
              className={cn(
                'flex h-12 items-center gap-3 rounded-lg px-3 text-left text-base transition-colors hover:bg-muted',
                'focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none',
                destructive && 'text-destructive',
              )}
            >
              {Icon && <Icon className="size-5" />}
              {label}
            </button>
          ))}
          <DrawerClose asChild>
            <Button variant="outline" className="mt-2 h-11">
              Cancelar
            </Button>
          </DrawerClose>
        </div>
      </DrawerContent>
    </Drawer>
  )
}
