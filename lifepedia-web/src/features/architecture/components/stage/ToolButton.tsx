import type { LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type Props = React.ComponentProps<'button'> & { icon: LucideIcon; label: string; active?: boolean }

/** Botão redondo da barra da maquete (só ícone; o nome vai no `title` e no `aria-label`). */
export function ToolButton({ icon: Icon, label, active, className, ...rest }: Props) {
  return (
    <Button type="button" size="icon-sm" variant={active ? 'default' : 'ghost'} aria-label={label} title={label}
      aria-pressed={active} className={cn('rounded-full', className)} {...rest}>
      <Icon />
    </Button>
  )
}
