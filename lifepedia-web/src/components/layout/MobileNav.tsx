import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { NavLink, useLocation } from 'react-router'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { NAV_SECTIONS } from './nav-items'

/** Breakpoint `lg` do Tailwind. */
const DESKTOP_QUERY = '(min-width: 64rem)'

/** Menu hambúrguer animado para telas menores que `lg`. */
export function MobileNav() {
  const [open, setOpen] = useState(false)
  const panelId = useId()
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  // Fecha ao trocar de rota.
  const { pathname } = useLocation()
  const [lastPath, setLastPath] = useState(pathname)
  if (pathname !== lastPath) {
    setLastPath(pathname)
    setOpen(false)
  }

  useEffect(() => {
    if (!open) return

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    const desktop = window.matchMedia(DESKTOP_QUERY)
    const onBreakpoint = () => desktop.matches && setOpen(false)

    document.addEventListener('keydown', onKeyDown)
    desktop.addEventListener('change', onBreakpoint)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panelRef.current?.querySelector<HTMLElement>('a')?.focus({ preventScroll: true })

    return () => {
      document.removeEventListener('keydown', onKeyDown)
      desktop.removeEventListener('change', onBreakpoint)
      document.body.style.overflow = previousOverflow
    }
  }, [open])

  const close = () => setOpen(false)
  let index = 0

  return (
    <>
      <Button
        ref={buttonRef}
        variant="ghost"
        size="icon"
        className="lg:hidden"
        aria-label={open ? 'Fechar menu' : 'Abrir menu'}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
      >
        <span aria-hidden className="relative block h-3.5 w-5">
          <span
            className={cn(
              'absolute top-0 left-0 h-0.5 w-5 rounded-full bg-current transition-transform duration-300 ease-out motion-reduce:transition-none',
              open && 'translate-y-1.5 rotate-45',
            )}
          />
          <span
            className={cn(
              'absolute top-1.5 left-0 h-0.5 w-5 rounded-full bg-current transition-opacity duration-200 motion-reduce:transition-none',
              open && 'opacity-0',
            )}
          />
          <span
            className={cn(
              'absolute top-3 left-0 h-0.5 w-5 rounded-full bg-current transition-transform duration-300 ease-out motion-reduce:transition-none',
              open && '-translate-y-1.5 -rotate-45',
            )}
          />
        </span>
      </Button>

      {createPortal(
        <>
          <div
            aria-hidden
            onClick={close}
            className={cn(
              'fixed inset-x-0 top-14 bottom-0 z-30 bg-black/40 transition-opacity duration-300 motion-reduce:transition-none lg:hidden',
              open ? 'opacity-100' : 'pointer-events-none opacity-0',
            )}
          />
          <div
            ref={panelRef}
            id={panelId}
            inert={!open}
            className={cn(
              'fixed inset-x-0 top-14 z-40 grid border-b bg-background shadow-lg lg:hidden',
              'transition-[grid-template-rows,opacity,box-shadow] duration-300 ease-out motion-reduce:transition-none',
              open ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] border-transparent opacity-0 shadow-none',
            )}
          >
            <div className="min-h-0 overflow-hidden">
              <nav aria-label="Navegação principal" className="mx-auto max-w-6xl space-y-4 px-4 py-4">
                {NAV_SECTIONS.map((section) => (
                  <div key={section.title}>
                    <p className="px-3 pb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">
                      {section.title}
                    </p>
                    <ul className="grid gap-1">
                      {section.items.map((item) => {
                        const delay = open ? 60 + index++ * 40 : 0
                        return (
                          <li
                            key={item.to}
                            style={{ transitionDelay: `${delay}ms` }}
                            className={cn(
                              'transition-[opacity,translate] duration-300 ease-out motion-reduce:transition-none',
                              open ? 'translate-y-0 opacity-100' : '-translate-y-2 opacity-0',
                            )}
                          >
                            <NavLink
                              to={item.to}
                              end={item.end}
                              onClick={close}
                              className={({ isActive }) =>
                                cn(
                                  'flex h-11 items-center gap-3 rounded-lg px-3 text-sm transition-colors',
                                  'focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none',
                                  isActive
                                    ? 'bg-muted font-medium text-foreground'
                                    : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground',
                                )
                              }
                            >
                              <item.icon className="size-4" />
                              {item.label}
                            </NavLink>
                          </li>
                        )
                      })}
                    </ul>
                  </div>
                ))}
              </nav>
            </div>
          </div>
        </>,
        document.body,
      )}
    </>
  )
}
