import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * `onOpenAutoFocus` de dialog: no toque foca o próprio dialog em vez do 1º campo (o teclado cobriria o formulário);
 * `autoFocus` explícito continua valendo.
 */
export function skipAutoFocusOnTouch(event: Event) {
  if (!window.matchMedia('(pointer: coarse)').matches) return
  event.preventDefault()
  const content = event.target as HTMLElement
  content.focus()
}
