import { SearchIcon, XIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

type Props = {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
}

/** Campo de busca da lista: avisa a mudança depois de uma pausa na digitação; Esc limpa. */
export function ListSearch({ value, onChange, placeholder = 'Buscar na lista', className }: Props) {
  const [text, setText] = useState(value)
  const [synced, setSynced] = useState(value)
  const timer = useRef<number | undefined>(undefined)
  if (value !== synced) {
    setSynced(value)
    setText(value)
  }
  useEffect(() => () => window.clearTimeout(timer.current), [])

  const type = (next: string) => {
    setText(next)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => onChange(next), 250)
  }
  const clear = () => {
    window.clearTimeout(timer.current)
    setText('')
    onChange('')
  }

  return (
    <div className={cn('relative w-full sm:w-64', className)}>
      <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <Input
        type="search"
        value={text}
        onChange={(e) => type(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Escape' && text) {
            e.stopPropagation()
            clear()
          }
        }}
        placeholder={placeholder}
        aria-label={placeholder}
        className="pr-8 pl-8 [&::-webkit-search-cancel-button]:hidden"
        maxLength={100}
      />
      {text && (
        <Button type="button" variant="ghost" size="icon-xs" aria-label="Limpar busca"
          className="absolute top-1/2 right-1 -translate-y-1/2" onClick={clear}>
          <XIcon />
        </Button>
      )}
    </div>
  )
}

/** Lista filtrada sem resultado: avisa e oferece limpar a busca. */
export function NoMatches({ onClear }: { onClear: () => void }) {
  return (
    <div className="flex flex-col items-center gap-2 py-10 text-center">
      <p className="text-sm text-muted-foreground">Nada encontrado com essa busca.</p>
      <Button size="sm" variant="outline" onClick={onClear}><XIcon /> Limpar busca</Button>
    </div>
  )
}
