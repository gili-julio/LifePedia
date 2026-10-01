import { CheckIcon } from 'lucide-react'
import { COLOR_SWATCHES } from '@/lib/colors'
import { cn } from '@/lib/utils'

type Props = {
  value: string
  onChange: (color: string) => void
}

/** Paleta sugerida + cor livre. */
export function ColorPicker({ value, onChange }: Props) {
  const normalized = value.toLowerCase()
  const isCustom = !COLOR_SWATCHES.includes(normalized)

  return (
    <div className="flex flex-wrap items-center gap-2" role="radiogroup" aria-label="Cor">
      {COLOR_SWATCHES.map((color) => (
        <button
          key={color}
          type="button"
          role="radio"
          aria-checked={normalized === color}
          aria-label={color}
          onClick={() => onChange(color)}
          className={cn(
            'flex size-7 items-center justify-center rounded-full ring-offset-2 ring-offset-background transition',
            'focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
            normalized === color && 'ring-2 ring-foreground',
          )}
          style={{ background: color }}
        >
          {normalized === color && <CheckIcon className="size-4 text-white" />}
        </button>
      ))}
      <label
        className={cn(
          'relative flex h-7 cursor-pointer items-center gap-1.5 rounded-full border px-2 text-xs text-muted-foreground',
          isCustom && 'border-foreground text-foreground',
        )}
        title="Escolher outra cor"
      >
        <span className="size-4 rounded-full border" style={{ background: value }} />
        Outra
        <input
          type="color"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="absolute inset-0 cursor-pointer opacity-0"
          aria-label="Escolher outra cor"
        />
      </label>
    </div>
  )
}
