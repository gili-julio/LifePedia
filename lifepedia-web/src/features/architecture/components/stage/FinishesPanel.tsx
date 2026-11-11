import { RotateCcwIcon, SaveIcon, SearchIcon, Trash2Icon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Slider } from '@/components/ui/slider'
import { cn } from '@/lib/utils'
import type { MaterialOverride, MaterialOverrides, Variant } from '../../types'
import type { MaterialInfo } from '../../viewer/materials'
import { TEXTURES, texturePreview, textureById } from '../../viewer/textures'
import { StagePanel } from './StagePanel'

/** Cores prontas para testar em paredes e móveis. */
const PRESETS = [
  { name: 'Branco gelo', color: '#f4f1ea' },
  { name: 'Off-white', color: '#e9e2d4' },
  { name: 'Areia', color: '#d6c6aa' },
  { name: 'Cinza claro', color: '#c8c8c4' },
  { name: 'Grafite', color: '#4a4b4d' },
  { name: 'Preto', color: '#202020' },
  { name: 'Verde sálvia', color: '#9cae8a' },
  { name: 'Verde oliva', color: '#6b7046' },
  { name: 'Azul petróleo', color: '#2f5d6b' },
  { name: 'Azul céu', color: '#a9c6d8' },
  { name: 'Terracota', color: '#b5653d' },
  { name: 'Rosa queimado', color: '#c99a94' },
  { name: 'Mostarda', color: '#d2a03a' },
  { name: 'Madeira', color: '#a5764b' },
]

const ORIGINAL = 'original'

type Props = {
  materials: MaterialInfo[]
  selected: number | null
  onSelect: (index: number | null) => void
  onHighlight: (index: number | null) => void
  draft: MaterialOverrides
  onChange: (index: number, override: MaterialOverride | null) => void
  variants: Variant[]
  variantId: string | null
  onPickVariant: (id: string | null) => void
  dirty: boolean
  /** Sem ele, dá para testar mas não salvar */
  onSave?: (name: string, asNew: boolean) => Promise<boolean>
  onDelete?: (id: string) => void
  onClose: () => void
}

