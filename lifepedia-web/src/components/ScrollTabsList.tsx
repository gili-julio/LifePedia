import { useEffect, useRef, type ReactNode } from 'react'
import { TabsList } from '@/components/ui/tabs'

/** Abas que rolam dentro da faixa no celular (a página não ganha rolagem lateral), com a ativa sempre à vista. */
export function ScrollTabsList({ value, children }: { value: string; children: ReactNode }) {
  const strip = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = strip.current
    const active = el?.querySelector<HTMLElement>('[role="tab"][data-state="active"]')
    if (!el || !active) return
    // Só rola a faixa na horizontal; scrollIntoView também mexeria na rolagem da página.
    const box = el.getBoundingClientRect()
    const tab = active.getBoundingClientRect()
    const gutter = 16
    if (tab.left < box.left + gutter) el.scrollLeft += tab.left - box.left - gutter
    else if (tab.right > box.right - gutter) el.scrollLeft += tab.right - box.right + gutter
  }, [value])

  return (
    <div ref={strip} className="-mx-4 overflow-x-auto px-4 [scrollbar-width:none] sm:mx-0 sm:px-0">
      <TabsList className="w-max">{children}</TabsList>
    </div>
  )
}
