import { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { EntryType } from '../types'
import { useCategoryLookup } from '../use-category-lookup'
import { ColorDot } from './CategoryBadge'

type Props = {
  id?: string
  type: EntryType
  /** id da categoria ou '' */
  value: string
  onChange: (categoryId: string) => void
  invalid?: boolean
  className?: string
  size?: 'sm' | 'default'
}

export function CategorySelect({ id, type, value, onChange, invalid, className, size }: Props) {
  const { ofType } = useCategoryLookup()
  const options = ofType(type)
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger id={id} size={size} className={className ?? 'w-full'} aria-invalid={invalid}>
        <SelectValue placeholder="Selecione" />
      </SelectTrigger>
      <SelectContent>
        {options.map((category) => (
          <SelectItem key={category.id} value={category.id}>
            <ColorDot color={category.color} />
            {category.name}
          </SelectItem>
        ))}
        {!options.length && (
          <>
            <SelectSeparator />
            <p className="px-2 py-1.5 text-xs text-muted-foreground">Nenhuma categoria. Crie em Financeiro → Categorias.</p>
          </>
        )}
      </SelectContent>
    </Select>
  )
}