/** Provador de acabamentos: paleta dos materiais, cor ou textura por material e variantes salvas. */
export function FinishesPanel(props: Props) {
  const { materials, selected, onSelect, onHighlight, draft, onChange, variants, variantId, onPickVariant, dirty, onSave,
    onDelete, onClose } = props
  const [query, setQuery] = useState('')
  const [naming, setNaming] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const variant = variants.find((v) => v.id === variantId) ?? null
  const current = materials.find((m) => m.index === selected) ?? null

  const list = useMemo(() => {
    const term = query.trim().toLowerCase()
    return [...materials].filter((m) => !term || m.name.toLowerCase().includes(term)).sort((a, b) => b.area - a.area)
  }, [materials, query])

  async function save(asNew: boolean, name: string) {
    if (!onSave || !name.trim()) return
    setSaving(true)
    try {
      if (await onSave(name.trim(), asNew)) setNaming(null)
    } finally {
      setSaving(false)
    }
  }

  return (
    <StagePanel title="Acabamentos" onClose={onClose}>
      <div className="space-y-2">
        <Label htmlFor="variant">Variante</Label>
        <Select value={variantId ?? ORIGINAL} onValueChange={(v) => onPickVariant(v === ORIGINAL ? null : v)}>
          <SelectTrigger id="variant" className="w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ORIGINAL}>Original (como no arquivo)</SelectItem>
            {variants.map((v) => <SelectItem key={v.id} value={v.id}>{v.name}</SelectItem>)}
          </SelectContent>
        </Select>
        {dirty && (
          <p className="text-xs text-muted-foreground">
            {onSave ? 'Mudanças ainda não salvas.' : 'Você pode testar à vontade; só quem edita o projeto salva variantes.'}
          </p>
        )}
        {onSave && naming == null && (
          <div className="flex flex-wrap gap-1.5">
            {variant && dirty && (
              <Button size="sm" disabled={saving} onClick={() => save(false, variant.name)}><SaveIcon /> Salvar</Button>
            )}
            {dirty && <Button size="sm" variant="outline" onClick={() => setNaming('')}>Salvar como nova…</Button>}
            {variant && onDelete && (
              <Button size="sm" variant="ghost" className="text-destructive" onClick={() => onDelete(variant.id)}>
                <Trash2Icon /> Excluir variante
              </Button>
            )}
          </div>
        )}
        {naming != null && (
          <form className="flex gap-2" onSubmit={(e) => {
            e.preventDefault()
            void save(true, naming)
          }}>
            <Input autoFocus value={naming} maxLength={60} placeholder="Ex.: Piso claro e parede verde"
              className="text-base md:text-sm" aria-label="Nome da variante" onChange={(e) => setNaming(e.target.value)} />
            <Button type="submit" size="sm" disabled={saving || !naming.trim()}>Salvar</Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setNaming(null)}>Cancelar</Button>
          </form>
        )}
      </div>

      {current ? (
        <MaterialEditor material={current} override={draft[String(current.index)] ?? null}
          onChange={(o) => onChange(current.index, o)} onBack={() => onSelect(null)} />
      ) : (
        <p className="rounded-xl bg-muted/60 p-3 text-xs text-muted-foreground">
          Toque numa parede, piso ou móvel da maquete (ou escolha na lista) para trocar a cor ou a textura.
        </p>
      )}

      <div className="space-y-2">
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar material"
            className="pl-8 text-base md:text-sm" aria-label="Buscar material" />
        </div>
        <ul className="space-y-0.5" onMouseLeave={() => onHighlight(selected)}>
          {list.map((m) => {
            const changed = !!draft[String(m.index)]
            return (
              <li key={m.index}>
                <button type="button" onClick={() => onSelect(m.index)} onMouseEnter={() => onHighlight(m.index)}
                  onFocus={() => onHighlight(m.index)}
                  className={cn('flex w-full items-center gap-2 rounded-lg px-1.5 py-1 text-left text-sm hover:bg-muted',
                    m.index === selected && 'bg-muted')}>
                  <Swatch material={m} override={draft[String(m.index)]} />
                  <span className="min-w-0 flex-1 truncate">{m.name}</span>
                  {changed && <Badge variant="secondary" className="shrink-0">alterado</Badge>}
                  <span className="shrink-0 text-xs text-muted-foreground tabular-nums">{area(m.area)}</span>
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </StagePanel>
  )
}

type EditorProps = {
  material: MaterialInfo
  override: MaterialOverride | null
  onChange: (override: MaterialOverride | null) => void
  onBack: () => void
}

function MaterialEditor({ material, override, onChange, onBack }: EditorProps) {
  const texture = override?.texture ? textureById(override.texture) : null
  const color = override?.color ?? (override?.texture ? '#ffffff' : material.color)
  const groups = useMemo(() => [...new Set(TEXTURES.map((t) => t.group))], [])

  const set = (patch: Partial<MaterialOverride>) =>
    onChange({ color: override?.color ?? null, texture: override?.texture ?? null, scale: override?.scale ?? null, ...patch })

  return (
    <div className="space-y-3 rounded-xl border p-3">
      <div className="flex items-center gap-2">
        <Swatch material={material} override={override ?? undefined} large />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{material.name}</p>
          <p className="text-xs text-muted-foreground">{area(material.area)} nesta maquete</p>
        </div>
        <Button size="xs" variant="ghost" onClick={onBack}>Lista</Button>
      </div>

      <div className="space-y-2">
        <Label>Cor</Label>
        <div className="flex flex-wrap gap-1.5">
          {PRESETS.map((p) => (
            <button key={p.color} type="button" title={p.name} aria-label={p.name} onClick={() => set({ color: p.color })}
              className={cn('relative size-7 rounded-full border shadow-sm after:absolute after:-inset-1',
                override?.color === p.color && 'ring-2 ring-primary ring-offset-2 ring-offset-background')}
              style={{ backgroundColor: p.color }} />
          ))}
          <label className="relative flex size-7 cursor-pointer items-center justify-center overflow-hidden rounded-full border text-xs"
            title="Outra cor">
            <span aria-hidden>+</span>
            <input type="color" value={color} className="absolute inset-0 cursor-pointer opacity-0" aria-label="Escolher outra cor"
              onChange={(e) => set({ color: e.target.value })} />
          </label>
        </div>
        {texture && <p className="text-xs text-muted-foreground">Com textura, a cor tinge a textura (branco = original dela).</p>}
      </div>

      <div className="space-y-2">
        <Label>Textura</Label>
        {groups.map((group) => (
          <div key={group} className="space-y-1">
            <p className="text-xs text-muted-foreground">{group}</p>
            <div className="grid grid-cols-4 gap-1.5">
              {TEXTURES.filter((t) => t.group === group).map((t) => (
                <button key={t.id} type="button" title={t.name} onClick={() => set({ texture: t.id, scale: t.scale, color: null })}
                  className={cn('overflow-hidden rounded-lg border text-left', override?.texture === t.id && 'ring-2 ring-primary')}>
                  <img src={texturePreview(t.id)} alt="" className="aspect-square w-full object-cover" />
                  <span className="block truncate px-1 py-0.5 text-[10px] leading-tight">{t.name}</span>
                </button>
              ))}
            </div>
          </div>
        ))}
        {texture && (
          <div className="space-y-1.5 pt-1">
            <div className="flex justify-between text-xs">
              <span>Tamanho da peça</span>
              <span className="tabular-nums">{(override?.scale ?? texture.scale).toLocaleString('pt-BR')} m</span>
            </div>
            <Slider min={0.1} max={4} step={0.05} value={[override?.scale ?? texture.scale]} aria-label="Tamanho da textura"
              onValueChange={([v]) => set({ scale: v })} />
          </div>
        )}
      </div>

      {override && (
        <Button size="sm" variant="outline" className="w-full" onClick={() => onChange(null)}>
          <RotateCcwIcon /> Voltar ao original
        </Button>
      )}
    </div>
  )
}

function Swatch({ material, override, large }: { material: MaterialInfo; override?: MaterialOverride; large?: boolean }) {
  const size = large ? 'size-10' : 'size-6'
  const src = override?.texture ? texturePreview(override.texture) : override?.color ? null : material.preview
  return src ? <img src={src} alt="" className={cn(size, 'shrink-0 rounded-md border object-cover')} />
    : <span className={cn(size, 'shrink-0 rounded-md border')} style={{ backgroundColor: override?.color ?? material.color }} />
}

function area(m2: number) {
  return `${m2.toLocaleString('pt-BR', { maximumFractionDigits: m2 < 10 ? 1 : 0 })} m²`
}
