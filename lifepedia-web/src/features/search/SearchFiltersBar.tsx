import { ChevronDownIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import type { AppModule } from '@/features/auth/types'
import { useGroup } from '@/features/groups/api'
import { amountToInput, parseAmount } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { KINDS, MODULES, PERIODS } from './kinds'
import type { ParsedQuery } from './query'
import type { Period, SearchFilters } from './types'

type Props = {
  filters: SearchFilters
  onChange: (filters: SearchFilters) => void
  /** Valor digitado no texto ("> 100"), mostrado como filtro. */
  parsed: ParsedQuery
  hidden: ReadonlySet<AppModule>
  /** Grupo da página aberta (permite voltar a buscar nele). */
  routeGroupId?: string
}

/** Filtros da busca geral numa linha: grupo, módulos, período, entrada/saída e valor. */
export function SearchFiltersBar({ filters, onChange, parsed, hidden, routeGroupId }: Props) {
  const set = (patch: Partial<SearchFilters>) => onChange({ ...filters, ...patch })
  const modules = MODULES.filter((m) => !hidden.has(m.id))
  const group = useGroup(filters.groupId ?? routeGroupId ?? '')
  const groupName = (filters.groupId ?? routeGroupId) ? group.data?.name : undefined
  const period = PERIODS.find((p) => p.id === filters.period)!
  const moduleLabel = filters.modules.length === 1 ? MODULES.find((m) => m.id === filters.modules[0])?.label
    : filters.modules.length ? `${filters.modules.length} módulos` : 'Tudo'

  return (
    <div className="flex gap-1.5 overflow-x-auto px-3 py-2 [scrollbar-width:none]">
      {routeGroupId && (
        <Chip active={!!filters.groupId} onClick={() => set({ groupId: filters.groupId ? undefined : routeGroupId })}
          title={filters.groupId ? 'Buscar em tudo' : 'Buscar só neste grupo'}>
          {filters.groupId ? <>Só em {groupName ?? 'este grupo'} <XIcon /></> : <>Só em {groupName ?? 'este grupo'}</>}
        </Chip>
      )}
      {filters.kinds?.length ? (
        <Chip active onClick={() => set({ kinds: undefined })} title="Voltar a todos os tipos">
          {KINDS[filters.kinds[0]].label} <XIcon />
        </Chip>
      ) : (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Chip active={filters.modules.length > 0}>{moduleLabel} <ChevronDownIcon /></Chip>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" className="max-h-72">
            {modules.map((m) => (
              <DropdownMenuCheckboxItem key={m.id} checked={filters.modules.includes(m.id)} onSelect={(e) => e.preventDefault()}
                onCheckedChange={(checked) => set({ modules: checked ? [...filters.modules, m.id] : filters.modules.filter((x) => x !== m.id) })}>
                {m.label}
              </DropdownMenuCheckboxItem>
            ))}
            {filters.modules.length > 0 && (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => set({ modules: [] })}>Buscar em tudo</DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Chip active={filters.period !== 'all'}>{period.label} <ChevronDownIcon /></Chip>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          <DropdownMenuRadioGroup value={filters.period} onValueChange={(v) => set({ period: v as Period })}>
            {PERIODS.map((p) => <DropdownMenuRadioItem key={p.id} value={p.id}>{p.label}</DropdownMenuRadioItem>)}
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Chip active={!!filters.type}>
            {filters.type === 'INCOME' ? 'Entradas' : filters.type === 'EXPENSE' ? 'Saídas' : 'Entradas e saídas'} <ChevronDownIcon />
          </Chip>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          <DropdownMenuRadioGroup value={filters.type ?? 'ALL'} onValueChange={(v) => set({ type: v === 'ALL' ? undefined : (v as SearchFilters['type']) })}>
            <DropdownMenuRadioItem value="ALL">Entradas e saídas</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="EXPENSE">Só saídas</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="INCOME">Só entradas</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
      <AmountFilter filters={filters} parsed={parsed} onChange={(min, max) => set({ min, max })} />
    </div>
  )
}

function AmountFilter({ filters, parsed, onChange }: {
  filters: SearchFilters
  parsed: ParsedQuery
  onChange: (min?: number, max?: number) => void
}) {
  const { formatCurrency } = useMoney()
  const [open, setOpen] = useState(false)
  const [min, setMin] = useState('')
  const [max, setMax] = useState('')
  const low = filters.min ?? parsed.min
  const high = filters.max ?? parsed.max
  const label = low !== undefined && high !== undefined
    ? low === high ? formatCurrency(low) : `${formatCurrency(low)} a ${formatCurrency(high)}`
    : low !== undefined ? `Acima de ${formatCurrency(low)}` : high !== undefined ? `Até ${formatCurrency(high)}` : 'Valor'

  return (
    <Popover open={open} onOpenChange={(next) => {
      if (next) {
        setMin(low !== undefined ? amountToInput(low) : '')
        setMax(high !== undefined ? amountToInput(high) : '')
      }
      setOpen(next)
    }}>
      <PopoverTrigger asChild>
        <Chip active={low !== undefined || high !== undefined}>{label} <ChevronDownIcon /></Chip>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-64 space-y-3">
        <p className="text-xs text-muted-foreground">Também dá para digitar na busca: "&gt; 100", "&lt; 50", "100-200" ou "50,90".</p>
        <div className="flex items-center gap-2">
          <Input inputMode="decimal" placeholder="Mínimo" aria-label="Valor mínimo" value={min} onChange={(e) => setMin(e.target.value)} />
          <span className="text-muted-foreground">a</span>
          <Input inputMode="decimal" placeholder="Máximo" aria-label="Valor máximo" value={max} onChange={(e) => setMax(e.target.value)} />
        </div>
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="ghost" onClick={() => { onChange(undefined, undefined); setOpen(false) }}>Limpar</Button>
          <Button size="sm" onClick={() => {
            onChange(toValue(min), toValue(max))
            setOpen(false)
          }}>Aplicar</Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function toValue(raw: string) {
  const value = parseAmount(raw)
  return Number.isNaN(value) ? undefined : value
}

function Chip({ active, className, ...props }: React.ComponentProps<'button'> & { active?: boolean }) {
  return (
    <button type="button" {...props}
      className={cn('inline-flex h-7 shrink-0 items-center gap-1 rounded-full border px-2.5 text-xs whitespace-nowrap transition-colors',
        '[&_svg]:size-3.5 [&_svg]:opacity-70 hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
        active ? 'border-primary/40 bg-primary/10 text-foreground' : 'text-muted-foreground', className)} />
  )
}
