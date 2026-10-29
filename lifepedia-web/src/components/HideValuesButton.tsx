import { EyeIcon, EyeOffIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { setHideValues, useHideValues } from '@/lib/hide-values'

/** Olhinho: oculta ou mostra os valores em dinheiro em todo o app. */
export function HideValuesButton({ size = 'icon-sm', variant = 'ghost' }: { size?: 'icon' | 'icon-sm'; variant?: 'ghost' | 'outline' }) {
  const hidden = useHideValues()
  return (
    <Button variant={variant} size={size} aria-label="Ocultar valores" aria-pressed={hidden}
      title={hidden ? 'Mostrar valores' : 'Ocultar valores'} onClick={() => setHideValues(!hidden)}>
      {hidden ? <EyeOffIcon /> : <EyeIcon />}
    </Button>
  )
}
