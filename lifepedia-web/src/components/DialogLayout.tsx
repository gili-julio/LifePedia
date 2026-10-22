import type { ComponentProps } from 'react'
import { DialogContent } from '@/components/ui/dialog'
import { cn, skipAutoFocusOnTouch } from '@/lib/utils'

/** Dialog com altura máxima da tela: cabeçalho e rodapé fixos, `DialogBody` rola. */
export function DialogScrollContent({ className, ...props }: ComponentProps<typeof DialogContent>) {
  return (
    <DialogContent
      className={cn('max-h-[calc(100dvh-2rem)] grid-rows-[auto_minmax(0,1fr)_auto]', className)}
      onOpenAutoFocus={skipAutoFocusOnTouch}
      {...props}
    />
  )
}

/** Miolo rolável do `DialogScrollContent`. */
export function DialogBody({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('-mx-4 -my-1 space-y-4 overflow-y-auto overscroll-contain px-4 py-1', className)} {...props} />
}
