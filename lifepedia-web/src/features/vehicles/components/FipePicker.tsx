import { Loader2Icon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { useMoney } from '@/lib/hide-values'
import { useFipeBrands, useFipeModels, useFipePrice, useFipeYears } from '../api'
import type { FipeKind, FipeOption, FipeRef } from '../types'

type Props = {
  kind: FipeKind
  value: FipeRef | null
  onChange: (ref: FipeRef | null, names?: { brand: string; model: string; year: number | null }) => void
}

/** Marca, modelo e ano na Tabela FIPE (listas da API, que consulta a FIPE e guarda por 1 dia). */
export function FipePicker({ kind, value, onChange }: Props) {
  const { formatCurrency } = useMoney()
  const [brand, setBrand] = useState(value?.kind === kind ? value.brand : '')
  const [model, setModel] = useState(value?.kind === kind ? value.model : '')
  const [year, setYear] = useState(value?.kind === kind ? value.year : '')
  const [filter, setFilter] = useState('')
  const brands = useFipeBrands(kind)
  const models = useFipeModels(kind, brand || null)
  const years = useFipeYears(kind, brand || null, model || null)
  const price = useFipePrice(kind, brand || null, model || null, year || null)
  const error = [brands.error, models.error, years.error, price.error].find(Boolean)
  const shownModels = (models.data ?? []).filter((m) => !filter || m.name.toLowerCase().includes(filter.toLowerCase()))

  function pick(nextYear: string) {
    setYear(nextYear)
    const b = brands.data?.find((o) => o.code === brand)?.name ?? ''
    const m = models.data?.find((o) => o.code === model)?.name ?? ''
    const y = Number(years.data?.find((o) => o.code === nextYear)?.name.slice(0, 4))
    onChange({ kind, brand, model, year: nextYear }, { brand: b, model: m, year: Number.isFinite(y) && y < 3000 ? y : null })
  }

  function clear() {
    setBrand('')
    setModel('')
    setYear('')
    setFilter('')
    onChange(null)
  }

  return (
    <div className="space-y-3 rounded-2xl border p-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium">Tabela FIPE (opcional)</p>
        {(brand || value) && <Button type="button" size="sm" variant="ghost" onClick={clear}><XIcon /> Tirar</Button>}
      </div>
      <Field>
        <FieldLabel htmlFor="fipe-brand">Marca</FieldLabel>
        <OptionSelect id="fipe-brand" options={brands.data} loading={brands.isLoading} value={brand} placeholder="Escolha a marca"
          onChange={(v) => { setBrand(v); setModel(''); setYear(''); setFilter('') }} />
      </Field>
      {brand && (
        <Field>
          <FieldLabel htmlFor="fipe-model">Modelo</FieldLabel>
          <Input placeholder="Filtrar modelos (ex.: 1.0, Flex)" value={filter} className="text-base md:text-sm"
            onChange={(e) => setFilter(e.target.value)} aria-label="Filtrar modelos" />
          <OptionSelect id="fipe-model" options={shownModels} loading={models.isLoading} value={model}
            placeholder={shownModels.length ? `Escolha entre ${shownModels.length} modelos` : 'Nenhum modelo com esse filtro'}
            onChange={(v) => { setModel(v); setYear('') }} />
        </Field>
      )}
      {model && (
        <Field>
          <FieldLabel htmlFor="fipe-year">Ano e combustível</FieldLabel>
          <OptionSelect id="fipe-year" options={years.data} loading={years.isLoading} value={year} placeholder="Escolha o ano"
            onChange={pick} />
        </Field>
      )}
      {year && price.data?.value != null && (
        <FieldDescription>
          Valor na FIPE: <strong className="text-foreground">{formatCurrency(price.data.value)}</strong>
          {price.data.month ? ` (${price.data.month})` : ''}. Atualizado sozinho todo mês.
        </FieldDescription>
      )}
      {error && <p className="text-sm text-destructive">{error instanceof ApiError ? error.message : 'FIPE indisponível agora.'}</p>}
    </div>
  )
}

function OptionSelect({ id, options, loading, value, placeholder, onChange }: {
  id: string
  options: FipeOption[] | undefined
  loading: boolean
  value: string
  placeholder: string
  onChange: (value: string) => void
}) {
  if (loading) return <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2Icon className="size-4 animate-spin" /> Carregando…</p>
  return (
    <Select key={options?.length ?? 0} value={value} onValueChange={(v) => { if (v) onChange(v) }}>
      <SelectTrigger id={id} className="w-full"><SelectValue placeholder={placeholder} /></SelectTrigger>
      <SelectContent className="max-h-72">
        {(options ?? []).map((o) => <SelectItem key={o.code} value={o.code}>{o.name}</SelectItem>)}
      </SelectContent>
    </Select>
  )
}
