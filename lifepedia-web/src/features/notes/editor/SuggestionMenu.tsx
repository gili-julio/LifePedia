import type { LucideIcon } from 'lucide-react'
import { forwardRef, useImperativeHandle, useState } from 'react'
import { cn } from '@/lib/utils'

export type SuggestionItem = { id: string; label: string; hint?: string; icon?: LucideIcon; emoji?: string | null }

export type SuggestionMenuHandle = { onKeyDown: (event: KeyboardEvent) => boolean }

type Props = { items: SuggestionItem[]; command: (item: SuggestionItem) => void; empty: string }

/** Lista que abre no cursor (setas, Enter, Tab). Recomeça a cada abertura (é recriada pelo editor). */
export const SuggestionMenu = forwardRef<SuggestionMenuHandle, Props>(function SuggestionMenu({ items, command, empty }, ref) {
  const [selected, setSelected] = useState(0)
  const [lastItems, setLastItems] = useState(items)
  if (lastItems !== items) {
    setLastItems(items)
    setSelected(0)
  }

  useImperativeHandle(ref, () => ({
    onKeyDown: (event) => {
      if (items.length === 0) return false
      if (event.key === 'ArrowDown') {
        setSelected((s) => (s + 1) % items.length)
        return true
      }
      if (event.key === 'ArrowUp') {
        setSelected((s) => (s - 1 + items.length) % items.length)
        return true
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        command(items[selected])
        return true
      }
      return false
    },
  }), [items, selected, command])

  return (
    <div role="listbox" aria-label="Sugestões"
      className="max-h-72 w-64 overflow-y-auto rounded-xl border bg-popover p-1 text-popover-foreground shadow-md">
      {items.length === 0 ? <p className="px-2 py-1.5 text-sm text-muted-foreground">{empty}</p> : items.map((item, i) => (
        <button key={item.id} type="button" role="option" aria-selected={i === selected}
          onMouseDown={(event) => event.preventDefault()} onClick={() => command(item)}
          onMouseEnter={() => setSelected(i)}
          className={cn('flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm',
            i === selected && 'bg-accent text-accent-foreground')}>
          {item.icon ? <item.icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
            : <span className="w-4 shrink-0 text-center" aria-hidden>{item.emoji ?? '📄'}</span>}
          <span className="min-w-0 flex-1 truncate">{item.label}</span>
          {item.hint && <span className="shrink-0 text-xs text-muted-foreground">{item.hint}</span>}
        </button>
      ))}
    </div>
  )
})
